#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Internationalization (i18n) Module
Supports: zh_CN (default), en_US
"""

# Current language
CURRENT_LANG = "zh_CN"

# ============================================================
# Translations
# ============================================================
TRANSLATIONS = {
    "zh_CN": {
        # Window
        "app_title": "OpenIris YOLO 训练平台",
        "version": "版本",
        "status_ready": "就绪",

        # Menu
        "menu_file": "文件",
        "menu_load_config": "加载配置",
        "menu_save_config": "保存配置",
        "menu_exit": "退出",
        "menu_dataset": "数据集",
        "menu_validate": "校验数据集",
        "menu_tools": "工具",
        "menu_export": "导出模型",
        "menu_visualize": "训练可视化",
        "menu_env_check": "环境检测",
        "menu_help": "帮助",
        "menu_about": "关于",

        # Tabs
        "tab_basic": "基础配置",
        "tab_advanced": "高级配置",
        "tab_augment": "数据增强",

        # Basic tab
        "dataset_config": "数据集配置",
        "pretrained_model": "预训练模型",
        "browse": "浏览",
        "or_type_path": "或输入本地路径",
        "training_params": "训练参数",
        "epochs": "训练轮次",
        "batch_size": "批次大小",
        "image_size": "输入尺寸",
        "device": "训练设备",
        "experiment_name": "实验名称",

        # Advanced tab
        "optimizer": "优化器",
        "algorithm": "算法",
        "learning_rate": "学习率",
        "weight_decay": "权重衰减",
        "label_smoothing": "标签平滑",
        "early_stop_patience": "早停轮次",
        "amp_mixed_precision": "混合精度训练 (AMP)",
        "workers": "工作线程数",

        # Augmentation tab
        "data_augmentation": "数据增强",

        # Controls
        "training_control": "训练控制",
        "start_training": "开始训练",
        "stop": "停止",
        "validate": "校验",
        "export": "导出",
        "training_log": "训练日志",
        "clear_log": "清空日志",
        "training_in_progress": "训练中...",
        "training_complete": "训练完成",
        "training_failed": "训练失败",
        "training_stopped": "已停止",
        "ready_to_train": "准备就绪",

        # Errors
        "error_no_dataset": "请先选择数据集配置文件",
        "error_confirm_stop": "确定要停止训练吗?",
        "error_load_config": "加载配置失败",
        "error_export_no_weights": "请先选择模型权重文件",
        "error_no_dataset_first": "请先选择数据集配置",

        # Environment check
        "env_check_title": "环境检测结果",
        "env_python": "Python",
        "env_gpu": "GPU",
        "env_cuda": "CUDA",
        "env_not_installed": "未安装",
        "env_not_available": "不可用",

        # About
        "about_title": "关于",
        "about_text": "OpenIris YOLO 训练平台\n\n"
                      "版本: 2.0\n"
                      "框架: Ultralytics YOLOv11\n"
                      "目标: Android NCNN 部署\n\n"
                      "基于 tkinter 的图形化训练工具\n"
                      "支持自定义数据集、多种模型改进、一键导出",

        # Tooltips
        "tip_dataset": "数据集配置文件 (YAML)\n"
                       "必须包含: train/val 路径、类别数、类别名\n"
                       "可使用 dataset_custom.yaml 作为模板",
        "tip_model": "预训练模型权重文件\n"
                     "- yolov11n.pt: Nano,最快,移动端推荐\n"
                     "- yolov11s.pt: Small,平衡\n"
                     "- yolov11m.pt: Medium,更高精度\n"
                     "Android NCNN 部署请使用 yolov11n.pt",
        "tip_model_path": "点击「浏览」选择本地 .pt 权重文件\n"
                          "也可在下拉框中直接输入路径",
        "tip_epochs": "训练总轮次\n"
                      "- 快速验证: 30\n"
                      "- 标准训练: 100\n"
                      "- 高精度: 200+\n"
                      "轮次越多训练时间越长",
        "tip_batch": "每批次训练图片数\n"
                     "取决于 GPU 显存:\n"
                     "- 8GB: 16-32\n"
                     "- 12GB: 32-48\n"
                     "- 24GB: 64-128\n"
                     "有效批次 = batch × accumulate",
        "tip_imgsz": "输入图像尺寸 (像素)\n"
                     "NCNN 部署必须使用 640\n"
                     "越大越精确但越慢\n"
                     "可选: 320, 416, 512, 640, 800, 1024",
        "tip_device": "训练计算设备\n"
                      "- 0: 第一块 GPU\n"
                      "- 0,1: 多 GPU\n"
                      "- cpu: 仅 CPU (很慢)",
        "tip_name": "实验名称,用于输出目录\n"
                    "结果保存到: runs/train/<名称>/",
        "tip_optimizer": "优化算法\n"
                        "- SGD: 经典,适合大模型\n"
                        "- Adam: 自适应,收敛快\n"
                        "- AdamW: Adam + 权重衰减 (推荐)\n"
                        "- auto: 自动选择",
        "tip_lr": "初始学习率\n"
                  "- SGD: 0.01\n"
                  "- Adam/AdamW: 0.001\n"
                  "过大导致发散,过小导致收敛慢",
        "tip_weight_decay": "L2 正则化强度\n"
                            "防止过拟合\n"
                            "范围: 0.0001 ~ 0.001\n"
                            "推荐: 0.0005",
        "tip_patience": "早停耐心值 (轮次数)\n"
                        "N 轮无改善则停止训练\n"
                        "0 = 禁用\n"
                        "推荐: 50-100",
        "tip_label_smoothing": "软化 one-hot 标签,减少过拟合\n"
                               "范围: 0.0 ~ 0.1\n"
                               "0 = 不平滑 (默认)\n"
                               "小数据集推荐: 0.01-0.05",
        "tip_amp": "自动混合精度训练\n"
                   "使用 FP16 加速,FP32 保持稳定\n"
                   "速度提升 2-3 倍,显存节省 50%\n"
                   "NCNN FP16 部署必须开启",
        "tip_workers": "数据加载线程数\n"
                       "越大加载越快\n"
                       "Windows 推荐: 4\n"
                       "Linux 推荐: 8-16",
        "tip_mosaic": "Mosaic 增强概率\n"
                      "将 4 张图拼接为 1 张\n"
                      "多尺度学习效果好\n"
                      "1.0 = 始终开启,0.0 = 关闭",
        "tip_mixup": "Mixup 增强概率\n"
                     "混合两张图像和标签\n"
                     "减少小数据集过拟合\n"
                     "推荐: 0.0-0.3",
        "tip_fliplr": "水平翻转概率\n"
                      "0.5 = 50% 图像翻转\n"
                      "大多数场景有效\n"
                      "左右有区分时关闭",
        "tip_degrees": "随机旋转范围 (度)\n"
                       "0 = 不旋转\n"
                       "通用: 10-15\n"
                       "方向敏感时关闭",
        "tip_scale": "随机缩放范围 (增益)\n"
                     "0.5 = 缩放 0.5x ~ 1.5x\n"
                     "模拟距离变化\n"
                     "推荐: 0.0-0.5",
        "tip_translate": "随机平移范围 (比例)\n"
                         "水平/垂直平移图像\n"
                         "0.1 = 图像尺寸的 10%\n"
                         "推荐: 0.0-0.2",
        "tip_shear": "随机剪切强度 (度)\n"
                     "使图像倾斜\n"
                     "推荐: 0.0-10.0\n"
                     "刚性物体时关闭",
        "tip_hsv_h": "HSV-色调增强范围\n"
                     "随机偏移色彩\n"
                     "小值: 0.01-0.02\n"
                     "影响颜色感知",
        "tip_hsv_s": "HSV-饱和度增强范围\n"
                     "改变颜色强度\n"
                     "室外: 0.5-0.7\n"
                     "室内: 0.3-0.5",
        "tip_hsv_v": "HSV-明度增强范围\n"
                     "模拟光照变化\n"
                     "推荐: 0.3-0.5\n"
                     "移动端拍照很重要",
        "tip_erasing": "随机擦除概率\n"
                       "裁剪图像随机区域\n"
                       "提升遮挡鲁棒性\n"
                       "推荐: 0.0-0.4",
    },

    "en_US": {
        # Window
        "app_title": "OpenIris YOLO Training Platform",
        "version": "Version",
        "status_ready": "Ready",

        # Menu
        "menu_file": "File",
        "menu_load_config": "Load Config",
        "menu_save_config": "Save Config",
        "menu_exit": "Exit",
        "menu_dataset": "Dataset",
        "menu_validate": "Validate Dataset",
        "menu_tools": "Tools",
        "menu_export": "Export Model",
        "menu_visualize": "Visualize Results",
        "menu_env_check": "Check Environment",
        "menu_help": "Help",
        "menu_about": "About",

        # Tabs
        "tab_basic": "Basic",
        "tab_advanced": "Advanced",
        "tab_augment": "Augmentation",

        # Basic tab
        "dataset_config": "Dataset Config",
        "pretrained_model": "Pretrained Model",
        "browse": "Browse",
        "or_type_path": "or type path",
        "training_params": "Training Parameters",
        "epochs": "Epochs",
        "batch_size": "Batch Size",
        "image_size": "Image Size",
        "device": "Device",
        "experiment_name": "Experiment Name",

        # Advanced tab
        "optimizer": "Optimizer",
        "algorithm": "Algorithm",
        "learning_rate": "Learning Rate",
        "weight_decay": "Weight Decay",
        "label_smoothing": "Label Smoothing",
        "early_stop_patience": "Early Stop Patience",
        "amp_mixed_precision": "AMP (Mixed Precision)",
        "workers": "Workers",

        # Augmentation tab
        "data_augmentation": "Data Augmentation",

        # Controls
        "training_control": "Training Control",
        "start_training": "Start Training",
        "stop": "Stop",
        "validate": "Validate",
        "export": "Export",
        "training_log": "Training Log",
        "clear_log": "Clear Log",
        "training_in_progress": "Training in progress...",
        "training_complete": "Training complete",
        "training_failed": "Training failed",
        "training_stopped": "Stopped",
        "ready_to_train": "Ready to train",

        # Errors
        "error_no_dataset": "Please select a dataset config file first.",
        "error_confirm_stop": "Stop training?",
        "error_load_config": "Failed to load config",
        "error_export_no_weights": "Please select model weights file first.",
        "error_no_dataset_first": "Please select a dataset config first.",

        # Environment check
        "env_check_title": "Environment Check",
        "env_python": "Python",
        "env_gpu": "GPU",
        "env_cuda": "CUDA",
        "env_not_installed": "Not installed",
        "env_not_available": "Not available",

        # About
        "about_title": "About",
        "about_text": "OpenIris YOLO Training Platform\n\n"
                      "Version: 2.0\n"
                      "Framework: Ultralytics YOLOv11\n"
                      "Target: Android NCNN Deployment\n\n"
                      "Tkinter-based GUI training tool\n"
                      "Custom datasets, model improvements, one-click export",

        # Tooltips
        "tip_dataset": "Dataset config file (YAML)\n"
                       "Must contain: train/val paths, nc, names\n"
                       "Use dataset_custom.yaml as template",
        "tip_model": "Pretrained model weights\n"
                     "- yolov11n.pt: Nano, fastest, for mobile\n"
                     "- yolov11s.pt: Small, balanced\n"
                     "- yolov11m.pt: Medium, higher accuracy\n"
                     "For Android NCNN deploy, use yolov11n.pt",
        "tip_model_path": "Click 'Browse' to select local .pt file\n"
                          "Or type the path directly",
        "tip_epochs": "Total training epochs\n"
                      "- Fast test: 30\n"
                      "- Standard: 100\n"
                      "- High precision: 200+\n"
                      "More epochs = longer training",
        "tip_batch": "Images per batch\n"
                     "Depends on GPU VRAM:\n"
                     "- 8GB: 16-32\n"
                     "- 12GB: 32-48\n"
                     "- 24GB: 64-128\n"
                     "Effective = batch × accumulate",
        "tip_imgsz": "Input image size (pixels)\n"
                     "MUST be 640 for NCNN\n"
                     "Larger = more accurate but slower\n"
                     "Options: 320, 416, 512, 640, 800, 1024",
        "tip_device": "Training device\n"
                      "- 0: First GPU\n"
                      "- 0,1: Multi-GPU\n"
                      "- cpu: CPU only (slow)",
        "tip_name": "Experiment name for output folder\n"
                    "Results saved to: runs/train/<name>/",
        "tip_optimizer": "Optimization algorithm\n"
                        "- SGD: Classic, good for large models\n"
                        "- Adam: Adaptive, fast convergence\n"
                        "- AdamW: Adam + weight decay (recommended)\n"
                        "- auto: Auto-select",
        "tip_lr": "Initial learning rate\n"
                  "- SGD: 0.01\n"
                  "- Adam/AdamW: 0.001\n"
                  "Too high = divergence, too low = slow",
        "tip_weight_decay": "L2 regularization strength\n"
                            "Prevents overfitting\n"
                            "Range: 0.0001 ~ 0.001\n"
                            "Recommended: 0.0005",
        "tip_patience": "Early stopping patience (epochs)\n"
                        "Stop if no improvement for N epochs\n"
                        "0 = disabled\n"
                        "Recommended: 50-100",
        "tip_label_smoothing": "Softens one-hot labels\n"
                               "Range: 0.0 ~ 0.1\n"
                               "0 = no smoothing\n"
                               "Small datasets: 0.01-0.05",
        "tip_amp": "Automatic Mixed Precision\n"
                   "FP16 for speed, FP32 for stability\n"
                   "2-3x faster, 50% less VRAM\n"
                   "Required for NCNN FP16 deployment",
        "tip_workers": "Data loading threads\n"
                       "Higher = faster loading\n"
                       "Windows: 4\n"
                       "Linux: 8-16",
        "tip_mosaic": "Mosaic augmentation probability\n"
                      "Combines 4 images into one\n"
                      "Great for multi-scale learning\n"
                      "1.0 = always, 0.0 = disabled",
        "tip_mixup": "Mixup augmentation probability\n"
                     "Blends two images and labels\n"
                     "Reduces overfitting\n"
                     "0.0-0.3 recommended",
        "tip_fliplr": "Horizontal flip probability\n"
                      "0.5 = flip 50% of images\n"
                      "Good for most scenarios\n"
                      "Disable if left/right matters",
        "tip_degrees": "Random rotation range (degrees)\n"
                       "0 = no rotation\n"
                       "10-15 for general use\n"
                       "Disable if orientation matters",
        "tip_scale": "Random zoom range (gain)\n"
                     "0.5 = scale 0.5x ~ 1.5x\n"
                     "Simulates distance variation\n"
                     "0.0-0.5 recommended",
        "tip_translate": "Random translation range (fraction)\n"
                         "Shifts image horizontally/vertically\n"
                         "0.1 = 10% of image size\n"
                         "0.0-0.2 recommended",
        "tip_shear": "Random shear intensity (degrees)\n"
                     "Skews the image\n"
                     "0.0-10.0 recommended\n"
                     "Disable for rigid objects",
        "tip_hsv_h": "HSV-Hue augmentation range\n"
                     "Shifts color hue randomly\n"
                     "Small values: 0.01-0.02\n"
                     "Affects color perception",
        "tip_hsv_s": "HSV-Saturation augmentation range\n"
                     "Changes color intensity\n"
                     "Outdoor: 0.5-0.7\n"
                     "Indoor: 0.3-0.5",
        "tip_hsv_v": "HSV-Value (brightness) range\n"
                     "Simulates lighting changes\n"
                     "0.3-0.5 recommended\n"
                     "Important for mobile camera",
        "tip_erasing": "Random erasing probability\n"
                       "Cuts random patches from image\n"
                       "Improves occlusion robustness\n"
                       "0.0-0.4 recommended",
    },
}


def t(key: str) -> str:
    """Get translation for current language."""
    return TRANSLATIONS.get(CURRENT_LANG, TRANSLATIONS["zh_CN"]).get(key, key)


def set_language(lang: str):
    """Switch language."""
    global CURRENT_LANG
    if lang in TRANSLATIONS:
        CURRENT_LANG = lang


def get_language() -> str:
    """Get current language code."""
    return CURRENT_LANG


def get_available_languages() -> dict:
    """Get available languages."""
    return {"zh_CN": "简体中文", "en_US": "English"}
