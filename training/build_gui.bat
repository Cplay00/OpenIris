@echo off
REM Build OpenIris Training GUI as standalone exe
REM Requires: pip install pyinstaller

echo ========================================
echo Building OpenIris Training GUI
echo ========================================

REM Check PyInstaller
pip show pyinstaller >nul 2>&1
if errorlevel 1 (
    echo [INFO] Installing PyInstaller...
    pip install pyinstaller
)

REM Build
echo [INFO] Building exe...
pyinstaller --clean training\build_gui.spec

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
