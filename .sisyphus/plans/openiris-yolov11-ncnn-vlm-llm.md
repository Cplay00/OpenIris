# OpenIris Android YOLOv11 + NCNN + VLM/LLM 工作计划

> 版本：v1.0
> 生成日期：2025-04-27
> 目标计划路径：`.sisyphus/plans/openiris-yolov11-ncnn-vlm-llm.md`
> 状态：已确认，具备执行条件

---

## 1. 项目核心目标

构建 Android 原生应用 **OpenIris**，基于 `ncnn-android-yolov11` 二次开发，实现 YOLOv11 本地目标检测，结合 OpenAI-compatible VLM 与 LLM，输出结构化中文分析结果。

| 项 | 内容 |
|---|---|
| 应用名 | OpenIris |
| 包名 | com.yolo.openiris |
| 最低 Android 版本 | Android 12 (API 31) |
| 目标芯片 | 骁龙 865 及以上 |
| 技术栈 | Kotlin + Java/C++ JNI + NCNN |
| 基础项目 | https://github.com/gaoxumustwin/ncnn-android-yolov11 |

### 必须功能（Must Have）
1. 静态图片目标检测 + VLM 识别 + LLM 融合摘要。
2. 实时摄像头目标检测 + VLM 间隔识别 + LLM 融合摘要。
3. OpenAI-compatible VLM/LLM 配置（Base URL、API Key、模型名、调用间隔）。
4. JSON 导出。
5. 标注图片导出。
6. NCNN CPU + Vulkan GPU 推理。
7. 中文 UI，内部字段英文，摘要中文。
8. TDD 优先（纯逻辑模块写单元测试）。

### 第二优先级（Second Priority）
1. 视频文件检测 + 时间轴摘要。
2. 标注视频导出。

### 调研/预留（Research / Reserved）
1. 本地 LLM 可行性调研。
2. Snapdragon NPU 可行性调研。

---

## 2. 已确认需求决策

| # | 决策项 | 选择 |
|---|---|---|
| 1 | Android 技术栈 | A - 原生 Kotlin + Java/C++ JNI + NCNN |
| 2 | 目标设备 | Android 12，骁龙 865+ |
| 3 | 代码基础 | A - 基于 ncnn-android-yolov11 二次开发 |
| 4 | YOLO 模型来源 | A - 已有 .pt，需转换为 NCNN |
| 5 | 模型管理 | C - 默认 assets 内置，预留切换接口 |
| 6 | 检测类别 | C - 支持 COCO + 自定义 labels.txt |
| 7 | VLM 接口 | C - OpenAI-compatible，Base URL + API Key + Model |
| 8 | LLM 接口 | C - OpenAI-compatible，默认云端，预留本地 |
| 9 | 实时 VLM 间隔 | D - 默认 5 秒，可配置 2/5/10/30/自定义 |
| 10 | 离线能力 | B - YOLO 本地，VLM/LLM 走 API |
| 11 | 测试策略 | A - TDD 优先 |
| 12 | 输出形式 | C + 可选 D/E |
| 13 | 应用名/包名 | OpenIris / com.yolo.openiris |
| 14 | 性能目标 | A - 默认目标 |
| 15 | 默认 VLM 模型 | qwen3.5-35b-a3b |
| 16 | 默认 LLM 模型 | deepseek-v4-flash |
| 17 | 配置存储 | A - 本地加密存储，不硬编码 API Key |

---

## 3. 性能目标

| 场景 | 目标 |
|---|---|
| 实时 YOLO 检测 | 骁龙 865+ 上 ≥ 15 FPS |
| 理想 FPS | 20–30 FPS（视模型和分辨率） |
| VLM 调用 | 默认每 5 秒一次，不阻塞 YOLO |
| 图片检测 | YOLO 本地推理 < 1 秒 |
| 视频检测 | 允许离线处理，不强制实时 |

---

## 4. 技术风险与应对

### 风险 1：qwen3.5-35b-a3b 视觉能力未确认
- **应对**：Wave 2 中加入早期 VLM 兼容性验证任务，使用固定测试图片调用接口，确认 image input 支持及返回格式稳定性。

### 风险 2：NCNN 与 Snapdragon NPU
- **应对**：首版只承诺 NCNN CPU + Vulkan GPU；NPU 作为 Wave 6 独立调研项，评估 SNPE/QNN 路线，不硬承诺。

### 风险 3：视频检测复杂度
- **应对**：视频检测纳入计划但列为第二优先级；标注视频导出可选。

---

## 5. 推荐执行波次

### Wave 1：项目基础与模型转换基础
1. 检查本地是否存在 ncnn-android-yolov11；若不存在则克隆。
2. 调整应用名为 OpenIris，包名为 com.yolo.openiris。
3. 梳理现有 NCNN / JNI / Camera 结构。
4. 建立 .pt → ONNX → NCNN param/bin 转换流程脚本。
5. 规范模型文件和标签文件目录结构。
6. 建立测试基础设施评估（JUnit、Espresso、真机环境）。
7. 设计配置与结果数据结构草案。

### Wave 2：核心数据结构、配置、API Adapter
1. 设计 YOLO 检测结果结构（label, confidence, bbox）。
2. 设计 VLM 结果结构（name, count, attributes）。
3. 设计 LLM 融合结果结构（summary, objects, discrepancies）。
4. 设计统一对象结果模型。
5. 设计 OpenAI-compatible 配置模型。
6. 实现配置校验逻辑（TDD）。
7. 实现 API Key 本地加密存储（EncryptedSharedPreferences）。
8. 实现 VLM 请求构造器（支持 image_url / base64）。
9. 实现 LLM 请求构造器（支持结构化 prompt）。
10. 默认模型名：
    - VLM：qwen3.5-35b-a3b
    - LLM：deepseek-v4-flash
