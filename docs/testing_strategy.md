# OpenIris 测试基础设施评估

## 1. 测试策略总览

根据需求确认,采用 **TDD 优先** 策略,混合自动化测试与真机 QA。

```
┌─────────────────────────────────────────────┐
│              测试金字塔                        │
├─────────────────────────────────────────────┤
│  单元测试 (Unit Tests)                        │
│  - 纯逻辑模块                                 │
│  - Kotlin/Java 代码                          │
│  - JUnit 4/5                                 │
├─────────────────────────────────────────────┤
│  集成测试 (Integration Tests)                 │
│  - API 客户端测试                            │
│  - 模块间交互                                │
│  - Robolectric (部分 Android 依赖)            │
├─────────────────────────────────────────────┤
│  真机 QA (Device QA)                          │
│  - JNI/NCNN 推理                              │
│  - Camera 实时检测                            │
│  - GPU/Vulkan 加速                            │
│  - 性能基准                                   │
└─────────────────────────────────────────────┘
```

## 2. 单元测试模块

### 2.1 可测试模块列表

| 模块 | 测试内容 | 工具 | 优先级 |
|------|---------|------|--------|
| ConfigManager | 配置读写、加密存储 | JUnit + Mockito | 高 |
| AppConfig | 数据类序列化/反序列化 | JUnit | 高 |
| AiModelConfigStore | 提供商/模型 CRUD、apiKey 加密存储 | JUnit + Mockito | 高 |
| AiApiClient | HTTP 请求构建、流式/非流式响应解析、错误处理 | JUnit + MockWebServer | 高 |
| AiModelManager | 模型选择、调用路由、超时处理 | JUnit + Mockito | 高 |
| DetectionResult | 数据转换、JSON 序列化 | JUnit | 高 |
| VlmRequest/Response | 请求构建、响应解析 | JUnit | 高 |
| LlmRequest/Response | 请求构建、响应解析 | JUnit | 高 |
| ResultFusion | YOLO + VLM 结果合并逻辑 | JUnit | 高 |
| JsonExporter | JSON 导出格式校验 | JUnit | 中 |
| VlmScheduler | 间隔调度、防堆积 | JUnit | 中 |

### 2.2 单元测试示例结构

```
app/src/test/java/com/yolo/openiris/
├── ai/
│   ├── AiApiClientTest.kt          # HTTP 请求/响应解析
│   ├── AiModelConfigStoreTest.kt   # 持久化存储 CRUD
│   └── AiModelManagerTest.kt       # 模型调用路由
├── config/
│   └── AppConfigValidatorTest.kt   # ✅ 已实现
├── vlm/
│   └── VlmRequestBuilderTest.kt    # ✅ 已实现
├── llm/
│   └── LlmRequestBuilderTest.kt    # ✅ 已实现
└── fusion/
    └── ResultFusionTest.kt          # ✅ 已实现
```

## 3. 集成测试

### 3.1 API 客户端测试

使用 **MockWebServer** 模拟 OpenAI-compatible API:

```kotlin
// 测试 VLM 客户端
@Test
fun testVlmClient_imageRecognition() {
    val mockServer = MockWebServer()
    mockServer.enqueue(MockResponse()
        .setResponseCode(200)
        .setBody("""
            {
              "id": "test",
              "choices": [{
                "message": {
                  "role": "assistant",
                  "content": "{\"objects\":[{\"name\":\"person\",\"count\":2}]}"
                }
              }]
            }
        """.trimIndent()))

    val client = VlmClient(baseUrl = mockServer.url("/").toString())
    val result = client.recognize(testImageBase64)

    assertEquals(2, result.objects[0].count)
}
```

### 3.2 融合流程测试

```kotlin
@Test
fun testFusion_yoloAndVlmMatch() {
    val yoloResult = DetectionResult(objects = listOf(
        DetectedObject("person", 0, 0.9f, BoundingBox(0f, 0f, 100f, 200f)),
        DetectedObject("car", 1, 0.8f, BoundingBox(50f, 50f, 150f, 100f))
    ))

    val vlmResult = VlmResult(objects = listOf(
        VlmObject("person", 2, listOf("standing")),
        VlmObject("car", 1, listOf("red"))
    ))

    val fused = ResultFusion.fuse(yoloResult, vlmResult)

    assertTrue(fused.objects.any { it.name == "person" && it.count == 2 })
    assertTrue(fused.objects.any { it.evidence.containsAll(listOf("yolo", "vlm")) })
}
```

## 4. 真机 QA 测试场景

### 4.1 测试设备要求

