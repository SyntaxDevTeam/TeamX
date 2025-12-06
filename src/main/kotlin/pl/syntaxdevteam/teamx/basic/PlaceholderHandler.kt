package pl.syntaxdevteam.teamx.basic

import pl.syntaxdevteam.teamx.TeamX
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player

@Suppress("UnstableApiUsage")
class PlaceholderHandler(private val plugin: TeamX) : PlaceholderExpansion() {
    private val luckPerms = plugin.hookHandler.luckPerms
    private val teamPower = luckPerms?.let { TeamPowerCalculator(plugin, it) }

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
            "name" -> getTeamName(player.name)
            "full_name" -> getTeamFullName(player.name)
            "format" -> getTeamFormat(player.name)
            "leader" -> getTeamLeader(player.name)
            "description" -> getTeamDescription(player.name)
            "created_at" -> getTeamCreatedAt(player.name)
            "member_count" -> getTeamMemberCount(player.name).toString()
            "members" -> getTeamMemberUUIDs(player.name)
            "power" -> getTeamPower(player.name).toString()
            "full_team" -> getFullTeam(player.name)
            else -> null
        }
    }


    private fun getTeamName(player: String): String {
        val uuid = plugin.uuidManager.getUUID(player)
        val cached = plugin.teamCacheManager.get(uuid) ?: return "Solo"
        return cached.name

    }

    private fun getTeamFormat(player: String): String {
        val uuid = plugin.uuidManager.getUUID(player)
        val cached = plugin.teamCacheManager.get(uuid) ?: return ""
        return cached.format
    }

    private fun getTeamFullName(player: String): String {
        val uuid = plugin.uuidManager.getUUID(player)
        val cached = plugin.teamCacheManager.get(uuid) ?: return plugin.config.getString("Placeholders.chat_format.solo", "<gray>Solo</gray>") ?: "<gray>Solo</gray>"
        return "${cached.format}${cached.name}"
    }

    private fun getTeamLeader(player: String): String {
        val uuid = plugin.uuidManager.getUUID(player)
        val cached = plugin.teamCacheManager.get(uuid) ?: return ""
        return cached.leaderName
    }

    private fun getTeamDescription(player: String): String {
        val uuid = plugin.uuidManager.getUUID(player)
        val cached = plugin.teamCacheManager.get(uuid) ?: return ""
        return cached.description
    }

    private fun getTeamCreatedAt(player: String): String {
        val uuid = plugin.uuidManager.getUUID(player)
        val cached = plugin.teamCacheManager.get(uuid) ?: return ""
        return cached.createdAt
    }

    private fun getTeamMemberCount(player: String): Int {
        val uuid = plugin.uuidManager.getUUID(player)
        val cached = plugin.teamCacheManager.get(uuid) ?: return 0
        return cached.members.size
    }

    private fun getTeamMemberUUIDs(player: String): String {
        val uuid = plugin.uuidManager.getUUID(player)
        val cached = plugin.teamCacheManager.get(uuid) ?: return ""
        return if (plugin.config.getString("Placeholders.members_format") != "newline") {
            cached.members.joinToString(", ")
        } else {
            cached.members.joinToString("\n")
        }
    }

    private fun getTeamPower(player: String): Int {
        val uuid = plugin.uuidManager.getUUID(player)
        val cached = plugin.teamCacheManager.get(uuid) ?: return 0
        return teamPower?.calculateTeamPower(cached.teamId) ?: 0
    }

    private fun getFullTeam(player: String): String {
        val teamName = getTeamName(player)

        if (teamName.equals("solo", ignoreCase = true) || teamName.isBlank()) {
            return plugin.config.getString("Placeholders.chat_format.solo", "<gray>Solo</gray>") ?: "<gray>Solo</gray>"
        }

        val placeholders = mapOf(
            "{full_name}" to getTeamFullName(player),
            "{leader}" to (getTeamLeader(player)),
            "{member_count}" to getTeamMemberCount(player).toString(),
            "{members}" to getTeamMemberUUIDs(player),
            "{format}" to (getTeamFormat(player)),
            "{team_name}" to teamName,
            "{description}" to (getTeamDescription(player)),
            "{created_at}" to (getTeamCreatedAt(player)),
            "{power}" to getTeamPower(player).toString()
        )

        var formatted = plugin.config.getString("Placeholders.chat_format.team", "{format}{team_name}") ?: "{format}{team_name}"
        formatted = placeholders.entries.fold(formatted) { acc, (key, value) ->
            acc.replace(key, value, false)
        }

        plugin.logger.debug("Full team: $formatted")

        return formatted
    }
}
