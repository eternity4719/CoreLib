package me.albert.corelib.objects

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

open class EntityScanEvent(isAsync: Boolean) : Event(isAsync) {

    override fun getHandlers(): HandlerList {
        return HANDLERS
    }

    companion object {
        private val HANDLERS = HandlerList()

        // Bukkit 的事件反射系统必须要找到这个静态方法
        @JvmStatic
        fun getHandlerList(): HandlerList {
            return HANDLERS
        }
    }
}