package me.albert.corelib.utils

import me.albert.corelib.instance
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataHolder
import org.bukkit.persistence.PersistentDataType

/**
 * 内部私有工具：获取类型匹配
 */
@PublishedApi
internal inline fun <reified T : Any> getPdcType(): PersistentDataType<*, T> {
    val type = when (T::class) {
        String::class -> PersistentDataType.STRING
        Int::class -> PersistentDataType.INTEGER
        Double::class -> PersistentDataType.DOUBLE
        Float::class -> PersistentDataType.FLOAT
        Long::class -> PersistentDataType.LONG
        Byte::class -> PersistentDataType.BYTE
        Short::class -> PersistentDataType.SHORT
        ByteArray::class -> PersistentDataType.BYTE_ARRAY
        IntArray::class -> PersistentDataType.INTEGER_ARRAY
        LongArray::class -> PersistentDataType.LONG_ARRAY
        Boolean::class -> PersistentDataType.BOOLEAN
        else -> throw IllegalArgumentException("不支持的 PDC 存储类型: ${T::class.java.name}")
    }
    @Suppress("UNCHECKED_CAST")
    return type as PersistentDataType<*, T>
}

// =================== 核心扩展：直接赋予 Holder 操作能力 ===================

/**
 * 运算符重载 SET：支持 holder["key"] = value 自动判断类型
 * 如果传入 null，则自动删除该键
 */
inline operator fun <reified T : Any> PersistentDataHolder.set(keyStr: String, value: T?) {
    val key = NamespacedKey(instance, keyStr)
    if (value == null) {
        this.persistentDataContainer.remove(key)
    } else {
        this.persistentDataContainer.set(key, getPdcType<T>(), value)
    }
}

/**
 * 运算符重载 GET：支持 val v: Type? = holder["key"]
 */
inline operator fun <reified T : Any> PersistentDataHolder.get(keyStr: String): T? {
    val key = NamespacedKey(instance, keyStr)
    return this.persistentDataContainer.get(key, getPdcType<T>())
}

/**
 * 扩展方法：检查是否存在某个键
 */
fun PersistentDataHolder.hasPD(keyStr: String): Boolean {
    return this.persistentDataContainer.has(NamespacedKey(instance, keyStr))
}

/**
 * 扩展方法：删除某个键
 */
fun PersistentDataHolder.removePD(keyStr: String) {
    this.persistentDataContainer.remove(NamespacedKey(instance, keyStr))
}
