# OpenIris 项目架构流程图

> **版本**: v0.4-Alpha | **更新日期**: 2026-06-16
>
> 本文档以 Mermaid 流程图展示 OpenIris 项目的整体架构、数据流、模块关系与技术能力。

---

## 1. 系统总体架构

```mermaid
graph TB
    subgraph "📱 Android 应用层"
        MainActivity["🏠 MainActivity<br/>主入口"]
        ImageDetect["🖼️ ImageDetectActivity<br/>图片检测"]
        RealtimeDetect["📷 RealtimeDetectActivity<br/>实时检测"]
        VideoDetect["🎬 VideoDetectActivity<br/>视频检测"]
        Settings["⚙️ SettingsActivity<br/>全局设置"]
        AiModelSettings["🤖 AiModelSettingsActivity<br/>AI 模型管理"]
    end

    subgraph "🧠 推理引擎层"
        YOLO["YOLOv11n NCNN<br/>本地目标检测"]
        VLM["VLM 视觉语言模型<br/>图像理解"]
        LLM["LLM 大语言模型<br/>结果融合推理"]
        Fusion["ResultFusion<br/>结果融合引擎"]
    end

    subgraph "🔧 基础设施层"
        ConfigManager["ConfigManager<br/>加密配置管理"]
        AiApiClient["AiApiClient<br/>多提供商 API 客户端"]
        AiModelManager["AiModelManager<br/>模型生命周期管理"]
        Export["Exporters<br/>导出引擎"]
        Tracker["SlidingWindowTracker<br/>滑动窗口追踪"]
    end

    subgraph "☁️ 外部服务"
        OpenAI["OpenAI 兼容 API"]
        Anthropic["Anthropic 兼容 API"]
        CustomAPI["自定义 API 端点"]
    end

    MainActivity --> ImageDetect
    MainActivity --> RealtimeDetect
    MainActivity --> VideoDetect
    MainActivity --> Settings
    MainActivity --> AiModelSettings

    ImageDetect --> YOLO
    ImageDetect --> VLM
    ImageDetect --> LLM
    RealtimeDetect --> YOLO
    RealtimeDetect --> VLM
    VideoDetect --> YOLO
    VideoDetect --> VLM
    VideoDetect --> LLM

    YOLO --> Fusion
    VLM --> Fusion
    LLM --> Fusion

    VLM --> AiApiClient
    LLM --> AiApiClient
    AiApiClient --> AiModelManager
    AiApiClient --> ConfigManager
    AiApiClient --> OpenAI
    AiApiClient --> Anthropic
    AiApiClient --> CustomAPI

    RealtimeDetect --> Tracker
    ImageDetect --> Export
    VideoDetect --> Export

    AiModelSettings --> AiModelManager
    Settings --> ConfigManager
```

---

## 2. YOLOv11 网络架构详解

> 本节详细展示 YOLOv11 的 Backbone / Neck / Head 结构及各子模块内部数据流，
> 对应项目中 `Yolov11Ncnn.java` 使用的 NCNN 推理模型。

### 2.1 整体网络结构 (Backbone → Neck → Head)

