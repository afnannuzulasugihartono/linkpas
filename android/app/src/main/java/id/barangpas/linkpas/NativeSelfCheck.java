package id.barangpas.linkpas;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.content.pm.verify.domain.DomainVerificationManager;
import android.content.pm.verify.domain.DomainVerificationUserState;
import android.net.Uri;
import android.os.Build;

import androidx.browser.customtabs.CustomTabsService;

import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class NativeSelfCheck {
    static final String HOST = "linkpas.vercel.app";
    static final String EXPECTED_RELEASE_FINGERPRINT =
            "61:A0:47:4A:18:10:42:11:6C:15:25:AC:A2:E9:E6:A9:29:F8:9D:12:D1:3E:D2:9C:A0:BF:43:3E:00:6B:90:A6";

    private NativeSelfCheck() {}

    static JSONObject collect(Context context) {
        JSONObject diagnostics = new JSONObject();
        try {
            diagnostics.put("android_sdk", Build.VERSION.SDK_INT);

            SignerResult signer = readSigner(context);
            diagnostics.put("signer_state", signer.state);
            diagnostics.put("signer_match", signer.known ? signer.matches : JSONObject.NULL);

            AppLinksResult appLinks = readAppLinks(context);
            diagnostics.put("app_links_state", appLinks.state);
            diagnostics.put("link_handling_allowed",
                    appLinks.allowedKnown ? appLinks.linkHandlingAllowed : JSONObject.NULL);

            BrowserResult browser = readBrowserCapability(context);
            diagnostics.put("browser_provider",
                    NativeDiagnosticSanitizer.redact(browser.defaultProvider, 120));
            diagnostics.put("twa_capability_state", browser.capabilityState);
            diagnostics.put("twa_capable_provider",
                    NativeDiagnosticSanitizer.redact(browser.twaCapableProvider, 120));
        } catch (Exception ignored) {
            try {
                diagnostics.put("self_check_state", "collection_failed");
            } catch (Exception ignoredAgain) {
                // Keep diagnostics best effort only.
            }
        }
        return diagnostics;
    }

    static String mapDomainStateForSdk(int sdkInt, Integer rawState) {
        if (sdkInt < Build.VERSION_CODES.S) return "unsupported_api";
        if (rawState == null) return "missing_host";
        if (rawState == DomainVerificationUserState.DOMAIN_STATE_VERIFIED) return "verified";
        if (rawState == DomainVerificationUserState.DOMAIN_STATE_SELECTED) return "user_selected";
        if (rawState == DomainVerificationUserState.DOMAIN_STATE_NONE) return "none";
        return "unknown_state";
    }

    static String classifyTwaCapability(boolean querySucceeded,
                                        boolean defaultProviderSupports,
                                        boolean anyProviderSupports) {
        if (!querySucceeded) return "query_failed";
        if (defaultProviderSupports) return "default_provider_supported";
        if (anyProviderSupports) return "available_provider_only";
        return "not_advertised";
    }

    static boolean fingerprintMatches(String expected, String actual) {
        String normalizedExpected = normalizeFingerprint(expected);
        String normalizedActual = normalizeFingerprint(actual);
        return !normalizedExpected.isEmpty() && normalizedExpected.equals(normalizedActual);
    }

    static String normalizeFingerprint(String fingerprint) {
        if (fingerprint == null) return "";
        return fingerprint.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.US);
    }

    private static SignerResult readSigner(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageInfo info = packageManager.getPackageInfo(
                        context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                SigningInfo signingInfo = info.signingInfo;
                signatures = signingInfo == null ? null : signingInfo.getApkContentsSigners();
            } else {
                @SuppressWarnings("deprecation")
                PackageInfo info = packageManager.getPackageInfo(
                        context.getPackageName(), PackageManager.GET_SIGNATURES);
                @SuppressWarnings("deprecation")
                Signature[] legacySignatures = info.signatures;
                signatures = legacySignatures;
            }

            if (signatures == null || signatures.length == 0) {
                return new SignerResult("unavailable", false, false);
            }

            boolean matches = false;
            for (Signature signature : signatures) {
                if (signature == null) continue;
                String actual = sha256Fingerprint(signature.toByteArray());
                if (fingerprintMatches(EXPECTED_RELEASE_FINGERPRINT, actual)) {
                    matches = true;
                    break;
                }
            }
            return new SignerResult(matches ? "match" : "mismatch", true, matches);
        } catch (Exception ignored) {
            return new SignerResult("error", false, false);
        }
    }

    private static AppLinksResult readAppLinks(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return new AppLinksResult("unsupported_api", false, false);
        }
        try {
            DomainVerificationManager manager =
                    context.getSystemService(DomainVerificationManager.class);
            if (manager == null) {
                return new AppLinksResult("manager_unavailable", false, false);
            }
            DomainVerificationUserState userState =
                    manager.getDomainVerificationUserState(context.getPackageName());
            if (userState == null) {
                return new AppLinksResult("no_declared_domains", false, false);
            }
            Map<String, Integer> hostStates = userState.getHostToStateMap();
            Integer hostState = hostStates == null ? null : hostStates.get(HOST);
            return new AppLinksResult(
                    mapDomainStateForSdk(Build.VERSION.SDK_INT, hostState),
                    true,
                    userState.isLinkHandlingAllowed());
        } catch (PackageManager.NameNotFoundException ignored) {
            return new AppLinksResult("package_not_found", false, false);
        } catch (Exception ignored) {
            return new AppLinksResult("query_error", false, false);
        }
    }

    private static BrowserResult readBrowserCapability(Context context) {
        PackageManager packageManager = context.getPackageManager();
        String defaultProvider = resolveDefaultBrowser(packageManager);
        boolean querySucceeded = true;
        boolean defaultSupports = false;
        String capableProvider = "unresolved";
        List<ResolveInfo> allCapable = Collections.emptyList();

        try {
            Intent twaService = new Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION);
            twaService.addCategory(CustomTabsService.TRUSTED_WEB_ACTIVITY_CATEGORY);
            allCapable = packageManager.queryIntentServices(twaService, 0);
            if (allCapable == null) allCapable = Collections.emptyList();

            if (!allCapable.isEmpty()) {
                ResolveInfo first = allCapable.get(0);
                if (first.serviceInfo != null && first.serviceInfo.packageName != null) {
                    capableProvider = first.serviceInfo.packageName;
                }
            }

            if (!"unresolved".equals(defaultProvider)) {
                Intent defaultTwaService = new Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION);
                defaultTwaService.addCategory(CustomTabsService.TRUSTED_WEB_ACTIVITY_CATEGORY);
                defaultTwaService.setPackage(defaultProvider);
                List<ResolveInfo> defaultMatches =
                        packageManager.queryIntentServices(defaultTwaService, 0);
                defaultSupports = defaultMatches != null && !defaultMatches.isEmpty();
                if (defaultSupports) capableProvider = defaultProvider;
            }
        } catch (Exception ignored) {
            querySucceeded = false;
        }

        return new BrowserResult(
                defaultProvider,
                capableProvider,
                classifyTwaCapability(querySucceeded, defaultSupports, !allCapable.isEmpty()));
    }

    private static String resolveDefaultBrowser(PackageManager packageManager) {
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/"));
            ResolveInfo resolved = packageManager.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolved != null && resolved.activityInfo != null
                    && resolved.activityInfo.packageName != null) {
                return resolved.activityInfo.packageName;
            }
        } catch (Exception ignored) {
            // Browser discovery is best effort only.
        }
        return "unresolved";
    }

    private static String sha256Fingerprint(byte[] certificateBytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificateBytes);
        StringBuilder out = new StringBuilder(digest.length * 3 - 1);
        for (int i = 0; i < digest.length; i++) {
            if (i > 0) out.append(':');
            out.append(String.format(Locale.US, "%02X", digest[i] & 0xff));
        }
        return out.toString();
    }

    private static final class SignerResult {
        final String state;
        final boolean known;
        final boolean matches;

        SignerResult(String state, boolean known, boolean matches) {
            this.state = state;
            this.known = known;
            this.matches = matches;
        }
    }

    private static final class AppLinksResult {
        final String state;
        final boolean allowedKnown;
        final boolean linkHandlingAllowed;

        AppLinksResult(String state, boolean allowedKnown, boolean linkHandlingAllowed) {
            this.state = state;
            this.allowedKnown = allowedKnown;
            this.linkHandlingAllowed = linkHandlingAllowed;
        }
    }

    private static final class BrowserResult {
        final String defaultProvider;
        final String twaCapableProvider;
        final String capabilityState;

        BrowserResult(String defaultProvider, String twaCapableProvider, String capabilityState) {
            this.defaultProvider = defaultProvider;
            this.twaCapableProvider = twaCapableProvider;
            this.capabilityState = capabilityState;
        }
    }
}
