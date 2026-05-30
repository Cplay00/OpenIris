@echo off
chcp 65001 >nul 2>&1
REM Build OpenIris Training GUI as standalone exe
REM Output: training\dist\OpenIrisTraining\

echo ========================================
echo Building OpenIris Training GUI
echo ========================================

REM Navigate to project root
cd /d "%~dp0.."
echo [INFO] Project root: %cd%

REM Check PyInstaller
pip show pyinstaller >nul 2>&1
if errorlevel 1 (
    echo [INFO] Installing PyInstaller...
    pip install pyinstaller
)

REM Clean previous build artifacts
echo [INFO] Cleaning build cache...
if exist "%~dp0build" rmdir /s /q "%~dp0build"
if exist "%~dp0dist\OpenIrisTraining" rmdir /s /q "%~dp0dist\OpenIrisTraining"
for /r "%~dp0" %%d in (__pycache__) do if exist "%%d" rmdir /s /q "%%d" 2>nul

REM Build using spec file
echo [INFO] Building exe...
cd /d "%~dp0"
pyinstaller build_gui.spec

if errorlevel 1 (
    echo.
    echo [ERROR] Build failed!
    cd /d "%~dp0.."
    pause
    exit /b 1
)

REM Clean build cache after successful build
echo [INFO] Cleaning build cache...
if exist "%~dp0build" rmdir /s /q "%~dp0build"
for /r "%~dp0" %%d in (__pycache__) do if exist "%%d" rmdir /s /q "%%d" 2>nul

cd /d "%~dp0.."
echo.
echo ========================================
echo Build complete!
echo ========================================
echo.
echo Output: %~dp0dist\OpenIrisTraining\OpenIrisTraining.exe
echo.
echo The entire dist\OpenIrisTraining folder is portable.
echo.
pause
