# OpenIris Training Environment Setup Script (PowerShell)
# 使用 D:\YOLO11Preinit 参考环境

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "OpenIris Training Environment Setup" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 检查参考环境是否存在
if (-not (Test-Path "D:\YOLO11Preinit\Yolo11Pre\python.exe")) {
    Write-Host "[ERROR] 参考环境不存在: D:\YOLO11Preinit\Yolo11Pre" -ForegroundColor Red
    Write-Host "请确保 D:\YOLO11Preinit 目录完整"
    exit 1
}

Write-Host "[INFO] 找到参考环境: D:\YOLO11Preinit\Yolo11Pre" -ForegroundColor Green

# 激活环境
Write-Host "[INFO] 激活环境..." -ForegroundColor Yellow
& "D:\YOLO11Preinit\Yolo11Pre\Scripts\Activate.ps1"

# 检查 Python 版本
Write-Host ""
Write-Host "[INFO] Python 版本:" -ForegroundColor Yellow
python --version

# 检查关键包
Write-Host ""
Write-Host "[INFO] 检查关键依赖:" -ForegroundColor Yellow
python -c "import torch; print(f'  PyTorch: {torch.__version__}')"
python -c "import ultralytics; print(f'  Ultralytics: {ultralytics.__version__}')"
python -c "import cv2; print(f'  OpenCV: {cv2.__version__}')"

# 检查 CUDA
Write-Host ""
Write-Host "[INFO] CUDA 状态:" -ForegroundColor Yellow
python -c "import torch; print(f'  CUDA 可用: {torch.cuda.is_available()}')"
python -c "import torch; print(f'  GPU: {torch.cuda.get_device_name(0) if torch.cuda.is_available() else ""N/A""}')"

# 安装额外依赖
Write-Host ""
Write-Host "[INFO] 检查额外依赖..." -ForegroundColor Yellow

$packages = @("pyyaml", "matplotlib", "seaborn", "pandas", "scikit-learn")

foreach ($pkg in $packages) {
    $result = pip show $pkg 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[INFO] 安装 $pkg..." -ForegroundColor Yellow
        pip install $pkg
    } else {
        Write-Host "[INFO] $pkg 已安装" -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "环境设置完成!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "可用命令:" -ForegroundColor White
Write-Host "  1. 运行训练:" -ForegroundColor Gray
Write-Host "     python training/scripts/train_adapted.py" -ForegroundColor Yellow
Write-Host ""
Write-Host "  2. 启动 GUI:" -ForegroundColor Gray
Write-Host "     python training/gui/launcher.py" -ForegroundColor Yellow
Write-Host ""
Write-Host "  3. 验证数据集:" -ForegroundColor Gray
Write-Host "     python training/scripts/dataset_validator.py --data training/configs/dataset_custom.yaml" -ForegroundColor Yellow
Write-Host ""