```mermaid
graph TB
    subgraph "📷 输入"
        Input["输入图像<br/>640×640×3"]
    end

    subgraph "🦴 Backbone 特征提取"
        B1["Conv<br/>下采样 + 特征映射"]
        B2["Conv"]
        B3["C3k2<br/>c3k=False"]
        B4["Conv"]
        B5["C3k2<br/>c3k=True"]
        B6["Conv"]
        B7["C3k2<br/>c3k=False"]
        B8["Conv"]
        B9["C3k2<br/>c3k=True"]
        B10["SPPF<br/>空间金字塔池化"]
        B11["C2PSA<br/>通道注意力"]
    end

    subgraph "🔗 Neck 多尺度融合 (FPN + PAN)"
        N1["Upsample<br/>上采样"]
        N2["Concat<br/>特征拼接"]
        N3["C3k2<br/>c3k=False"]
        N4["Conv"]
        N5["Upsample<br/>上采样"]
        N6["Concat<br/>特征拼接"]
        N7["C3k2<br/>c3k=False"]
        N8["Conv"]
        N9["C3k2<br/>c3k=True"]
        N10["Conv"]
        N11["C3k2<br/>c3k=False"]
        N12["C3k2<br/>c3k=True"]
        N13["Concat<br/>特征拼接"]
        N14["Concat<br/>特征拼接"]
    end

    subgraph "🎯 Head 检测头"
        H1["Detect<br/>小目标检测"]
        H2["Detect<br/>中目标检测"]
        H3["Detect<br/>大目标检测"]
    end

    subgraph "📊 输出"
        CIoU["CIoU Loss<br/>边界框回归"]
        CLSLoss["CLS Loss<br/>分类损失"]
        Result["检测结果<br/>框 + 类别 + 置信度"]
    end

    Input --> B1 --> B2 --> B3 --> B4 --> B5 --> B6 --> B7 --> B8 --> B9 --> B10 --> B11

    B7 -->|P3| N1
    B9 -->|P4| N2
    N1 --> N2
    N2 --> N3
    N3 --> N4
    N4 -->|P3_out| H1

    B5 -->|P4| N5
    N5 --> N6
    B7 -->|P3 skip| N6
    N6 --> N7
    N7 --> N8
    N8 -->|P4_out| H2

    B11 -->|P5| N13
    N8 --> N9
    N9 --> N10
    N10 --> N14
    N14 --> N12
    N12 -->|P5_out| H3

    H1 --> CIoU
    H1 --> CLSLoss
    H2 --> CIoU
    H2 --> CLSLoss
    H3 --> CIoU
    H3 --> CLSLoss
    CIoU --> Result
    CLSLoss --> Result
```

### 2.2 C3k2 模块 (核心构建块)

```mermaid
flowchart LR
    subgraph "C3k2 c3k=False"
        C3A_In["输入"]
        C3A_Conv1["Conv"]
        C3A_Split["Split"]
        C3A_BN1["Bottleneck ×N"]
        C3A_BN2["Bottleneck ×N"]
        C3A_Concat["Concat"]
        C3A_Conv2["Conv"]
        C3A_Out["输出"]

        C3A_In --> C3A_Conv1 --> C3A_Split
        C3A_Split --> C3A_BN1
        C3A_Split --> C3A_BN2
        C3A_BN1 --> C3A_Concat
        C3A_BN2 --> C3A_Concat
        C3A_Concat --> C3A_Conv2 --> C3A_Out
    end

    subgraph "C3k2 c3k=True"
        C3B_In["输入"]
        C3B_Conv1["Conv"]
        C3B_Split["Split"]
        C3B_C3k1["C3k N=2"]
        C3B_C3k2["C3k N=2"]
        C3B_Concat["Concat"]
        C3B_Conv2["Conv"]
        C3B_Out["输出"]

        C3B_In --> C3B_Conv1 --> C3B_Split
        C3B_Split --> C3B_C3k1
        C3B_Split --> C3B_C3k2
        C3B_C3k1 --> C3B_Concat
        C3B_C3k2 --> C3B_Concat
        C3B_Concat --> C3B_Conv2 --> C3B_Out
    end
```

> **差异**: `c3k=False` 使用 `Bottleneck`；`c3k=True` 使用 `C3k(N=2)` 作为子模块。

### 2.3 C3k 模块与 Bottleneck

