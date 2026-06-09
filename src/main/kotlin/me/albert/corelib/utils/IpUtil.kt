package me.albert.corelib.utils

import com.github.jarod.qqwry.IPZone
import com.github.jarod.qqwry.QQWry
import org.bukkit.entity.Player
import java.net.InetSocketAddress


/**
 * 纯真 IP 数据库 (qqwry.dat) 的 Kotlin 封装。
 *
 * QQWry 实例本身线程安全，可被多线程共享，因此这里用单例懒加载。
 * 数据库文件 qqwry.dat 已放入资源目录，QQWry 无参构造会自动从 classpath 加载。
 *
 * 注意：纯真库只支持 IPv4，传入 IPv6 或非法格式会返回 null。
 */
object IpUtil {

    /**
     * 懒加载的 QQWry 实例。首次使用时把 26MB 的数据库读入内存（约一次性开销）。
     * 加载失败（资源缺失等）时为 null，所有查询方法将安全返回 null。
     */
    private val qqwry: QQWry? by lazy {
        runCatching { QQWry() }
            .onFailure { it.printStackTrace() }
            .getOrNull()
    }

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
