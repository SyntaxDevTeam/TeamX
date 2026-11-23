package pl.syntaxdevteam.teamx.hooks

import org.bukkit.Bukkit
import pl.syntaxdevteam.plotsx.api.PlotsXApi
import java.util.UUID

object PlotsXHook {
    fun visitablePlots(player: UUID): List<String>? {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlotsX")) return null
        val api = try {
            Bukkit.getServicesManager().load(PlotsXApi::class.java)
        } catch (_: NoClassDefFoundError) {
            return null
        } ?: return null
        return api.getAccessiblePlots(player).map { plot -> "${plot.name} (#${plot.id}, ${plot.world})" }
    }
}
