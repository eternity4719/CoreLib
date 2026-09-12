package me.albert.corelib.utils

import net.minecraft.core.component.DataComponents
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.inventory.ItemStack
import net.minecraft.world.item.ItemStack as NmsItemStack

/**
 * 分类标记的展示名(ItemCollect 定的约定, XCore 抽取机等也认):容器首格放一件改成这个名的物品,
 * 该容器就只收同类物品;标记本身受保护, 漏斗/抽取机永远抽不走
 */
const val SORT_MARKER_NAME = "%分类%"

/** 是否分类标记:直接读自定义名组件比纯文本, 不建 Bukkit meta(一个区块几百个桶, 每个掉落物都要判一遍) */
val NmsItemStack.isSortMarker: Boolean
    get() = get(DataComponents.CUSTOM_NAME)?.string.equals(SORT_MARKER_NAME, ignoreCase = true)

val ItemStack.isSortMarker: Boolean
    get() = CraftItemStack.unwrap(this).isSortMarker
