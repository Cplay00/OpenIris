# OpenIris 项目目录结构规范

## 根目录结构

```
Yolo11forAndroid/
├── buildapk/                                     # 主项目(可直接构建 APK)
│   ├── app/
│   │   ├── build.gradle                          # 应用构建配置(compileSdk 35, minSdk 31)
│   │   └── src/
│   │       ├── main/
│   │       │   ├── AndroidManifest.xml
│   │       │   ├── assets/models/yolov11n/       # NCNN 模型文件
│   │       │   │   ├── model.param
│   │       │   │   ├── model.bin
│   │       │   │   └── labels.txt
│   │       │   ├── java/com/yolo/openiris/       # Kotlin/Java 源码
│   │       │   │   ├── ai/                       # AI 多提供商系统
│   │       │   │   ├── config/                   # 配置管理
│   │       │   │   ├── detection/                # 检测结果模型
│   │       │   │   ├── dialog/                   # 弹窗组件
│   │       │   │   ├── export/                   # 结果导出
│   │       │   │   ├── fusion/                   # 结果融合
│   │       │   │   ├── llm/                      # LLM 客户端(旧)
│   │       │   │   ├── ui/                       # UI 组件
│   │       │   │   ├── utils/                    # 工具类
│   │       │   │   └── vlm/                      # VLM 客户端(旧)
│   │       │   ├── jni/                          # C++ JNI 源码
│   │       │   │   ├── CMakeLists.txt
│   │       │   │   ├── yolov11ncnn.cpp
│   │       │   │   ├── yolov11.cpp
│   │       │   │   ├── ndkcamera.cpp / .h
│   │       │   │   └── omp_stubs.c
│   │       │   └── res/                          # Android 资源
│   │       └── test/                             # 单元测试
│   ├── build.gradle                              # 根构建脚本
│   ├── settings.gradle
│   ├── gradle.properties
│   ├── gradlew / gradlew.bat
│   └── BUILD_INSTRUCTIONS.md
├── ncnn-android-yolov11/                         # 基础参考项目
├── tools/                                        # 工具脚本
│   └── export_yolov11_ncnn.py                    # 模型转换脚本
├── docs/                                         # 文档
├── Package/                                      # APK 输出目录(不提交 Git)
├── .gitignore
├── LICENSE
└── README.md
```

## 源码模块详解

### Activity 层

| 文件 | 职责 |
|------|------|
| `MainActivity.kt` | 主页入口,导航到各检测模式和设置 |
| `ImageDetectActivity.kt` | 图片检测:选择图片 → YOLO → VLM → LLM → 展示/导出 |
| `RealtimeDetectActivity.kt` | 实时检测:摄像头帧 → YOLO → 定时 VLM → LLM |
| `VideoDetectActivity.kt` | 视频检测:视频抽帧 → YOLO → VLM → LLM → 时间轴 |
| `SettingsActivity.kt` | 旧版设置页面(API 基础配置) |
| `AiModelSettingsActivity.kt` | AI 模型管理页面(多提供商/模型配置) |
| `AiProviderEditActivity.kt` | AI 提供商编辑页面 |
| `OpenIrisApplication.kt` | Application 入口,初始化 ConfigManager 和 AiModelManager |

### ai/ — AI 多提供商系统

| 文件 | 职责 |
|------|------|
| `AiProvider.kt` | 提供商数据模型(id, baseUrl, apiKey, apiFormat, models) |
| `AiModel.kt` | 模型数据模型(modelId, hasVision, assignedTasks, customHeaders) |
| `AiModelManager.kt` | 模型管理器单例,统一调用入口(流式/非流式) |
| `AiModelConfigStore.kt` | 持久化存储(apiKey 加密,provider 元数据 SharedPreferences) |
| `AiApiClient.kt` | 统一 HTTP 客户端,支持 OpenAI-compatible 和 Anthropic 格式 |
| `AiResult.kt` | 调用结果模型(AiResult, StructuredOutput, RecognizedObject) |

### config/ — 配置管理

| 文件 | 职责 |
|------|------|
| `AppConfig.kt` | 全局配置数据类(序列化到 EncryptedSharedPreferences) |
| `AppConfigValidator.kt` | 配置校验逻辑 |
| `ConfigManager.kt` | 配置读写管理器单例 |
| `EncryptedApiKeyStore.kt` | API Key 加密存储 |

### detection/ — 检测结果模型

