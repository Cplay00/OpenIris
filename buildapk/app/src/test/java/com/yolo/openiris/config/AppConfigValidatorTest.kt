package com.yolo.openiris.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// 测试常量，避免硬编码真实 API Key 格式
private const val TEST_API_KEY = "test-key-not-real"

class AppConfigValidatorTest {

    @Test
    fun `合法配置应通过校验`() {
        val result = AppConfigValidator.validate(validConfig())

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `Base URL 为空时应返回必填错误`() {
        val result = AppConfigValidator.validate(validConfig(apiBaseUrl = "  "))

        assertFalse(result.isValid)
        assertTrue(result.hasError(ConfigField.API_BASE_URL))
        assertEquals("Base URL 不能为空", result.errors.first().message)
    }

    @Test
    fun `Base URL 不是合法绝对地址时应校验失败`() {
        val result = AppConfigValidator.validate(validConfig(apiBaseUrl = "not-a-url"))

        assertFalse(result.isValid)
        assertTrue(result.hasError(ConfigField.API_BASE_URL))
    }

    @Test
    fun `Base URL 协议不是 http 或 https 时应校验失败`() {
        val result = AppConfigValidator.validate(validConfig(apiBaseUrl = "ftp://example.com/v1"))

        assertFalse(result.isValid)
        assertTrue(result.hasError(ConfigField.API_BASE_URL))
    }

    @Test
    fun `API Key 为空时应校验失败`() {
        val result = AppConfigValidator.validate(validConfig(apiKey = "  "))

        assertFalse(result.isValid)
        assertTrue(result.hasError(ConfigField.API_KEY))
    }

    @Test
    fun `VLM model 为空时应校验失败`() {
        val result = AppConfigValidator.validate(validConfig(vlmModel = ""))

        assertFalse(result.isValid)
        assertTrue(result.hasError(ConfigField.VLM_MODEL))
    }

    @Test
    fun `LLM model 为空时应校验失败`() {
        val result = AppConfigValidator.validate(validConfig(llmModel = " "))

        assertFalse(result.isValid)
        assertTrue(result.hasError(ConfigField.LLM_MODEL))
    }

    @Test
    fun `实时 VLM 间隔小于最小值时应校验失败`() {
        val result = AppConfigValidator.validate(
            validConfig(vlmIntervalSeconds = AppConfig.MIN_VLM_INTERVAL - 1)
        )

        assertFalse(result.isValid)
        assertTrue(result.hasError(ConfigField.VLM_INTERVAL_SECONDS))
    }

    @Test
    fun `实时 VLM 间隔大于最大值时应校验失败`() {
        val result = AppConfigValidator.validate(
            validConfig(vlmIntervalSeconds = AppConfig.MAX_VLM_INTERVAL + 1)
        )

        assertFalse(result.isValid)
        assertTrue(result.hasError(ConfigField.VLM_INTERVAL_SECONDS))
    }

    @Test
    fun `默认实时 VLM 间隔应在合法范围内`() {
        val error = AppConfigValidator.validateRealtimeVlmInterval(AppConfig.DEFAULT_VLM_INTERVAL)

        assertEquals(null, error)
    }

    @Test
    fun `AppConfig 应复用统一校验逻辑`() {
        val config = validConfig(apiBaseUrl = "bad-url")

        assertFalse(config.isValid())
        assertTrue(config.validate().hasError(ConfigField.API_BASE_URL))
    }

    @Test
    fun `getFullApiBaseUrl 应补齐 v1 并去掉尾部斜杠`() {
        val config = validConfig(apiBaseUrl = "https://example.com/openai/")

        assertEquals("https://example.com/openai/v1", config.getFullApiBaseUrl())
    }

    private fun validConfig(
        apiBaseUrl: String = "https://api.openai.com",
        apiKey: String = TEST_API_KEY,
        vlmModel: String = "qwen-vl-plus",
        llmModel: String = "deepseek-chat",
        vlmIntervalSeconds: Int = AppConfig.DEFAULT_VLM_INTERVAL
    ): AppConfig {
        return AppConfig(
            apiBaseUrl = apiBaseUrl,
            apiKey = apiKey,
            vlmModel = vlmModel,
            llmModel = llmModel,
            vlmIntervalSeconds = vlmIntervalSeconds
        )
    }
}
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// 测试常量，避免硬编码真实 API Key 格式
private const val TEST_API_KEY = "test-key-not-real"
