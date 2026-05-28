package me.albert.corelib.inventory

import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

// 1. 定义物品与点击事件的包装类
class GuiItem(
    val itemStack: ItemStack,
    val onClick: ((InventoryClickEvent) -> Unit)? = null
)

// 2. DSL 构造器
class GuiBuilder(val title: String, val size: Int) {
    private val slots = mutableMapOf<Int, GuiItem>()
    private var onCloseAction: ((InventoryCloseEvent) -> Unit)? = null

    // 新增：是否允许在空白格乱放/操作东西，默认不运行 (false)
    private var allowEmptyClick = false

    /**
     * 在指定位置放置物品并设置监听器
     * @param slot 槽位编号 (0 ~ size-1)
     * @param item Bukkit 的 ItemStack
     * @param onClick 点击事件回调，如果为 null 则允许玩家移动/拿走该物品
     */
    fun item(slot: Int, item: ItemStack, onClick: ((InventoryClickEvent) -> Unit)? = null) {
        slots[slot] = GuiItem(item, onClick)
    }

    /**
     * 监听背包关闭事件
     */
    fun onClose(action: (InventoryCloseEvent) -> Unit) {
        onCloseAction = action
    }

    /**
     * 新增：设置是否允许玩家在空白格交互（放入物品、点击空格等）
     * @param allow true 为允许，false 为拦截（默认）
     */
    fun allowEmptySlot(allow: Boolean) {
        this.allowEmptyClick = allow
    }

    // 构建出最终的 Inventory 并绑定数据
    fun build(): Inventory {
        // 将 allowEmptyClick 传入 Holder
        val holder = GuiHolder(slots, allowEmptyClick, onCloseAction)

        val customInventory = Bukkit.createInventory(holder, size, title)
        slots.forEach { (slot, guiItem) ->
            customInventory.setItem(slot, guiItem.itemStack)
        }
        return customInventory
    }
}

// 3. 自定义 Holder 用于在事件中识别和获取当前 GUI 的配置
class GuiHolder(
    val slots: Map<Int, GuiItem>,
    val allowEmptyClick: Boolean, // 新增属性
    val onCloseAction: ((InventoryCloseEvent) -> Unit)?
) : org.bukkit.inventory.InventoryHolder {
    override fun getInventory(): Inventory = Bukkit.createInventory(this, 9)
}

// 顶层扩展函数，方便随时随地创建 GUI
fun createGui(title: String, size: Int, block: GuiBuilder.() -> Unit): Inventory {
    return GuiBuilder(title, size).apply(block).build()
}


// 4. GUI 管理与事件监听器
class GuiManager(plugin: JavaPlugin) : Listener {

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? GuiHolder ?: return

        // 如果点击的是玩家自己的背包底栏，且不是处于该自定义GUI的交互中，可以不作处理
        if (event.rawSlot >= event.inventory.size) return

        val guiItem = holder.slots[event.slot]

        if (guiItem != null) {
            if (guiItem.onClick != null) {
                event.isCancelled = true // 阻止默认的移动行为
                guiItem.onClick(event) // 执行自定义逻辑
            } else {
                // 监听函数为 null，允许移动该物品 (不取消事件)
            }
        } else {
            // 修改点：根据配置决定是否拦截空白格的乱放/点击行为
            if (!holder.allowEmptyClick) {
                event.isCancelled = true
            }
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder as? GuiHolder ?: return
        holder.onCloseAction?.invoke(event)
    }
}