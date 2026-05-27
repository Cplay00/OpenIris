package com.yolo.openiris.config

import android.content.SharedPreferences

/**
 * API Key 加密存储适配层。
 *
 * 底层仍然使用 ConfigManager 提供的 EncryptedSharedPreferences，
 * 调用方只通过明确的保存、读取、更新、清空接口访问敏感字段。
 */
internal class EncryptedApiKeyStore(
    private val encryptedPrefs: SharedPreferences,
    private val apiKeyPreferenceKey: String
) {

    fun save(apiKey: String) {
        val normalizedApiKey = apiKey.trim()

        encryptedPrefs.edit().apply {
            if (normalizedApiKey.isEmpty()) {
                remove(apiKeyPreferenceKey)
            } else {
                putString(apiKeyPreferenceKey, normalizedApiKey)
            }
            apply()
        }
    }

    fun get(): String {
        return encryptedPrefs.getString(apiKeyPreferenceKey, "")?.trim().orEmpty()
    }

    fun clear() {
        encryptedPrefs.edit()
            .remove(apiKeyPreferenceKey)
            .apply()
    }

    fun hasValue(): Boolean {
        return get().isNotEmpty()
    }
}
