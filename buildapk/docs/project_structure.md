# OpenIris 项目目录结构规范

## 根目录结构

```
Yolo11forAndroid/
├── .sisyphus/
│   └── plans/
│       └── openiris-yolov11-ncnn-vlm-llm.md    # 工作计划
├── ncnn-android-yolov11/                        # 基础项目（二次开发）
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── assets/                          # 模型文件
│   │   │   │   └── yolov11n_ncnn_model/
│   │   │   │       ├── yolov11n.ncnn.param
│   │   │   │       └── yolov11n.ncnn.bin
│   │   │   ├── java/com/yolo/openiris/          # Java/Kotlin 源码
│   │   │   ├── jni/                             # C++ JNI 源码
│   │   │   └── res/                             # Android 资源
│   │   └── build.gradle
│   └── ...
├── models/                                       # 模型转换输出目录
│   ├── yolov11n.onnx
│   ├── yolov11n.ncnn.param
│   ├── yolov11n.ncnn.bin
│   └── labels.txt                                # 类别标签文件
├── tools/                                        # 工具脚本
│   └── export_yolov11_ncnn.py                    # 模型转换脚本
├── docs/                                         # 文档
│   ├── architecture.md                           # 架构设计
│   └── data_structures.md                        # 数据结构设计
└── tests/                                        # 测试相关
    └── ...
```

## Android 项目内部结构（调整后）

```
app/src/main/
├── AndroidManifest.xml                           # 应用清单
├── assets/                                       # 模型资源
│   ├── models/                                   # 模型目录
│   │   └── yolov11n/
│   │       ├── model.param
│   │       ├── model.bin
│   │       └── labels.txt
│   └── test_images/                              # 测试图片（可选）
├── java/com/yolo/openiris/                       # Java/Kotlin 源码
│   ├── MainActivity.kt                           # 主界面
│   ├── OpenIrisApplication.kt                    # 应用入口
│   ├── config/                                   # 配置模块
│   │   ├── AppConfig.kt                          # 应用配置
│   │   └── ConfigManager.kt                      # 配置管理器
│   ├── detection/                                # 检测模块
│   │   ├── YoloDetector.kt                       # YOLO 检测器封装
│   │   ├── DetectionResult.kt                    # 检测结果数据类
│   │   └── DetectionMode.kt                      # 检测模式枚举
│   ├── camera/                                   # 摄像头模块
│   │   ├── CameraManager.kt                      # 摄像头管理器
│   │   └── CameraPreviewView.kt                  # 预览视图
│   ├── media/                                    # 媒体处理模块
│   │   ├── ImagePicker.kt                        # 图片选择器
│   │   ├── VideoProcessor.kt                     # 视频处理器
│   │   └── FrameExtractor.kt                     # 帧提取器
│   ├── vlm/                                      # VLM 模块
│   │   ├── VlmClient.kt                          # VLM API 客户端
│   │   ├── VlmRequest.kt                         # VLM 请求数据类
│   │   ├── VlmResponse.kt                        # VLM 响应数据类
│   │   └── VlmScheduler.kt                       # VLM 调度器（实时模式）
│   ├── llm/                                      # LLM 模块
│   │   ├── LlmClient.kt                          # LLM API 客户端
│   │   ├── LlmRequest.kt                         # LLM 请求数据类
│   │   ├── LlmResponse.kt                        # LLM 响应数据类
│   │   ├── LlmProvider.kt                        # LLM 提供者接口
│   │   └── CloudLlmProvider.kt                   # 云端 LLM 实现
│   ├── fusion/                                   # 融合模块
│   │   ├── ResultFusion.kt                       # 结果融合器
│   │   ├── FusionResult.kt                       # 融合结果数据类
│   │   └── FusionPromptBuilder.kt                # Prompt 构建器
│   ├── export/                                   # 导出模块
│   │   ├── JsonExporter.kt                       # JSON 导出器
│   │   ├── ImageExporter.kt                      # 图片导出器
│   │   └── VideoExporter.kt                      # 视频导出器（可选）
│   ├── ui/                                       # UI 模块
│   │   ├── components/                           # 可复用组件
│   │   ├── dialogs/                              # 对话框
│   │   └── adapters/                             # 适配器
│   └── utils/                                    # 工具类
│       ├── ImageUtils.kt                         # 图像工具
│       ├── FileUtils.kt                          # 文件工具
│       └── SecurityUtils.kt                      # 安全工具（加密存储）
├── jni/                                          # C++ JNI 源码
│   ├── CMakeLists.txt
│   ├── yolov11ncnn.cpp                           # JNI 接口实现
│   ├── yolov11.cpp                               # YOLO 推理核心
│   ├── yolov11.h                                 # YOLO 头文件
│   ├── ndkcamera.cpp                             # NDK Camera
│   └── ndkcamera.h
└── res/                                          # Android 资源
    ├── layout/
    ├── values/
    ├── drawable/
    └── mipmap-*/
```

## 模型文件命名规范

```
assets/models/
├── {model_name}/
│   ├── model.param          # NCNN 网络结构
│   ├── model.bin            # NCNN 权重
│   └── labels.txt           # 类别标签
```

示例:
```
assets/models/
├── yolov11n/
│   ├── model.param
│   ├── model.bin
│   └── labels.txt
├── yolov11s/
│   ├── model.param
│   ├── model.bin
│   └── labels.txt
└── custom_model/
    ├── model.param
    ├── model.bin
    └── labels.txt
```

## 标签文件格式 (labels.txt)

每行一个类别名称，索引从 0 开始:

```
person
bicycle
car
motorcycle
airplane
...
```

## 配置文件规范

应用配置使用 `EncryptedSharedPreferences` 存储:

```kotlin
// 配置键名规范
const val KEY_API_BASE_URL = "api_base_url"
const val KEY_API_KEY = "api_key"
const val KEY_VLM_MODEL = "vlm_model"
const val KEY_LLM_MODEL = "llm_model"
const val KEY_VLM_INTERVAL = "vlm_interval_seconds"
const val KEY_USE_GPU = "use_gpu"
const val KEY_MODEL_NAME = "model_name"
```

## 日志规范

```
Tag: OpenIris-{Module}
例如:
- OpenIris-Detection
- OpenIris-VLM
- OpenIris-LLM
- OpenIris-Camera
- OpenIris-Export
```
