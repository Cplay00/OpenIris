@echo off
REM OpenIris Training Environment Setup Script
REM Uses YOLO_ENV_PATH environment variable or default path

if not defined YOLO_ENV_PATH set YOLO_ENV_PATH=C:\TrainingEnv
REM ??? %YOLO_ENV_PATH%\.. ???????

echo ========================================
echo OpenIris Training Environment Setup
echo ========================================

REM ????????????????
if not exist "%YOLO_ENV_PATH%\python.exe" (
    echo [ERROR] ???????????: %YOLO_ENV_PATH%
    echo ?????%YOLO_ENV_PATH%\.. ??????
    pause
    exit /b 1
)

echo [INFO] ?????????? %YOLO_ENV_PATH%

REM ???????
echo [INFO] ???????..
call %YOLO_ENV_PATH%\Scripts\activate.bat

REM ????Python ???
echo [INFO] Python ???:
python --version

REM ????????
echo.
echo [INFO] ??????????
python -c "import torch; print(f'  PyTorch: {torch.__version__}')"
python -c "import ultralytics; print(f'  Ultralytics: {ultralytics.__version__}')"
python -c "import cv2; print(f'  OpenCV: {cv2.__version__}')"

REM ????CUDA
echo.
echo [INFO] CUDA ????
python -c "import torch; print(f'  CUDA ???: {torch.cuda.is_available()}')"
python -c "import torch; print(f'  GPU: {torch.cuda.get_device_name(0) if torch.cuda.is_available() else \"N/A\"}')"

REM ??????????????????
echo.
echo [INFO] ??????????..
pip show pyyaml >nul 2>&1
if errorlevel 1 (
    echo [INFO] ??? pyyaml...
    pip install pyyaml
)

pip show matplotlib >nul 2>&1
if errorlevel 1 (
    echo [INFO] ??? matplotlib...
    pip install matplotlib
)

pip show seaborn >nul 2>&1
if errorlevel 1 (
    echo [INFO] ??? seaborn...
    pip install seaborn
)

echo.
echo ========================================
echo ?????????!
echo ========================================
echo.
echo ??????:
echo   1. ??????:
echo      python training/scripts/train_adapted.py
echo.
echo   2. ??? GUI:
echo      python training/gui/launcher.py
echo.
echo   3. ????????
echo      python training/scripts/dataset_validator.py --data training/configs/dataset_custom.yaml
echo.
pause


