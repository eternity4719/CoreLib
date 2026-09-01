package me.albert.corelib.utils

import me.albert.corelib.instance
import me.albert.corelib.task.EntityScanEvent
import me.albert.corelib.utils.Expire.EXPIRE_KEY
import org.bukkit.entity.Entity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.EntitiesLoadEvent

/**
 * 实体自动过期清理。给实体打 PDC 时间戳标签 [EXPIRE_KEY],到期后由每秒的 [EntityScanEvent] 扫到并移除,
 * 区块实体加载([EntitiesLoadEvent])时也对账一次。
 *
 * 用途:为临时生成的 DisplayEntity / 投射物等兜底,防止逻辑异常(任务被打断、服务器卡顿、区块卸载等)
 * 导致实体永久残留。即便业务代码自己也会清理,这里作为最后一道保险。
 *
 * PDC 持久化,服务器重启后标签仍在,实体加载到时照常被扫描清除。
 *
 * 用法:`entity.expireAfter(40)` // 40 tick 后过期清理
 */
object Expire : Listener {

    /** 过期时间戳(毫秒)的 PDC 键 */
    private const val EXPIRE_KEY = "corelib_expire_at"

    fun init() {
        instance.registerEvents(this)
    }

    /** 设置实体在 [ticks] tick(20 tick = 1 秒)后过期被清理 */
    fun setExpire(entity: Entity, ticks: Long) {
        entity[EXPIRE_KEY] = System.currentTimeMillis() + (ticks * 50)
    }

    @EventHandler
    fun onScan(event: EntityScanEvent) {
        val entity = event.entity
        if (!isExpired(entity)) return
        // EntityScanEvent 在异步线程触发,removeSafely 内部切回实体所属区域线程
        entity.removeSafely()
    }

    /**
     * 区块实体一读进世界就对账一次,不等下一秒的扫描。
     * EntityScan 只遍历 loadedChunks,区块若只被短暂拉起(如 teleportAsync 的 5 tick 加载)
     * 扫描周期撞不上,过期实体会随区块再次落盘、永远清不掉;在这里补上。
     *
     * 事件在该区块的区域线程上同步触发,**当场删、不要 launch 到下一 tick**:追踪器在实体进世界
     * (onTrackingStart → ChunkMap.addEntity → updatePlayers)时就已经把生成包发给附近玩家了,
     * 同 tick 内跟上销毁包客户端来不及画出来;拖到下一 tick 玩家就会看到过期的怪闪一下。
     */
    @EventHandler
    fun onEntitiesLoad(event: EntitiesLoadEvent) {
        for (entity in event.entities) {
            if (isExpired(entity)) entity.removeIfValid()
        }
    }

    private fun isExpired(entity: Entity): Boolean {
        val expireAt = entity.get<Long>(EXPIRE_KEY) ?: return false
        return System.currentTimeMillis() >= expireAt
    }
}

/** 便捷扩展:[ticks] tick 后自动过期清理本实体(20 tick = 1 秒) */
fun Entity.expireAfter(ticks: Long) = Expire.setExpire(this, ticks)
