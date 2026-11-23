package pl.syntaxdevteam.teamx.cache

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import pl.syntaxdevteam.teamx.TeamX
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap


class TeamCacheManager(private val plugin: TeamX) : Listener {
    private class Entry(@Volatile var data: CachedTeamData?)

    private val database = plugin.databaseHandler
    private val cache = ConcurrentHashMap<UUID, Entry>()

    fun loadPlayerTeamData(uuid: UUID) {
        val entry = cache.compute(uuid) { _, previous -> Entry(previous?.data) } ?: return
        plugin.server.asyncScheduler.runNow(plugin) {
            val teamData = database.getTeamByUUID(uuid.toString())
            if (teamData == null || teamData.size < 6) {
                entry.data = null
                return@runNow
            }
            val teamId = teamData[0].toInt()
            val name = teamData[1]
            val leaderUUID = UUID.fromString(teamData[2])
            val leaderName = plugin.server.getOfflinePlayer(leaderUUID).name.orEmpty()
            val format = teamData[3]
            val createdAt = teamData[4]
            val description = teamData[5]

            val membersUUIDs = database.getMemberUUIDsByUUID(leaderUUID.toString())
            val members = membersUUIDs.mapNotNull { plugin.server.getOfflinePlayer(UUID.fromString(it)).name }

            val fullName = "$format$name"

            entry.data = CachedTeamData(
                teamId, name, format, leaderName, description, createdAt,
                members.size, members, fullName
            )
        }
    }

    private fun invalidate(uuid: UUID) {
        cache.remove(uuid)
    }

    fun get(uuid: UUID): CachedTeamData? = cache[uuid]?.data

    fun refreshCacheForOnlinePlayers() {
        plugin.server.onlinePlayers.forEach { player ->
            loadPlayerTeamData(player.uniqueId)
        }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val uuid = event.player.uniqueId
        loadPlayerTeamData(uuid)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        invalidate(uuid)
    }
}
