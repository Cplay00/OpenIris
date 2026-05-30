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

REM Clean previous build artifacts (keep dist output)
echo [INFO] Cleaning build cache...
if exist "%~dp0build" rmdir /s /q "%~dp0build"
if exist "%~dp0__pycache__" rmdir /s /q "%~dp0__pycache__"
if exist "%~dp0gui\__pycache__" rmdir /s /q "%~dp0gui\__pycache__"
if exist "%~dp0scripts\__pycache__" rmdir /s /q "%~dp0scripts\__pycache__"
if exist "%~dp0tools\__pycache__" rmdir /s /q "%~dp0tools\__pycache__"
if exist "%~dp0tools\dataset_builder\__pycache__" rmdir /s /q "%~dp0tools\dataset_builder\__pycache__"

REM Build with spec file, set dist and work path to training folder
echo [INFO] Building exe...
pyinstaller --clean ^
    --distpath "%~dp0dist" ^
    --workpath "%~dp0build" ^
    --specpath "%~dp0" ^
    "%~dp0build_gui.spec"

if errorlevel 1 (
    echo.
    echo [ERROR] Build failed!
    pause
    exit /b 1
)

REM Clean build cache after successful build
echo [INFO] Cleaning build cache...
if exist "%~dp0build" rmdir /s /q "%~dp0build"
if exist "%~dp0OpenIrisTraining.spec" del /q "%~dp0OpenIrisTraining.spec"
for /d %%i in ("%~dp0gui\__pycache__") do rmdir /s /q "%%i" 2>nul
for /d %%i in ("%~dp0scripts\__pycache__") do rmdir /s /q "%%i" 2>nul
for /d %%i in ("%~dp0tools\__pycache__") do rmdir /s /q "%%i" 2>nul
for /d %%i in ("%~dp0tools\dataset_builder\__pycache__") do rmdir /s /q "%%i" 2>nul

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
