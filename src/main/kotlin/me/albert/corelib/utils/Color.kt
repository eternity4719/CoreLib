package me.albert.corelib.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

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
private val rgbRegex = Regex("&x(&[0-9a-fA-F]){6}", RegexOption.IGNORE_CASE)
private val colorRegex = Regex("§.")

fun String.removeColors() = replace(colorRegex, "")

fun Component.toLegacy() = LegacyComponentSerializer.legacySection().serialize(this)

fun Component.toPlainText() = PlainTextComponentSerializer.plainText().serialize(this)

private val colorCodes = setOf(
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    'a', 'b', 'c', 'd', 'e', 'f'
)

/**
 * 将模板中的 &x 颜色码转换为对应的 MiniMessage 标签。
 *
 * 用于 legacy 颜色码与 MiniMessage 占位符（如 <player>、<message>）混排、
 * 且最终需交给 mm.deserialize 生成 Component 的场景。此时不能像 [String.bukkit]
 * 那样直接 replace("&","§")，因为 Component 里残留的 § 不会被客户端渲染。
 *
 * 会在颜色码与 &r 处补齐装饰闭合标签，模拟 legacy 行为，避免 &a&lhh&b00 中的
 * 00 被 <bold> 意外延续加粗。
 */
fun String.ampToMini(): String {
    // 先处理 RGB 颜色码 &x&R&R&G&G&B&B
    var result = rgbRegex.replace(this.rBukkit) { match ->
        val hex = match.value.replace("&", "").substring(1)
        "<color:#$hex>"
    }
    // 再处理普通颜色码，模拟 legacy 行为：颜色码和 &r 重置所有装饰
    val openDecorations = mutableListOf<String>()
    result = legacyRegex.replace(result) { match ->
        val code = match.groupValues[1][0].lowercaseChar()
        val tag = legacy[code] ?: return@replace match.value
        if (code in colorCodes || code == 'r') {
            val closeTags = openDecorations.reversed().joinToString("") { "</$it>" }
            openDecorations.clear()
            "$closeTags<$tag>"
        } else {
            openDecorations.add(tag)
            "<$tag>"
        }
    }
    return result
}
