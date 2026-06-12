package com.yolo.openiris.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AiProviderTest {

    @Test
    fun `valid https URL should pass validation`() {
        assertNull(AiProvider.validateBaseUrl("https://api.openai.com/v1"))
    }

    @Test
    fun `empty URL should fail validation`() {
        assertNotNull(AiProvider.validateBaseUrl(""))
        assertNotNull(AiProvider.validateBaseUrl("   "))
    }

    @Test
    fun `non-http scheme should fail validation`() {
        assertNotNull(AiProvider.validateBaseUrl("ftp://example.com"))
        assertNotNull(AiProvider.validateBaseUrl("file:///etc/passwd"))
    }

    @Test
    fun `http to public host should warn`() {
        assertNotNull(AiProvider.validateBaseUrl("http://api.openai.com"))
    }

    @Test
    fun `http to localhost should pass`() {
        assertNull(AiProvider.validateBaseUrl("http://localhost:8080"))
        assertNull(AiProvider.validateBaseUrl("http://127.0.0.1:8080"))
    }

    @Test
    fun `private IP 10-dot should be blocked`() {
        assertNotNull(AiProvider.validateBaseUrl("https://10.0.0.1"))
        assertNotNull(AiProvider.validateBaseUrl("https://10.255.255.255"))
    }

    @Test
    fun `private IP 192-dot-168 should be blocked`() {
        assertNotNull(AiProvider.validateBaseUrl("https://192.168.1.1"))
        assertNotNull(AiProvider.validateBaseUrl("https://192.168.0.1"))
    }

    @Test
    fun `private IP 172-dot-16-31 should be blocked`() {
        assertNotNull(AiProvider.validateBaseUrl("https://172.16.0.1"))
        assertNotNull(AiProvider.validateBaseUrl("https://172.31.255.255"))
    }

    @Test
    fun `link-local IP should be blocked`() {
        assertNotNull(AiProvider.validateBaseUrl("https://169.254.1.1"))
    }

    @Test
    fun `invalid URL format should fail`() {
        assertNotNull(AiProvider.validateBaseUrl("not-a-url"))
        assertNotNull(AiProvider.validateBaseUrl("://missing-scheme"))
    }

    @Test
    fun `toString should mask apiKey`() {
        val provider = AiProvider(
            name = "Test",
            baseUrl = "https://api.openai.com",
            apiKey = "sk-secret-key-12345"
        )
        val str = provider.toString()
        assert(!str.contains("sk-secret-key-12345")) { "toString should not contain apiKey" }
        assert(str.contains("apiKey=***")) { "toString should mask apiKey" }
    }

    @Test
    fun `getEffectiveBaseUrl should append v1 for OpenAI format`() {
        val provider = AiProvider(
            name = "Test",
            baseUrl = "https://api.openai.com",
            apiKey = "test"
        )
        assertEquals("https://api.openai.com/v1", provider.getEffectiveBaseUrl())
    }

    @Test
    fun `getEffectiveBaseUrl should not duplicate v1`() {
        val provider = AiProvider(
            name = "Test",
            baseUrl = "https://api.openai.com/v1",
            apiKey = "test"
        )
        assertEquals("https://api.openai.com/v1", provider.getEffectiveBaseUrl())
    }

    @Test
    fun `getEffectiveBaseUrl should not append v1 for Anthropic`() {
        val provider = AiProvider(
            name = "Test",
            baseUrl = "https://api.anthropic.com",
            apiKey = "test",
            apiFormat = ApiFormat.ANTHROPIC
        )
        assertEquals("https://api.anthropic.com", provider.getEffectiveBaseUrl())
    }
}
