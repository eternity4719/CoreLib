package me.albert.corelib.utils

import net.kyori.adventure.text.Component

fun buildMessage(block: MessageBuilder.() -> Unit): Component =
    MessageBuilder().apply(block).build()

class MessageBuilder {
    private var component = Component.empty()

    operator fun String.unaryPlus() {
        component = component.append(Component.text(this))
    }

    fun append(c: Component) {
        component = component.append(c)
    }

    fun build() = component
}
