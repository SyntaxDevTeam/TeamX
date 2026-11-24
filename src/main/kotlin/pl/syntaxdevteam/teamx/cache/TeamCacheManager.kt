package pl.syntaxdevteam.teamx.cache

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import pl.syntaxdevteam.teamx.TeamX
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TeamCacheManager(private val plugin: TeamX) : Listener {
    @Volatile private var cache = emptyMap<UUID, CachedTeamData>()
    private val online = ConcurrentHashMap.newKeySet<UUID>()
    private val database get() = plugin.databaseHandler

    fun initialize() {
        plugin.server.onlinePlayers.forEach { online.add(it.uniqueId) }
        plugin.operations.submit { refreshCacheForOnlinePlayers() }
    }

    // Called by the serialized database worker, including immediately after a membership mutation.
    fun refreshCacheForOnlinePlayers() {
        val clans = mutableMapOf<Int, CachedTeamData>()
        cache = try {
            loadSnapshot(clans)
        } catch (failure: Exception) {
            plugin.logger.err("Could not refresh clan membership: ${failure.message}")
            emptyMap()
        }
    }

    private fun loadSnapshot(clans: MutableMap<Int, CachedTeamData>): Map<UUID, CachedTeamData> = buildMap {
        for (uuid in online) {
            if (containsKey(uuid)) continue
            val team = database.getTeamByUUID(uuid.toString()) ?: continue
            val id = team[0].toInt()
            val clan = clans.getOrPut(id) {
                val leader = UUID.fromString(team[2])
                val roster = database.members(id)
                val memberNames = roster.filter { it.uuid != leader }.mapNotNull { name(it.uuid) }
                CachedTeamData(id, team[1], team[3], name(leader).orEmpty(), team[5], team[4],
                    memberNames.size, memberNames, team[3] + team[1], team[6], leader, roster, database.getHome(id))
            }
            clan.roster.filter { it.uuid in online }.forEach { put(it.uuid, clan) }
            if (uuid in online) put(uuid, clan)
        }
    }

    private fun name(uuid: UUID) = plugin.server.getOfflinePlayer(uuid).name

    fun get(uuid: UUID): CachedTeamData? = if (uuid in online) cache[uuid] else null

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        online.add(event.player.uniqueId)
        plugin.operations.submit { refreshCacheForOnlinePlayers() }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        online.remove(event.player.uniqueId)
        plugin.operations.submit { refreshCacheForOnlinePlayers() }
    }
}