11. 加入 VLM 兼容性验证任务（固定图片测试）。

### Wave 3：图片检测链路
1. 图片选择器（Gallery / File Picker）。
2. 图片预处理（Resize、Normalize、NCNN Mat 转换）。
3. YOLOv11 NCNN 图片推理。
4. 检测框绘制与 Overlay。
5. VLM 图片识别调用。
6. LLM 融合 YOLO + VLM 结果。
7. 中文摘要展示面板。
8. JSON 导出功能。
9. 标注图片导出功能。
10. 图片模式端到端 QA。

### Wave 4：实时检测链路
1. CameraX 或现有摄像头链路评估与集成。
2. 实时帧输入与 YOLO 推理节流。
3. Overlay 实时绘制检测框。
4. Vulkan GPU 开关配置。
5. 实时 FPS 统计与显示。
6. VLM 间隔触发器（默认 5 秒，可配置）。
7. VLM 请求队列与防堆积机制。
8. LLM 融合最近一段时间 YOLO/VLM 结果。
9. 实时模式中文结果面板。
10. 真机性能验证（骁龙 865+，≥ 15 FPS）。

### Wave 5：视频检测链路（第二优先级）
1. 视频选择器。
2. 视频元数据读取（时长、分辨率、帧率）。
3. 抽帧策略（时间间隔或关键帧）。
4. YOLO 对抽样帧检测。
5. VLM 按时间间隔识别关键帧。
6. LLM 生成视频整体摘要。
7. 视频时间轴结果展示。
8. 视频 JSON 导出。
9. 标注视频导出（可选实现或调研）。

### Wave 6：本地 LLM 与 NPU 调研
1. 调研 Android 本地 LLM 框架：
   - llama.cpp Android
   - MLC LLM
   - ExecuTorch
   - ONNX Runtime Mobile
   - MNN
2. 评估小模型内存占用、推理速度、APK 包体增量。
3. 设计 `LlmProvider` 抽象接口，预留本地实现。
4. 调研 Snapdragon NPU：
   - Qualcomm SNPE
   - Qualcomm QNN
   - 与 NCNN 主路线差异
5. 输出调研结论报告，不强制落地首版。

### Final Wave：最终验证
1. **计划符合性审查**
   - 所有 Must Have 是否已实现。
   - 所有 Must NOT Have 是否未违反。
2. **代码质量审查**
   - Android Gradle Build 通过。
   - 单元测试全部通过。
   - 静态代码检查（Lint / ktlint）。
   - JNI / C++ 编译无警告。
3. **真机 QA**
   - 静态图片检测端到端。
   - 实时检测 FPS 达标。
   - VLM 兼容性通过。
   - LLM 融合结果正确。
   - JSON 导出正确。
   - 标注图片导出正确。
   - Vulkan GPU 开关生效。
   - 骁龙 865+ 性能目标达成。
4. **范围一致性检查**
   - 未擅自扩大范围。
   - 视频检测作为第二优先级。
   - 本地 LLM 和 NPU 仅调研/预留。

---

## 6. 验收标准

### 图片检测
- [ ] 能选择本地图片。
- [ ] YOLO 输出 bbox、label、confidence。
- [ ] VLM 输出对象名称、数量、属性。
- [ ] LLM 输出中文融合摘要。
- [ ] 可导出 JSON。
- [ ] 可导出带标注图片。

### 实时检测
- [ ] 能打开摄像头。
- [ ] 能实时显示 YOLO 检测框。
- [ ] 骁龙 865+ 上 YOLO ≥ 15 FPS。
- [ ] VLM 默认每 5 秒调用一次。
- [ ] VLM 调用不阻塞 YOLO。
- [ ] 不发生请求堆积。
- [ ] LLM 能整合最近 YOLO/VLM 结果并输出中文摘要。

### 视频检测
- [ ] 能选择本地视频。
- [ ] 能按策略抽帧。
- [ ] 能对抽样帧执行 YOLO。
- [ ] 能按时间间隔触发 VLM。
- [ ] 能生成视频级摘要。
- [ ] 能导出 JSON。
- [ ] 标注视频导出作为可选项。

### 模型转换
- [ ] .pt → ONNX → NCNN param/bin 流程可复现。
- [ ] 转换后模型能在 Android 端加载。
- [ ] 标签文件能与模型类别匹配。
- [ ] 支持 COCO 或自定义 labels。

### 配置安全
- [ ] API Key 不硬编码。
- [ ] API Key 本地加密存储。
- [ ] 支持 Base URL。
- [ ] 支持自定义 VLM/LLM 模型名。
- [ ] 支持实时 VLM 间隔配置。

---

## 7. 配置默认值

| 配置项 | 默认值 |
|---|---|
| API Base URL | https://api.openai.com/v1 |
| VLM Model | qwen3.5-35b-a3b |
| LLM Model | deepseek-v4-flash |
| 实时 VLM 间隔 | 5 秒 |
| GPU 加速 | 自动检测，优先 Vulkan |
| 语言输出 | 中文 |

---

## 8. 备注

- 本计划基于用户已确认的全部需求决策生成。
- 任何新增功能或修改默认值需重新评估计划。
- 建议在具备文件操作权限的环境中执行 `/start-work` 开始开发。
