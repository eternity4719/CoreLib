package me.albert.corelib.utils


import net.milkbowl.vault.economy.Economy
import org.black_ixx.playerpoints.PlayerPoints
import org.black_ixx.playerpoints.PlayerPointsAPI
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.util.*

object EconomyManager {

    // ==========================================
    // 动态获取 API 实例（无缓存，防热重载）
    // ==========================================

    val vaultEco: Economy?
        get() = Bukkit.getServicesManager().getRegistration(Economy::class.java)?.provider

    val pointsAPI: PlayerPointsAPI?
        get() = if (Bukkit.getPluginManager().isPluginEnabled("PlayerPoints")) PlayerPoints.getInstance().api else null

    // ==========================================
    // Vault API (游戏币 - Money) - 全 UUID 化
    // ==========================================

    /** 获取游戏币余额 */
    fun getMoney(uuid: UUID): Double {
        val eco = vaultEco ?: return 0.0
        val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
        return eco.getBalance(offlinePlayer)
    }

    /** 给予游戏币 */
    fun giveMoney(uuid: UUID, amount: Double): Boolean {
        if (amount <= 0) return true
        val eco = vaultEco ?: return false
        val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
        return eco.depositPlayer(offlinePlayer, amount).transactionSuccess()
    }

    /** 扣除游戏币 */
    fun takeMoney(uuid: UUID, amount: Double): Boolean {
        if (amount <= 0) return true
        val eco = vaultEco ?: return false
        val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
        if (eco.getBalance(offlinePlayer) < amount) return false
        return eco.withdrawPlayer(offlinePlayer, amount).transactionSuccess()
    }

    // ==========================================
    // PlayerPoints API (点券 - Points) - 保持 UUID
    // ==========================================

    /** 获取点券余额 */
    fun getPoints(uuid: UUID): Int = pointsAPI?.look(uuid) ?: 0

    /** 给予点券 */
    fun givePoints(uuid: UUID, amount: Int): Boolean {
        if (amount <= 0) return true
        return pointsAPI?.give(uuid, amount) ?: false
    }

    /** 扣除点券 */
    fun takePoints(uuid: UUID, amount: Int): Boolean {
        if (amount <= 0) return true
        val api = pointsAPI ?: return false
        if (api.look(uuid) < amount) return false
        return api.take(uuid, amount)
    }
}

// ==========================================
// Vault (游戏币 - Money) 的 UUID 拓展函数
// ==========================================

/** 获取玩家游戏币余额 */
val UUID.money: Double get() = EconomyManager.getMoney(this)

/** 给予玩家游戏币 */
fun UUID.giveMoney(amount: Double): Boolean = EconomyManager.giveMoney(this, amount)

/** 扣除玩家游戏币 */
fun UUID.takeMoney(amount: Double): Boolean = EconomyManager.takeMoney(this, amount)


// ==========================================
// PlayerPoints (点券 - Points) 的 UUID 拓展函数
// ==========================================

/** 获取玩家点券余额 */
val UUID.points: Int get() = EconomyManager.getPoints(this)

/** 给予玩家点券 */
fun UUID.givePoints(amount: Int): Boolean = EconomyManager.givePoints(this, amount)

/** 扣除玩家点券 */
fun UUID.takePoints(amount: Int): Boolean = EconomyManager.takePoints(this, amount)


// ==========================================
// Vault (游戏币) 拓展函数
// ==========================================


/** 给予玩家游戏币 */
fun OfflinePlayer.giveMoney(amount: Double): Boolean = EconomyManager.giveMoney(uniqueId, amount)

/** 扣除玩家游戏币 */
fun OfflinePlayer.takeMoney(amount: Double): Boolean = EconomyManager.takeMoney(uniqueId, amount)


// ==========================================
// PlayerPoints (点券) 拓展函数
// ==========================================

/** 给予玩家点券 */
fun OfflinePlayer.givePoints(amount: Int): Boolean = EconomyManager.givePoints(uniqueId, amount)

/** 扣除玩家点券 */
fun OfflinePlayer.takePoints(amount: Int): Boolean = EconomyManager.takePoints(uniqueId, amount)

val OfflinePlayer.points: Int get() = uniqueId.points

val OfflinePlayer.money: Double get() = uniqueId.money