package cn.netdiscovery.monica.config.category

import cn.netdiscovery.monica.config.KEY_GENERAL_SETTINGS
import cn.netdiscovery.monica.config.storage.ConfigType
import cn.netdiscovery.monica.domain.GeneralSettings

/**
 * 配置定义
 * 
 * 集中定义所有配置的元信息，包括分类、存储类型、验证规则和默认值。
 * 
 * @author: Tony Shen
 * @date: 2025-12-12
 */
object ConfigDefinitions {
    
    /**
     * 初始化所有配置定义
     */
    fun initialize() {
        registerAppSettings()
        registerUserPreferences()
        registerTemporaryConfigs()
    }
    
    /**
     * 注册应用设置
     */
    private fun registerAppSettings() {
        // GeneralSettings
        ConfigCategoryManager.register(
            ConfigCategoryManager.ConfigMetadata(
                key = KEY_GENERAL_SETTINGS,
                category = ConfigCategory.APP_SETTINGS,
                storageType = ConfigType.RX_CACHE,
                defaultValue = GeneralSettings(
                    outputBoxR = 255,
                    outputBoxG = 255,
                    outputBoxB = 255,
                    size = 512,
                    maxHistorySize = 50,
                    deepSeekApiKey = "",
                    geminiApiKey = "",
                    algorithmUrl = "",
                    themeId = "LIGHT"
                )
            )
        )
    }
    
    /**
     * 注册用户偏好设置
     */
    private fun registerUserPreferences() {
        // 语言设置
        ConfigCategoryManager.register(
            ConfigCategoryManager.ConfigMetadata(
                key = "selected_language",
                category = ConfigCategory.USER_PREFERENCE,
                storageType = ConfigType.PREFERENCES,
                defaultValue = "zh"
            )
        )
    }
    
    /**
     * 注册临时配置
     */
    private fun registerTemporaryConfigs() {
        // 裁剪相关临时配置已在 Constants.kt 中定义
        // 这里可以添加其他临时配置的定义
    }
}