package me.albert.corelib.inventory

import me.albert.corelib.utils.bukkit
import me.albert.corelib.utils.isNull
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.*
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
class GuiHolder(title: String, size: Int) : InventoryHolder {
    val slots = mutableMapOf<Int, GuiItem>()
    var onClose: ((InventoryCloseEvent) -> Unit)? = null
    val customInventory = Bukkit.createInventory(this, size, title)


    var allowInteract = false
    var onClick: ((InventoryClickEvent) -> Unit)? = null
    var onItemClick: ((InventoryClickEvent) -> Unit)? = null
    var onOpen: ((InventoryOpenEvent) -> Unit)? = null

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

    fun onOpen(listener: (InventoryOpenEvent) -> Unit) {
        onOpen = listener
    }

    /**
     * 监听背包关闭事件
     */
    fun onClose(action: (InventoryCloseEvent) -> Unit) {
        onClose = action
    }

    // 构建出最终的 Inventory 并绑定数据
    fun build(): Inventory {
        slots.forEach { (slot, guiItem) ->
            customInventory.setItem(slot, guiItem.itemStack)
        }
        return customInventory
    }

    override fun getInventory(): Inventory {
        return customInventory
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

        when (event.action) {
            InventoryAction.COLLECT_TO_CURSOR,
            InventoryAction.MOVE_TO_OTHER_INVENTORY -> {
                if (!holder.allowInteract) {
                    event.isCancelled = true
                }
            }

            else -> {}
        }

        if (event.clickedInventory?.holder !is GuiHolder) {
            return
        }

        if (!holder.allowInteract) {
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val holder = event.inventory.holder as? GuiHolder ?: return
        if (holder.allowInteract) return
        // 只要拖拽涉及到 GUI 的格子就取消
        if (event.rawSlots.any { it < event.view.topInventory.size }) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder as? GuiHolder ?: return
        holder.onClose?.invoke(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onOpen(event: InventoryOpenEvent) {
        val holder = event.inventory.holder as? GuiHolder ?: return
        holder.onOpen?.invoke(event)
    }
}