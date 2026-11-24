package pl.syntaxdevteam.teamx.chat

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Marker
import org.apache.logging.log4j.core.Filter.Result
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.filter.AbstractFilter
import org.apache.logging.log4j.message.Message

/** Paper logs player commands before Bukkit or Brigadier can inspect them. */
internal class ClanCommandLogFilter : AbstractFilter(), AutoCloseable {
    private val command = Regex("""^/?(?:teamx:)?team\s+chat\s+\S""")
    private val template = "{} issued server command: {}"
    private val configuration: Configuration? = (LogManager.getContext(false) as? LoggerContext)?.configuration

    fun install(): Boolean {
        val config = configuration ?: return false
        start()
        config.addFilter(this)
        return true
    }

    override fun close() {
        configuration?.removeFilter(this)
        stop()
    }

    override fun filter(logger: Logger?, level: Level?, marker: Marker?, message: String?,
                        player: Any?, input: Any?): Result = decision(message, input)

    @Suppress("DEPRECATION")
    override fun filter(logger: Logger?, level: Level?, marker: Marker?, message: Message?, error: Throwable?): Result =
        decision(message?.format, message?.parameters?.getOrNull(1))

    private fun decision(format: String?, input: Any?): Result =
        if (format == template && input is String && command.containsMatchIn(input.trimStart())) Result.DENY
        else Result.NEUTRAL
}
