package me.albert.corelib.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.albert.corelib.instance
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Level
import kotlin.coroutines.CoroutineContext

/*
 * 自家协程调度层(2026-09-11 起替换 mccoroutine-folia,API 名字照旧:plugin.launch / scope /
 * entityDispatcher / regionDispatcher / globalRegionDispatcher / asyncDispatcher)。
 *
 * 换掉的原因:mccoroutine 的 EntityDispatcher 把 EntityScheduler.run 的返回值扔了,实体已移除时协程永远
 * 挂着——Job 挂在插件作用域上不完成,dispatcher 攥着实体,线上攒到 11 万个协程拖着 30 万只死实体;
 * 它的会话表又按 Plugin 作键而 PluginBase.equals 按名字,热重载时新旧实例串号,corelib 曾为此堆了
 * 一整套"毒会话指纹/自愈"补丁。我们实际只用四个 dispatcher + 每插件一个作用域,自己写更干净。
 *
 * 语义(和之前一致,别改):
 * - 所有 dispatcher 的 isDispatchNeeded 恒 true → launch 默认下一 tick 才跑,同线程也不会当场执行;
 *   要当场跑到第一个挂起点用 CoroutineStart.UNDISPATCHED。
 * - delay 不走 Folia 调度器(不实现 Delay),仍是 kotlinx 默认计时线程 + 回派,所以 delay(50ms) 不等于一 tick,
 *   逐帧循环用 nextTick()/yield()。
 * - 调度器拒绝任务(实体已移除、插件已禁用)时**取消协程再当场跑一次 block 让它走完**:没启动的协程
 *   在恢复入口看到 Job 已取消直接抛 CancellationException,协程体一行不跑;已挂起的在挂起点抛出,
 *   finally 在当前线程跑一次。withContext 的调用方会收到 CancellationException,当"目标已不在"处理。
 * - 实体调度器 retired 回调也会跑 block(实体已移除仍会跑),协程体照旧自己判 isValid/isOnline。
 */

// ---------------- dispatcher ----------------

/** Folia 调度器包装的 dispatcher 基类:调度失败就取消协程并让它走完,不留永远挂起的 Job */
abstract class FoliaDispatcher(protected val plugin: Plugin) : CoroutineDispatcher() {

    override fun isDispatchNeeded(context: CoroutineContext) = true

    final override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (plugin.isEnabled && runCatching { schedule(block) }.getOrDefault(false)) return
        context[Job]?.cancel(CancellationException("调度被拒:${describe()}"))
        block.run()
    }

    /** 把 [block] 交给对应的 Folia 调度器,接了返回 true */
    protected abstract fun schedule(block: Runnable): Boolean

    protected abstract fun describe(): String
}

/** 实体调度器:实体所在区域线程执行;实体已移除(调度器退役)时 run 返回 null → 取消协程 */
class EntityDispatcher(plugin: Plugin, val entity: Entity) : FoliaDispatcher(plugin) {
    override fun schedule(block: Runnable) = entity.scheduler.run(plugin, { block.run() }, { block.run() }) != null
    override fun describe() = "实体 ${entity.type} ${entity.uniqueId} 已移除或插件 ${plugin.name} 已禁用"
}

/** 区域调度器:按坐标定区域线程执行 */
class RegionDispatcher(plugin: Plugin, val location: Location) : FoliaDispatcher(plugin) {
    override fun schedule(block: Runnable): Boolean {
        Bukkit.getRegionScheduler().execute(plugin, location) { block.run() }
        return true
    }

    override fun describe() = "插件 ${plugin.name} 已禁用"
}

/** 全局区域线程 */
class GlobalRegionDispatcher(plugin: Plugin) : FoliaDispatcher(plugin) {
    override fun schedule(block: Runnable): Boolean {
        Bukkit.getGlobalRegionScheduler().execute(plugin) { block.run() }
        return true
    }

    override fun describe() = "插件 ${plugin.name} 已禁用"
}

/** 异步线程池 */
class AsyncDispatcher(plugin: Plugin) : FoliaDispatcher(plugin) {
    override fun schedule(block: Runnable): Boolean {
        Bukkit.getAsyncScheduler().runNow(plugin) { block.run() }
        return true
    }

    override fun describe() = "插件 ${plugin.name} 已禁用"
}

// ---------------- 每插件作用域 ----------------

/** 每个插件实例一份:作用域 + 两个无参 dispatcher 复用 */
private class PluginCoroutineHolder(plugin: Plugin) {
    val scope = CoroutineScope(
        SupervisorJob() + CoroutineName(plugin.name) +
                CoroutineExceptionHandler { _, e -> plugin.logger.log(Level.SEVERE, "协程未捕获异常", e) }
    )
    val global: CoroutineDispatcher = GlobalRegionDispatcher(plugin)
    val async: CoroutineDispatcher = AsyncDispatcher(plugin)
}

