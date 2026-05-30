@echo off
REM Build OpenIris Training GUI as standalone exe
REM Run from project root or training folder
REM Requires: pip install pyinstaller

echo ========================================
echo Building OpenIris Training GUI
echo ========================================

REM Navigate to project root (parent of training)
cd /d "%~dp0.."
echo [INFO] Project root: %cd%

REM Check PyInstaller
pip show pyinstaller >nul 2>&1
if errorlevel 1 (
    echo [INFO] Installing PyInstaller...
    pip install pyinstaller
)

REM Build using spec file
echo [INFO] Building exe...
pyinstaller --clean "%~dp0build_gui.spec"

if errorlevel 1 (
    echo [ERROR] Build failed!
    pause
    exit /b 1
)

echo.
echo ========================================
echo Build complete!
echo ========================================
echo.
echo Output: dist\OpenIrisTraining\OpenIrisTraining.exe
echo.
echo You can distribute the entire dist\OpenIrisTraining folder.
echo.
pause
