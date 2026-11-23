package pl.syntaxdevteam.teamx.hooks

import org.bukkit.Bukkit
import pl.syntaxdevteam.cleanerx.api.CleanerXAPI

object CleanerXHook {
    fun containsBannedWord(name: String): Boolean {
        if (!Bukkit.getPluginManager().isPluginEnabled("CleanerX")) return false
        return Bukkit.getServicesManager().load(CleanerXAPI::class.java)?.containsBannedWord(name) == true
    }
}
