package me.albert.corelib.inventory

import me.albert.corelib.utils.ItemUtil
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** 分页 GUI 默认每页展示数量(54 格界面，留底排放翻页) */
const val PAGE_SIZE = 45

/** 总页数:向上取整,至少 1 页 */
fun pageCount(size: Int, pageSize: Int = PAGE_SIZE): Int = maxOf(1, (size + pageSize - 1) / pageSize)

/**
 * 打开一个通用分页 GUI：54 格，前 [pageSize] 格放内容，左下/右下放翻页箭头。
 *
 * [onOpen] / [onClose] 会把**当前页码**(从 0 开始)一并给出，调用方不用去解析标题——
 * 翻页同样是重开界面：点「下一页」会先对旧页触发 [onClose]，再对新页触发 [onOpen]。
 *
 * 想让玩家下次打开回到上次停留的那一页，传一个 [remember] 键即可(同一玩家按键各记各的)：
 * 关闭时记下页码，下次打开覆盖 [page] 作为初始页。内存态，重启即清。
 *
 * @param titlePrefix 标题前缀，后面自动补 " §8第 N/M 页"
 * @param items       全部数据项(翻页为纯内存切片)
 * @param page        初始页(从 0 开始)；给了 [remember] 且有记录时以记录为准
 * @param remember    页码记忆键(如 "block_shop")，null 不记
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
    remember: String? = null,
    render: (T) -> ItemStack,
    onClick: (T, InventoryClickEvent) -> Unit,
    onOpen: (page: Int, event: InventoryOpenEvent) -> Unit = { _, _ -> },
    onClose: (page: Int, event: InventoryCloseEvent) -> Unit = { _, _ -> },
) {
    // 记忆只在入口查一次:翻页箭头走 openPage 直接给页码,否则会被记忆里的旧页顶掉
    val memoryKey = remember?.let { PageMemory.Key(uniqueId, it) }
    val initial = memoryKey?.let { PageMemory.pages[it] } ?: page
    openPage(titlePrefix, items, initial, pageSize, memoryKey, render, onClick, onOpen, onClose)
}

/** [openPagedGui] 的实际开页:[page] 即所开页码,翻页递归调它;[memoryKey] 非空则关闭时记页码 */
private fun <T> Player.openPage(
    titlePrefix: String,
    items: List<T>,
    page: Int,
    pageSize: Int,
    memoryKey: PageMemory.Key?,
    render: (T) -> ItemStack,
    onClick: (T, InventoryClickEvent) -> Unit,
    onOpen: (page: Int, event: InventoryOpenEvent) -> Unit,
    onClose: (page: Int, event: InventoryCloseEvent) -> Unit,
) {
    val totalPages = pageCount(items.size, pageSize)
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
            openPage(titlePrefix, items, current - 1, pageSize, memoryKey, render, onClick, openCallback, onClose)
        }
        if (current + 1 < totalPages) item(53, ItemUtil.make(Material.ARROW, "&e下一页 »")) {
            openPage(titlePrefix, items, current + 1, pageSize, memoryKey, render, onClick, openCallback, onClose)
        }
        onOpen { e -> openCallback(current, e) }
        onClose { e ->
            if (memoryKey != null) PageMemory.pages[memoryKey] = current
            onClose(current, e)
        }
    }
    openInventory(inv)
}

/**
 * 分页 GUI 的页码记忆表：(玩家, 键) → 上次关闭时停留的页码。
 * 用并发表：Folia 下开关界面跑在各自玩家所在区域的线程上，两人同时关界面就是两个线程同时写。
 */
private object PageMemory {
    data class Key(val player: UUID, val gui: String)

    val pages = ConcurrentHashMap<Key, Int>()
}
