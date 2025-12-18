package cn.netdiscovery.monica.config.storage

/**
 * 统一配置存储接口
 * 
 * 抽象了不同存储实现的差异，提供统一的配置读写接口。
 * 
 * @author: Monica Team
 * @date: 2025-12-12
 */
interface ConfigStorage {
    /**
     * 保存配置值
     * 
     * @param key 配置键
     * @param value 配置值（支持基本类型和可序列化对象）
     */
    fun <T> save(key: String, value: T)
    
    /**
     * 加载配置值
     * 
     * @param key 配置键
     * @param default 默认值（当配置不存在时返回）
     * @return 配置值，如果不存在则返回默认值
     */
    fun <T> load(key: String, default: T): T
    
    /**
     * 检查配置是否存在
     * 
     * @param key 配置键
     * @return 如果配置存在返回 true，否则返回 false
     */
    fun exists(key: String): Boolean
    
    /**
     * 删除配置
     * 
     * @param key 配置键
     */
    fun remove(key: String)
    
    /**
     * 清空所有配置
     */
    fun clear()
    
    /**
     * 获取所有配置键
     * 
     * @return 配置键列表
     */
    fun getAllKeys(): List<String>
}

