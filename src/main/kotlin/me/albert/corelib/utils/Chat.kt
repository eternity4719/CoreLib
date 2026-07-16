package me.albert.corelib.utils

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*
import java.util.concurrent.ConcurrentHashMap

val chatHandlers = ConcurrentHashMap<UUID, (String) -> Unit>()


fun Player.handleChat(callback: (String) -> Unit) {
    chatHandlers[uniqueId] = callback
}

object ChatHandler : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        val uuid = player.uniqueId
        val callback = chatHandlers[uuid] ?: return
        event.isCancelled = true
        callback.invoke(event.message)
        chatHandlers.remove(uuid)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        chatHandlers.remove(event.player.uniqueId)
    }
}