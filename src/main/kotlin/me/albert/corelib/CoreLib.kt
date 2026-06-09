package me.albert.corelib


import me.albert.corelib.inventory.GuiManager
import me.albert.corelib.utils.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

lateinit var instance: CoreLib
val server get() = instance.server


class CoreLib : JavaPlugin() {


    override fun onEnable() {
        instance = this
        saveDefaultConfig()
        GuiManager(this)
        PlayerNameUtil.load(this)
        IpUtil.load(this)
        registerEvents(ChatHandler)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("corelib.admin")) {
            sender.sendMessage("§c你没有权限执行此指令！")
            return true
        }
        if (args.size >= 2 && args[0].contentEquals("mini", true)) {
            val input = args.drop(1).joinToString(" ")
            val msg = MiniMessage.miniMessage().deserialize(input)
            sender.sendMessage(Component.text(prefix).append { msg })
            return true
        }
        when (args.size) {
            2 -> {
                val subCmd = args[0].lowercase()
                when (subCmd) {
                    "nocd" -> {
                        val targetName = args[1]
                        val targetUUID = PlayerNameUtil.getUUID(targetName)

                        if (targetUUID == null) {
                            sender.sendMessage("§c未找到玩家 $targetName")
                            return true
                        }

                        // 切换免 CD 状态
                        if (CDUtil.isNoCd(targetUUID)) {
                            CDUtil.removeNoCdUUID(targetUUID)
                            sender.sendMessage("§a[CoreLib] 已§c关闭§a 玩家 §e$targetName §a的免冷却特权。")
                            return true
                        }
                        CDUtil.addNoCdUUID(targetUUID)
                        sender.sendMessage("§a[CoreLib] 已§b开启§a 玩家 §e$targetName §a的免冷却特权！")
                        return true
                    }
                }
            }
        }
        sendHelp(sender, label)
        return true
    }

    // 自动补全
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (!sender.hasPermission("corelib.admin")) return emptyList()

        return when (args.size) {
            1 -> listOf("nocd", "mini").filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> {
                if (args[0].equals("nocd", ignoreCase = true)) {
                    val players = mutableListOf<String>()
                    Bukkit.getOnlinePlayers().forEach { players.add(it.name) }
                    players.filter { it.startsWith(args[1], ignoreCase = true) }
                } else emptyList()
            }

            else -> emptyList()
        }
    }

    private fun sendHelp(sender: CommandSender, label: String) {
        sender.sendMessage("§8========= §bCoreLib 管理菜单 §8=========")
        sender.sendMessage("§3/$label nocd <玩家名> §7- 切换指定玩家的免CD状态(开启/关闭)")
        sender.sendMessage("§3/$label mini <内容> §7- 预览minimessage")
    }


}
