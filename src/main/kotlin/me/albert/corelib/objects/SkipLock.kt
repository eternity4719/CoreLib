package me.albert.corelib.objects

import java.util.concurrent.ConcurrentHashMap

/**
 * 基于 key 的"跳过式"互斥:同一个 key 正在执行时,重复调用直接跳过。
 * 注意:这是 try-lock 语义,不是排队等待。
 */
class SkipLock<K : Any> {
    private val running = ConcurrentHashMap.newKeySet<K>()

    /**
     * 如果该 key 当前空闲,执行 action 并返回 true;
     * 如果正在执行,跳过并返回 false。
     */
    inline fun tryRun(key: K, action: () -> Unit): Boolean {
        if (!acquire(key)) return false
        try {
            action()
        } finally {
            release(key)
        }
        return true
    }

    fun acquire(key: K): Boolean = running.add(key)
    fun release(key: K) { running.remove(key) }
}
