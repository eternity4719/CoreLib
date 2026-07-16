package me.albert.corelib.objects

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.logging.Level

class CustomConfig(filename: String, private val plugin: Plugin) {

    lateinit var config: FileConfiguration
        private set

    val configFile: File

    init {
        val file = File(plugin.dataFolder, filename)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            if (plugin.getResource(filename) != null) {
                plugin.saveResource(filename, false)
            } else {
                runCatching { file.createNewFile() }
                    .onFailure { it.printStackTrace() }
            }
        }
        this.configFile = file
        reload()
    }

    fun save() {
        runCatching {
            config.save(configFile)
        }.onFailure { ex ->
            plugin.logger.log(Level.SEVERE, "Could not save config to $configFile", ex)
        }
    }

    fun reload() {
        config = YamlConfiguration.loadConfiguration(configFile)
        val defConfigStream = plugin.getResource(configFile.name) ?: return

        val reader = InputStreamReader(defConfigStream, StandardCharsets.UTF_8)
        config.setDefaults(YamlConfiguration.loadConfiguration(reader))
    }
}