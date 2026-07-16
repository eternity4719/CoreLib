package me.albert.corelib.utils

import com.github.jarod.qqwry.IPZone
import com.github.jarod.qqwry.QQWry
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.net.InetSocketAddress


/**
 * 纯真 IP 数据库 (qqwry.dat) 的 Kotlin 封装。
 *
 * QQWry 实例本身线程安全，可被多线程共享，因此这里用单例缓存。
 * 数据库文件需用户自行放入插件数据文件夹（不再内置，以减小 jar 体积）。
 * 文件缺失或损坏会导致服务器启动失败（IP 库为硬性依赖）。
 * 替换 qqwry.dat 后调用 [reload] 即可热更新，无需重启服务器。
 *
 * 注意：纯真库只支持 IPv4，传入 IPv6 或非法格式会返回 null。
 */
object IpUtil {

    private const val DB_FILE_NAME = "qqwry.dat"

    /**
     * 当前加载的 QQWry 实例（约 26MB 常驻内存）。
     * 未调用 [load]、文件缺失或加载失败时为 null，所有查询方法将安全返回 null。
     */
    @Volatile
    private var qqwry: QQWry? = null

    /**
     * 从插件数据文件夹加载 qqwry.dat。
     * 文件缺失或加载失败时记录严重错误并关闭服务器（IP 库是硬性依赖）。
     * 应在插件 onEnable 时调用一次。
     */
    fun load(plugin: JavaPlugin) {
        val dbFile = File(plugin.dataFolder, DB_FILE_NAME)
        if (!dbFile.exists()) {
            plugin.logger.severe(
                "[CoreLib] 未找到 $DB_FILE_NAME，IP 库为必需组件，服务器即将关闭。" +
                        "请将纯真数据库放入: ${dbFile.absolutePath}"
            )
            qqwry = null
            plugin.server.shutdown()
            return
        }
        qqwry = runCatching { QQWry(dbFile.toPath()) }
            .onFailure { plugin.logger.severe("[CoreLib] 加载 $DB_FILE_NAME 失败，服务器即将关闭: ${it.message}") }
            .getOrNull()
        if (qqwry == null) {
            plugin.server.shutdown()
            return
        }
        plugin.logger.info("[CoreLib] 纯真 IP 数据库已加载，版本: $databaseVersion")
    }

    /** 重新从数据文件夹加载数据库，用于替换 qqwry.dat 后热更新。 */
    fun reload(plugin: JavaPlugin) = load(plugin)

    /** 数据库版本号，例如 2021.08.11；加载失败时为 "unknown"。 */
    val databaseVersion: String
        get() = qqwry?.databaseVersion ?: "unknown"

    /**
     * 查询 IP 归属地。
     *
     * @param ip IPv4 地址，例如 "114.114.114.114"
     * @return 查询结果；IP 非法、为 IPv6 或数据库未加载时返回 null
     */
    fun query(ip: String): IpLocation? {
        val db = qqwry ?: return null
        val zone: IPZone = runCatching { db.findIP(ip.trim()) }.getOrNull() ?: return null
        return IpLocation(
            ip = zone.ip,
            country = zone.mainInfo,
            area = zone.subInfo,
        )
    }

    /**
     * 查询 IP 归属地并拼成完整字符串，例如 "中国江苏省南京市 电信"。
     *
     */
    fun queryString(ip: String): String? {
        return query(ip)?.full?.takeIf { it.isNotBlank() }
    }
}

/**
 * IP 归属地查询结果。
 *
 * @param ip 查询的 IP 地址
 * @param country 主信息，一般为国家/省市，例如 "中国江苏省南京市"
 * @param area 子信息，一般为运营商/机构，例如 "电信"
 */
data class IpLocation(
    val ip: String,
    val country: String,
    val area: String,
) {
    /** 完整归属地，country 与 area 用空格拼接并去除首尾空白。 */
    val full: String get() = "$country $area".trim()
}


fun getPlayerIp(player: Player): String? {
    val address: InetSocketAddress = player.address ?: return null // 极少数情况下可能为 null
    return address.address?.hostAddress
}

fun Player.getIP(): String? {
    return getPlayerIp(this)
}

fun Player.getIpRegion(): String? {
    return IpUtil.queryString(getIP() ?: return null)
}

fun Player.getIpLoc(): IpLocation? {
    return IpUtil.query(getIP() ?: return null)
}
