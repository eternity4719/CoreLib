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

private val colorRegex = Regex("§.")

/**
 * 单遍扫描用的 token 正则:按出现顺序匹配三类片段(顺序敏感,RGB 必须在 legacy 之前):
 *  1. RGB 颜色码 `&x&R&R&G&G&B&B`
 *  2. legacy 颜色/装饰码 `&<code>`
 *  3. MiniMessage 标签 `</?名字(:参数)?>`
 *
 * 第 3 类只认「合法标签名 = 英文/数字/下划线/连字符」的尖括号:标签名后可跟 `:参数`(URL、#hex、
 * 引号、中文等任意内容)。故 `<red>`、`</click>`、`<gradient:#..>`、`<hover:show_text:'中文'>` 都识别,
 * 而 `<&eName&7>`、`<你好>` 这种以 `&`/中文/符号开头的尖括号不当标签,尖括号保留为字面、内部 `&` 码正常转换。
 */
private val tokenRegex = Regex(
    "(&x(?:&[0-9a-fA-F]){6})|&([0-9a-fk-orA-FK-OR])|(</?[a-zA-Z0-9_-]+(?::[^>]*)?>)",
    RegexOption.IGNORE_CASE,
)

fun String.removeColors() = replace(colorRegex, "")

fun Component.toLegacy() = LegacyComponentSerializer.legacySection().serialize(this)

fun Component.toPlainText() = PlainTextComponentSerializer.plainText().serialize(this)

private val colorCodes = setOf(
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    'a', 'b', 'c', 'd', 'e', 'f'
)

/**
 * 将模板中的 & 颜色码转换为对应的 MiniMessage 标签,可与 MiniMessage 标签(<click>、<player> 等)混排,
 * 最终交给 mm.deserialize 生成 Component。此时不能像 [String.bukkit] 那样直接 replace("&","§"),
 * 因为 Component 里残留的 § 不会被客户端渲染。
 *
 * 单遍按 token 处理,兼顾两件事:
 *  - **模拟 legacy**:颜色码与 &r 会重置(闭合)此前的所有装饰,避免 &a&lhh&b00 中的 00 被 <bold> 意外延续。
 *  - **不与成对 MM 标签交叉**:装饰若在 <click>…</click> 内打开、却在其外闭合,会形成
 *    <click>…<bold>…</click></bold> 交叉嵌套,MiniMessage 无法配对而把 </bold> 当字面文本输出。
 *    故在每个 MM 标签边界处先闭合当前装饰、放标签、再重开,保证装饰始终在标签内正确嵌套;
 *    颜色则靠 MiniMessage 的样式继承自然跨越标签,无需重开。
 */
fun String.ampToMini(): String {
    val openDecorations = mutableListOf<String>()
    fun closeDecos() = openDecorations.reversed().joinToString("") { "</$it>" }
    fun reopenDecos() = openDecorations.joinToString("") { "<$it>" }

    return tokenRegex.replace(this.rBukkit) { match ->
        val rgb = match.groupValues[1]
        val legacyCode = match.groupValues[2]
        val mmTag = match.groupValues[3]
        when {
            // RGB 颜色:重置装饰(legacy 语义)后上色
            rgb.isNotEmpty() -> {
                val hex = rgb.replace("&", "").substring(1) // 去掉全部 & 与前导 x,留 6 位十六进制
                val close = closeDecos()
                openDecorations.clear()
                "$close<color:#$hex>"
            }
            // legacy 颜色/装饰码
            legacyCode.isNotEmpty() -> {
                val code = legacyCode[0].lowercaseChar()
                val tag = legacy[code] ?: return@replace match.value
                if (code in colorCodes || code == 'r') {
                    val close = closeDecos()
                    openDecorations.clear()
                    "$close<$tag>"
                } else {
                    openDecorations.add(tag)
                    "<$tag>"
                }
            }
            // MiniMessage 标签边界:装饰不得跨界,先闭合再重开,保证正确嵌套
            else -> "${closeDecos()}$mmTag${reopenDecos()}"
        }
    }
}
