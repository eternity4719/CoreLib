package me.albert.corelib.inventory

import me.albert.corelib.utils.bukkit
import me.albert.corelib.utils.isNull
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

// 1. 定义物品与点击事件的包装类
class GuiItem(
    val itemStack: ItemStack,
    val onClick: ((InventoryClickEvent) -> Unit)? = null
)

// 2. DSL 构造器
class GuiHolder(val title: String, val size: Int) : InventoryHolder {
    val slots = mutableMapOf<Int, GuiItem>()
    var onCloseAction: ((InventoryCloseEvent) -> Unit)? = null

    // 新增：是否允许在空白格乱放/操作东西，默认不运行 (false)
    var allowEmptyClick = false
    var onClick: ((InventoryClickEvent) -> Unit)? = null
    var onItemClick: ((InventoryClickEvent) -> Unit)? = null

    /**
     * 在指定位置放置物品并设置监听器
     * @param slot 槽位编号 (0 ~ size-1)
     * @param item Bukkit 的 ItemStack
     * @param onClick 点击事件回调，如果为 null 则允许玩家移动/拿走该物品
     */
    fun item(slot: Int, item: ItemStack, onClick: ((InventoryClickEvent) -> Unit)? = null) {
        slots[slot] = GuiItem(item, onClick)
    }

    fun onClick(listener: (InventoryClickEvent) -> Unit) {
        onClick = listener
    }

    fun onItemClick(listener: (InventoryClickEvent) -> Unit) {
        onItemClick = listener
    }

    /**
     * 监听背包关闭事件
     */
    fun onClose(action: (InventoryCloseEvent) -> Unit) {
        onCloseAction = action
    }

    // 构建出最终的 Inventory 并绑定数据
    fun build(): Inventory {
        val customInventory = Bukkit.createInventory(this, size, title)
        slots.forEach { (slot, guiItem) ->
            customInventory.setItem(slot, guiItem.itemStack)
        }
        return customInventory
    }

    override fun getInventory(): Inventory {
        return Bukkit.createInventory(null, size, title)
    }
}

// 顶层扩展函数，方便随时随地创建 GUI
fun createGui(title: String, size: Int, block: GuiHolder.() -> Unit): Inventory {
    return GuiHolder(title.bukkit, size).apply(block).build()
}


// 4. GUI 管理与事件监听器
class GuiManager(plugin: JavaPlugin) : Listener {

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? GuiHolder ?: return


        if (event.action == InventoryAction.MOVE_TO_OTHER_INVENTORY && !holder.allowEmptyClick) {
            event.isCancelled = true
        }

        if (event.clickedInventory?.holder !is GuiHolder) {
            return
        }

        if (!holder.allowEmptyClick) {
            event.isCancelled = true
        }


        val guiItem = holder.slots[event.slot]

        if (guiItem != null) {
            event.isCancelled = true
            guiItem.onClick?.invoke(event) // 执行自定义逻辑
        }
        if (!event.currentItem.isNull) {
            holder.onItemClick?.invoke(event)
        }
        holder.onClick?.invoke(event)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder as? GuiHolder ?: return
        holder.onCloseAction?.invoke(event)
    }
}