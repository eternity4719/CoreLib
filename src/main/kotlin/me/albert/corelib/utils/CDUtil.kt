package me.albert.corelib.utils

import org.bukkit.Bukkit
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object CDUtil {

    // 所有的冷却数据
    val cds: ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>> = ConcurrentHashMap()

    // 内部管理免 CD 的玩家集合（使用 ConcurrentHashMap.newKeySet() 保证多线程并发安全）
    private val noCdPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    /**
     * 检查并处理玩家冷却
     */
    @JvmStatic
    fun isInCd(uuid: UUID, key: String, time: Long): Long {
        // 1. 优先判定管理员免 CD 逻辑
        if (noCdPlayers.contains(uuid)) {
            Bukkit.getPlayer(uuid)?.sendActionBar("§c已为您跳过剩余冷却: $time")
            return 0L
        }

        val current = System.currentTimeMillis()

        // 2. 获取或创建该玩家的冷却数据
        val playerCd = cds.computeIfAbsent(uuid) { ConcurrentHashMap() }

        // 3. 获取上次触发时间
        val lastTime = playerCd[key]
        if (lastTime == null) {
            playerCd[key] = current
            return 0L
        }

        val passed = current - lastTime
        val remaining = time - passed

        // 4. 冷却时间已过，刷新冷却起始点
        if (remaining <= 0L) {
            playerCd[key] = current
            return 0L
        }

        return remaining
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