package me.albert.corelib.utils

import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * 跨插件传数据的通用事件。线上各插件经常各自单独 PlugManX reload,任何两个插件之间共享的类
 * (自定义事件、ServicesManager 接口)都会在一方重载后变成旧类加载器里的死引用,所以事件类只能
 * 住在 corelib(它不可能单独重载,所有插件都攥着它的类),两边只共享 [channel] 字符串约定。
 *
 * [data] 里只放 JDK 与 Bukkit 的类型(String、数字、Player、ItemStack、Location 这些),
 * **不放任何插件自己的类**,否则接收方在发送方重载后拿到的就是旧类的实例。
 *
 * 生命周期完全交给 Bukkit:插件禁用时 `HandlerList.unregisterAll(plugin)` 摘掉它的监听器,
 * `callEvent` 跳过已禁用插件的处理器。代价是接收方不在(重载的那不到一秒)时发的消息就丢了,
 * 所以只用于丢一两条无所谓的通知/统计,不能丢的东西(钱、物品)走数据库。
 *
 * async 标记跟发送线程走:Canvas 的 `PaperEventManager.callEvent` 会校验,同步事件从非 tick 线程
 * 触发、或异步事件从 tick 线程触发都直接抛异常;Folia 系的 `isPrimaryThread` 对任意区域线程都为 true。
 *
 * 用法:发送 `DataEvent("spirit.count", mapOf("player" to name, "key" to "low")).callEvent()`;
 * 接收在普通 `@EventHandler` 里先按 [channel] 过滤,再 `val player: String = event["player"]` 取字段。
 */
class DataEvent(val channel: String, val data: Map<String, Any>) : Event(!Bukkit.isPrimaryThread()) {

    /** 按 [key] 取字段并转成期望类型(由接收变量的声明推断);缺失或类型不符直接抛,两插件约定不一致属于 bug,让它在日志里炸出来 */
    inline operator fun <reified T> get(key: String): T =
        data[key] as? T ?: error("DataEvent[$channel] 字段 $key 缺失或不是 ${T::class.simpleName}")

    override fun getHandlers() = HANDLERS

    companion object {
        private val HANDLERS = HandlerList()

        // Bukkit 的事件反射系统必须要找到这个静态方法
        @JvmStatic
        fun getHandlerList() = HANDLERS
    }
}
