package me.albert.corelib.utils

import net.kyori.adventure.text.minimessage.MiniMessage

val mm = MiniMessage.miniMessage()

// & 颜色码 → MiniMessage 标签映射
private val legacy = mapOf(
    '0' to "black", '1' to "dark_blue", '2' to "dark_green", '3' to "dark_aqua",
    '4' to "dark_red", '5' to "dark_purple", '6' to "gold", '7' to "gray",
    '8' to "dark_gray", '9' to "blue", 'a' to "green", 'b' to "aqua",
    'c' to "red", 'd' to "light_purple", 'e' to "yellow", 'f' to "white",
    'k' to "obfuscated", 'l' to "bold", 'm' to "strikethrough", 'n' to "underlined",
    'o' to "italic", 'r' to "reset"
)

private val legacyRegex = Regex("&([0-9a-fk-orA-FK-OR])")
private val colorRegex = Regex("§.")

fun String.removeColors() = replace(colorRegex, "")

/** 将模板中的 &x 颜色码转换为对应的 MiniMessage 标签 */
fun String.ampToMini(): String =
    legacyRegex.replace(this) { "<${legacy[it.groupValues[1][0].lowercaseChar()]}>" }
