package pl.syntaxdevteam.teamx.basic

import net.luckperms.api.LuckPerms
import net.luckperms.api.context.ContextManager
import net.luckperms.api.context.ImmutableContextSet
import net.luckperms.api.model.user.User
import net.luckperms.api.query.QueryOptions
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import pl.syntaxdevteam.teamx.TeamX
import java.util.concurrent.CompletableFuture


class TeamPowerCalculator(private val plugin: TeamX, private val luckPerms: LuckPerms) {

    fun calculateTeamPower(teamId: Int): Int {
        val members = plugin.databaseHandler.getTeamMembers(teamId)
        val leader = plugin.databaseHandler.getLeaderByTeamId(teamId)
        return (members + listOfNotNull(leader)).distinct().sumOf { member ->
            val uuid = java.util.UUID.fromString(member)
            Bukkit.getPlayer(uuid)?.let(::getPlayerPower)
                ?: getOfflinePlayerPower(luckPerms.userManager.loadUser(uuid))
        }
    }

    private fun getPlayerPower(player: Player): Int {
        return when {
            player.hasPermission("teamx.power.star_three") -> 3
            player.hasPermission("teamx.power.star_two") -> 2
            player.hasPermission("teamx.power.star_one") -> 1
            else -> 0
        }
    }

    private fun getOfflinePlayerPower(userFuture: CompletableFuture<User>): Int {
        return userFuture.thenApply { user ->
            val contextManager: ContextManager = luckPerms.contextManager
            val contextSet: ImmutableContextSet = contextManager.getContext(user).orElseGet { contextManager.staticContext }
            val queryOptions: QueryOptions = QueryOptions.contextual(contextSet)
            val permissionData = user.cachedData.getPermissionData(queryOptions)

            when {
                permissionData.checkPermission("teamx.power.star_three").asBoolean() -> 3
                permissionData.checkPermission("teamx.power.star_two").asBoolean() -> 2
                permissionData.checkPermission("teamx.power.star_one").asBoolean() -> 1
                else -> 0
            }
        }.join()
    }
}