```mermaid
flowchart TB
    subgraph "C3k N=?"
        CK_In["输入"]
        CK_Conv1["Conv (1×1)"]
        CK_Conv2["Conv (3×3)"]
        CK_BN["Bottleneck ×N"]
        CK_Concat["Concat"]
        CK_Conv3["Conv"]
        CK_Out["输出"]

        CK_In --> CK_Conv1 --> CK_Conv2
        CK_In --> CK_BN
        CK_Conv2 --> CK_BN
        CK_BN --> CK_Concat
        CK_In --> CK_Concat
        CK_Concat --> CK_Conv3 --> CK_Out
    end

    subgraph "Bottleneck"
        BN_In["输入"]
        BN_Conv1["Conv (3×3)"]
        BN_Conv2["Conv (3×3)"]
        BN_Out["输出"]
        BN_Shortcut["Shortcut = ?<br/>(残差连接)"]

        BN_In --> BN_Conv1 --> BN_Conv2
        BN_In -.->|Shortcut| BN_Shortcut
        BN_Shortcut --> BN_Out
        BN_Conv2 --> BN_Out
    end
```

### 2.4 C2PSA 与 PSABlock (通道注意力)

```mermaid
flowchart TB
    subgraph "C2PSA"
        C2_In["输入"]
        C2_Conv1["Conv"]
        C2_PSA1["PSABlock ×N"]
        C2_Concat["Concat"]
        C2_Conv2["Conv"]
        C2_Out["输出"]

        C2_In --> C2_Conv1
        C2_In --> C2_Concat
        C2_Conv1 --> C2_PSA1
        C2_PSA1 --> C2_Concat
        C2_Concat --> C2_Conv2 --> C2_Out
    end

    subgraph "PSABlock (Self-Attention)"
        PSA_In["输入"]
        PSA_Attn["Attention<br/>自注意力机制"]
        PSA_Conv1["Conv"]
        PSA_Conv2["Conv"]
        PSA_Shortcut["Shortcut = ?<br/>(残差连接)"]
        PSA_Out["输出"]

        PSA_In --> PSA_Attn
        PSA_In -.->|Shortcut| PSA_Shortcut
        PSA_Attn --> PSA_Conv1 --> PSA_Conv2
        PSA_Conv2 --> PSA_Out
        PSA_Shortcut --> PSA_Out
    end
```

### 2.5 SPPF 空间金字塔池化

```mermaid
flowchart LR
    SPPF_In["输入"]
    SPPF_Conv1["Conv (1×1)"]
    SPPF_MP1["MaxPool2d<br/>k=5"]
    SPPF_MP2["MaxPool2d<br/>k=5"]
    SPPF_MP3["MaxPool2d<br/>k=5"]
    SPPF_Concat["Concat<br/>多尺度特征"]
    SPPF_Conv2["Conv (1×1)"]
    SPPF_Out["输出"]

    SPPF_In --> SPPF_Conv1 --> SPPF_MP1 --> SPPF_MP2 --> SPPF_MP3
    SPPF_Conv1 --> SPPF_Concat
    SPPF_MP1 --> SPPF_Concat
    SPPF_MP2 --> SPPF_Concat
    SPPF_MP3 --> SPPF_Concat
    SPPF_Concat --> SPPF_Conv2 --> SPPF_Out
```

### 2.6 Detect 检测头

```mermaid
flowchart LR
    D_In["特征图输入"]
    D_Conv1["Conv"]
    D_Conv2["Conv"]
    D_DWConv1["DWConv<br/>深度可分离卷积"]
    D_Conv3["Conv"]
    D_DWConv2["DWConv"]
    D_Conv4["Conv"]
    D_Conv5["Conv (Conv2d)"]
    D_CIoU["CIoU<br/>边界框回归"]
    D_CLS["CLS Loss<br/>分类损失"]

    D_In --> D_Conv1 --> D_Conv2 --> D_Conv5 --> D_CIoU
    D_In --> D_DWConv1 --> D_Conv3 --> D_DWConv2 --> D_Conv4 --> D_Conv5 --> D_CLS
```

---

### 2.7 模型规格总览

