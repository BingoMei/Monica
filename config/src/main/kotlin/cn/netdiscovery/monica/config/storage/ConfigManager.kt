package cn.netdiscovery.monica.config.storage

import com.safframework.rxcache.RxCache
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 统一配置管理器
 * 
 * 管理不同类型的配置存储，提供统一的配置访问接口。
 * 支持多种存储后端：
 * - RxCache: 用于复杂对象（如 GeneralSettings）
 * - Preferences: 用于简单键值对（如语言设置）
 * - File: 用于 JSON 配置文件（如滤镜参数元数据）
 * 
 * 注意：需要先调用 initialize() 方法初始化，传入 RxCache 实例。
 * 
 * @author: Tony Shen
 * @date: 2025-12-12
 */
object ConfigManager {
    
    private val logger: Logger = LoggerFactory.getLogger(ConfigManager::class.java)
    
    private var _rxCacheStorage: ConfigStorage? = null
    
    /**
     * RxCache 存储（用于复杂对象）
     */
    val rxCacheStorage: ConfigStorage
        get() = _rxCacheStorage ?: throw IllegalStateException("ConfigManager not initialized. Call initialize() first.")
    
    /**
     * Preferences 存储（用于简单键值对）
     */
    val preferencesStorage: ConfigStorage = PreferencesConfigStorage()
    
    /**
     * 默认存储（优先使用 RxCache）
     */
    val defaultStorage: ConfigStorage
        get() = rxCacheStorage
    
    /**
     * 初始化 ConfigManager
     * 
     * @param rxCache RxCache 实例
     */
    fun initialize(rxCache: RxCache) {
        _rxCacheStorage = RxCacheConfigStorage(rxCache)
        logger.info("ConfigManager initialized")
    }
    
    /**
     * 根据配置类型选择合适的存储
     * 
     * @param configType 配置类型
     * @return 对应的存储实例
     */
    fun getStorage(configType: ConfigType = ConfigType.DEFAULT): ConfigStorage {
        return when (configType) {
            ConfigType.RX_CACHE -> rxCacheStorage
            ConfigType.PREFERENCES -> preferencesStorage
            ConfigType.DEFAULT -> defaultStorage
        }
    }
    
    /**
     * 保存配置（使用默认存储）
     */
    fun <T> save(key: String, value: T, configType: ConfigType = ConfigType.DEFAULT) {
        try {
            getStorage(configType).save(key, value)
            logger.debug("Config saved: key=$key, type=$configType")
        } catch (e: Exception) {
            logger.error("Failed to save config: key=$key, type=$configType", e)
            throw e
        }
    }
    
    /**
     * 加载配置（使用默认存储）
     */
    fun <T> load(key: String, default: T, configType: ConfigType = ConfigType.DEFAULT): T {
        return try {
            val value = getStorage(configType).load(key, default)
            logger.debug("Config loaded: key=$key, type=$configType, found=${value != default}")
            value
        } catch (e: Exception) {
            logger.warn("Failed to load config: key=$key, type=$configType, using default", e)
            default
        }
    }
    
    /**
     * 检查配置是否存在
     */
    fun exists(key: String, configType: ConfigType = ConfigType.DEFAULT): Boolean {
        return getStorage(configType).exists(key)
    }
    
    /**
     * 删除配置
     */
    fun remove(key: String, configType: ConfigType = ConfigType.DEFAULT) {
        try {
            getStorage(configType).remove(key)
            logger.debug("Config removed: key=$key, type=$configType")
        } catch (e: Exception) {
            logger.error("Failed to remove config: key=$key, type=$configType", e)
            throw e
        }
    }
    
    /**
     * 清空指定类型的配置
     */
    fun clear(configType: ConfigType = ConfigType.DEFAULT) {
        try {
            getStorage(configType).clear()
            logger.info("Config cleared: type=$configType")
        } catch (e: Exception) {
            logger.error("Failed to clear config: type=$configType", e)
            throw e
        }
    }
    
    /**
     * 获取所有配置键
     */
    fun getAllKeys(configType: ConfigType = ConfigType.DEFAULT): List<String> {
        return getStorage(configType).getAllKeys()
    }
}

/**
 * 配置类型枚举
 */
enum class ConfigType {
    /**
     * 使用 RxCache 存储（默认，用于复杂对象）
     */
    RX_CACHE,
    
    /**
     * 使用 Preferences 存储（用于简单键值对）
     */
    PREFERENCES,
    
    /**
     * 使用默认存储（当前为 RxCache）
     */
    DEFAULT
}

