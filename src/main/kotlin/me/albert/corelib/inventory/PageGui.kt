package me.albert.corelib.inventory

import me.albert.corelib.utils.ItemUtil
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.inventory.ItemStack

/** 分页 GUI 默认每页展示数量(54 格界面，留底排放翻页) */
const val PAGE_SIZE = 45

/**
 * 打开一个通用分页 GUI：54 格，前 [pageSize] 格放内容，左下/右下放翻页箭头。
 *
 * [onOpen] / [onClose] 会把**当前页码**(从 0 开始)一并给出，调用方不用去解析标题——
 * 典型用途是记住玩家上次停留的页码，下次打开直接回到那一页。翻页同样是重开界面：
 * 点「下一页」会先对旧页触发 [onClose]，再对新页触发 [onOpen]，想记"最后停在哪一页"
 * 取最后一次的值即可。
 *
 * @param titlePrefix 标题前缀，后面自动补 " §8第 N/M 页"
 * @param items       全部数据项(翻页为纯内存切片)
 * @param page        初始页(从 0 开始)
 * @param render      把单个数据项渲染成展示物品
 * @param onClick     点击某项时的回调
 * @param onOpen      界面打开后回调(当前页码, 事件)
 * @param onClose     界面关闭后回调(当前页码, 事件)
 */
fun <T> Player.openPagedGui(
    titlePrefix: String,
    items: List<T>,
    page: Int = 0,
    pageSize: Int = PAGE_SIZE,
    render: (T) -> ItemStack,
    onClick: (T, InventoryClickEvent) -> Unit,
    onOpen: (page: Int, event: InventoryOpenEvent) -> Unit = { _, _ -> },
    onClose: (page: Int, event: InventoryCloseEvent) -> Unit = { _, _ -> },
) {
    val totalPages = maxOf(1, (items.size + pageSize - 1) / pageSize)
    val current = page.coerceIn(0, totalPages - 1)
    val from = current * pageSize
    val pageItems = items.subList(from, minOf(from + pageSize, items.size))
    val title = "$titlePrefix §8第${current + 1}/$totalPages 页"
    // 取别名:GuiHolder 里的 onOpen/onClose 是同名 DSL 方法，直接写会和参数撞上
    val openCallback = onOpen

    val inv = createGui(title, 54) {
        for ((i, data) in pageItems.withIndex()) {
            item(i, render(data)) { e -> onClick(data, e) }
        }
        if (current > 0) item(45, ItemUtil.make(Material.ARROW, "&e« 上一页")) {
            openPagedGui(titlePrefix, items, current - 1, pageSize, render, onClick, openCallback, onClose)
        }
        if (current + 1 < totalPages) item(53, ItemUtil.make(Material.ARROW, "&e下一页 »")) {
            openPagedGui(titlePrefix, items, current + 1, pageSize, render, onClick, openCallback, onClose)
        }
        onOpen { e -> openCallback(current, e) }
        onClose { e -> onClose(current, e) }
    }
    openInventory(inv)
}
