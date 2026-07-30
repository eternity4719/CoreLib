package me.albert.corelib.utils

import me.albert.corelib.instance
import me.albert.corelib.utils.CDUtil.isInCd
import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong


fun UUID.checkCD(key: String, time: Long): Long {
    return CDUtil.isInCd(this, key, time)
}

fun Entity.checkCD(key: String, time: Long): Long {
    return this.uniqueId.checkCD(key, time)
}

fun UUID.inCD(key: String, time: Long) = checkCD(key, time) > 0

fun Entity.inCD(key: String, time: Long) = checkCD(key, time) > 0

object CDUtil {

    // 所有的冷却数据：uuid -> (key -> 冷却结束时刻的毫秒时间戳)
    val cds: ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>> = ConcurrentHashMap()

    // 内部管理免 CD 的玩家集合（使用 ConcurrentHashMap.newKeySet() 保证多线程并发安全）
    val noCdPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    // 惰性清扫的最小间隔：由 isInCd 顺手触发，不额外占用调度任务
    private const val SWEEP_INTERVAL = 10_000L
    private val lastSweep = AtomicLong(0L)

    /**
     * 检查并处理玩家冷却
     */
    @JvmStatic
    fun isInCd(uuid: UUID, key: String, time: Long): Long {
        // 1. 优先判定管理员免 CD 逻辑
        if (noCdPlayers.contains(uuid)) {
            Bukkit.getPlayer(uuid)?.sendActionBar("§c已为您跳过本次冷却(${time}ms)")
            return 0L
        }

        val current = System.currentTimeMillis()
        sweep(current)

        // 2. 获取或创建该玩家的冷却数据
        val playerCd = cds.computeIfAbsent(uuid) { ConcurrentHashMap() }

        // 3. 首次触发或冷却已过，重新计时
        val expireAt = playerCd[key]
        val remaining = if (expireAt == null) 0L else expireAt - current
        if (remaining <= 0L) {
            playerCd[key] = current + time
            return 0L
        }

        return remaining
    }

    /**
     * 惰性清理：由 [isInCd] 顺手触发，扔到异步线程执行，不占用主线程/区域线程
     *
     * 只删过期项，因此对使用方完全透明：这些 key 下次被查到时本来也会判为可用并重新计时
     * （离线玩家、已销毁的实体不会有人再来查它们，条目就一直留着）
     */
    private fun sweep(current: Long) {
        val last = lastSweep.get()
        if (current - last < SWEEP_INTERVAL) {
            return
        }
        // CAS 抢占：同一轮只让一个线程清扫
        if (!lastSweep.compareAndSet(last, current)) {
            return
        }
        instance.launchAsync {
            cds.forEach { (uuid, keys) ->
                keys.entries.removeIf { it.value <= current }
                if (keys.isEmpty()) {
                    // 判空与写入有竞态，用 computeIfPresent 原子地只删掉仍然为空的表
                    cds.computeIfPresent(uuid) { _, existing -> existing.ifEmpty { null } }
                }
            }
        }
    }

    /**
     * 添加玩家到免 CD 列表
     */
    @JvmStatic
    fun addNoCdUUID(uuid: UUID) {
        noCdPlayers.add(uuid)
    }

    /**
     * 将玩家从免 CD 列表中移除
     */
    @JvmStatic
    fun removeNoCdUUID(uuid: UUID) {
        noCdPlayers.remove(uuid)
    }

    /**
     * 检查玩家是否处于免 CD 状态
     */
    @JvmStatic
    fun isNoCd(uuid: UUID): Boolean {
        return noCdPlayers.contains(uuid)
    }

    /**
     * 清理全服所有的冷却数据（同时清空免 CD 列表）
     */
    @JvmStatic
    fun clearAllCD() {
        cds.clear()
        noCdPlayers.clear()
    }
}