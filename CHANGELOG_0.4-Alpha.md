# OpenIris v0.4-Alpha (Pre-Release)

**发布日期**: 2026-06-12
**版本**: 0.4-Alpha
**目标SDK**: Android 16 (API 36)

---

## 🔒 安全与隐私修复

1. **apiKey 脱敏** — AiProvider 重写 toString/hashCode/equals，apiKey 在日志中显示为 ***
2. **SSRF 防护** — AiProvider.validateBaseUrl() 验证 URL、强制 HTTPS、阻止内网地址
3. **ProGuard 收紧** — 仅保留 Gson 序列化必需的模型类
4. **Network Security Config** — 新增 network_security_config.xml，禁止明文流量
5. **加密降级阻止** — ConfigManager 在加密不可用时阻止 API Key 写入
6. **logging-interceptor 安全** — 改为 debugImplementation，Release 不包含日志拦截器
7. **敏感日志脱敏** — 所有 apiKey/providerId 相关日志包裹在 BuildConfig.DEBUG 中
8. **隐私信息清除** — README 个人 URL、ncnn 旧目录、local.properties 清理
9. **analyzeCurrentFrame 修复** — 改用 captureFrame() 获取真实摄像头帧
10. **测试硬编码 Key** — sk-test-key 替换为 TEST_API_KEY 常量

---

## 🧵 竞态条件修复

1. **VideoDetectActivity** — analysisResults 改为 CopyOnWriteArrayList
2. **ImageDetectActivity** — 添加 isAnalyzing 标志防止并发分析
3. **Python GUI** — _stop_flag 改为 threading.Event()
4. **SlidingWindowTracker** — addDetections 内联逻辑避免可重入锁开销
5. **Bitmap 生命周期** — captureAndSave 中正确回收 bitmap
6. **GUI train_proc** — 添加 threading.Lock 保护进程引用
7. **subprocess 超时** — 所有 subprocess.run 添加 timeout=300

---

## 🏗️ 代码质量改进

1. **Force Unwrap 消除** — 4处 !! 替换为安全调用
2. **依赖版本升级**:
   - core-ktx: 1.8.0 → 1.15.0
   - appcompat: 1.5.1 → 1.7.0
   - gson: 2.9.1 → 2.11.0
   - okhttp: 4.10.0 → 4.12.0
   - coroutines: 1.7.3 → 1.9.0
3. **Android 14+ 权限** — 添加 READ_MEDIA_VISUAL_USER_SELECTED
4. **导出流水线** — onnx2ncnn 失败时立即返回
5. **COCO JSON 验证** — 添加必需字段检查
6. **文件编码** — augmentor/splitter 添加 encoding="utf-8"

---

## 📝 编码修复

- 使用 ftfy 修复 Python/Markdown/YAML 文件中的编码问题
- 修复合并的注释+代码行（9处）

---

## 🧪 测试补充

- **AiProviderTest.kt** — 14个用例（URL验证、SSRF防护、apiKey脱敏）
- **SlidingWindowTrackerTest.kt** — 4个用例
- **AppBasicTest.kt** — 基础集成测试

---

## 📦 构建

- 目标SDK: Android 16 (API 36)
- 编译SDK: 36
- Java: 21