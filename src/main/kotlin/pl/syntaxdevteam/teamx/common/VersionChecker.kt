package pl.syntaxdevteam.teamx.common

import org.bukkit.Bukkit
import pl.syntaxdevteam.teamx.TeamX

class VersionChecker(private val plugin: TeamX) {

    companion object {
        private val SUPPORTED_VERSIONS = setOf(
            "1.20.6",
            "1.21",
            "1.21.1",
            "1.21.2",
            "1.21.3",
            "1.21.4",
            "1.21.5",
            "1.21.6",
            "1.21.7",
            "1.21.8",
            "1.21.9",
            "1.21.10",
            "1.21.11",
            "26.1"
        )
    }

    private fun getRawVersion(): String =
        Bukkit.getServer().bukkitVersion

    fun getServerVersion(): String =
        getRawVersion().substringBefore("-")

    fun isSupported(): Boolean =
        getServerVersion().removeSuffix(".0") in SUPPORTED_VERSIONS

    fun checkAndLog(): Boolean {
        val version = getServerVersion()
        return if (isSupported()) {
            plugin.logger.success("The server is running on supported version $version.")
            true
        } else {
            plugin.logger.warning("Warning! Unsupported version $version – use with caution!")
            false
        }
    }
}
