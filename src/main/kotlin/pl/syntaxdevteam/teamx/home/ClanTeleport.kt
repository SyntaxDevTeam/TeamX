package pl.syntaxdevteam.teamx.home

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import pl.syntaxdevteam.teamx.TeamX
import pl.syntaxdevteam.teamx.hooks.PlotsXHook
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Location
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerMoveEvent

class ClanTeleport(private val plugin: TeamX) : Listener {
    private val pending = ConcurrentHashMap<UUID, ScheduledTask>()

    private fun enabled() = plugin.config.getBoolean("team.home.enabled", true)

    fun setHome(player: Player, save: Boolean) {
        if (!plugin.config.getBoolean("team.home.enabled", true)) return plugin.clanMessages.tell(player, "disabled")
        val location = if (save) player.location else null
        if (location != null && plugin.config.getBoolean("team.home.require-plotsx", false) &&
            !PlotsXHook.canSetHome(player.uniqueId, location)
        ) return plugin.clanMessages.tell(player, "home_plot_required")
        val home = location?.let { ClanHome(it.world.uid, it.x, it.y, it.z, it.yaw, it.pitch) }
        plugin.operations.submit(player) {
            val team = plugin.operations.managedClan(player, manager = true) ?: return@submit
            val db = plugin.databaseHandler
            if (!db.setHome(team[0].toInt(), home)) {
                plugin.clanMessages.tell(player, if (save) "failed" else "home_missing")
                return@submit
            }
            plugin.teamCacheManager.refreshCacheForOnlinePlayers()
            plugin.clanMessages.tell(player, if (save) "home_saved" else "home_deleted")
        }
    }

    fun teleport(player: Player) {
        if (!enabled()) return plugin.clanMessages.tell(player, "disabled")
        val clan = plugin.teamCacheManager.get(player.uniqueId) ?: return plugin.clanMessages.tell(player, "membership_required")
        val home = clan.home ?: return plugin.clanMessages.tell(player, "home_missing")
        pending.remove(player.uniqueId)?.cancel()
        val seconds = plugin.config.getLong("team.home.delay-seconds", 5).coerceIn(0, 3600)
        if (seconds == 0L) return teleport(player, clan.teamId, home)
        val task = player.scheduler.runDelayed(plugin, { scheduled ->
            if (pending.remove(player.uniqueId, scheduled)) teleport(player, clan.teamId, home)
        }, null, seconds * 20)
        if (task != null) {
            pending[player.uniqueId] = task
            player.sendMessage(plugin.clanMessages.message("home_wait", mapOf("seconds" to seconds.toString())))
        }
    }

    private fun teleport(player: Player, teamId: Int, home: ClanHome) {
        val clan = plugin.teamCacheManager.get(player.uniqueId)
        if (clan?.teamId != teamId || clan.home != home || !enabled()) return plugin.clanMessages.tell(player, "home_cancelled")
        val world = plugin.server.getWorld(home.world) ?: return plugin.clanMessages.tell(player, "home_world_missing")
        player.teleportAsync(Location(world, home.x, home.y, home.z, home.yaw, home.pitch)).whenComplete { success, _ ->
            plugin.operations.onPlayer(player) { plugin.clanMessages.tell(player, if (success == true) "home_arrived" else "failed") }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        if (event.hasChangedPosition()) cancel(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        if (event.finalDamage > 0) cancel(player)
    }

    private fun cancel(player: Player) {
        val task = pending.remove(player.uniqueId) ?: return
        task.cancel()
        plugin.clanMessages.tell(player, "home_cancelled")
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        pending.remove(uuid)?.cancel()
    }
}
