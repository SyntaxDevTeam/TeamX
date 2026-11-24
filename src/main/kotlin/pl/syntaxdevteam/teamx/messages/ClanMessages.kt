package pl.syntaxdevteam.teamx.messages

import org.bukkit.entity.Player
import pl.syntaxdevteam.teamx.TeamX

class ClanMessages(private val plugin: TeamX) {
    fun message(key: String, values: Map<String, String> = emptyMap()) =
        plugin.messageHandler.teamMessage("clan", key, values)

    fun tell(player: Player, key: String) = player.sendMessage(message(key))

    fun broadcast(player: Player, key: String, values: Map<String, String>) {
        val clan = plugin.teamCacheManager.get(player.uniqueId) ?: return tell(player, "membership_required")
        val output = message(key, values + mapOf("player" to player.name, "tag" to clan.tag))
        for (member in clan.roster) {
            val recipient = plugin.server.getPlayer(member.uuid) ?: continue
            plugin.operations.onPlayer(recipient) {
                if (plugin.teamCacheManager.get(recipient.uniqueId)?.teamId == clan.teamId &&
                    plugin.teamCacheManager.get(player.uniqueId)?.teamId == clan.teamId
                ) recipient.sendMessage(output)
            }
        }
    }
}
