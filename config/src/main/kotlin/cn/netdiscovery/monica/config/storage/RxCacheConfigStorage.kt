package cn.netdiscovery.monica.config.storage

import com.safframework.rxcache.RxCache
import com.safframework.rxcache.ext.get
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * RxCache 配置存储适配器
 * 
 * 适配现有的 RxCache 实现，用于存储复杂对象（如 GeneralSettings）。
 * 
 * 注意：由于 RxCache 的 get 方法需要 reified 类型参数，我们使用 Any 类型进行通用处理。
 * 对于类型安全的场景，建议使用具体的类型调用。
 * 
 * @param rxCache RxCache 实例，由外部传入以避免循环依赖
 * 
 * @author: Monica Team
 * @date: 2025-12-12
 */
class RxCacheConfigStorage(
    private val rxCache: RxCache
) : ConfigStorage {
    
    private val logger: Logger = LoggerFactory.getLogger(RxCacheConfigStorage::class.java)
    
    override fun <T> save(key: String, value: T) {
        try {
            rxCache.saveOrUpdate(key, value)
        } catch (e: Exception) {
            logger.error("Failed to save config with key: $key", e)
            throw ConfigStorageException("Failed to save config: $key", e)
        }
    }
    
    @Suppress("UNCHECKED_CAST")
    override fun <T> load(key: String, default: T): T {
        return try {
            // RxCache.get 需要 reified 类型参数，这里使用 Any 作为通用类型
            // 实际使用时，类型转换由调用方保证（通过 default 参数的类型推断）
            val result = rxCache.get<Any>(key)?.data
            if (result != null) {
                // 尝试类型转换
                // 对于基本类型，进行显式转换
                when {
                    default is String && result is String -> result as T
                    default is Int && result is Number -> result.toInt() as T
                    default is Long && result is Number -> result.toLong() as T
                    default is Float && result is Number -> result.toFloat() as T
                    default is Double && result is Number -> result.toDouble() as T
                    default is Boolean && result is Boolean -> result as T
                    // 对于复杂对象，检查类型是否匹配
                    result::class.java.isAssignableFrom(default!!::class.java) -> result as T
                    default::class.java.isAssignableFrom(result::class.java) -> result as T
                    else -> {
                        logger.warn("Type mismatch for key: $key, expected: ${default!!::class.java.simpleName}, got: ${result::class.java.simpleName}")
                        default
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
        return try {
            rxCache.get<Any>(key) != null
        } catch (e: Exception) {
            logger.warn("Failed to check existence of config with key: $key", e)
            false
        }
    }
    
    override fun remove(key: String) {
        try {
            rxCache.remove(key)
        } catch (e: Exception) {
            logger.error("Failed to remove config with key: $key", e)
            throw ConfigStorageException("Failed to remove config: $key", e)
        }
    }
    
    override fun clear() {
        try {
            rxCache.clear()
        } catch (e: Exception) {
            logger.error("Failed to clear RxCache", e)
            throw ConfigStorageException("Failed to clear config storage", e)
        }
    }
    
    override fun getAllKeys(): List<String> {
        // RxCache 不直接提供获取所有键的接口，返回空列表
        // 如果需要此功能，可以考虑维护一个键列表
        logger.warn("RxCache does not support getAllKeys(), returning empty list")
        return emptyList()
    }
}

/**
 * 配置存储异常
 */
class ConfigStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)

