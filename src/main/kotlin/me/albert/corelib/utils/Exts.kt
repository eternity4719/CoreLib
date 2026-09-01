package me.albert.corelib.utils

import com.github.shynixn.mccoroutine.folia.*
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import me.albert.corelib.instance
import me.albert.corelib.server
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.metadata.Metadatable
import org.bukkit.plugin.IllegalPluginAccessException
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import java.lang.ref.WeakReference
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/* =========================================================================
 * 1. 颜色与字符处理优化
 * ========================================================================= */

val String.bukkit: String
    get() = mm.deserialize(rBukkit).toLegacy().replace("&", "§")

val String.rBukkit: String
    get() = replace("§", "&")

fun CommandSender.asPlayer(tip: String = "&c玩家才能使用此命令"): Player? {
    if (this is Player) return this
    if (tip.isNotBlank()) sendMsg(tip)
    return null
}

val gson = Gson()

/** 共享单例，只读！ItemStack 可变，需修改请先 clone()，否则会污染所有引用处 */
val air = ItemStack(Material.AIR)

/** 共享单例，只读！ItemStack 可变，需修改请先 clone()，否则会污染所有引用处 */
val stone = ItemStack(Material.STONE)

var prefix = "§7[§b系统§7] §a"

val String.prefixed: String get() = prefix + this.bukkit

fun CommandSender.sendMsg(msg: String) {
    this.sendMessage(msg.prefixed)
}

fun Entity.isInCurrentRegion() = server.isOwnedByCurrentRegion(this)

fun Location.isInCurrentRegion() = server.isOwnedByCurrentRegion(this)

fun Entity.removeIfValid(): Boolean {
    check(isInCurrentRegion()) { "removeIfValid must be called on the owning region of $this" }
    if (isValid) {
        remove()
        return true
    }
    return false
}

/* ---- 插件生命周期墓碑:热重载/禁用竞态防护 ---- */

// 收到过 PluginDisableEvent 的实例。服务端的禁用序列(发事件→置禁用→注销监听器)
// 不是原子的,窗口期(事件已发、isEnabled 还是 true)在途的 launch 会把 MCCoroutine
// 刚销毁的会话意外重建并永久残留;墓碑在窗口期开始前(LOWEST)立起,launchGuarded
// 据此拦截重建。必须按身份比较并用弱引用:PluginBase.equals 按插件名,普通集合会
// 误伤重载后的新实例,强引用会把旧实例和它的类加载器钉在内存里
private val dyingPlugins = CopyOnWriteArrayList<WeakReference<Plugin>>()

private fun Plugin.isDying() = dyingPlugins.any { it.get() === this }

