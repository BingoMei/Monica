package cn.netdiscovery.monica.config.category

import cn.netdiscovery.monica.config.storage.ConfigManager
import cn.netdiscovery.monica.config.storage.ConfigStorage
import cn.netdiscovery.monica.config.storage.ConfigType
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 配置分类管理器
 * 
 * 根据配置分类选择合适的存储和验证策略。
 * 
 * @author: Monica Team
 * @date: 2025-12-12
 */
object ConfigCategoryManager {
    
    private val logger: Logger = LoggerFactory.getLogger(ConfigCategoryManager::class.java)
    
    /**
     * 配置元信息
     */
    data class ConfigMetadata<T>(
        val key: String,
        val category: ConfigCategory,
        val storageType: ConfigType,
        val validator: ConfigValidator<T>? = null,
        val defaultValue: T
    )
    
    /**
     * 配置注册表
     */
    private val configRegistry = mutableMapOf<String, ConfigMetadata<*>>()
    
    /**
     * 注册配置
     */
    fun <T> register(metadata: ConfigMetadata<T>) {
        configRegistry[metadata.key] = metadata
        logger.debug("Registered config: key=${metadata.key}, category=${metadata.category}")
    }
    
    /**
     * 获取配置元信息
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getMetadata(key: String): ConfigMetadata<T>? {
        return configRegistry[key] as? ConfigMetadata<T>
    }
    
    /**
     * 保存配置（带验证）
     */
    fun <T> save(key: String, value: T): ValidationResult {
        val metadata = getMetadata<T>(key)
        
        // 验证配置值
        metadata?.validator?.let { validator ->
            val error = validator.validate(value)
            if (error != null) {
                logger.warn("Config validation failed: key=$key, error=$error")
                return ValidationResult.failure(error)
            }
        }
        
        // 保存配置
        try {
            val storageType = metadata?.storageType ?: ConfigType.DEFAULT
            ConfigManager.save(key, value, storageType)
            logger.debug("Config saved: key=$key, category=${metadata?.category}")
            return ValidationResult.success()
        } catch (e: Exception) {
            logger.error("Failed to save config: key=$key", e)
            return ValidationResult.failure("Failed to save config: ${e.message}")
        }
    }
    
    /**
     * 加载配置（带默认值）
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> load(key: String): T? {
        val metadata = getMetadata<T>(key)
        val storageType = metadata?.storageType ?: ConfigType.DEFAULT
        val defaultValue = metadata?.defaultValue
        
        return if (defaultValue != null) {
            ConfigManager.load(key, defaultValue, storageType) as T
        } else {
            logger.warn("No default value for config: key=$key")
            null
        }
    }
    
    /**
     * 加载配置（带自定义默认值）
     */
    fun <T> load(key: String, default: T): T {
        val metadata = getMetadata<T>(key)
        val storageType = metadata?.storageType ?: ConfigType.DEFAULT
        return ConfigManager.load(key, default, storageType)
    }
    
    /**
     * 验证配置值（不保存）
     */
    fun <T> validate(key: String, value: T): ValidationResult {
        val metadata = getMetadata<T>(key)
        val validator = metadata?.validator ?: return ValidationResult.success()
        
        val error = validator.validate(value)
        return if (error != null) {
            ValidationResult.failure(error)
        } else {
            ValidationResult.success()
        }
    }
    
    /**
     * 获取配置的存储类型
     */
    fun getStorageType(key: String): ConfigType {
        return getMetadata<Any>(key)?.storageType ?: ConfigType.DEFAULT
    }
    
    /**
     * 获取配置的分类
     */
    fun getCategory(key: String): ConfigCategory? {
        return getMetadata<Any>(key)?.category
    }
    
    /**
     * 获取所有已注册的配置键
     */
    fun getAllKeys(): List<String> {
        return configRegistry.keys.toList()
    }
    
    /**
     * 获取指定分类的所有配置键
     */
    fun getKeysByCategory(category: ConfigCategory): List<String> {
        return configRegistry.filter { it.value.category == category }.keys.toList()
    }
    
    /**
     * 清除指定分类的所有配置
     */
    fun clearCategory(category: ConfigCategory) {
        val keys = getKeysByCategory(category)
        keys.forEach { key ->
            val metadata = getMetadata<Any>(key)
            val storageType = metadata?.storageType ?: ConfigType.DEFAULT
            ConfigManager.remove(key, storageType)
        }
        logger.info("Cleared all configs in category: $category")
    }
}

