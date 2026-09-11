package me.albert.corelib.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import kotlin.coroutines.CoroutineContext

/**
 * 实体调度器上的协程 dispatcher,替代 mccoroutine-folia 自带的 `EntityDispatcher`。
 *
 * mccoroutine 那个把 `EntityScheduler.run` 的返回值直接扔了:实体已移除(调度器退役)时 run 返回
 * null、什么都不跑,协程就永远停在"待恢复"——Job 一直挂在插件作用域的子任务链上不完成,Job 的
 * context 里攥着 dispatcher、dispatcher 攥着实体,整只实体连同它引用的玩家/仇恨目标全部漏掉。
 * 2026-09-11 线上堆快照:11 万个永不完成的 StandaloneCoroutine + 11 万个 EntityDispatcher,拖着
 * 9 万只僵尸猪灵、5 万只守卫者、11 万个掉落物、372 个已下线的 ServerPlayer;来源是异步线程上的
 * EntityScanEvent 监听器对着刚死的怪 launch(刷怪塔每秒死几十只,必撞),老年代一夜从 14 GB 涨到 41 GB。
 *
 * 这里 run 返回 null 就先取消 Job、再当场 run 一次 block 让协程走完:没启动的协程在恢复入口
 * 看到 Job 已取消直接抛 CancellationException 结束,协程体一行不跑;已挂起的(UNDISPATCHED 起的
 * 循环)在挂起点抛出,finally 会在当前线程跑一次——实体反正已经没了,这和 mccoroutine 自己的
 * retired 回调也在"别的"线程上跑整个 block 是同一口径。`withContext(safeEntityDispatcher(x))`
 * 的调用方会收到 CancellationException,当"目标已不在"处理即可,外层协程不受牵连。
 */
class SafeEntityDispatcher(private val plugin: Plugin, private val entity: Entity) : CoroutineDispatcher() {

    override fun isDispatchNeeded(context: CoroutineContext) = true

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (plugin.isEnabled) {
            val task = entity.scheduler.run(plugin, { block.run() }, { block.run() })
            if (task != null) return
        }
        context[Job]?.cancel(CancellationException("实体已移除,调度器已退役"))
        block.run()
    }
}

/** 实体调度器 dispatcher,给 `withContext` 用;`entity.launch {}` 内部走的就是它 */
fun Plugin.safeEntityDispatcher(entity: Entity): CoroutineDispatcher = SafeEntityDispatcher(this, entity)