| 设备 | 芯片 | 最低 Android | 用途 |
|------|------|-------------|------|
| 主力测试机 | 骁龙 865+ | Android 12 | 功能验证、性能基准 |
| 中端设备 | 骁龙 7 系 | Android 12 | 兼容性测试 |
| 旧设备 | 骁龙 855 | Android 10 | 降级测试(可选) |

### 4.2 功能测试清单

#### 图片检测模式
- [ ] 正常图片检测(人物、车辆、动物)
- [ ] 空图片/无对象图片
- [ ] 模糊/低质量图片
- [ ] 大分辨率图片(> 4K)
- [ ] VLM API 超时/失败处理
- [ ] LLM 融合结果正确性
- [ ] JSON 导出格式校验
- [ ] 标注图片导出质量

#### 实时检测模式
- [ ] 摄像头正常开启/关闭
- [ ] 前后摄像头切换
- [ ] 检测框实时绘制
- [ ] FPS 显示正确
- [ ] GPU/CPU 切换
- [ ] VLM 间隔触发(验证不每帧调用)
- [ ] VLM 请求防堆积
- [ ] 网络断开后恢复
- [ ] 后台/前台切换
- [ ] 长时间运行稳定性(30 分钟)

#### 视频检测模式
- [ ] 短视频(< 10 秒)
- [ ] 长视频(> 5 分钟)
- [ ] 不同分辨率视频
- [ ] 抽帧策略正确性
- [ ] 时间轴结果展示
- [ ] JSON 导出完整性

#### 配置与存储
- [ ] API Key 加密存储
- [ ] 配置持久化
- [ ] 无效配置提示
- [ ] 模型切换(如支持)

### 4.3 性能测试基准

```kotlin
// 性能测试指标
object PerformanceBenchmarks {
    const val MIN_FPS_REALTIME = 15f
    const val TARGET_FPS_REALTIME = 25f
    const val MAX_INFERENCE_TIME_MS = 1000L
    const val MAX_VLM_RESPONSE_TIME_MS = 10000L
    const val MAX_LLM_RESPONSE_TIME_MS = 5000L
    const val MAX_MEMORY_MB = 512
}
```

### 4.4 性能测试场景

| 场景 | 指标 | 通过标准 |
|------|------|---------|
| YOLO 单张图片 | 推理时间 | < 1000ms |
| YOLO 实时检测 | 平均 FPS | ≥ 15 FPS |
| YOLO 实时检测 | 最低 FPS | ≥ 10 FPS |
| VLM API 调用 | 响应时间 | < 10s |
| LLM API 调用 | 响应时间 | < 5s |
| 连续运行 30 分钟 | 内存增长 | < 50MB |
| 连续运行 30 分钟 | 无崩溃 | 0 次 |
| GPU 模式 | FPS 提升 | > CPU 模式 20% |

## 5. 测试工具链

### 5.1 单元测试依赖

```groovy
// app/build.gradle
dependencies {
    // JUnit
    testImplementation 'junit:junit:4.13.2'
    testImplementation 'org.junit.jupiter:junit-jupiter:5.9.0'

    // Mockito
    testImplementation 'org.mockito:mockito-core:4.8.0'
    testImplementation 'org.mockito.kotlin:mockito-kotlin:4.0.0'

    // MockWebServer
    testImplementation 'com.squareup.okhttp3:mockwebserver:4.10.0'

    // Robolectric (Android 单元测试)
    testImplementation 'org.robolectric:robolectric:4.9'

    // AssertJ
    testImplementation 'org.assertj:assertj-core:3.23.1'
}
```

### 5.2 集成测试依赖

```groovy
dependencies {
    // Android 测试
    androidTestImplementation 'androidx.test.ext:junit:1.1.4'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.0'
    androidTestImplementation 'androidx.test:runner:1.5.0'
    androidTestImplementation 'androidx.test:rules:1.5.0'

    // UI Automator
    androidTestImplementation 'androidx.test.uiautomator:uiautomator:2.2.0'
}
```

## 6. CI/CD 测试流程(建议)

```yaml
# 理想 CI 流程
stages:
  - lint
  - unit_test
  - build
  - integration_test
  - device_qa

lint:
  script:
    - ./gradlew ktlintCheck

unit_test:
  script:
    - ./gradlew testDebugUnitTest

build:
  script:
    - ./gradlew assembleDebug

integration_test:
  script:
    - ./gradlew connectedAndroidTest  # 需要模拟器或真机

device_qa:
  # 真机自动化测试(如 Firebase Test Lab)
  script:
    - gcloud firebase test android run --type instrumentation
```

## 7. 测试数据准备

