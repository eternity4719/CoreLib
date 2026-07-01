package me.albert.corelib.utils

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/** 统计存储区内与 [item] 相似的物品总数 */
fun Inventory.amountOf(item: ItemStack): Int {
    val template = item.asOne()
    return storageContents.sumOf { if (it != null && it.isSimilar(template)) it.amount else 0 }
}

/** 存储区内所有物品总数 */
val Inventory.totalAmount: Int
    get() = storageContents.sumOf { if (it != null && !it.isEmpty) it.amount else 0 }

/** 从存储区移除 [amount] 个与 [item] 相似的物品 */
fun Inventory.removeItems(item: ItemStack, amount: Int) {
    val template = item.asOne()
    var remaining = amount
    for (stack in storageContents) {
        if (remaining <= 0) break
        if (stack != null && stack.isSimilar(template)) {
            val take = minOf(stack.amount, remaining)
            stack.amount -= take
            remaining -= take
        }
    }
}

/** 向存储区放入 [amount] 个与 [item] 相似的物品 */
fun Inventory.addItems(item: ItemStack, amount: Int) {
    val template = item.asOne()
    repeat(amount) { addItem(template.clone()) }
}

/** 是否有足够空位放下 [amount] 个 [item] */
fun Inventory.hasSpace(item: ItemStack, amount: Int): Boolean {
    val slots = (amount + item.maxStackSize - 1) / item.maxStackSize
    return emptySlots >= slots.coerceAtLeast(1)
}

/** 存储区内的空位数量 */
val Inventory.emptySlots: Int
    get() = storageContents.count { it.isNull }

val Inventory.hasEmptySlots get() = firstEmpty() != -1

/** 随机取出存储区内的一个物品(数量为 1),并从原堆扣除 1 个 */
fun Inventory.takeRandomStack(): ItemStack {
    val items = storageContents.filterNotNull().filter { !it.isEmpty }
    val stack = items.random()
    val result = stack.asOne()
    stack.amount -= 1
    return result
}
