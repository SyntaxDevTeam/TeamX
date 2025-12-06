package pl.syntaxdevteam.teamx.cache

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import pl.syntaxdevteam.teamx.TeamX
import java.util.*
import java.util.concurrent.ConcurrentHashMap


class TeamCacheManager(private val plugin: TeamX): Listener {
    private val cache = ConcurrentHashMap<UUID, CachedTeamData>()

    fun loadPlayerTeamData(uuid: UUID) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val teamData = plugin.databaseHandler.getTeamByUUID(uuid.toString()) ?: return@Runnable
            if (teamData.size < 6) return@Runnable
            val teamId = teamData[0].toInt()
            val name = teamData[1]
            val leaderUUID = UUID.fromString(teamData[2])
            val leaderName = plugin.uuidManager.getPlayerName(leaderUUID.toString()) ?: ""
            val format = teamData[3]
            val createdAt = teamData[4]
            val description = teamData[5]

            val membersUUIDs = plugin.databaseHandler.getMemberUUIDsByUUID(uuid.toString())
            val members = membersUUIDs.mapNotNull { plugin.uuidManager.getPlayerName(it) }

            val fullName = "$format$name"

            cache[uuid] = CachedTeamData(
                teamId, name, format, leaderName, description, createdAt,
                members.size, members, fullName
            )
        })
    }

    private fun invalidate(uuid: UUID) {
        cache.remove(uuid)
    }

    fun get(uuid: UUID): CachedTeamData? = cache[uuid]

    fun refreshCacheForOnlinePlayers() {
        plugin.server.onlinePlayers.forEach { player ->
            loadPlayerTeamData(player.uniqueId)
        }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val uuid = plugin.uuidManager.getUUID(event.player.name)
        plugin.teamCacheManager.loadPlayerTeamData(uuid)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val uuid = plugin.uuidManager.getUUID(event.player.name)
        invalidate(uuid)
    }
}
