package pl.syntaxdevteam.teamx.ping

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import pl.syntaxdevteam.teamx.TeamX
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ClanPing(private val plugin: TeamX) : Listener {
    private val lastPing = ConcurrentHashMap<UUID, Long>()

    fun ping(player: Player) {
        if (!plugin.config.getBoolean("team.ping.enabled", true)) return plugin.clanMessages.tell(player, "disabled")
        if (plugin.teamCacheManager.get(player.uniqueId) == null) return plugin.clanMessages.tell(player, "membership_required")
        val delay = TimeUnit.SECONDS.toNanos(plugin.config.getLong("team.ping.cooldown-seconds", 10).coerceAtLeast(0))
        val now = System.nanoTime()
        val previous = lastPing[player.uniqueId]
        if (previous != null && now - previous < delay) return plugin.clanMessages.tell(player, "ping_cooldown")
        lastPing[player.uniqueId] = now
        val location = player.location
        plugin.clanMessages.broadcast(player, "ping", mapOf("world" to location.world.name, "x" to location.blockX.toString(),
            "y" to location.blockY.toString(), "z" to location.blockZ.toString()))
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        lastPing.remove(event.player.uniqueId)
    }
}