| 文件 | 职责 |
|------|------|
| `DetectionResult.kt` | YOLO 检测结果 + BoundingBox + DetectedObject |
| `AnalysisResult.kt` | 统一分析结果(YOLO + VLM + LLM 融合,含 DetectionMode 枚举) |
| `UnifiedObjectResult.kt` | 统一对象结果(融合 YOLO/VLM/LLM 的派生读模型) |
| `SlidingWindowTracker.kt` | 滑动窗口跟踪器 |

### dialog/ — 弹窗组件

| 文件 | 职责 |
|------|------|
| `ConnectionTestDialog.kt` | AI 连接测试弹窗 |
| `DefaultModelPickerDialog.kt` | 默认模型选择弹窗 |
| `ModelSettingsDialog.kt` | 模型高级设置弹窗 |

### vlm/ — VLM 视觉语言模型客户端(旧版独立模块)

| 文件 | 职责 |
|------|------|
| `VlmClient.kt` | VLM API 调用客户端 |
| `VlmRequestBuilder.kt` | 请求构造器 |
| `VlmApiModels.kt` | API 数据模型 |
| `VlmResult.kt` | VLM 结果数据类 |
| `VlmScheduler.kt` | 定时调度器 |
| `VlmCompatibilityValidator.kt` | 兼容性验证 |

### llm/ — LLM 大语言模型客户端(旧版独立模块)

| 文件 | 职责 |
|------|------|
| `LlmClient.kt` | LLM API 调用客户端 |
| `LlmRequestBuilder.kt` | 请求构造器 |
| `LlmApiModels.kt` | API 数据模型 |
| `LlmResult.kt` | LLM 结果数据类 |

### fusion/ — 结果融合

| 文件 | 职责 |
|------|------|
| `ResultFusion.kt` | YOLO + VLM + LLM 结果融合逻辑 |

### export/ — 结果导出

| 文件 | 职责 |
|------|------|
| `Exporters.kt` | JSON 导出(JsonExporter)和标注图片导出(AnnotatedImageExporter) |

### ui/ — UI 组件

| 文件 | 职责 |
|------|------|
| `CapsuleView.kt` | 胶囊标签视图 |
| `CustomToast.kt` | 自定义 Toast |
| `OverlayView.kt` | 检测框覆盖层 |

### utils/ — 工具类

| 文件 | 职责 |
|------|------|
| `ImageUtils.kt` | 图片处理工具(缩放、绘制检测框等) |

### JNI 层 (C++)

| 文件 | 职责 |
|------|------|
| `yolov11ncnn.cpp` | JNI 桥接:loadModel / detectBitmap / openCamera 等 |
| `yolov11.cpp` | NCNN 推理封装(Inference 类) |
| `ndkcamera.cpp / .h` | NDK Camera2 封装 |
| `omp_stubs.c` | OpenMP stub(兼容 NDK r27) |

## 测试文件

```
app/src/test/java/com/yolo/openiris/
├── config/AppConfigValidatorTest.kt
├── fusion/ResultFusionTest.kt
├── llm/LlmRequestBuilderTest.kt
└── vlm/VlmRequestBuilderTest.kt
```

## 模型文件命名规范

```
assets/models/
├── {model_name}/
│   ├── model.param          # NCNN 网络结构
│   ├── model.bin            # NCNN 权重
│   └── labels.txt           # 类别标签(每行一个,索引从 0 开始)
```

当前支持的模型:`yolov11n`(80 类 COCO 目标)

## 布局文件

| 文件 | 对应 Activity |
|------|---------------|
| `main.xml` | MainActivity |
| `activity_image_detect.xml` | ImageDetectActivity |
| `activity_realtime_detect.xml` | RealtimeDetectActivity |
| `activity_video_detect.xml` | VideoDetectActivity |
| `activity_settings.xml` | SettingsActivity |
| `activity_ai_model_settings.xml` | AiModelSettingsActivity |
| `activity_ai_provider_edit.xml` | AiProviderEditActivity |
| `dialog_connection_test.xml` | ConnectionTestDialog |
| `dialog_default_model_picker.xml` | DefaultModelPickerDialog |
| `dialog_model_settings.xml` | ModelSettingsDialog |

## 日志规范

```
Tag: OpenIris-{Module}
例如:
- OpenIris-Main
- OpenIris-Detection
- OpenIris-VLM
- OpenIris-LLM
- OpenIris-Camera
- OpenIris-Export
- AiModelManager
- AiApiClient
```

## 版本信息

- **当前版本**: 0.3.4-Alpha
- **compileSdk**: 35
- **minSdk**: 31 (Android 12)
- **targetSdk**: 35
- **applicationId**: com.yolo.openiris
