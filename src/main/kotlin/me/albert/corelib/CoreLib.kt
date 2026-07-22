package me.albert.corelib


import me.albert.corelib.inventory.GuiManager
import me.albert.corelib.task.EntityScan
import me.albert.corelib.utils.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

lateinit var instance: CoreLib
val server get() = instance.server
val debug = false


class CoreLib : JavaPlugin() {


    override fun onEnable() {
        instance = this
        registerEvents(PluginLifecycle)
        saveDefaultConfig()
        GuiManager(this)
        PlayerNameUtil.load(this)
        IpUtil.load(this)
        EntityScan.init()
        Expire.init()
        registerEvents(ChatHandler)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("corelib.admin")) {
            sender.sendMessage("§c你没有权限执行此指令！")
            return true
        }
        when (args.getOrNull(0)?.lowercase()) {
            "mini" -> {
                if (args.size >= 2) {
                    val input = args.drop(1).joinToString(" ")
                    val msg = MiniMessage.miniMessage().deserialize(input)
                    sender.sendMessage(Component.text(prefix).append(msg))
                    return true
                }
            }

            "nocd" -> {
                if (args.size == 2) {
                    val targetName = args[1]
                    val targetUUID = PlayerNameUtil.getUUID(targetName)
                    if (targetUUID == null) {
                        sender.sendMessage("§c未找到玩家 $targetName")
                        return true
                    }
                    // 切换免 CD 状态
                    val enable = !CDUtil.isNoCd(targetUUID)
                    if (enable) CDUtil.addNoCdUUID(targetUUID) else CDUtil.removeNoCdUUID(targetUUID)
                    val state = if (enable) "§b开启" else "§c关闭"
                    sender.sendMessage("§a[CoreLib] 已$state§a 玩家 §e$targetName §a的免冷却特权！")
                    return true
                }
            }
        }

        // 无参 / 未知命令 / 参数不全:展示帮助菜单
        sendHelp(sender, label)
        return true
    }

    // 自动补全:只补子命令,玩家名交给 Bukkit 默认补全(返回 null)
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String>? {
        if (!sender.hasPermission("corelib.admin")) return emptyList()
        if (args.size == 1) {
            return listOf("nocd", "mini").filter { it.startsWith(args[0], ignoreCase = true) }
        }
        return null
    }

    private fun sendHelp(sender: CommandSender, label: String) {
        sender.sendMessage("§8========= §bCoreLib 管理菜单 §8=========")
        sender.sendMessage("§3/$label nocd <玩家名> §7- 切换指定玩家的免CD状态(开启/关闭)")
        sender.sendMessage("§3/$label mini <内容> §7- 预览minimessage")
    }


}