| 参数 | 值 | 说明 |
|------|-----|------|
| **模型** | YOLOv11n (Nano) | 移动端最优轻量模型 |
| **输入尺寸** | 640 × 640 × 3 | RGB 图像 |
| **精度** | FP16 | NCNN 推理引擎 |
| **激活函数** | SiLU | 不可使用 ReLU |
| **输出层** | 3 个 Detect Head | 小/中/大目标 |
| **损失函数** | CIoU + CLSLoss | 边界框 + 分类 |
| **核心模块** | C3k2 / C3k / C2PSA / SPPF | 特征提取与融合 |
| **注意力** | PSABlock (Self-Attention) | C2PSA 内嵌 |
| **输入归一化** | /255, mean=0 | Ultralytics 默认 |
| **ONNX opset** | ≥12 | NCNN 兼容要求 |

---

## 3. 检测模式数据流

### 3.1 实时检测模式

```mermaid
flowchart LR
    subgraph "📷 输入"
        Camera["Camera2 实时帧"]
    end

    subgraph "🔍 本地推理"
        NCNN["NCNN YOLOv11n<br/>每帧推理"]
        Overlay["OverlayView<br/>实时绘制检测框"]
        Tracker2["SlidingWindowTracker<br/>15s 滑动窗口统计"]
    end

    subgraph "🤖 AI 增强"
        Scheduler["VlmScheduler<br/>按间隔调度"]
        VLMApi["VLM API<br/>图像理解"]
        LLMApi["LLM API<br/>融合推理"]
    end

    subgraph "📊 输出"
        Panel["实时检测面板"]
        Summary["融合摘要"]
    end

    Camera --> NCNN
    NCNN --> Overlay
    NCNN --> Tracker2
    NCNN -->|关键帧| Scheduler
    Scheduler --> VLMApi
    VLMApi --> LLMApi
    LLMApi --> Summary
    Tracker2 --> Panel
```

### 3.2 图片检测模式

```mermaid
flowchart LR
    subgraph "🖼️ 输入"
        UserPick["用户选择图片"]
        Bitmap["Bitmap 预处理"]
    end

    subgraph "🔍 本地推理"
        NCNN2["NCNN YOLOv11n<br/>目标检测"]
        DetResult["DetectionResult<br/>边界框 + 置信度"]
    end

    subgraph "🤖 AI 增强"
        VLM2["VLM 视觉分析<br/>图像语义理解"]
        LLM2["LLM 融合推理<br/>综合判定"]
    end

    subgraph "📊 输出"
        Analysis["AnalysisResult<br/>统一对象 + 融合摘要"]
        Cards["三栏卡片展示<br/>YOLO / AI / 综合"]
        Export2["导出<br/>JSON + 标注图片"]
    end

    UserPick --> Bitmap
    Bitmap --> NCNN2
    NCNN2 --> DetResult
    DetResult --> VLM2
    VLM2 --> LLM2
    DetResult --> Fusion2["ResultFusion"]
    LLM2 --> Fusion2
    Fusion2 --> Analysis
    Analysis --> Cards
    Analysis --> Export2
```

### 3.3 视频检测模式

```mermaid
flowchart LR
    subgraph "🎬 输入"
        UserVideo["用户选择视频"]
        MediaCodec["MediaCodec 解码"]
    end

    subgraph "🔍 逐帧推理"
        FrameSample["抽帧策略"]
        NCNN3["NCNN YOLOv11n<br/>逐帧检测"]
        DetResults["List&lt;DetectionResult&gt;"]
    end

    subgraph "🤖 AI 分析"
        VLM3["VLM 关键帧分析"]
        LLM3["LLM 汇总推理"]
    end

    subgraph "📊 输出"
        Timeline["时间轴展示"]
        VideoSummary["视频摘要"]
        Export3["导出<br/>JSON + 标注图片"]
    end

    UserVideo --> MediaCodec
    MediaCodec --> FrameSample
    FrameSample --> NCNN3
    NCNN3 --> DetResults
    DetResults -->|关键帧| VLM3
    VLM3 --> LLM3
    DetResults --> Fusion3["ResultFusion"]
    LLM3 --> Fusion3
    Fusion3 --> Timeline
    Fusion3 --> VideoSummary
    Fusion3 --> Export3
```

