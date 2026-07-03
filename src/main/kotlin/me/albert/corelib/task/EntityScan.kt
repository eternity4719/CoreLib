package me.albert.corelib.task

import kotlinx.coroutines.delay
import me.albert.corelib.debug
import me.albert.corelib.instance
import me.albert.corelib.utils.launchAsync
import org.bukkit.Bukkit
import kotlin.time.Duration.Companion.seconds

/**
 * 每秒扫描一次全服所有生物/实体，为每个实体触发 [EntityScanEvent]。
 * EntityScanEvent 是异步事件，故循环跑在异步线程上调用 callEvent。
 */
object EntityScan {

    fun init() {
        instance.launchAsync {
            while (true) {
                delay(1.seconds)
                runCatching {
                    scan()
                }.onFailure {
                    if (debug) it.printStackTrace()
                }
            }
        }
    }

    private fun scan() {
        // world.entities 在异步线程不安全，改为遍历已加载区块取各 chunk 的实体
        for (world in Bukkit.getWorlds()) {
            for (chunk in world.loadedChunks) {
                for (entity in chunk.entities) {
                    if (!entity.isValid) continue
                    EntityScanEvent(entity).callEvent()
                }
            }
        }
    }
}
