package me.albert.corelib.utils

import com.destroystokyo.paper.profile.PlayerProfile
import com.destroystokyo.paper.profile.ProfileProperty
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ResolvableProfile
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import java.util.*

fun getSkullProfile(texture: String): PlayerProfile {
    val profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(texture.encodeToByteArray()))
    profile.setProperty(ProfileProperty("textures", texture))
    return profile
}


@Suppress("UnstableApiUsage")
fun getHead(texture: String, baseType: Material = Material.PLAYER_HEAD): ItemStack {
    val item = ItemStack(baseType)
    if (baseType != Material.PLAYER_HEAD) {
        item.editMeta { it.itemModel = Material.PLAYER_HEAD.key }
    }
    item.setData(
        DataComponentTypes.PROFILE,
        ResolvableProfile.resolvableProfile(getSkullProfile(texture))
    )
    return item
}

/**
 * 在 [location] 生成一个显示指定皮肤头(材质 [texture])的 [ItemDisplay]。
 * 皮肤头无法用 BlockDisplay 显示(只能显示方块),故用 ItemDisplay 承载头颅物品。
 *
 * @param init 生成后的初始化回调(设亮度、插值、变换矩阵等)
 */
fun spawnSkullDisplay(location: Location, texture: String, init: (ItemDisplay) -> Unit = {}): ItemDisplay {
    return location.spawn<ItemDisplay>().apply {
        setItemStack(getHead(texture))
        init(this)
    }
}