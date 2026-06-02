package me.albert.corelib.objects

import java.util.concurrent.ConcurrentHashMap

/**
 * 跳过式按 key 互斥,支持超时接管。
 * @param timeoutMillis key 被占用超过这个时长后视为失效,允许重新获取。默认不超时。
 */
class SkipLock<K : Any>(
    private val timeoutMillis: Long = Long.MAX_VALUE
) {
    // key -> 获取时间戳,这个时间戳同时充当释放凭证
    private val running = ConcurrentHashMap<K, Long>()

    /**
     * 尝试占用 key。成功返回一个 token(release 时要传回),
     * 已有人在跑且未超时则返回 null。
     */
    fun acquire(key: K): Long? {
        val now = System.currentTimeMillis()
        var acquired = false
        running.compute(key) { _, since ->
            if (since == null || now - since >= timeoutMillis) {
                acquired = true
                now
            } else since
        }
        return if (acquired) now else null
    }

    /** 只有 token 匹配时才真正释放,避免误删超时后被别人接管的锁。 */
    fun release(key: K, token: Long) {
        running.computeIfPresent(key) { _, since ->
            if (since == token) null else since
        }
    }

    inline fun tryRun(key: K, action: () -> Unit): Boolean {
        val token = acquire(key) ?: return false
        try {
            action()
        } finally {
            release(key, token)
        }
        return true
    }
}
