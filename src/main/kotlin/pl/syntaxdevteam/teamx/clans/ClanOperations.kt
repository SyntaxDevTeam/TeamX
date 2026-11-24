package pl.syntaxdevteam.teamx.clans

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import pl.syntaxdevteam.teamx.TeamX
import pl.syntaxdevteam.teamx.messages.teamMessage
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

class ClanOperations(private val plugin: TeamX) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "TeamX database").apply { isDaemon = true }
    }

    fun submit(sender: CommandSender? = null, action: () -> Unit) {
        try {
            executor.execute {
                try {
                    action()
                } catch (failure: Exception) {
                    plugin.logger.err("Clan operation failed: ${failure.message}")
                    sender?.sendMessage(plugin.messageHandler.teamMessage("clan", "failed"))
                }
            }
        } catch (_: RejectedExecutionException) {
            // Plugin shutdown has already stopped accepting operations.
        }
    }

    fun managedClan(player: Player, manager: Boolean = false): Array<String>? {
        val team = plugin.databaseHandler.getTeamByUUID(player.uniqueId.toString())
        if (team == null) {
            plugin.clanMessages.tell(player, "membership_required")
            return null
        }
        if (team[2] == player.uniqueId.toString() || manager &&
            plugin.databaseHandler.members(team[0].toInt()).any { it.uuid == player.uniqueId && it.rank == ClanRank.MANAGER }
        ) return team
        player.sendMessage(plugin.messageHandler.teamMessage("team", "not_team_owner"))
        return null
    }

    fun onPlayer(player: Player, action: () -> Unit) {
        if (plugin.isEnabled) player.scheduler.run(plugin, { action() }, null)
    }

    override fun close() {
        executor.shutdownNow()
    }
}