internal object PluginLifecycle : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onDisable(event: PluginDisableEvent) {
        dyingPlugins.removeIf { it.get() == null }
        dyingPlugins.add(WeakReference(event.plugin))
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

/**
 * 调度守卫:墓碑/禁用状态丢弃调度并限频告警;毒会话自愈后重试。
 *
 * 注意这只保护调度层:残留实例的监听器在调度之前执行的代码(如删实体)
 * 拦不住,所以跳过必须可见——每 10 秒最多告警一次,提示尽快重启。
 * dispatcher 属性访问也可能抛(会话创建时的 isEnabled 检查),所以求值
 * 放在 catch 范围内。
 */
private fun Plugin.launchGuarded(build: () -> Job): Job {
    if (!isEnabled || isDying()) return skipLaunch()
    return try {
        build()
    } catch (_: IllegalPluginAccessException) {
        if (isEnabled) healStaleSession(build) else skipLaunch()
    }
}

/**
 * 毒会话指纹:自身启用但调度被拒。MCCoroutine 的会话表用 Plugin 作键,
 * 而 PluginBase.equals 按插件名——热重载竞态残留的旧实例会话会被新实例
 * 按名字撞上,调度时携带的是旧实例引用。清掉串号的死会话再重试一次,
 * 重试创建的就是本实例的全新会话,本次调用无损完成
 */
private fun Plugin.healStaleSession(build: () -> Job): Job {
    return try {
        mcCoroutineConfiguration.disposePluginSession()
        instance.logger.warning("检测到插件 $name 的残留协程会话(热重载竞态产物),已清除重建")
        build()
    } catch (_: Exception) {
        skipLaunch()
    }
}

private fun Plugin.skipLaunch(): Job {
    val total = skippedLaunches.incrementAndGet()
    val now = System.currentTimeMillis()
    if (now - lastSkipWarnAt >= 10_000) {
        lastSkipWarnAt = now
        instance.logger.warning(
            "已丢弃插件 $name 的协程调度(累计 $total 次)——" +
                    "实例已禁用或正在禁用,疑似热重载残留,若持续出现请重启服务器"
        )
    }
    return Job().apply { cancel() }
}

fun Plugin.launch(
    entity: Entity, start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
) = launchGuarded { launch(entityDispatcher(entity), start, block) }

fun Plugin.launch(
    location: Location, start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
) = launchGuarded { launch(regionDispatcher(location), start, block) }

fun Plugin.launchAsync(
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
) = launchGuarded { launch(asyncDispatcher, start, block) }

fun Plugin.launchGlobal(
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
) = launchGuarded { launch(globalRegionDispatcher, start, block) }

fun ItemStack?.isSame(other: ItemStack?): Boolean {
    return this?.isSimilar(other) == true && this.amount == other?.amount
}


val ItemStack?.isNull: Boolean
    get() = this == null || this.isEmpty

/* =========================================================================
 * 2. 物品名校验与 RPG Lore 属性解析 (性能大幅优化版)
 * ========================================================================= */

fun ItemStack?.checkName(name: String): Boolean = checkNames(name)

fun ItemStack?.checkNames(vararg names: String): Boolean {
    if (this == null || isEmpty) return false
    val meta = itemMeta ?: return false
    if (!meta.hasDisplayName()) return false
    val currentName = meta.displayName

    // 性能优化：在外层只进行一次转换，避免在 any 循环里反复解析已经转换过的 name
    return names.any { currentName.equals(it.bukkit.trim(), ignoreCase = true) }
}

fun Entity.checkMainHand(vararg name: String): Boolean {
    if (this !is LivingEntity) return false
    val mainHand = equipment?.itemInMainHand ?: return false
    return mainHand.checkNames(*name)
}

fun checkItemDisplayName(item: ItemStack?, display: String): Boolean {
    if (item == null || item.isEmpty) return false
    val meta = item.itemMeta ?: return false
    return meta.hasDisplayName() && meta.displayName.equals(display, ignoreCase = true)
}

fun JavaPlugin.registerEvents(listener: Listener) {
    server.pluginManager.registerEvents(listener, this)
}

/** 把含 `%s%` 占位符的 lore 模板转换颜色后拆成前后缀；模板不含 `%s%` 时返回 null */
private fun parseLorePattern(str: String): Pair<String, String>? {
    val target = str.bukkit
    if (!target.contains("%s%")) return null
    val (first, second) = target.split("%s%", limit = 2)
    return first to second
}

/**
 * 提取 Lore 中的 RPG 属性值
 * 优化点：只进行 1 次 ItemMeta 深拷贝，使用原生 substring 避免多余的临时字符串对象生成
 */
fun ItemStack?.getLoreAttributeValue(str: String): Double {
    if (this == null || isEmpty) return -1.0
    val meta = itemMeta ?: return -1.0
    val lore = meta.lore ?: return -1.0

    val (first, second) = parseLorePattern(str) ?: return -1.0

    for (line in lore) {
        if (line.startsWith(first) && line.endsWith(second)) {
            return line.substring(first.length, line.length - second.length).toDoubleOrNull() ?: 0.0
        }
    }
    return -1.0
}

/**
 * 动态刷新 Lore 中的 RPG 属性值
 * 优化点：无属性变更时绝对不写回 ItemMeta，避免大服高频受击/刷新属性时主线程卡顿
 */
fun ItemStack.setLoreAttribute(str: String, value: Double) {
    if (isEmpty) return
    val meta = itemMeta ?: return
    val lore = meta.lore ?: return

    val (first, second) = parseLorePattern(str) ?: return
    val formattedValue = String.format(Locale.ROOT, "%.2f", value) // 显式指定 ROOT 规避德语等 VPS 的逗号 Bug

    var changed = false
    val newLore = lore.map { line ->
        val newLine = if (line.startsWith(first) && line.endsWith(second)) "$first$formattedValue$second" else line
        if (newLine != line) changed = true
        newLine
    }

    if (changed) {
        meta.lore = newLore
        this.itemMeta = meta
    }
}

/**
 * 判断单个方块是否会造成窒息伤害。
 * isOccluding() 表示完全不透明的实心方块（石头、泥土、混凝土等），
 * 玻璃、台阶、楼梯、栅栏等不会。
 */
fun Block.causesSuffocation(): Boolean =
    type.isOccluding && isSolid

/**
 * 检测该坐标作为玩家站立点是否会窒息。
 * 玩家占据脚部和头部两格。
 */
fun Location.willSuffocate(): Boolean {
    val feet = block
    val head = feet.getRelative(0, 1, 0)
    return feet.causesSuffocation() || head.causesSuffocation()
}

/**
 * 寻找安全传送点时用：空间不会窒息，且脚下有实心支撑。
 */
fun Location.isSafe(): Boolean =
    !willSuffocate() && block.getRelative(0, -1, 0).type.isSolid


/* =========================================================================
 * 3. Metadata 简易包装
 * ========================================================================= */

fun Metadatable.setMetadata(key: String, value: Any) {
    setMetadata(key, FixedMetadataValue(instance, value))
}

fun Metadatable.deleteMetadata(key: String) {
    removeMetadata(key, instance)
}

inline fun <reified T> Metadatable.getMeta(key: String): T? {
    // 优化：直接获取第一个，不需要先通过 hasMetadata 进行二次哈希查找
    return getMetadata(key).firstOrNull()?.value() as? T
}


/* =========================================================================
 * 4. 世界与位置扩展
 * ========================================================================= */

fun Location.playSound(sound: Sound, volume: Float, pitch: Float) {
    world?.playSound(this, sound, volume, pitch)
}

fun Location.dropItem(item: ItemStack) = world?.dropItem(this, item)

inline fun <reified T : Entity> Location.spawn(): T {
    return this.world.spawn(this, T::class.java)
}

fun Player.playSound(sound: Sound, volume: Float, pitch: Float) {
    this.playSound(this, sound, volume, pitch)
}


/* =========================================================================
 * 5. 逻辑流与数学扩展 (契约 Contracts 智能类型转换)
 * ========================================================================= */

typealias UnitBlock = () -> Unit

@OptIn(ExperimentalContracts::class)
inline fun <T> T?.isNull(block: UnitBlock = {}): Boolean {
    contract {
        // 告诉编译器：如果返回 false，说明它绝对不为 null
        returns(false) implies (this@isNull != null)
    }
    if (this == null) block()
    return this == null
}

@OptIn(ExperimentalContracts::class)
inline fun <T> T?.isNotNull(block: (T) -> Unit = {}): Boolean {
    contract {
        returns(true) implies (this@isNotNull != null)
    }
    if (this != null) block(this)
    return this != null
}

@OptIn(ExperimentalContracts::class)
inline fun <T> T?.isNotNullAnd(condition: Boolean, block: (T) -> Unit = {}): Boolean {
    contract {
        returns(true) implies (this@isNotNullAnd != null)
    }
    val check = this != null && condition
    if (check) block(this)
    return check
}

@OptIn(ExperimentalContracts::class)
inline fun <T> T?.isNullAnd(condition: Boolean, block: UnitBlock = {}): Boolean {
    contract {
        returns(false) implies (this@isNullAnd != null)
    }
    val check = this == null && condition
    if (check) block()
    return check
}

inline fun <T> T?.isNullOr(condition: Boolean, block: UnitBlock = {}): Boolean {
    val check = this == null || condition
    if (check) block()
    return check
}

inline fun <T> T.isEqual(other: Any?, block: (T) -> Unit = {}): Boolean {
    val check = this == other
    if (check) block(this)
    return check
}

@OptIn(ExperimentalContracts::class)
inline fun Boolean?.isTrue(block: UnitBlock = {}): Boolean {
    contract {
        // 对于 Boolean?，只要返回 true 且不为 null，编译器就会自动智能转换为非空 [Boolean](且为true)
        returns(true) implies (this@isTrue != null)
    }
    if (this == true) block()
    return this == true
}

@OptIn(ExperimentalContracts::class)
inline fun Boolean?.isFalse(block: UnitBlock = {}): Boolean {
    contract {
        returns(true) implies (this@isFalse != null)
    }
    if (this == false) block()
    return this == false
}

@OptIn(ExperimentalContracts::class)
inline fun Boolean?.isNotTrue(block: UnitBlock = {}): Boolean {
    contract {
        returns(false) implies (this@isNotTrue != null)
    }
    if (this != true) block()
    return this != true
}

@OptIn(ExperimentalContracts::class)
inline fun Boolean?.isNotFalse(block: UnitBlock = {}): Boolean {
    contract {
        returns(false) implies (this@isNotFalse != null)
    }
    if (this != false) block()
    return this != false
}

@OptIn(ExperimentalContracts::class)
inline fun Boolean?.isTrueAnd(condition: Boolean, block: UnitBlock = {}): Boolean {
    contract {
        returns(true) implies (this@isTrueAnd != null)
    }
    val check = this == true && condition
    if (check) block()
    return check
}

@OptIn(ExperimentalContracts::class)
inline fun Boolean?.isFalseAnd(condition: Boolean, block: UnitBlock): Boolean {
    contract {
        returns(true) implies (this@isFalseAnd != null)
    }
    val check = this == false && condition
    if (check) block()
    return check
}

fun Boolean?.toInt(): Int = if (this == true) 1 else 0

fun Int.negative(): Int = -this

fun Int.negativeIf(condition: Boolean): Int = if (condition) -this else this

fun Long.negativeIf(condition: Boolean): Long = if (condition) -this else this

tailrec fun Int.pow(exp: Int, acc: Int = 1): Int =
    if (exp == 0) acc else this.pow(exp - 1, acc * this)

tailrec fun Long.pow(exp: Int, acc: Long = 1): Long =
    if (exp == 0) acc else this.pow(exp - 1, acc * this)

private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

/** 毫秒时间戳 → `yyyy-MM-dd HH:mm:ss`(DateTimeFormatter 不可变,多线程并发安全) */
fun Long.formatTime(): String = timeFormat.format(Instant.ofEpochMilli(this))
