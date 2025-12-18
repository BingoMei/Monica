package cn.netdiscovery.monica.config.category

/**
 * 配置分类枚举
 * 
 * 用于区分不同类型的配置，便于统一管理和验证。
 * 
 * @author: Monica Team
 * @date: 2025-12-12
 */
enum class ConfigCategory {
    /**
     * 应用设置（用户可修改的设置，如 GeneralSettings）
     */
    APP_SETTINGS,
    
    /**
     * UI 配置（UI 相关的配置，如滤镜参数元数据）
     */
    UI_CONFIG,
    
    /**
     * 业务配置（业务逻辑相关的配置，如 API 密钥、算法 URL）
     */
    BUSINESS_CONFIG,
    
    /**
     * 用户偏好（用户个人偏好设置，如语言、主题）
     */
    USER_PREFERENCE,
    
    /**
     * 临时配置（临时存储的配置，如裁剪状态）
     */
    TEMPORARY
}

