package pl.syntaxdevteam.teamx.invitations

import org.bukkit.entity.Player
import pl.syntaxdevteam.teamx.clans.ClanRank
import pl.syntaxdevteam.teamx.TeamX
import pl.syntaxdevteam.teamx.messages.teamMessage
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class InvitationManager(private val plugin: TeamX) {
    private val invitations = ConcurrentHashMap<UUID, TeamInvitation>()
    private val mH get() = plugin.messageHandler

    fun current(uuid: UUID): TeamInvitation? = invitations[uuid]?.takeUnless {
        System.currentTimeMillis() - it.timestamp >= EXPIRATION_SECONDS * 1000
    }

    fun sendInvitation(teamId: String, inviter: Player, invited: Player) {
        val invitation = TeamInvitation(teamId, inviter.uniqueId, invited.uniqueId)
        invitations[invited.uniqueId] = invitation
        invited.sendMessage(mH.teamMessage("invitation", "received", mapOf("leader" to inviter.name)))
        plugin.server.asyncScheduler.runDelayed(plugin, {
            invitations.computeIfPresent(invited.uniqueId) { _, active ->
                if (active !== invitation) active
                else {
                    plugin.server.getPlayer(invited.uniqueId)?.sendMessage(mH.teamMessage("invitation", "expired"))
                    null
                }
            }
        }, EXPIRATION_SECONDS, TimeUnit.SECONDS)
    }

    fun acceptInvitation(invited: Player) {
        val invitation = invitations.remove(invited.uniqueId)
        if (invitation == null || System.currentTimeMillis() - invitation.timestamp >= EXPIRATION_SECONDS * 1000) {
            invited.sendMessage(mH.teamMessage("invitation", "no_invitation"))
            return
        }
        val db = plugin.databaseHandler
        val team = db.getTeamByUUID(invitation.leaderUUID.toString())
        val teamId = invitation.teamId.toInt()
        if (team?.get(0)?.toInt() != teamId ||
            db.members(teamId).none { it.uuid == invitation.leaderUUID && it.rank in managers } ||
            !db.addPlayerToTeam(teamId, invited.uniqueId.toString())
        ) {
            invited.sendMessage(mH.teamMessage("clan", "failed"))
            return
        }
        plugin.teamCacheManager.refreshCacheForOnlinePlayers()
        invited.sendMessage(mH.teamMessage("invitation", "accepted", mapOf("team_name" to team[1])))
        plugin.server.getPlayer(invitation.leaderUUID)?.sendMessage(
            mH.teamMessage("invitation", "player_accepted", mapOf("player" to invited.name))
        )
    }

    fun declineInvitation(invited: Player) {
        val invitation = current(invited.uniqueId)
        if (invitation == null || !invitations.remove(invited.uniqueId, invitation)) {
            invited.sendMessage(mH.teamMessage("invitation", "no_invitation"))
            return
        }
        invited.sendMessage(mH.teamMessage("invitation", "denied"))
        plugin.server.getPlayer(invitation.leaderUUID)?.sendMessage(
            mH.teamMessage("invitation", "player_denied", mapOf("player" to invited.name))
        )
    }

    companion object {
        private const val EXPIRATION_SECONDS = 120L
        internal val managers = setOf(ClanRank.LEADER, ClanRank.MANAGER)
    }
}
