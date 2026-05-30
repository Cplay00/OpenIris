@echo off
REM OpenIris Quick Start Training Script
REM 使用参考环境快速开始训练

echo ========================================
echo OpenIris Quick Start Training
echo ========================================

REM 设置项目根目录
set PROJECT_ROOT=%~dp0..\..

REM 检查参考环境
if not exist "D:\YOLO11Preinit\Yolo11Pre\python.exe" (
    echo [ERROR] 参考环境不存在
    echo 请确保 D:\YOLO11Preinit 目录完整
    pause
    exit /b 1
)

REM 激活环境
echo [INFO] 激活参考环境...
call D:\YOLO11Preinit\Yolo11Pre\Scripts\activate.bat

REM 检查数据集配置
if not exist "%PROJECT_ROOT%\training\configs\dataset_custom.yaml" (
    echo [WARNING] 数据集配置不存在: training\configs\dataset_custom.yaml
    echo [INFO] 请先配置数据集或使用 --data 参数指定
    echo.
    echo 示例:
    echo   python training/scripts/train_adapted.py --data path/to/your/data.yaml
    echo.
    pause
    exit /b 1
)

REM 运行训练
echo [INFO] 开始训练...
echo.
python "%PROJECT_ROOT%\training\scripts\train_adapted.py" ^
    --data "%PROJECT_ROOT%\training\configs\dataset_custom.yaml" ^
    --epochs 200 ^
    --batch 32 ^
    --name openiris_quickstart

echo.
echo ========================================
echo 训练完成!
echo ========================================
echo.
echo 最优权重位置:
echo   %PROJECT_ROOT%\runs\train\openiris_quickstart\weights\best.pt
echo.
echo 后续步骤:
echo   1. 评估模型:
echo      python training/scripts/evaluate.py --weights runs/train/openiris_quickstart/weights/best.pt
echo.
echo   2. 导出到 Android:
echo      python training/tools/export_pipeline.py --weights runs/train/openiris_quickstart/weights/best.pt
echo.
pause
