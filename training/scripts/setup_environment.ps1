# Uses YOLO_ENV_PATH environment variable or default path
if (-not $env:YOLO_ENV_PATH) { $env:YOLO_ENV_PATH = 'C:\TrainingEnv' }
# OpenIris Training Environment Setup Script (PowerShell)
# 浣跨敤 $env:YOLO_ENV_PATH\.. 鍙傝€冪幆澧?

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "OpenIris Training Environment Setup" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 妫€鏌ュ弬鑰冪幆澧冩槸鍚﹀瓨鍦?
if (-not (Test-Path "$env:YOLO_ENV_PATH\python.exe")) {
    Write-Host "[ERROR] 鍙傝€冪幆澧冧笉瀛樺湪: $env:YOLO_ENV_PATH" -ForegroundColor Red
    Write-Host "璇风‘淇?$env:YOLO_ENV_PATH\.. 鐩綍瀹屾暣"
    exit 1
}

Write-Host "[INFO] 鎵惧埌鍙傝€冪幆澧? $env:YOLO_ENV_PATH" -ForegroundColor Green

# 婵€娲荤幆澧?
Write-Host "[INFO] 婵€娲荤幆澧?.." -ForegroundColor Yellow
& "$env:YOLO_ENV_PATH\Scripts\Activate.ps1"

# 妫€鏌?Python 鐗堟湰
Write-Host ""
Write-Host "[INFO] Python 鐗堟湰:" -ForegroundColor Yellow
python --version

# 妫€鏌ュ叧閿寘
Write-Host ""
Write-Host "[INFO] 妫€鏌ュ叧閿緷璧?" -ForegroundColor Yellow
python -c "import torch; print(f'  PyTorch: {torch.__version__}')"
python -c "import ultralytics; print(f'  Ultralytics: {ultralytics.__version__}')"
python -c "import cv2; print(f'  OpenCV: {cv2.__version__}')"

# 妫€鏌?CUDA
Write-Host ""
Write-Host "[INFO] CUDA 鐘舵€?" -ForegroundColor Yellow
python -c "import torch; print(f'  CUDA 鍙敤: {torch.cuda.is_available()}')"
python -c "import torch; print(f'  GPU: {torch.cuda.get_device_name(0) if torch.cuda.is_available() else ""N/A""}')"

# 瀹夎棰濆渚濊禆
Write-Host ""
Write-Host "[INFO] 妫€鏌ラ澶栦緷璧?.." -ForegroundColor Yellow

$packages = @("pyyaml", "matplotlib", "seaborn", "pandas", "scikit-learn")

foreach ($pkg in $packages) {
    $result = pip show $pkg 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[INFO] 瀹夎 $pkg..." -ForegroundColor Yellow
        pip install $pkg
    } else {
        Write-Host "[INFO] $pkg 宸插畨瑁? -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "鐜璁剧疆瀹屾垚!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "鍙敤鍛戒护:" -ForegroundColor White
Write-Host "  1. 杩愯璁粌:" -ForegroundColor Gray
Write-Host "     python training/scripts/train_adapted.py" -ForegroundColor Yellow
Write-Host ""
Write-Host "  2. 鍚姩 GUI:" -ForegroundColor Gray
Write-Host "     python training/gui/launcher.py" -ForegroundColor Yellow
Write-Host ""
Write-Host "  3. 楠岃瘉鏁版嵁闆?" -ForegroundColor Gray
Write-Host "     python training/scripts/dataset_validator.py --data training/configs/dataset_custom.yaml" -ForegroundColor Yellow
Write-Host ""


