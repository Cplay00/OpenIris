# OpenIris

<div align="center">

![OpenIris](https://img.shields.io/badge/OpenIris-v0.5--Alpha-blue?style=for-the-badge)

**基于 YOLOv11 + VLM/LLM 的实时目标检测与智能识别系统**

</div>

## 版本更新

### v0.5-Alpha (2026-06-19)

**APP：** 修复暗色模式UI、统一子卡片样式、修复按钮拖动、优化置信度计算

**训练工具：** 优化GUI国际化、改进数据集验证器、优化训练脚本

### v0.4-Alpha
- 初始公开版本

## 快速开始

### Android 应用
``bash
git clone -b alpha https://github.com/Cplay00/OpenIris.git
cd OpenIris/buildapk
./gradlew assembleDebug
``n
### 模型训练
``bash
pip install -r training/requirements.txt
python training/run_gui.py
``n
## License

Apache License 2.0