---

## 4. AI 多提供商架构

```mermaid
flowchart TB
    subgraph "🤖 AI 请求层"
        AiRequest["AI 检测请求"]
        FormatDetect["ApiFormat 格式检测<br/>OPENAI_COMPATIBLE / ANTHROPIC"]
    end

    subgraph "📡 传输层"
        RequestBuilder["RequestBuilder<br/>请求构建"]
        StreamParser["流式响应解析<br/>SSE"]
        ErrorRetry["错误重试机制"]
    end

    subgraph "🔑 安全层"
        EncryptedStore["EncryptedApiKeyStore<br/>加密密钥存储"]
        SSRF["SSRF 防护<br/>URL 验证 + HTTPS 强制"]
        NetworkSec["NetworkSecurityConfig<br/>禁止明文流量"]
    end

    subgraph "🏢 提供商"
        Provider1["OpenAI / GPT-4o"]
        Provider2["Anthropic / Claude"]
        Provider3["DeepSeek"]
        Provider4["Qwen (通义千问)"]
        Provider5["自定义端点"]
    end

    subgraph "📋 模型管理"
        AiModelMgr["AiModelManager"]
        AiProvider["AiProvider<br/>提供商配置"]
        AiModel["AiModel<br/>模型元数据"]
        ModelConfigStore["AiModelConfigStore<br/>模型配置持久化"]
    end

    AiRequest --> FormatDetect
    FormatDetect --> RequestBuilder
    RequestBuilder --> StreamParser
    StreamParser --> ErrorRetry
    ErrorRetry --> EncryptedStore
    EncryptedStore --> SSRF
    SSRF --> NetworkSec
    NetworkSec --> Provider1
    NetworkSec --> Provider2
    NetworkSec --> Provider3
    NetworkSec --> Provider4
    NetworkSec --> Provider5

    AiModelMgr --> AiProvider
    AiModelMgr --> AiModel
    AiModelMgr --> ModelConfigStore
```

---

## 5. 训练平台工作流

```mermaid
flowchart TB
    subgraph "📊 数据准备"
        Dataset["数据集<br/>COCO / 自定义"]
        Augment["数据增强<br/>hyp_augment.yaml"]
        Split["数据集划分<br/>train / val / test"]
        DatasetBuilder["dataset_builder<br/>数据集构建工具"]
    end

    subgraph "🏋️ 模型训练"
        TrainGUI["tkinter GUI<br/>图形化训练界面"]
        I18N["i18n 国际化<br/>zh_CN / en_US"]
        Ultralytics["Ultralytics 框架"]
        TrainScript["train.py<br/>训练脚本"]
        HyperParams["超参数配置<br/>hyp_train.yaml"]
    end

    subgraph "🔬 模型改进(可选)"
        CBAM["CBAM 注意力"]
        SE["SE 注意力"]
        GhostConv["GhostConv"]
        Enhanced["Enhanced 模型"]
    end

    subgraph "📦 模型导出"
        ExportPipeline["export_pipeline.py<br/>导出流水线"]
        ONNX["ONNX 导出<br/>opset ≥ 12"]
        NCNNExport["NCNN 转换<br/>onnx2ncnn"]
        ModelParam["model.param<br/>网络结构"]
        ModelBin["model.bin<br/>权重文件"]
        Labels["labels.txt<br/>类别标签"]
    end

    subgraph "📱 部署"
        CopyToAssets["复制到 assets/models/"]
        BuildAPK["Gradle 构建 APK"]
        APK["OpenIris APK"]
    end

    Dataset --> DatasetBuilder
    DatasetBuilder --> Augment
    Augment --> Split
    Split --> TrainGUI
    TrainGUI --> I18N
    TrainGUI --> Ultralytics
    Ultralytics --> TrainScript
    TrainScript --> HyperParams

    TrainScript --> CBAM
    TrainScript --> SE
    TrainScript --> GhostConv
    TrainScript --> Enhanced

    TrainScript --> ExportPipeline
    ExportPipeline --> ONNX
    ONNX --> NCNNExport
    NCNNExport --> ModelParam
    NCNNExport --> ModelBin
    NCNNExport --> Labels

    ModelParam --> CopyToAssets
    ModelBin --> CopyToAssets
    Labels --> CopyToAssets
    CopyToAssets --> BuildAPK
    BuildAPK --> APK
```

