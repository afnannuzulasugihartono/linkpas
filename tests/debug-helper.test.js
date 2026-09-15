const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const helper = fs.readFileSync(path.join(__dirname, '..', 'LINKPAS-Debug.bat'), 'utf8');

test('Windows helper is scoped to LINKPAS package/App Links diagnostics', () => {
  assert.match(helper, /id\.barangpas\.linkpas/);
  assert.match(helper, /linkpas\.vercel\.app/);
  assert.match(helper, /pm get-app-links %PACKAGE%/i);
  assert.match(helper, /dumpsys package %PACKAGE%/i);
  assert.match(helper, /logcat -d -v threadtime --pid !APP_PID! -t 400/i);
  assert.match(helper, /logcat -d -v threadtime -t 800/i);
  assert.match(helper, /Compress-Archive/i);
});

test('Windows helper avoids broad or user-content collection commands', () => {
  assert.doesNotMatch(helper, /adb(?:\.exe)?"?\s+bugreport/i);
  assert.doesNotMatch(helper, /screencap/i);
  assert.doesNotMatch(helper, /screenrecord/i);
  assert.doesNotMatch(helper, /\bshell\s+cat\s+\/sdcard/i);
  assert.doesNotMatch(helper, /\bpull\s+\/sdcard/i);
  assert.doesNotMatch(helper, /getprop\s+ro\.(?:serialno|boot\.serialno)/i);
  assert.doesNotMatch(helper, /devices\.txt/i);
});

test('Windows helper requires exactly one authorized device before collection', () => {
  assert.match(helper, /DEVICE_COUNT/);
  assert.match(helper, /if not "!DEVICE_COUNT!"=="1"/);
  assert.match(helper, /Tidak ada device identifier yang disimpan ke bundle/);
});
