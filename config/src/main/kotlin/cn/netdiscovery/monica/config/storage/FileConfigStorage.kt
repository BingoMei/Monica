package cn.netdiscovery.monica.config.storage

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption

/**
 * 文件配置存储适配器（JSON 格式）
 * 
 * 用于存储 JSON 格式的配置文件（如滤镜参数元数据）。
 * 所有配置存储在一个 JSON 文件中。
 * 
 * @param configFile 配置文件路径
 * @param gson Gson 实例，用于序列化/反序列化
 * 
 * @author: Monica Team
 * @date: 2025-12-12
 */
class FileConfigStorage(
    private val configFile: File,
    private val gson: Gson = Gson()
) : ConfigStorage {
    
    private val logger: Logger = LoggerFactory.getLogger(FileConfigStorage::class.java)
    
    private val configMap: MutableMap<String, Any> by lazy {
        loadFromFile()
    }
    
    /**
     * 从文件加载配置
     */
    private fun loadFromFile(): MutableMap<String, Any> {
        return if (configFile.exists() && configFile.isFile) {
            try {
                val jsonContent = configFile.readText(Charsets.UTF_8)
                if (jsonContent.isBlank()) {
                    mutableMapOf()
                } else {
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    gson.fromJson(jsonContent, type) ?: mutableMapOf()
                }
            } catch (e: Exception) {
                logger.error("Failed to load config from file: ${configFile.absolutePath}", e)
                mutableMapOf()
            }
        } else {
            // 文件不存在，创建空配置
            mutableMapOf()
        }
    }
    
    /**
     * 保存配置到文件
     */
    private fun saveToFile() {
        try {
            // 确保父目录存在
            configFile.parentFile?.mkdirs()
            
            val jsonContent = gson.toJson(configMap)
            Files.write(
                configFile.toPath(),
                jsonContent.toByteArray(Charsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )
        } catch (e: Exception) {
            logger.error("Failed to save config to file: ${configFile.absolutePath}", e)
            throw ConfigStorageException("Failed to save config to file", e)
        }
    }
    
    override fun <T> save(key: String, value: T) {
        try {
            configMap[key] = value as Any
            saveToFile()
        } catch (e: Exception) {
            logger.error("Failed to save config with key: $key", e)
            throw ConfigStorageException("Failed to save config: $key", e)
        }
    }
    
    @Suppress("UNCHECKED_CAST")
    override fun <T> load(key: String, default: T): T {
        return try {
            val value = configMap[key]
            if (value != null) {
                // 尝试类型转换
                when {
                    default is String && value is String -> value as T
                    default is Int && value is Number -> value.toInt() as T
                    default is Long && value is Number -> value.toLong() as T
                    default is Float && value is Number -> value.toFloat() as T
                    default is Double && value is Number -> value.toDouble() as T
                    default is Boolean && value is Boolean -> value as T
                    else -> {
                        // 尝试使用 Gson 进行类型转换
                        val jsonValue = gson.toJson(value)
                        gson.fromJson(jsonValue, default!!::class.java) as T
                    }
                }
            } else {
                default
            }
        } catch (e: Exception) {
            logger.warn("Failed to load config with key: $key, using default value", e)
            default
        }
    }
    
    override fun exists(key: String): Boolean {
        return configMap.containsKey(key)
    }
    
    override fun remove(key: String) {
        try {
            configMap.remove(key)
            saveToFile()
        } catch (e: Exception) {
            logger.error("Failed to remove config with key: $key", e)
            throw ConfigStorageException("Failed to remove config: $key", e)
        }
    }
    
    override fun clear() {
        try {
            configMap.clear()
            saveToFile()
        } catch (e: Exception) {
            logger.error("Failed to clear config storage", e)
            throw ConfigStorageException("Failed to clear config storage", e)
        }
    }
    
    override fun getAllKeys(): List<String> {
        return configMap.keys.toList()
    }
}