---

## 6. 安全架构

```mermaid
flowchart LR
    subgraph "🔒 安全防护"
        subgraph "数据安全"
            Encrypted["EncryptedSharedPreferences<br/>API Key 加密存储"]
            ApiKeyMask["apiKey 脱敏<br/>日志显示 ***"]
            DebugGate["BuildConfig.DEBUG<br/>敏感日志门控"]
        end

        subgraph "网络安全"
            HTTPS["强制 HTTPS"]
            SSRFCheck["SSRF 防护<br/>阻止内网地址"]
            NetSec["NetworkSecurityConfig<br/>禁止明文流量"]
            NoInterceptor["Release 移除<br/>HttpLoggingInterceptor"]
        end

        subgraph "代码安全"
            ProGuard["ProGuard 收紧<br/>仅保留 Gson 模型类"]
            SafeCall["安全调用<br/>消除 Force Unwrap"]
            Android14["Android 14+ 权限<br/>READ_MEDIA_VISUAL_USER_SELECTED"]
        end
    end

    Encrypted --> ApiKeyMask
    ApiKeyMask --> DebugGate
    HTTPS --> SSRFCheck
    SSRFCheck --> NetSec
    NetSec --> NoInterceptor
    ProGuard --> SafeCall
    SafeCall --> Android14
```

---

## 7. 导出系统

```mermaid
flowchart LR
    subgraph "📊 数据源"
        DetRes["DetectionResult<br/>检测结果"]
        AiRes["AiResult<br/>AI 分析结果"]
        AnalysisRes["AnalysisResult<br/>融合结果"]
        Bitmap3["标注图片<br/>Bitmap"]
    end

    subgraph "📤 导出引擎"
        JsonExport["JSON 导出<br/>结构化数据"]
        ImageExport["图片导出<br/>标注框叠加"]
        VideoExport["视频导出<br/>逐帧标注"]
    end

    subgraph "💾 输出"
        AppStorage["App 外部存储"]
        CustomPath["自定义导出路径"]
    end

    DetRes --> JsonExport
    DetRes --> ImageExport
    AiRes --> JsonExport
    AnalysisRes --> JsonExport
    Bitmap3 --> ImageExport
    AnalysisRes --> VideoExport

    JsonExport --> AppStorage
    JsonExport --> CustomPath
    ImageExport --> AppStorage
    ImageExport --> CustomPath
    VideoExport --> AppStorage
```

---

## 8. UI 组件架构

```mermaid
graph TB
    subgraph "🎨 UI 组件"
        Overlay["OverlayView<br/>实时检测框绘制"]
        Capsule["CapsuleView<br/>胶囊式结果展示"]
        Toast["CustomToast<br/>自定义提示"]
    end

    subgraph "📋 弹窗组件"
        ConnTest["ConnectionTestDialog<br/>连接测试"]
        ModelPicker["DefaultModelPickerDialog<br/>默认模型选择"]
        ModelSettings["ModelSettingsDialog<br/>模型参数设置"]
    end

    subgraph "📐 布局"
        MainLayout["main.xml"]
        ImageLayout["activity_image_detect.xml"]
        RealtimeLayout["activity_realtime_detect.xml"]
        VideoLayout["activity_video_detect.xml"]
        SettingsLayout["activity_settings.xml"]
        AiSettingsLayout["activity_ai_model_settings.xml"]
        ProviderEditLayout["activity_ai_provider_edit.xml"]
    end

    Overlay --> RealtimeLayout
    Capsule --> ImageLayout
    Capsule --> VideoLayout
    ConnTest --> AiSettingsLayout
    ModelPicker --> AiSettingsLayout
    ModelSettings --> AiSettingsLayout
```

