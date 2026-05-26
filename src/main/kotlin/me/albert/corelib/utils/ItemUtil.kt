package me.albert.corelib.utils

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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
            meta.setDisplayName(name)
            meta.lore = lore
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
            meta.lore = currentLore
        }
    }

    // 重载：追加 Vararg 形式的 Lore
    fun addLore(item: ItemStack, vararg lore: String): ItemStack {
        return addLore(item, lore.toList())
    }

    /* ================= 序列化部分 (现代化高效 NBT 字节码) ================= */

    @OptIn(ExperimentalEncodingApi::class)
    fun itemToString(itemStack: ItemStack): String {
        // 使用 Base64.Default，避免部分老版本平台由于 NBT 过大产生换行符导致数据库/配置断行
        return Base64.Default.encode(itemStack.serializeAsBytes())
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun stringToItem(stringBlob: String): ItemStack {
        return ItemStack.deserializeBytes(Base64.Default.decode(stringBlob))
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