### 7.1 测试图片

```
app/src/test/resources/
├── images/
│   ├── person_single.jpg          # 单人
│   ├── person_multiple.jpg        # 多人
│   ├── vehicle_scene.jpg          # 车辆场景
│   ├── empty_scene.jpg            # 空场景
│   ├── low_quality.jpg            # 低质量
│   └── large_resolution.jpg       # 大分辨率
```

### 7.2 Mock API 响应

```
app/src/test/resources/
├── api_responses/
│   ├── vlm_success.json
│   ├── vlm_empty.json
│   ├── vlm_error.json
│   ├── llm_success.json
│   └── llm_error.json
```

## 8. 关键测试用例设计

### 8.1 VLM 兼容性验证(Wave 2 早期任务)

```kotlin
@Test
fun testVlmCompatibility_imageInputSupport() {
    // 使用固定测试图片
    val testImage = loadTestResource("images/person_single.jpg")

    // 构造 VLM 请求(OpenAI Vision 格式)
    val request = VlmRequest(
        model = "qwen3.5-35b-a3b",
        messages = listOf(
            Message(
                role = "user",
                content = listOf(
                    Content.Text("识别图中对象"),
                    Content.ImageUrl(ImageUrlData("data:image/jpeg;base64,${testImage.toBase64()}"))
                )
            )
        )
    )

    // 发送请求
    val response = vlmClient.send(request)

    // 验证:
    // 1. HTTP 200
    // 2. 响应包含 choices
    // 3. content 可解析为 JSON
    // 4. JSON 包含 objects 字段

    assertEquals(200, response.code)
    assertNotNull(response.choices)
    assertTrue(response.choices[0].message.content.contains("objects"))
}
```

### 8.2 实时模式 VLM 防堆积测试

```kotlin
@Test
fun testVlmScheduler_noPiling() {
    val scheduler = VlmScheduler(intervalMs = 5000)

    // 模拟快速连续触发(每 1 秒触发一次)
    repeat(10) {
        scheduler.trigger(image)
        Thread.sleep(1000)
    }

    // 验证:实际发送的 VLM 请求数 ≤ 2(5 秒内最多 1 个 + 边界)
    assertTrue(mockServer.requestCount <= 2)
}
```

### 8.3 结果融合一致性测试

```kotlin
@Test
fun testFusion_countMismatchDetected() {
    val yolo = DetectionResult(objects = listOf(
        DetectedObject("person", 0, 0.9f, bbox),  // YOLO 检测 1 人
    ))

    val vlm = VlmResult(objects = listOf(
        VlmObject("person", 2, listOf()),  // VLM 识别 2 人
    ))

    val fused = ResultFusion.fuse(yolo, vlm)

    // 应检测到数量不一致
    assertTrue(fused.discrepancies.any {
        it.type == "count_mismatch" && it.description.contains("person")
    })
}
```

## 9. 测试覆盖率目标

| 模块 | 目标覆盖率 | 说明 |
|------|-----------|------|
| config | 90% | 配置读写、校验 ✅ AppConfigValidatorTest |
| ai | 85% | AiApiClient 请求构建/解析、AiModelConfigStore CRUD |
| vlm | 85% | 请求构建、响应解析 ✅ VlmRequestBuilderTest |
| llm | 85% | 请求构建、响应解析 ✅ LlmRequestBuilderTest |
| fusion | 90% | 核心融合逻辑 ✅ ResultFusionTest |
| export | 80% | 导出格式 |
| detection | 80% | 结果处理、UnifiedObjectResult |
| detection (JNI) | N/A | 真机 QA 覆盖 |
| camera | N/A | 真机 QA 覆盖 |

## 10. 测试执行计划

### Wave 1-2(开发阶段)
- 单元测试跟随功能开发同步编写(TDD)
- 每次提交前运行 `./gradlew testDebugUnitTest`

### Wave 3-5(功能验证阶段)
- 模块集成测试
- 模拟器功能验证
- 真机初步测试

### Final Wave(发布前)
- 完整真机 QA
- 性能基准测试
- 长时间稳定性测试
- 边界条件测试

## 11. 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| JNI 代码难以单元测试 | 中 | 通过真机 QA 覆盖,提取纯逻辑到 Java/Kotlin |
| VLM/LLM API 不稳定 | 高 | 完善的错误处理、重试机制、Mock 测试 |
| Camera 兼容性 | 中 | 多设备真机测试、降级处理 |
| 性能不达标 | 高 | 早期性能基准、模型优化、分辨率调整 |
| 内存泄漏 | 中 | 长时间运行测试、LeakCanary 集成 |