---

## 9. 技术能力矩阵

| 能力域 | 技术组件 | 说明 |
|--------|----------|------|
| **本地推理** | YOLOv11n + NCNN | FP16 精度,640×640 输入,80 类 COCO 目标 |
| **AI 视觉理解** | VLM (OpenAI 兼容 API) | 多供应商支持,流式响应 |
| **AI 语义融合** | LLM (OpenAI 兼容 API) | YOLO + VLM 结果智能融合 |
| **实时检测** | Camera2 + OverlayView | 15s 滑动窗口统计,帧级叠加 |
| **图片分析** | Bitmap + EXIF 处理 | 支持 HEIF/HEIC,高分辨率 |
| **视频分析** | MediaCodec 解码 | 抽帧策略,时间轴展示 |
| **安全存储** | EncryptedSharedPreferences | API Key 加密,SSRF 防护 |
| **多提供商** | AiApiClient | OpenAI + Anthropic 格式兼容 |
| **结果导出** | JSON / 标注图片 / 视频 | 可配置导出路径 |
| **训练平台** | Ultralytics + tkinter GUI | i18n,注意力机制,一键导出部署 |
| **并发安全** | CopyOnWriteArrayList | 竞态条件防护,线程安全 |
| **兼容性** | Android 12+ (API 31) | targetSdk 36,Material Design 3 |

---

## 10. 版本演进

```mermaid
timeline
    title OpenIris 版本演进
    section v0.1-v0.2
        基础功能 : YOLO NCNN 推理
                  : 单一 VLM 客户端
                  : 实时 + 图片检测
    section v0.3
        多模式 : 视频检测
               : 多 AI 提供商
               : 结果融合引擎
               : 训练平台 GUI
    section v0.4
        安全加固 : 加密存储
                 : SSRF 防护
                 : 竞态修复
                 : ProGuard 收紧
                 : Android 16 目标
```

---

## 11. 模块依赖关系

```mermaid
graph LR
    subgraph "Activities"
        Main["MainActivity"]
        Img["ImageDetectActivity"]
        Rt["RealtimeDetectActivity"]
        Vid["VideoDetectActivity"]
    end

    subgraph "Core"
        Yolo["Yolov11Ncnn"]
        AiClient["AiApiClient"]
        AiMgr["AiModelManager"]
        Fusion4["ResultFusion"]
        Export4["Exporters"]
    end

    subgraph "Config"
        Cfg["ConfigManager"]
        EncKey["EncryptedApiKeyStore"]
        AppCfg["AppConfig"]
    end

    subgraph "Detection"
        Det["DetectionResult"]
        AiRes4["AiResult"]
        Analysis4["AnalysisResult"]
        Unified["UnifiedObjectResult"]
        Sliding["SlidingWindowTracker"]
    end

    Main --> Img
    Main --> Rt
    Main --> Vid

    Img --> Yolo
    Img --> AiClient
    Img --> Fusion4
    Img --> Export4

    Rt --> Yolo
    Rt --> AiClient
    Rt --> Sliding

    Vid --> Yolo
    Vid --> AiClient
    Vid --> Fusion4
    Vid --> Export4

    AiClient --> AiMgr
    AiClient --> Cfg
    Cfg --> EncKey
    Cfg --> AppCfg
    AiMgr --> AiRes4

    Yolo --> Det
    Fusion4 --> Det
    Fusion4 --> AiRes4
    Fusion4 --> Analysis4
    Fusion4 --> Unified
    Export4 --> Analysis4
```

---

*本文档基于 OpenIris v0.4-Alpha 源码分析自动生成。*
