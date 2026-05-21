package com.yolo.openiris

import android.app.Application
import com.yolo.openiris.config.ConfigManager

/**
 * OpenIris 应用入口
 */
class OpenIrisApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 初始化配置管理器
        ConfigManager.getInstance(this)
    }
}
