package me.albert.corelib.utils

import java.math.RoundingMode
import java.text.DecimalFormat

val priceFormat
    get() = DecimalFormat("#.##").apply {
        roundingMode = RoundingMode.DOWN
    }

/** 把数字格式化为最多两位小数的展示字符串 */
fun Double.format(format: DecimalFormat = priceFormat): String = format.format(this)

val Double.unit: String
    get() {
        return when (DecimalFormat("0.00").format(this).length) {
            13 -> "十亿"
            12 -> "亿"
            11 -> "千万"
            10 -> "百万"
            9 -> "十万"
            8 -> "万"
            7 -> "千"
            6 -> "百"
            else -> {
                ""
            }
        }
    }