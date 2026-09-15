@echo off
setlocal EnableExtensions EnableDelayedExpansion
title LINKPAS Beta Diagnostics

set "PACKAGE=id.barangpas.linkpas"
set "HOST=linkpas.vercel.app"
set "ADB=adb"

if defined LINKPAS_ADB (
  set "ADB=%LINKPAS_ADB%"
) else (
  where adb >nul 2>&1
  if errorlevel 1 (
    if exist "%~dp0platform-tools\adb.exe" (
      set "ADB=%~dp0platform-tools\adb.exe"
    ) else if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
      set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
    ) else (
      echo.
      echo ADB tidak ditemukan.
      echo Instal Android Platform Tools atau letakkan folder platform-tools di samping file ini.
      if not defined LINKPAS_NO_PAUSE pause
      exit /b 2
    )
  )
)

for /f %%I in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set "STAMP=%%I"
set "OUT=%~dp0LINKPAS-Debug-%STAMP%"
set "ZIP=%~dp0LINKPAS-Debug-%STAMP%.zip"
mkdir "%OUT%" >nul 2>&1

"%ADB%" start-server >nul 2>&1
set /a DEVICE_COUNT=0
for /f "skip=1 tokens=1,2" %%A in ('"%ADB%" devices 2^>nul') do (
  if "%%B"=="device" set /a DEVICE_COUNT+=1
)

if not "!DEVICE_COUNT!"=="1" (
  >"%OUT%\README.txt" (
    echo LINKPAS Beta Diagnostics
    echo.
    echo Status: membutuhkan tepat satu perangkat Android dengan USB debugging aktif.
    echo Perangkat terdeteksi dalam state device: !DEVICE_COUNT!
    echo Tidak ada device identifier yang disimpan ke bundle.
  )
  echo.
  echo Sambungkan tepat satu perangkat Android, izinkan USB debugging, lalu jalankan ulang.
  echo Folder diagnostik awal: "%OUT%"
  if not defined LINKPAS_NO_PAUSE pause
  exit /b 3
)

>"%OUT%\README.txt" (
  echo LINKPAS Beta Diagnostics
  echo.
  echo Package: %PACKAGE%
  echo Host: %HOST%
  echo Collected: %DATE% %TIME%
  echo.
  echo Bundle ini dibatasi untuk status package, App Links/TWA, dan logcat relevan LINKPAS.
  echo Bundle tidak mengambil screenshot, clipboard, file pengguna, bugreport penuh, atau device serial.
  echo Jika telemetry Supabase tidak cukup, kirim ZIP ini ke ChatGPT bersama perintah: cek LINKPAS.
)

"%ADB%" version >"%OUT%\adb-version.txt" 2>&1
"%ADB%" get-state >"%OUT%\device-state.txt" 2>&1
"%ADB%" shell pm path %PACKAGE% >"%OUT%\package-path.txt" 2>&1
"%ADB%" shell pm get-app-links %PACKAGE% >"%OUT%\app-links.txt" 2>&1

"%ADB%" shell dumpsys package %PACKAGE% 2>&1 | findstr /i ^
  /c:"Package [" ^
  /c:"versionName=" ^
  /c:"versionCode=" ^
  /c:"enabled=" ^
  /c:"android.intent.action.VIEW" ^
  /c:"%HOST%" ^
  /c:"DomainVerification" ^
  /c:"domainVerification" ^
  /c:"verified" ^
  /c:"ManageDataLauncherActivity" >"%OUT%\package-app-links.txt"

"%ADB%" shell dumpsys activity activities 2>&1 | findstr /i /c:"%PACKAGE%" /c:"%HOST%" >"%OUT%\activity.txt"

set "APP_PID="
for /f "delims=" %%P in ('"%ADB%" shell pidof %PACKAGE% 2^>nul') do set "APP_PID=%%P"
if defined APP_PID (
  "%ADB%" logcat -d -v threadtime --pid !APP_PID! -t 400 >"%OUT%\wrapper-logcat.txt" 2>&1
) else (
  >"%OUT%\wrapper-logcat.txt" echo LINKPAS wrapper process is not currently running.
)

"%ADB%" logcat -d -v threadtime -t 800 2>&1 | findstr /i ^
  /c:"%PACKAGE%" ^
  /c:"TrustedWebActivity" ^
  /c:"androidbrowserhelper" ^
  /c:"DomainVerification" ^
  /c:"%HOST%" >"%OUT%\system-filtered-logcat.txt"

set "LINKPAS_DEBUG_OUT=%OUT%"
set "LINKPAS_DEBUG_ZIP=%ZIP%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$out=$env:LINKPAS_DEBUG_OUT; $zip=$env:LINKPAS_DEBUG_ZIP; Compress-Archive -Path (Join-Path $out '*') -DestinationPath $zip -Force" >nul 2>&1

if exist "%ZIP%" (
  echo.
  echo Selesai. Bundle diagnostik:
  echo "%ZIP%"
  echo.
  echo Kirim ZIP tersebut ke ChatGPT lalu tulis: cek LINKPAS
  if not defined LINKPAS_NO_PAUSE pause
  exit /b 0
)

echo.
echo Pengumpulan selesai, tetapi ZIP gagal dibuat.
echo Folder hasil tetap tersedia di:
echo "%OUT%"
if not defined LINKPAS_NO_PAUSE pause
exit /b 4
