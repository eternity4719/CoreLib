package me.albert.corelib.utils

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import kotlin.io.encoding.Base64

object ItemUtil {

    /**
     * 构建或修改包含指定名称和 Lore 的 ItemStack (支持传递现有的 ItemStack)
     * 使用默认参数合四为一，同时利用内联 meta 修改，只做一次 Deep Clone
     */
    fun make(
        item: ItemStack,
        name: String,
        lore: List<String?>
    ): ItemStack = item.apply {
        // executeIfMetaPresent 或直接用 Bukkit 的 editMeta (1.18.2+)
        editMeta { meta ->
            meta.setDisplayName(name.bukkit)
            meta.lore = lore.map { it?.bukkit }
        }
    }

    // 重载：支持直接从 Material 创建
    fun make(
        material: Material,
        name: String,
        lore: List<String>
    ): ItemStack = make(ItemStack(material), name, lore)

    // 重载：支持 Vararg (可变参数) 传入 Lore
    fun make(
        material: Material,
        name: String,
        vararg lore: String
    ): ItemStack = make(ItemStack(material), name, lore.toList())

    fun make(
        item: ItemStack,
        name: String,
        vararg lore: String
    ): ItemStack = make(item, name, lore.toList())

    /**
     * 追加 Lore 列表
     * 优化点：仅获取一次 itemMeta，原地操作集合
     */
    fun addLore(item: ItemStack, lore: List<String>): ItemStack = item.apply {
        editMeta { meta ->
            val currentLore = meta.lore ?: ArrayList()
            currentLore.addAll(lore)
            meta.lore = currentLore.map { it?.bukkit }
        }
    }

    // 重载：追加 Vararg 形式的 Lore
    fun addLore(item: ItemStack, vararg lore: String): ItemStack {
        return addLore(item, lore.toList())
    }

    /* ================= 序列化部分 (现代化高效 NBT 字节码) ================= */


    fun itemToString(itemStack: ItemStack): String {
        // 使用 Base64.Default，避免部分老版本平台由于 NBT 过大产生换行符导致数据库/配置断行
        return Base64.encode(itemStack.serializeAsBytes())
    }

    fun stringToItem(stringBlob: String): ItemStack {
        return ItemStack.deserializeBytes(Base64.decode(stringBlob))
    }

    /* ================= 旧版兼容序列化 (Yaml 字符串) ================= */

    fun itemToStringOld(itemStack: ItemStack): String {
        val config = YamlConfiguration()
        config["item"] = itemStack
        return config.saveToString()
    }

    fun stringToItemOld(stringBlob: String): ItemStack {
        val config = YamlConfiguration()
        config.loadFromString(stringBlob)
        return config.getItemStack("item")!!
    }
}

/* ================= 构造 ================= */

/** 用 Material 直接构造一个带名字和 lore 的 ItemStack */
fun itemOf(material: Material, name: String, lore: List<String?> = emptyList()): ItemStack =
    ItemStack(material).withName(name, lore)

fun itemOf(material: Material, name: String, vararg lore: String): ItemStack =
    ItemStack(material).withName(name, lore.toList())

/* ================= 名字 / Lore ================= */

/** 设置显示名，可选地同时覆盖 lore */
fun ItemStack.withName(name: String, lore: List<String?> = emptyList()): ItemStack = apply {
    editMeta { meta ->
        meta.setDisplayName(name.bukkit)
        if (lore.isNotEmpty()) {
            meta.lore = lore.map { it?.bukkit }
        }
    }
}

/** 覆盖式设置 lore */
fun ItemStack.withLore(lore: List<String?>): ItemStack = apply {
    editMeta { it.lore = lore.map { l -> l?.bukkit } }
}

fun ItemStack.withLore(vararg lore: String): ItemStack = withLore(lore.toList())

/** 在原有 lore 末尾追加 */
fun ItemStack.appendLore(lore: List<String?>): ItemStack = apply {
    editMeta { meta ->
        val current = meta.lore ?: ArrayList()
        current.addAll(lore.map { it?.bukkit })
        meta.lore = current
    }
}

fun ItemStack.appendLore(vararg lore: String): ItemStack = appendLore(lore.toList())

/* ================= 序列化 ================= */

/** 新版 Base64 序列化（推荐） */
fun ItemStack.toBase64(): String = Base64.encode(serializeAsBytes())

fun String.toItemStack(): ItemStack = ItemStack.deserializeBytes(Base64.decode(this))



/** 物品展示名的纯文本:自定义名去格式,无自定义名时回退为小写空格化的材质名 */
val ItemStack.plainName: String
    get() = itemMeta?.displayName()?.toPlainText() ?: type.name.lowercase().replace('_', ' ')
