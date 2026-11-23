package pl.syntaxdevteam.teamx.basic

import pl.syntaxdevteam.teamx.TeamX
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player
import java.util.UUID

@Suppress("UnstableApiUsage")
class PlaceholderHandler(private val plugin: TeamX) : PlaceholderExpansion() {

    override fun getIdentifier(): String {
        return "teamx"
    }

    override fun getAuthor(): String {
        return plugin.pluginMeta.authors.joinToString()
    }

    override fun getVersion(): String {
        return plugin.pluginMeta.version
    }

    override fun onPlaceholderRequest(player: Player?, params: String): String? {
        if (player == null) {
            return ""
        }

        return when (params) {
            "name" -> getTeamName(player.uniqueId)
            "full_name" -> getTeamFullName(player.uniqueId)
            "format" -> getTeamFormat(player.uniqueId)
            "leader" -> getTeamLeader(player.uniqueId)
            "description" -> getTeamDescription(player.uniqueId)
            "created_at" -> getTeamCreatedAt(player.uniqueId)
            "member_count" -> getTeamMemberCount(player.uniqueId).toString()
            "members" -> getTeamMemberUUIDs(player.uniqueId)
            "power" -> getTeamPower(player.uniqueId).toString()
            "full_team" -> getFullTeam(player.uniqueId)
            else -> null
        }
    }


    private fun getTeamName(uuid: UUID): String {
        val cached = plugin.teamCacheManager.get(uuid) ?: return "Solo"
        return cached.name

    }

    private fun getTeamFormat(uuid: UUID): String {
        val cached = plugin.teamCacheManager.get(uuid) ?: return ""
        return cached.format
    }

    private fun getTeamFullName(uuid: UUID): String {
        val cached = plugin.teamCacheManager.get(uuid) ?: return plugin.config.getString("Placeholders.chat_format.solo", "<gray>Solo</gray>") ?: "<gray>Solo</gray>"
        return "${cached.format}${cached.name}"
    }

    private fun getTeamLeader(uuid: UUID): String {
        val cached = plugin.teamCacheManager.get(uuid) ?: return ""
        return cached.leaderName
    }

    private fun getTeamDescription(uuid: UUID): String {
        val cached = plugin.teamCacheManager.get(uuid) ?: return ""
        return cached.description
    }

    private fun getTeamCreatedAt(uuid: UUID): String {
        val cached = plugin.teamCacheManager.get(uuid) ?: return ""
        return cached.createdAt
    }

    private fun getTeamMemberCount(uuid: UUID): Int {
        val cached = plugin.teamCacheManager.get(uuid) ?: return 0
        return cached.members.size
    }

    private fun getTeamMemberUUIDs(uuid: UUID): String {
        val cached = plugin.teamCacheManager.get(uuid) ?: return ""
        return if (plugin.config.getString("Placeholders.members_format") != "newline") {
            cached.members.joinToString(", ")
        } else {
            cached.members.joinToString("\n")
        }
    }

    private fun getTeamPower(uuid: UUID): Int {
        val cached = plugin.teamCacheManager.get(uuid) ?: return 0
        val luckPerms = plugin.hookHandler.luckPerms ?: return 0
        return TeamPowerCalculator(plugin, luckPerms).calculateTeamPower(cached.teamId)
    }

    private fun getFullTeam(uuid: UUID): String {
        val teamName = getTeamName(uuid)

        if (teamName.equals("solo", ignoreCase = true) || teamName.isBlank()) {
            return plugin.config.getString("Placeholders.chat_format.solo", "<gray>Solo</gray>") ?: "<gray>Solo</gray>"
        }

        val placeholders = mapOf(
            "{full_name}" to getTeamFullName(uuid),
            "{leader}" to (getTeamLeader(uuid)),
            "{member_count}" to getTeamMemberCount(uuid).toString(),
            "{members}" to getTeamMemberUUIDs(uuid),
            "{format}" to (getTeamFormat(uuid)),
            "{team_name}" to teamName,
            "{description}" to (getTeamDescription(uuid)),
            "{created_at}" to (getTeamCreatedAt(uuid)),
            "{power}" to getTeamPower(uuid).toString()
        )

        var formatted = plugin.config.getString("Placeholders.chat_format.team", "{format}{team_name}") ?: "{format}{team_name}"
        formatted = placeholders.entries.fold(formatted) { acc, (key, value) ->
            acc.replace(key, value, false)
        }

        plugin.logger.debug("Full team: $formatted")

        return formatted
    }
}
