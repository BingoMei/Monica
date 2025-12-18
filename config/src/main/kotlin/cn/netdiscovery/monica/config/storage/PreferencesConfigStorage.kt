package cn.netdiscovery.monica.config.storage

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.prefs.Preferences

/**
 * Preferences 配置存储适配器
 * 
 * 适配 Java Preferences API，用于存储简单的键值对配置（如语言设置）。
 * 
 * @param preferencesNode Preferences 节点，默认为用户节点
 * 
 * @author: Monica Team
 * @date: 2025-12-12
 */
class PreferencesConfigStorage(
    private val preferencesNode: Preferences = Preferences.userNodeForPackage(PreferencesConfigStorage::class.java)
) : ConfigStorage {
    
    private val logger: Logger = LoggerFactory.getLogger(PreferencesConfigStorage::class.java)
    
    override fun <T> save(key: String, value: T) {
        try {
            when (value) {
                is String -> preferencesNode.put(key, value)
                is Int -> preferencesNode.putInt(key, value)
                is Long -> preferencesNode.putLong(key, value)
                is Float -> preferencesNode.putFloat(key, value)
                is Double -> preferencesNode.putDouble(key, value)
                is Boolean -> preferencesNode.putBoolean(key, value)
                is ByteArray -> preferencesNode.putByteArray(key, value)
                else -> {
                    // 对于复杂对象，序列化为 JSON 字符串
                    preferencesNode.put(key, value.toString())
                    logger.warn("Complex object serialized as string for key: $key")
                }
            }
            preferencesNode.flush()
        } catch (e: Exception) {
            logger.error("Failed to save config with key: $key", e)
            throw ConfigStorageException("Failed to save config: $key", e)
        }
    }
    
    @Suppress("UNCHECKED_CAST")
    override fun <T> load(key: String, default: T): T {
        return try {
            when (default) {
                is String -> preferencesNode.get(key, default) as T
                is Int -> preferencesNode.getInt(key, default) as T
                is Long -> preferencesNode.getLong(key, default) as T
                is Float -> preferencesNode.getFloat(key, default) as T
                is Double -> preferencesNode.getDouble(key, default) as T
                is Boolean -> preferencesNode.getBoolean(key, default) as T
                is ByteArray -> preferencesNode.getByteArray(key, default) as T
                else -> {
                    val value = preferencesNode.get(key, null)
                    if (value != null) {
                        // 尝试从字符串反序列化（需要类型信息）
                        logger.warn("Complex object deserialization not fully supported for key: $key, returning default")
                        default
                    } else {
                        default
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to load config with key: $key, using default value", e)
            default
        }
    }
    
    override fun exists(key: String): Boolean {
        return try {
            preferencesNode.get(key, null) != null
        } catch (e: Exception) {
            logger.warn("Failed to check existence of config with key: $key", e)
            false
        }
    }
    
    override fun remove(key: String) {
        try {
            preferencesNode.remove(key)
            preferencesNode.flush()
        } catch (e: Exception) {
            logger.error("Failed to remove config with key: $key", e)
            throw ConfigStorageException("Failed to remove config: $key", e)
        }
    }
    
    override fun clear() {
        try {
            preferencesNode.clear()
            preferencesNode.flush()
        } catch (e: Exception) {
            logger.error("Failed to clear Preferences", e)
            throw ConfigStorageException("Failed to clear config storage", e)
        }
    }
    
    override fun getAllKeys(): List<String> {
        return try {
            preferencesNode.keys().toList()
        } catch (e: Exception) {
            logger.error("Failed to get all keys from Preferences", e)
            emptyList()
        }
    }
}

