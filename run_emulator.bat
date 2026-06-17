@echo off
setlocal enabledelayedexpansion

if not exist venv (
    echo [ERROR] Virtual environment not found. Run setup.bat first.
    pause
    exit /b 1
)

:: ── Check ADB ────────────────────────────────────────────────────────────────
adb version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] ADB not found on PATH.
    echo         Install Android Studio, then add platform-tools to PATH:
    echo         e.g. C:\Users\%USERNAME%\AppData\Local\Android\Sdk\platform-tools
    pause
    exit /b 1
)

:: ── Find ANDROID_HOME (needed to launch the emulator binary) ─────────────────
set "EMULATOR_EXE="
if defined ANDROID_HOME (
    set "EMULATOR_EXE=%ANDROID_HOME%\emulator\emulator.exe"
)
if not defined EMULATOR_EXE (
    if defined ANDROID_SDK_ROOT (
        set "EMULATOR_EXE=%ANDROID_SDK_ROOT%\emulator\emulator.exe"
    )
)
:: Fallback: common default path
if not defined EMULATOR_EXE (
    set "EMULATOR_EXE=%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe"
)

if not exist "%EMULATOR_EXE%" (
    echo [ERROR] Could not find emulator.exe.
    echo         Make sure Android Studio is installed and ANDROID_HOME is set.
    echo         Expected location: %EMULATOR_EXE%
    pause
    exit /b 1
)

:: ── Check if emulator is already running ─────────────────────────────────────
adb devices | findstr /i "emulator" >nul 2>&1
if not errorlevel 1 (
    echo [OK] Emulator already running. Skipping launch.
    goto :start_app
)

:: ── Find available AVDs ───────────────────────────────────────────────────────
echo [..] Looking for Android Virtual Devices...
set "AVD_NAME="
set "AVD_COUNT=0"

for /f "delims=" %%A in ('"%EMULATOR_EXE%" -list-avds 2^>nul') do (
    set /a AVD_COUNT+=1
    if !AVD_COUNT! == 1 (
        set "AVD_NAME=%%A"
    )
    echo        Found AVD: %%A
)

if %AVD_COUNT% == 0 (
    echo [ERROR] No AVDs found.
    echo         Open Android Studio ^> Virtual Device Manager ^> Create Device.
    pause
    exit /b 1
)

echo [OK] Using AVD: %AVD_NAME%
echo [..] Launching emulator (this may take 30-60 seconds)...

:: Launch emulator in background
start "" "%EMULATOR_EXE%" -avd "%AVD_NAME%" -gpu swiftshader_indirect -no-snapshot-load

:: ── Wait for emulator to come online (up to 90 seconds) ──────────────────────
set /a WAIT=0
:wait_loop
timeout /t 3 /nobreak >nul
set /a WAIT+=3
adb devices | findstr /i "emulator" >nul 2>&1
if not errorlevel 1 goto :wait_boot
if %WAIT% GEQ 90 (
    echo [ERROR] Emulator did not come online after 90 seconds.
    echo         Try launching it manually from Android Studio.
    pause
    exit /b 1
)
echo [..] Waiting for emulator... (%WAIT%s)
goto :wait_loop

:wait_boot
echo [..] Emulator online. Waiting for boot to complete...
set /a WAIT=0
:boot_loop
timeout /t 4 /nobreak >nul
set /a WAIT+=4
for /f "delims=" %%B in ('adb shell getprop sys.boot_completed 2^>nul') do (
    if "%%B"=="1" goto :start_app
)
if %WAIT% GEQ 90 (
    echo [WARNING] Boot check timed out — trying to start anyway...
    goto :start_app
)
echo [..] Waiting for boot... (%WAIT%s)
goto :boot_loop

:start_app
echo [OK] Emulator ready. Starting ExamGuard...
echo.
call venv\Scripts\activate.bat
python main.py --camera emulator %*