/**
 * 按实例身份作键。PluginBase.equals/hashCode 按插件名,热重载后新旧实例同名会撞在一起,
 * 新实例拿到旧实例已取消的作用域——mccoroutine 的会话表就是这么串号的。
 */
private class PluginKey(val plugin: Plugin) {
    override fun equals(other: Any?) = other is PluginKey && other.plugin === plugin
    override fun hashCode() = System.identityHashCode(plugin)
}

private val holders = ConcurrentHashMap<PluginKey, PluginCoroutineHolder>()

/** 已取消的公共作用域:禁用中/已禁用的插件在上面 launch 只会得到一个取消态的 Job,协程体不跑 */
private val deadScope = CoroutineScope(SupervisorJob()).apply { cancel(CancellationException("插件已禁用")) }

// 收到过 PluginDisableEvent 的实例。服务端的禁用序列(发事件→置禁用→注销监听器)不是原子的,
// 窗口期(事件已发、isEnabled 还是 true)在途的 launch 会给正在死的实例重建作用域并永久残留、
// 钉住它的类加载器;墓碑在窗口期开始前(LOWEST)立起。弱引用:强引用同样会钉住旧实例
private val dyingPlugins = CopyOnWriteArrayList<WeakReference<Plugin>>()

private fun Plugin.isDying() = dyingPlugins.any { it.get() === this }

internal object PluginLifecycle : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onDisable(event: PluginDisableEvent) {
        dyingPlugins.removeIf { it.get() == null }
        dyingPlugins.add(WeakReference(event.plugin))
        holders.remove(PluginKey(event.plugin))?.scope?.cancel(CancellationException("插件 ${event.plugin.name} 已禁用"))
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onEnable(event: PluginEnableEvent) {
        // /plugman enable 会重新启用同一个实例,需摘掉墓碑
        dyingPlugins.removeIf { it.get() == null || it.get() === event.plugin }
    }
}

private val skippedLaunches = AtomicLong()

@Volatile
private var lastSkipWarnAt = 0L

/** 禁用中/已禁用的插件还在 launch:限频告警(多半是热重载残留),返回死作用域 */
private fun Plugin.skipLaunch(): CoroutineScope {
    val total = skippedLaunches.incrementAndGet()
    val now = System.currentTimeMillis()
    if (now - lastSkipWarnAt >= 10_000) {
        lastSkipWarnAt = now
        instance.logger.warning(
            "已丢弃插件 $name 的协程调度(累计 $total 次)——" +
                    "实例已禁用或正在禁用,疑似热重载残留,若持续出现请重启服务器"
        )
    }
    return deadScope
}

private fun Plugin.holder(): PluginCoroutineHolder? {
    if (!isEnabled || isDying()) return null
    return holders.computeIfAbsent(PluginKey(this)) { PluginCoroutineHolder(this) }
}

/** 该插件的协程作用域,随插件禁用整体取消 */
val Plugin.scope: CoroutineScope get() = holder()?.scope ?: skipLaunch()

val Plugin.globalRegionDispatcher: CoroutineDispatcher get() = holder()?.global ?: GlobalRegionDispatcher(this)

val Plugin.asyncDispatcher: CoroutineDispatcher get() = holder()?.async ?: AsyncDispatcher(this)

fun Plugin.regionDispatcher(location: Location): CoroutineDispatcher = RegionDispatcher(this, location)

fun Plugin.entityDispatcher(entity: Entity): CoroutineDispatcher = EntityDispatcher(this, entity)

/** 在插件作用域起协程,默认全局区域线程 */
fun Plugin.launch(
    context: CoroutineContext = globalRegionDispatcher,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job = scope.launch(context, start, block)

// ---------------- 免传插件的快捷入口 ----------------

/**
 * 在实体调度器上起协程,不用传插件:归属插件由 [block] 所属的类加载器反查
 * (suspend lambda 编译成调用方插件里的类),就是"写这段代码的插件"——
 * 协程作用域按插件分、随插件禁用而死,和手传自家 instance 完全等价。
 */
fun Entity.launch(
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job {
    val plugin = JavaPlugin.getProvidingPlugin(block.javaClass)
    return plugin.launch(plugin.entityDispatcher(this), start, block)
}

/** 区域线程版 [Entity.launch],归属规则相同 */
fun Location.launch(
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job {
    val plugin = JavaPlugin.getProvidingPlugin(block.javaClass)
    return plugin.launch(plugin.regionDispatcher(this), start, block)
}

fun Plugin.launchAsync(
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
) = launch(asyncDispatcher, start, block)

fun Plugin.launchGlobal(
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
) = launch(globalRegionDispatcher, start, block)
