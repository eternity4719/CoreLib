package me.albert.corelib.runtime

import org.bukkit.plugin.java.JavaPlugin

/**
 * CoreLibRuntime —— 纯运行时依赖容器插件。
 *
 * 本插件不包含任何业务逻辑，唯一作用是把 CoreLib 所需的第三方库
 * （kotlin stdlib、coroutines、exposed、HikariCP、qqwry、mccoroutine 等）
 * 打包进自身 jar 并在服务器启动时加载。由于传统 plugin.yml 体系下所有插件
 * 共享 classloader，这些类对 CoreLib 及其它下游插件均可见。
 *
 * 好处：CoreLib 主 jar 只含自身代码（几十 KB），日常迭代上传飞快；
 * 这些体积较大、更新频率低的依赖只随本插件部署一次。
 */
class CoreLibRuntime : JavaPlugin() {

    override fun onEnable() {
        logger.info("CoreLibRuntime 已加载，运行时依赖就绪。")
    }
}
