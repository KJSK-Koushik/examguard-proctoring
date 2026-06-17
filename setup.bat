@echo off
setlocal enabledelayedexpansion

echo ============================================
echo   ExamGuard Proctoring - Setup
echo ============================================
echo.

:: Check Python is installed
python --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Python is not installed or not on PATH.
    echo         Download it from https://www.python.org/downloads/
    echo         Make sure to check "Add Python to PATH" during install.
    pause
    exit /b 1
)

for /f "tokens=2 delims= " %%v in ('python --version 2^>^&1') do set PYVER=%%v
echo [OK] Python %PYVER% found.

:: Create virtual environment
if exist venv (
    echo [OK] Virtual environment already exists, skipping creation.
) else (
    echo [..] Creating virtual environment...
    python -m venv venv
    if errorlevel 1 (
        echo [ERROR] Failed to create virtual environment.
        pause
        exit /b 1
    )
    echo [OK] Virtual environment created.
)

:: Activate virtual environment
call venv\Scripts\activate.bat
if errorlevel 1 (
    echo [ERROR] Failed to activate virtual environment.
    pause
    exit /b 1
)
echo [OK] Virtual environment activated.

:: Upgrade pip silently
echo [..] Upgrading pip...
python -m pip install --upgrade pip --quiet
echo [OK] pip up to date.

:: Install dependencies
echo [..] Installing dependencies from requirements.txt...
echo      (This may take a few minutes on first run)
echo.
pip install -r requirements.txt
if errorlevel 1 (
    echo.
    echo [ERROR] Dependency installation failed.
    echo         Check the error above. Common fixes:
    echo           - mediapipe requires Python 3.9-3.11
    echo           - Try: pip install mediapipe --pre
    pause
    exit /b 1
)

echo.
echo ============================================
echo   Setup complete!
echo ============================================
echo.
echo To run ExamGuard:
echo   1. Double-click run.bat
echo      OR
echo   1. Open a terminal in this folder
echo   2. Run:  venv\Scripts\activate
echo   3. Run:  python main.py
echo.
echo To find your camera index (if camera fails):
echo   python tools\probe_cameras.py --max-index 8
echo.
echo ============================================
echo   Android Emulator Mode (optional)
echo ============================================
echo.
adb version >nul 2>&1
if errorlevel 1 (
    echo [WARNING] ADB not found on PATH.
    echo           To use Android emulator as camera input you need ADB.
    echo           Install Android Studio, then add platform-tools to PATH:
    echo           e.g. C:\Users\YourName\AppData\Local\Android\Sdk\platform-tools
    echo           Then use run_emulator.bat instead of run.bat.
) else (
    echo [OK] ADB found. To run with Android emulator as camera:
    echo        1. Start an AVD from Android Studio
    echo        2. Double-click run_emulator.bat
)
echo.
pause
