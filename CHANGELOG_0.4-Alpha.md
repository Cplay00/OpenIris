# OpenIris v0.4-Alpha 变更日志

**发布日期**: 2026-06-12
**版本**: 0.4-Alpha (Pre-Release)
**目标SDK**: Android 16 (API 36)

---

## 📊 变更统计

| 指标 | 数值 |
|------|------|
| 变更文件 | 131个 |
| 新增行 | 1,520行 |
| 删除行 | 8,656行 |
| 净减少 | 7,136行 |
| 新增测试 | 19个用例 |

---

## 🔒 安全与隐私修复 (12项)

### 高危修复
1. **apiKey 脱敏** — `AiProvider.kt` 重写 `toString()/hashCode()/equals()`，apiKey 在日志/序列化中显示为 `***`
2. **SSRF 防护** — `AiProvider.validateBaseUrl()` 验证 URL 格式、强制 HTTPS、阻止内网地址（10.x/172.16-31.x/192.168.x/127.x/169.254.x）
3. **ProGuard 收紧** — 仅保留 Gson 序列化必需的模型类，AI 客户端内部实现不再全部 keep
4. **Network Security Config** — 新增 `network_security_config.xml`，禁止明文流量，Debug 构建允许用户证书
5. **加密降级阻止** — `ConfigManager.saveAiProviderApiKey()` 在加密不可用时真正阻止写入（之前仅警告）
6. **logging-interceptor 安全** — 改为 `debugImplementation`，Release 构建不包含日志拦截器

### 中危修复
7. **17处敏感日志脱敏** — 所有 apiKey/providerId 相关日志包裹在 `BuildConfig.DEBUG` 中
8. **README 个人URL清除** — GitHub 个人仓库 URL 替换为占位符
9. **ncnn-android-yolov11/ 旧目录删除** — 67个过时文件清理
10. **local.properties 从 git 移除** — 含本地 SDK 路径的文件不再被跟踪
11. **analyzeCurrentFrame 空白bitmap修复** — 改用 `captureFrame()` 获取真实摄像头帧
12. **测试硬编码API Key** — `"sk-test-key"` 替换为 `TEST_API_KEY` 常量

---

## 🧵 竞态条件修复 (8项)

1. **VideoDetectActivity 并发安全** — `analysisResults` 改为 `CopyOnWriteArrayList`
2. **ImageDetectActivity 并发保护** — 添加 `isAnalyzing` AtomicBoolean 标志，防止多次快速点击触发并发分析
3. **Python GUI 线程安全** — `_stop_flag` 改为 `threading.Event()`
4. **SlidingWindowTracker 锁优化** — `addDetections()` 内联逻辑避免可重入锁开销
5. **Bitmap 生命周期管理** — `captureAndSave()` 中 annotatedBitmap 和 bitmap 正确回收
6. **GUI train_proc 线程安全** — 添加 `threading.Lock` 保护进程引用
7. **GUI stdout 管道清理** — finally 块中关闭 stdout 防止文件描述符泄漏
8. **subprocess 超时** — 所有 `subprocess.run` 调用添加 `timeout=300`

---

## 🏗️ 代码质量与架构改进 (10项)

1. **Force Unwrap 消除** — 4处 `!!` 替换为安全调用（`?.let`/`?: continue`/带异常信息的安全访问）
2. **依赖版本升级** (9个):
   - core-ktx: 1.8.0 → 1.15.0
   - appcompat: 1.5.1 → 1.7.0
   - material: 1.13.0 → 1.12.0
   - security-crypto: 1.1.0-alpha03 → 1.1.0-alpha06
   - gson: 2.9.1 → 2.11.0
   - okhttp: 4.10.0 → 4.12.0
   - coroutines-android: 1.7.3 → 1.9.0
   - mockito-core: 4.8.0 → 5.14.0
   - espresso-core: 3.5.0 → 3.6.1
3. **Android 14+ 权限** — 添加 `READ_MEDIA_VISUAL_USER_SELECTED` 支持选择性媒体访问
4. **导出流水线错误处理** — `onnx2ncnn` 失败时立即返回而非继续执行
5. **COCO JSON 验证** — `converter.py` 添加必需字段检查
6. **XML 解析异常处理** — `converter.py` 的 `ET.parse` 添加 try-except
7. **文件编码指定** — `augmentor.py`/`splitter.py` 的文件操作添加 `encoding="utf-8"`
8. **裁剪边框验证增强** — `augmentor.py` 添加 width/height 正值检查
9. **全局随机种子修复** — `augmentor.py` 改为实例级 `random.Random`
10. **matplotlib 后端顺序修复** — `visualize_results.py` 中 `use("Agg")` 移到 `import pyplot` 之前

---

## 📝 编码修复 (70个文件)

- 使用 `ftfy` 库修复全部源码中的 mojibake 编码问题
- 覆盖 39个 Kotlin 文件 + 31个 Python/Markdown/YAML/BAT 文件
- 共修复 245+ 行损坏的中文注释和字符串

---

## 🧪 测试补充 (新增19个用例)

### 新增单元测试
- **AiProviderTest.kt** (14个用例) — URL验证、SSRF防护、apiKey脱敏、API路径构建
- **SlidingWindowTrackerTest.kt** (4个用例) — 批量添加、统计、空状态

### 新增集成测试
- **AppBasicTest.kt** (1个用例) — 应用上下文基本验证

### 测试文件总数
- 单元测试: 6个文件
- 集成测试: 1个文件

---

## 🗑️ 清理项

- 删除 `ncnn-android-yolov11/` 旧副本目录（67个文件，与 buildapk/ 高度重复）
- 删除 `training/dist/` 编译产物（含个人路径的 PyInstaller 输出）
- `local.properties` 从 git 跟踪中移除

---

## ⚠️ 已知遗留

1. **Certificate Pinning** — 需要获取各 API 提供商的证书指纹才能配置
2. **apiKey 内存传递** — 仍以 String 形式在调用链中传递，需架构层面重构
3. **androidTest 覆盖率** — 目前仅有基础验证，建议后续补充 Espresso UI 测试

---

## 📋 审查组参与

| Agent | 职责 | 状态 |
|-------|------|------|
| Gauss | 安全与隐私审查 | ✅ |
| Huygens | 训练部分安全审查 | ✅ |
| Mill | 训练部分代码质量审查 | ✅ |
| Noether | 安全+竞态条件审查 | ✅ |
| Tesla | 代码质量+测试审查 | ✅ |
| Wegener | 最终验证审查 | ✅ |
