package pl.syntaxdevteam.teamx.messages

import net.kyori.adventure.text.Component
import pl.syntaxdevteam.message.MessageHandler

private val placeholderPattern = Regex("""\{([a-z_]+)\}|\uE000([a-z_]+)\uE001""").toPattern()

internal fun MessageHandler.teamMessage(
    category: String,
    key: String,
    values: Map<String, String> = emptyMap()
): Component {
    if (values.isEmpty()) return stringMessageToComponent(category, key)
    val markers = values.keys.associateWith { "\uE000${it}\uE001" }
    return stringMessageToComponent(category, key, markers).replaceText { replacement ->
        replacement.match(placeholderPattern).replacement { match, original ->
            val name = match.group(1) ?: match.group(2)
            values[name]?.let(original::content) ?: original
        }
    }
}
