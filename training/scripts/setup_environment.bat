@echo off
REM OpenIris Training Environment Setup Script
REM 使用 D:\YOLO11Preinit 参考环境

echo ========================================
echo OpenIris Training Environment Setup
echo ========================================

REM 检查参考环境是否存在
if not exist "D:\YOLO11Preinit\Yolo11Pre\python.exe" (
    echo [ERROR] 参考环境不存在: D:\YOLO11Preinit\Yolo11Pre
    echo 请确保 D:\YOLO11Preinit 目录完整
    pause
    exit /b 1
)

echo [INFO] 找到参考环境: D:\YOLO11Preinit\Yolo11Pre

REM 激活环境
echo [INFO] 激活环境...
call D:\YOLO11Preinit\Yolo11Pre\Scripts\activate.bat

REM 检查 Python 版本
echo [INFO] Python 版本:
python --version

REM 检查关键包
echo.
echo [INFO] 检查关键依赖:
python -c "import torch; print(f'  PyTorch: {torch.__version__}')"
python -c "import ultralytics; print(f'  Ultralytics: {ultralytics.__version__}')"
python -c "import cv2; print(f'  OpenCV: {cv2.__version__}')"

REM 检查 CUDA
echo.
echo [INFO] CUDA 状态:
python -c "import torch; print(f'  CUDA 可用: {torch.cuda.is_available()}')"
python -c "import torch; print(f'  GPU: {torch.cuda.get_device_name(0) if torch.cuda.is_available() else \"N/A\"}')"

REM 安装额外依赖（如果需要）
echo.
echo [INFO] 检查额外依赖...
pip show pyyaml >nul 2>&1
if errorlevel 1 (
    echo [INFO] 安装 pyyaml...
    pip install pyyaml
)

pip show matplotlib >nul 2>&1
if errorlevel 1 (
    echo [INFO] 安装 matplotlib...
    pip install matplotlib
)

pip show seaborn >nul 2>&1
if errorlevel 1 (
    echo [INFO] 安装 seaborn...
    pip install seaborn
)

echo.
echo ========================================
echo 环境设置完成!
echo ========================================
echo.
echo 可用命令:
echo   1. 运行训练:
echo      python training/scripts/train_adapted.py
echo.
echo   2. 启动 GUI:
echo      python training/gui/launcher.py
echo.
echo   3. 验证数据集:
echo      python training/scripts/dataset_validator.py --data training/configs/dataset_custom.yaml
echo.
pause
