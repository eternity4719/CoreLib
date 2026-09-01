package me.albert.corelib.utils

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.plugin.Plugin
import java.util.Properties

/**
 * 按各插件 mysql.yml 的 storage 段(host/port/database/username/password/useSSL)构建 MySQL 连接池。
 * 表前缀读取、建表(createMissingTablesAndColumns)由插件各自处理;
 * 调用方拿返回值自行 `Database.connect(...)` 并保留引用以便 onDisable 关池。
 */
fun mysqlDataSource(plugin: Plugin, storage: ConfigurationSection, poolSize: Int = 1): HikariDataSource {
    val config = HikariConfig().apply {
        poolName = plugin.name
        driverClassName = runCatching { Class.forName("com.mysql.cj.jdbc.Driver"); "com.mysql.cj.jdbc.Driver" }
            .getOrElse { plugin.logger.info("Driver class 'com.mysql.cj.jdbc.Driver' not found! Falling back to legacy."); "com.mysql.jdbc.Driver" }
        val host = storage.getString("host")
        val port = storage.getString("port")
        val database = storage.getString("database")
        jdbcUrl = "jdbc:mysql://$host:$port/$database?useInformationSchema=false&useServerPrepStmts=false"
        username = storage.getString("username")
        password = storage.getString("password")
        dataSourceProperties = Properties().apply {
            setProperty("useSSL", storage.getString("useSSL") ?: "false")
            setProperty("date_string_format", "yyyy-MM-dd HH:mm:ss")
        }
        connectionTestQuery = "SELECT 1"
        maximumPoolSize = poolSize
    }
    return HikariDataSource(config)
}
