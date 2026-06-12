package me.albert.corelib.inventory

import me.albert.corelib.utils.ItemUtil
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

/** 分页 GUI 默认每页展示数量(54 格界面，留底排放翻页) */
private const val PAGE_SIZE = 45

/**
 * 打开一个通用分页 GUI：54 格，前 [pageSize] 格放内容，左下/右下放翻页箭头。
 *
 * @param titlePrefix 标题前缀，后面自动补 " §8第 N/M 页"
 * @param items       全部数据项(翻页为纯内存切片)
 * @param page        初始页(从 0 开始)
 * @param render      把单个数据项渲染成展示物品
 * @param onClick     点击某项时的回调
 */
fun <T> Player.openPagedGui(
    titlePrefix: String,
    items: List<T>,
    page: Int = 0,
    pageSize: Int = PAGE_SIZE,
    render: (T) -> ItemStack,
    onClick: (T, InventoryClickEvent) -> Unit,
) {
    val totalPages = maxOf(1, (items.size + pageSize - 1) / pageSize)
    val current = page.coerceIn(0, totalPages - 1)
    val from = current * pageSize
    val pageItems = items.subList(from, minOf(from + pageSize, items.size))
    val title = "$titlePrefix §8第${current + 1}/$totalPages 页"

    val inv = createGui(title, 54) {
        for ((i, data) in pageItems.withIndex()) {
            item(i, render(data)) { e -> onClick(data, e) }
        }
        if (current > 0) item(45, ItemUtil.make(Material.ARROW, "&e« 上一页")) {
            openPagedGui(titlePrefix, items, current - 1, pageSize, render, onClick)
        }
        if (current + 1 < totalPages) item(53, ItemUtil.make(Material.ARROW, "&e下一页 »")) {
            openPagedGui(titlePrefix, items, current + 1, pageSize, render, onClick)
        }
    }
    openInventory(inv)
}
