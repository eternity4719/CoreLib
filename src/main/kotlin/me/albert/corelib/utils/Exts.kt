package me.albert.corelib.utils

import com.github.shynixn.mccoroutine.folia.*
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import me.albert.corelib.instance
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.metadata.Metadatable
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/* =========================================================================
 * 1. 颜色与字符处理优化
 * ========================================================================= */

val String.bukkit: String
    get() = if (contains('&')) this.replace("&", "§") else this

val String.rBukkit: String
    get() = if (contains('§')) this.replace("§", "&") else this

val gson = Gson()

val air = ItemStack(Material.AIR)
val stone = ItemStack(Material.STONE)

var prefix = "§7[§b系统§7] §a"

val String.prefixed: String get() = prefix + this.bukkit

fun CommandSender.sendMsg(msg: String) {
    this.sendMessage(msg.prefixed)
}

fun Plugin.launch(
    entity: Entity, start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
) {
    launch(entityDispatcher(entity), start, block)
}

fun Plugin.launch(
    location: Location, start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
) {
    launch(regionDispatcher(location), start, block)
}

fun Plugin.launchAsync(
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
) {
    launch(asyncDispatcher, start, block)
}

fun Plugin.launchGlobal(
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
) {
    launch(globalRegionDispatcher, start, block)
}

fun ItemStack?.isSame(other: ItemStack?): Boolean {
    return this?.isSimilar(other) == true && this.amount == other?.amount
}


val ItemStack?.isNull: Boolean
    get() {
        return (this == null || this.isEmpty)
    }

/* =========================================================================
 * 2. 物品名校验与 RPG Lore 属性解析 (性能大幅优化版)
 * ========================================================================= */

fun ItemStack?.checkName(name: String): Boolean =
    checkItemDisplayName(this, name.bukkit.trim())

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

/**
 * 提取 Lore 中的 RPG 属性值
 * 优化点：只进行 1 次 ItemMeta 深拷贝，使用原生 substring 避免多余的临时字符串对象生成
 */
fun ItemStack?.getLoreAttributeValue(str: String): Double {
    if (this == null || isEmpty) return -1.0
    val meta = itemMeta ?: return -1.0
    val lore = meta.lore ?: return -1.0

    val target = str.bukkit
    if (!target.contains("%s%")) return -1.0
    val (first, second) = target.split("%s%", limit = 2)

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
fun ItemStack?.setLoreAttribute(str: String, value: Double) {
    if (this == null || isEmpty) return
    val meta = itemMeta ?: return
    val lore = meta.lore ?: return

    if (!str.contains("%s%")) return
    val (first, second) = str.split("%s%", limit = 2)
    val formattedValue = String.format(Locale.ROOT, "%.2f", value) // 显式指定 ROOT 规避德语等 VPS 的逗号 Bug

    var changed = false
    val newLore = lore.map { line ->
        if (line.startsWith(first)) {
            changed = true
            "$first$formattedValue$second"
        } else line
    }

    if (changed) {
        meta.lore = newLore
        this.itemMeta = meta
    }
}


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

fun Location.dropItem(item: ItemStack) {
    world?.dropItem(this, item)
}

inline fun <reified T : Entity> Location.spawn(): T {
    return this.world.spawn(this, T::class.java)
}

fun Player.playSound(sound: Sound, volume: Float, pitch: Float) {
    this.playSound(this, sound, volume, pitch)
}


/* =========================================================================
 * 5. 逻辑流与数学扩展 (契约 Contracts 智能类型转换版)
 * ========================================================================= */

/* =========================================================================
 * 5. 逻辑流与数学扩展 (契约 Contracts 智能类型转换 修复版)
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
    if (check) block(this!!)
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