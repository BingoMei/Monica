package cn.netdiscovery.monica.config.category

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 配置验证器接口
 * 
 * 用于验证配置值的有效性。
 * 
 * @author: Monica Team
 * @date: 2025-12-12
 */
interface ConfigValidator<T> {
    /**
     * 验证配置值
     * 
     * @param value 配置值
     * @return 验证结果，如果有效返回 null，否则返回错误信息
     */
    fun validate(value: T): String?
}

/**
 * 配置验证结果
 */
data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
) {
    companion object {
        fun success() = ValidationResult(true)
        fun failure(message: String) = ValidationResult(false, message)
    }
}

/**
 * 通用配置验证器
 */
object CommonValidators {
    
    private val logger: Logger = LoggerFactory.getLogger(CommonValidators::class.java)
    
    /**
     * 字符串非空验证器
     */
    fun nonEmptyString(): ConfigValidator<String> = object : ConfigValidator<String> {
        override fun validate(value: String): String? {
            return if (value.isBlank()) {
                "Value cannot be empty"
            } else {
                null
            }
        }
    }
    
    /**
     * 字符串长度验证器
     */
    fun stringLength(min: Int, max: Int): ConfigValidator<String> = object : ConfigValidator<String> {
        override fun validate(value: String): String? {
            return when {
                value.length < min -> "Value length must be at least $min"
                value.length > max -> "Value length must be at most $max"
                else -> null
            }
        }
    }
    
    /**
     * 数值范围验证器
     */
    fun <T : Number> numberRange(min: T, max: T): ConfigValidator<T> = object : ConfigValidator<T> {
        override fun validate(value: T): String? {
            val doubleValue = value.toDouble()
            val minValue = min.toDouble()
            val maxValue = max.toDouble()
            return when {
                doubleValue < minValue -> "Value must be at least $min"
                doubleValue > maxValue -> "Value must be at most $max"
                else -> null
            }
        }
    }
    
    /**
     * 整数范围验证器
     */
    fun intRange(min: Int, max: Int): ConfigValidator<Int> = object : ConfigValidator<Int> {
        override fun validate(value: Int): String? {
            return when {
                value < min -> "Value must be at least $min"
                value > max -> "Value must be at most $max"
                else -> null
            }
        }
    }
    
    /**
     * URL 验证器
     */
    fun url(): ConfigValidator<String> = object : ConfigValidator<String> {
        override fun validate(value: String): String? {
            return try {
                java.net.URL(value)
                null
            } catch (e: Exception) {
                "Invalid URL format: $value"
            }
        }
    }
    
    /**
     * 组合验证器（多个验证器同时生效）
     */
    fun <T> combine(vararg validators: ConfigValidator<T>): ConfigValidator<T> = object : ConfigValidator<T> {
        override fun validate(value: T): String? {
            validators.forEach { validator ->
                validator.validate(value)?.let { return it }
            }
            return null
        }
    }
    
    /**
     * 可选验证器（值为 null 时跳过验证）
     */
    fun <T> optional(validator: ConfigValidator<T>): ConfigValidator<T?> = object : ConfigValidator<T?> {
        override fun validate(value: T?): String? {
            return if (value == null) {
                null
            } else {
                validator.validate(value)
            }
        }
    }
}

