package pl.syntaxdevteam.teamx.hooks

import org.bukkit.Bukkit
import org.bukkit.Location
import pl.syntaxdevteam.plotsx.api.PlotsXApi
import java.util.UUID

object PlotsXHook {
    private fun api(): PlotsXApi? {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlotsX")) return null
        return try {
            Bukkit.getServicesManager().load(PlotsXApi::class.java)
        } catch (_: NoClassDefFoundError) {
            return null
        }
    }

    fun canSetHome(player: UUID, location: Location): Boolean {
        val api = api() ?: return false
        val plot = api.getPlotAt(location.world.name, location.blockX, location.blockZ) ?: return false
        return api.getAccessiblePlots(player).any { it.id == plot.id }
    }

    fun visitablePlots(player: UUID): List<String>? =
        api()?.getAccessiblePlots(player)?.map { plot -> "${plot.name} (#${plot.id}, ${plot.world})" }
}
