package me.albert.corelib.utils

import java.math.RoundingMode
import java.text.DecimalFormat

// DecimalFormat 非线程安全，故每次访问都新建实例，不能缓存为常量
val priceFormat
    get() = DecimalFormat("#.##").apply {
        roundingMode = RoundingMode.DOWN
    }

/** 把数字格式化为最多两位小数的展示字符串 */
fun Double.format(format: DecimalFormat = priceFormat): String = format.format(this)

/** 数量级中文单位；按绝对值判断(负数同档)，超出「十亿」档与不足「百」档均返回空串 */
val Double.unit: String
    get() {
        val abs = kotlin.math.abs(this)
        return when {
            abs >= 1e10 -> ""
            abs >= 1e9 -> "十亿"
            abs >= 1e8 -> "亿"
            abs >= 1e7 -> "千万"
            abs >= 1e6 -> "百万"
            abs >= 1e5 -> "十万"
            abs >= 1e4 -> "万"
            abs >= 1e3 -> "千"
            abs >= 1e2 -> "百"
            else -> ""
        }
    }

/** 价格展示串:数值 + 数量级单位括注([unitColor] 上色) + 货币名 */
fun priceText(price: Double, currency: String, unitColor: String = "§7"): String {
    val unit = price.unit
    if (unit.isEmpty()) return "${price.format()} $currency"
    return "${price.format()}$unitColor($unit) $currency"
}
