package me.albert.corelib.utils

import me.albert.corelib.instance
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.Plugin
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object PlayerNameUtil : Listener {

    // 1. 明确 Value 不为 null。将 Key 映射为精确大小写的 Name，再由 Name 映射 UUID 是最稳妥的
    // 如果只需要获取 UUID，这里可以直接存 Map<String, UUID>，更省内存
    private val nameCache = ConcurrentHashMap<String, String>()
    private val uuidCache = ConcurrentHashMap<String, UUID>()

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val lowerName = player.name.lowercase(Locale.ROOT) // 统一使用 Locale.ROOT 避免某些系统语言环境下的奇葩 Bug

        nameCache[lowerName] = player.name
        uuidCache[lowerName] = player.uniqueId
    }

    /**
     * 安全地获取 OfflinePlayer。
     * 只有当缓存命中时才返回，绝对不触发 Mojang 网络的阻塞请求。
     */
    fun getPlayer(name: String): OfflinePlayer? {
        val lowerName = name.lowercase(Locale.ROOT)

        // 优先通过缓存的 UUID 获取，这样效率最高且绝对不卡主线程
        val uuid = uuidCache[lowerName]
        if (uuid != null) {
            return Bukkit.getOfflinePlayer(uuid)
        }

        // 降级使用精确大小写的名字获取
        val exactName = nameCache[lowerName] ?: return null
        return Bukkit.getOfflinePlayer(exactName)
    }

    fun getUUID(name: String): UUID? {
        return uuidCache[name.lowercase(Locale.ROOT)]
    }

    fun load(plugin: Plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin)


        // 2. 安全读取：在主线程获取离线玩家快照（仅获取引用），防止异步并发修改异常
        val offlinePlayers = Bukkit.getOfflinePlayers()

        // 3. 异步解析：将耗时的字符串转换和 Map 写入放入异步线程
        instance.launchAsync {
            for (player in offlinePlayers) {
                val name = player.name ?: continue
                val uuid = player.uniqueId
                val lowerName = name.lowercase(Locale.ROOT)

                nameCache[lowerName] = name
                uuidCache[lowerName] = uuid
            }
        }
    }
}


fun playerOf(name: String) = PlayerNameUtil.getPlayer(name)

fun uuidOrNullOf(name: String) = PlayerNameUtil.getUUID(name)

fun nameOrNullOf(name: String) = playerOf(name)?.name

fun nameOf(name: String) = nameOrNullOf(name) ?: name

fun uuidOf(name: String): UUID =
    uuidOrNullOf(name)
        ?: UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(StandardCharsets.UTF_8))