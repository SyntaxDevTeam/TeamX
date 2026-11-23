package pl.syntaxdevteam.teamx.commands

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import pl.syntaxdevteam.teamx.TeamX
import pl.syntaxdevteam.teamx.basic.TextSanitizer
import pl.syntaxdevteam.teamx.gui.InitialSelectionGUI
import pl.syntaxdevteam.teamx.hooks.CleanerXHook
import pl.syntaxdevteam.teamx.hooks.PlotsXHook
import pl.syntaxdevteam.teamx.messages.teamMessage
import java.util.concurrent.CompletableFuture

class TeamCommand(private val plugin: TeamX) {
    private val mH get() = plugin.messageHandler
    private val db get() = plugin.databaseHandler
    private val maxNameLength get() = plugin.config.getInt("team.max-name-length", 16)

    fun build(): LiteralCommandNode<CommandSourceStack> = Commands.literal("team")
        .requires { plugin.isEnabled && it.sender.hasPermission("teamx.cmd.team") }
        .executes { showHelp(it, 1) }
        .then(helpCommand())
        .then(plotsCommand())
        .then(createCommand())
        .then(renameCommand())
        .then(descriptionCommand())
        .then(premium("format").executes { runAsPlayer(it, ::openFormatMenu) })
        .then(premium("delete").executes { runAsPlayer(it, ::deleteTeam) })
        .then(kickCommand())
        .then(inviteCommand())
        .then(basic("accept").executes { runAsPlayer(it, ::acceptInvitation) })
        .then(basic("deny").executes { runAsPlayer(it, ::declineInvitation) })
        .then(basic("leave").executes { runAsPlayer(it, ::leaveTeam) })
        .then(admin())
        .build()

    private fun helpCommand(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("help")
        .requires { it.sender.hasPermission("teamx.cmd.team.help") }
        .executes { showHelp(it, 1) }
        .then(
            Commands.argument("page", IntegerArgumentType.integer())
                .executes { showHelp(it, IntegerArgumentType.getInteger(it, "page")) }
        )

    private fun plotsCommand(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("plots")
        .executes { runAsPlayer(it) { player -> showPlots(player, 1) } }
        .then(
            Commands.argument("page", IntegerArgumentType.integer()).executes { context ->
                runAsPlayer(context) { player -> showPlots(player, IntegerArgumentType.getInteger(context, "page")) }
            }
        )

    private fun createCommand(): LiteralArgumentBuilder<CommandSourceStack> = premium("create")
        .executes { usage(it, "team", "create_usage") }
        .then(
            Commands.argument("name", StringArgumentType.string())
                .executes { context -> runAsPlayer(context) { createTeam(it, text(context, "name"), "") } }
                .then(
                    Commands.argument("description", StringArgumentType.greedyString()).executes { context ->
                        runAsPlayer(context) { player ->
                            createTeam(player, text(context, "name"), text(context, "description"))
                        }
                    }
                )
        )

    private fun renameCommand(): LiteralArgumentBuilder<CommandSourceStack> = premium("rename")
        .executes { usage(it, "team", "rename_usage") }
        .then(
            Commands.argument("name", StringArgumentType.string()).executes { context ->
                runAsPlayer(context) { renameTeam(it, text(context, "name")) }
            }
        )

    private fun descriptionCommand(): LiteralArgumentBuilder<CommandSourceStack> = premium("description")
        .executes { usage(it, "team", "description_usage") }
        .then(
            Commands.argument("description", StringArgumentType.greedyString()).executes { context ->
                runAsPlayer(context) { updateDescription(it, text(context, "description")) }
            }
        )

    private fun kickCommand(): LiteralArgumentBuilder<CommandSourceStack> = premium("kick")
        .executes { usage(it, "team", "kick_usage") }
        .then(
            playerArgument().executes { context ->
                runAsPlayer(context) { kickPlayer(it, text(context, "player")) }
            }
        )

    private fun inviteCommand(): LiteralArgumentBuilder<CommandSourceStack> = premium("invite")
        .executes { usage(it, "invitation", "usage") }
        .then(
            playerArgument().executes { context ->
                runAsPlayer(context) { invitePlayer(it, text(context, "player")) }
            }
        )

    private fun showHelp(context: CommandContext<CommandSourceStack>, page: Int): Int {
        sendHelp(context.source.sender, page)
        return 1
    }

    private fun admin(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("admin")
        .requires { it.sender.hasPermission("teamx.cmd.team.admin") }
        .executes(::adminUsage)
        .then(adminPlayerCommand("adduser", ::adminAddPlayer))
        .then(adminPlayerCommand("removeuser", ::adminRemovePlayer))
        .then(adminPlayerCommand("setleader", ::adminSetLeader))
        .then(Commands.literal("rename")
            .executes(::adminUsage)
            .then(teamArgument().executes(::adminUsage)
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes { context ->
                        withTeam(context) { sender, id, _ -> adminRenameTeam(sender, id, text(context, "name")) }
                    })))
        .then(Commands.literal("delete")
            .executes(::adminUsage)
            .then(teamArgument().executes { withTeam(it, ::adminDeleteTeam) }))

    private fun adminPlayerCommand(
        name: String,
        action: (CommandSender, Int, String, String) -> Unit
    ): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal(name)
        .executes(::adminUsage)
        .then(teamArgument().executes(::adminUsage)
            .then(playerArgument().executes { context ->
                withTeam(context) { sender, id, team -> action(sender, id, team, text(context, "player")) }
            }))

    private fun premium(name: String) = permitted(name, "teamx.cmd.team.premium")

    private fun basic(name: String) = permitted(name, "teamx.cmd.basic")

    private fun permitted(name: String, groupPermission: String): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(name).requires {
            it.sender.hasPermission(groupPermission) || it.sender.hasPermission("teamx.cmd.team.$name")
        }

    private fun playerArgument(): RequiredArgumentBuilder<CommandSourceStack, String> =
        Commands.argument("player", StringArgumentType.string()).suggests { _, builder ->
            suggestNames(builder, plugin.server.onlinePlayers.map { it.name })
        }

    private fun teamArgument(): RequiredArgumentBuilder<CommandSourceStack, String> =
        Commands.argument("team", StringArgumentType.string()).suggests { _, builder ->
            suggestNames(builder, db.getTeamList())
        }

    private fun suggestNames(
        builder: SuggestionsBuilder,
        names: Iterable<String>
    ): CompletableFuture<Suggestions> =
        builder.apply {
            val prefix = remaining.removePrefix("\"").removePrefix("'")
            names.filter { it.startsWith(prefix, ignoreCase = true) }
                .forEach { suggest(StringArgumentType.escapeIfRequired(it)) }
        }.buildFuture()

    private fun text(context: CommandContext<CommandSourceStack>, name: String): String =
        StringArgumentType.getString(context, name)

    private fun usage(context: CommandContext<CommandSourceStack>, category: String, key: String): Int {
        context.source.sender.sendMessage(mH.teamMessage(category, key))
        return 0
    }

    private fun adminUsage(context: CommandContext<CommandSourceStack>) = usage(context, "team", "admin_usage")

    private fun runAsPlayer(context: CommandContext<CommandSourceStack>, action: (Player) -> Unit): Int {
        val sender = context.source.sender
        if (sender !is Player) return usage(context, "error", "console")
        action(sender)
        return 1
    }

    private fun withTeam(
        context: CommandContext<CommandSourceStack>,
        action: (CommandSender, Int, String) -> Unit
    ): Int {
        val sender = context.source.sender
        val teamName = text(context, "team")
        val team = db.getTeamByName(teamName)
        if (team == null) {
            sender.sendMessage(mH.teamMessage("error", "not_found", mapOf("team_name" to teamName)))
            return 0
        }
        action(sender, team[0].toInt(), teamName)
        return 1
    }

    private fun showPlots(sender: Player, page: Int) {
        val uuid = sender.uniqueId
        val plots = PlotsXHook.visitablePlots(uuid)
        if (plots == null) {
            sender.sendMessage(mH.teamMessage("plots", "unavailable"))
            return
        }
        if (plots.isEmpty()) {
            sender.sendMessage(mH.teamMessage("plots", "empty"))
            return
        }
        val pageSize = 10
        val pages = (plots.size - 1) / pageSize + 1
        val currentPage = page.coerceIn(1, pages)
        sender.sendMessage(mH.teamMessage("plots", "page", mapOf("page" to "$currentPage", "pages" to "$pages")))
        plots.asSequence().drop((currentPage - 1) * pageSize).take(pageSize).forEach(sender::sendMessage)
    }

    private fun createTeam(sender: Player, name: String, description: String) {
        val uuid = sender.uniqueId
        val cleanName = validateTeamName(sender, name) ?: return
        if (db.isTeamNameExists(cleanName)) {
            sender.sendMessage(mH.teamMessage("team", "create_exists", mapOf("team_name" to cleanName)))
            return
        }

        val existingTeam = db.getTeamByUUID(uuid.toString())
        if (existingTeam != null) {
            sender.sendMessage(mH.teamMessage("error", "already_in_team"))
            return
        }

        if (!db.addTeam(cleanName, uuid.toString(), description)) {
            sender.sendMessage(mH.teamMessage("team", "create_failed"))
            return
        }
        sender.sendMessage(mH.teamMessage("team", "created", mapOf("team_name" to cleanName)))
        plugin.logger.debug("Player ${sender.name} created a team:")
        plugin.logger.debug("Description: $description")
        plugin.teamCacheManager.loadPlayerTeamData(uuid)
    }

    private fun renameTeam(sender: Player, name: String) {
        val uuid = sender.uniqueId
        val newName = validateTeamName(sender, name) ?: return
        val teamData = db.getTeamByUUID(uuid.toString())
        if (teamData == null) {
            sender.sendMessage(mH.teamMessage("error", "no_have_team"))
            return
        }
        val teamId = teamData[0].toInt()
        if (db.isTeamNameExists(newName)) {
            sender.sendMessage(mH.teamMessage("team", "rename_exists"))
            return
        }
        if (db.updateTeamName(teamId, newName)) {
            sender.sendMessage(mH.teamMessage("team", "renamed", mapOf("team_name" to newName)))
            plugin.logger.debug("Team ID $teamId has been renamed to $newName")
        } else {
            sender.sendMessage(mH.teamMessage("team", "rename_failed"))
        }
        plugin.teamCacheManager.loadPlayerTeamData(uuid)
    }

    private fun updateDescription(sender: Player, description: String) {
        val uuid = sender.uniqueId
        val teamData = db.getTeamByUUID(uuid.toString())
        if (teamData == null) {
            sender.sendMessage(mH.teamMessage("error", "no_have_team"))
            return
        }
        val teamId = teamData[0].toInt()
        if (db.updateTeamDescription(teamId, description)) {
            sender.sendMessage(mH.teamMessage("team", "description_updated"))
            plugin.logger.debug("Team ID $teamId description has been updated to $description")
        } else {
            sender.sendMessage(mH.teamMessage("team", "description_failed"))
        }
        plugin.teamCacheManager.loadPlayerTeamData(uuid)
    }

    private fun openFormatMenu(sender: Player) {
        val uuid = sender.uniqueId
        val teamData = db.getTeamByUUID(uuid.toString())
        if (teamData == null) {
            sender.sendMessage(mH.teamMessage("error", "no_have_team"))
            return
        }

        val teamOwnerUUID = teamData[2]
        if (teamOwnerUUID != uuid.toString()) {
            sender.sendMessage(mH.teamMessage("error", "not_team_owner"))
            return
        }

        InitialSelectionGUI(plugin).open(sender)
    }

    private fun deleteTeam(sender: Player) {
        val uuid = sender.uniqueId
        val teamData = db.getTeamByUUID(uuid.toString())
        if (teamData == null) {
            sender.sendMessage(mH.teamMessage("error", "no_have_team"))
            return
        }
        val teamId = teamData[0].toInt()
        val teamName = teamData[1]
        val teamLeaderUUID = teamData[2]

        if (teamLeaderUUID != uuid.toString()) {
            sender.sendMessage(mH.teamMessage("error", "not_team_owner"))
            return
        }

        val teamMembers = db.getTeamMembers(teamId)
        var success = true

        for (memberUuid in teamMembers) {
            if (!db.removePlayerFromTeam(teamId, memberUuid)) {
                success = false
            }
        }

        if (success && db.removeTeam(teamId, false)) {
            sender.sendMessage(mH.teamMessage("team", "deleted", mapOf("team_name" to teamName)))
        } else {
            sender.sendMessage(mH.teamMessage("team", "delete_failed", mapOf("team_name" to teamName)))
        }
        plugin.teamCacheManager.loadPlayerTeamData(uuid)
    }

    private fun kickPlayer(sender: Player, playerName: String) {
        val uuid = sender.uniqueId
        val playerUUID = plugin.uuidManager.getUUID(playerName)
        val teamData = db.getTeamByUUID(playerUUID.toString())

        if (teamData == null) {
            sender.sendMessage(mH.teamMessage("error", "player_not_in_team", mapOf("player" to playerName)))
            return
        }

        val teamId = teamData[0].toInt()
        val teamName = teamData[1]
        val teamLeaderUUID = teamData[2]

        if (teamLeaderUUID != uuid.toString()) {
            sender.sendMessage(mH.teamMessage("error", "not_team_owner"))
            return
        }

        if (db.removePlayerFromTeam(teamId, playerUUID.toString())) {
            sender.sendMessage(mH.teamMessage("team", "player_removed", mapOf("player" to playerName, "team_name" to teamName)))
            plugin.server.getPlayer(playerUUID)?.sendMessage(mH.teamMessage("team", "you_kicked", mapOf("team_name" to teamName)))
        } else {
            sender.sendMessage(mH.teamMessage("team", "remove_failed", mapOf("player" to playerName, "team_name" to teamName)))
        }
        plugin.teamCacheManager.loadPlayerTeamData(uuid)
    }

    private fun invitePlayer(sender: Player, invitedPlayerName: String) {
        val uuid = sender.uniqueId
        val invitedPlayer = plugin.server.getPlayer(invitedPlayerName)
        if (invitedPlayer == null) {
            sender.sendMessage(mH.teamMessage("error", "player_not_found", mapOf("player" to invitedPlayerName)))
            return
        }

        val invitedPlayerUUID = invitedPlayer.uniqueId
        val existingTeam = db.getTeamByUUID(invitedPlayerUUID.toString())
        if (existingTeam != null) {
            sender.sendMessage(mH.teamMessage("error", "player_already_in_team", mapOf("player" to invitedPlayerName)))
            return
        }

        val teamData = db.getTeamByUUID(uuid.toString())
        if (teamData == null) {
            sender.sendMessage(mH.teamMessage("error", "no_have_team"))
            return
        }

        val teamId = teamData[0]
        plugin.invitationManager.sendInvitation(teamId, sender, invitedPlayer)
        sender.sendMessage(mH.teamMessage("invitation", "sent", mapOf("player" to invitedPlayerName)))
    }

    private fun acceptInvitation(sender: Player) {
        val uuid = sender.uniqueId
        val existingTeam = db.getTeamByUUID(uuid.toString())
        if (existingTeam != null) {
            sender.sendMessage(mH.teamMessage("error", "already_in_team"))
            return
        }

        plugin.invitationManager.acceptInvitation(sender)
        plugin.teamCacheManager.loadPlayerTeamData(uuid)
    }

    private fun declineInvitation(sender: Player) {
        val uuid = sender.uniqueId
        val existingTeam = db.getTeamByUUID(uuid.toString())
        if (existingTeam != null) {
            sender.sendMessage(mH.teamMessage("error", "already_in_team"))
            return
        }

        plugin.invitationManager.declineInvitation(sender)
    }

    private fun leaveTeam(sender: Player) {
        val uuid = sender.uniqueId
        val teamData = db.getTeamByUUID(uuid.toString())
        if (teamData == null) {
            sender.sendMessage(mH.teamMessage("error", "no_have_team"))
            return
        }

        val teamId = teamData[0].toInt()
        val teamName = teamData[1]
        if (db.removePlayerFromTeam(teamId, uuid.toString())) {
            sender.sendMessage(mH.teamMessage("team", "player_leaved", mapOf("player" to sender.name, "team_name" to teamName)))
        } else {
            sender.sendMessage(mH.teamMessage("team", "leave_failed", mapOf("player" to sender.name, "team_name" to teamName)))
        }
        plugin.teamCacheManager.loadPlayerTeamData(uuid)
    }

    private fun adminAddPlayer(sender: CommandSender, teamId: Int, teamName: String, playerName: String) {
        val playerUUID = plugin.uuidManager.getUUID(playerName)
        if (db.addPlayerToTeam(teamId, playerUUID.toString())) {
            sender.sendMessage(mH.teamMessage("team", "player_added", mapOf("player" to playerName, "team_name" to teamName)))
        } else {
            sender.sendMessage(mH.teamMessage("team", "add_failed", mapOf("player" to playerName, "team_name" to teamName)))
        }
        plugin.teamCacheManager.refreshCacheForOnlinePlayers()
    }

    private fun adminRemovePlayer(sender: CommandSender, teamId: Int, teamName: String, playerName: String) {
        val playerUUID = plugin.uuidManager.getUUID(playerName)
        if (db.removePlayerFromTeam(teamId, playerUUID.toString())) {
            sender.sendMessage(mH.teamMessage("team", "player_removed", mapOf("player" to playerName, "team_name" to teamName)))
        } else {
            sender.sendMessage(mH.teamMessage("team", "remove_failed", mapOf("player" to playerName, "team_name" to teamName)))
        }
        plugin.teamCacheManager.refreshCacheForOnlinePlayers()
    }

    private fun adminSetLeader(sender: CommandSender, teamId: Int, teamName: String, playerName: String) {
        val playerUUID = plugin.uuidManager.getUUID(playerName)
        if (db.setTeamLeader(teamId, playerUUID.toString())) {
            sender.sendMessage(mH.teamMessage("team", "leader_set", mapOf("player" to playerName, "team_name" to teamName)))
        } else {
            sender.sendMessage(mH.teamMessage("team", "set_leader_failed", mapOf("player" to playerName, "team_name" to teamName)))
        }
        plugin.teamCacheManager.refreshCacheForOnlinePlayers()
    }

    private fun adminRenameTeam(sender: CommandSender, teamId: Int, name: String) {
        val newName = validateTeamName(sender, name) ?: return
        if (db.isTeamNameExists(newName)) {
            sender.sendMessage(mH.teamMessage("team", "rename_exists"))
            return
        }
        if (db.updateTeamName(teamId, newName)) {
            sender.sendMessage(mH.teamMessage("team", "renamed", mapOf("team_name" to newName)))
            plugin.logger.debug("Team ID $teamId has been renamed to $newName")
        } else {
            sender.sendMessage(mH.teamMessage("team", "rename_failed"))
        }
        plugin.teamCacheManager.refreshCacheForOnlinePlayers()
    }

    private fun adminDeleteTeam(sender: CommandSender, teamId: Int, teamName: String) {
        val teamMembers = db.getTeamMembers(teamId)
        var success = true

        for (memberUuid in teamMembers) {
            if (!db.removePlayerFromTeam(teamId, memberUuid)) {
                success = false
            }
        }

        if (success && db.removeTeam(teamId, false)) {
            sender.sendMessage(mH.teamMessage("team", "deleted", mapOf("team_name" to teamName)))
        } else {
            sender.sendMessage(mH.teamMessage("team", "delete_failed", mapOf("team_name" to teamName)))
        }
        plugin.teamCacheManager.refreshCacheForOnlinePlayers()
    }

    private fun validateTeamName(sender: CommandSender, input: String): String? {
        val name = TextSanitizer.stripFormatting(input).replace('_', ' ').trim()
        return when {
            name.isEmpty() -> {
                sender.sendMessage(mH.teamMessage("error", "empty_name"))
                null
            }
            name.length > maxNameLength -> {
                sender.sendMessage(mH.teamMessage("error", "name_too_long", mapOf("limit" to maxNameLength.toString())))
                null
            }
            CleanerXHook.containsBannedWord(name) -> {
                sender.sendMessage(mH.teamMessage("error", "banned_word"))
                null
            }
            else -> name
        }
    }

    private fun sendHelp(sender: CommandSender, page: Int) {
        val isPremium = sender.hasPermission("teamx.cmd.team.premium")
        val isAdmin = sender.hasPermission("teamx.cmd.team.admin")

        val commands = mutableListOf(
            "  <gold>/team plots [page] <gray>- <white>Lists plots you can access through PlotsX.",
            "  <gold>/team create <name> [description] ",
            "         <gray>- <white>Creates a team with the given name and description.",
            "  <gold>/team rename <new_name> <gray>- <white>Changes the team name.",
            "  <gold>/team description <new_description>",
            "             <gray>- <white>Changes the team description.",
            "  <gold>/team format <gray>- <white>Opens a GUI for editing the team format.",
            "  <gold>/team delete <gray>- <white>Deletes the team",
            "                          (only the leader can delete their team).",
            "  <gold>/team invite <player>",
            "             <gray>- <white>Sends a team invitation to the specified player ",
            "                          (only the leader can delete their team).",
            " ",
            "  <gold>/team kick <player> <gray>- <white>Removes a player from the team",
            "                          (only the leader can delete their team).",
        )
        if (isPremium){
            commands.addAll(listOf(
                " ",
                "  <gold>/team accept <gray>- <white>Accepts a team invitation.",
                "  <gold>/team deny <gray>- <white>Denies a team invitation.",
                "  <gold>/team leave <gray>- <white>Leaves the team."
            ))
        }

        if (isAdmin) {
            commands.addAll(listOf(
                " ",
                "Administrative Commands:",
                "  <gold>/team admin adduser <team_name> <player>",
                "             <gray>- <white>Adds a player to a team as an administrator.",
                "  <gold>/team admin removeuser <team_name> <player>",
                "        <gray>- <white>Removes a player from a team as an administrator.",
                "  <gold>/team admin setleader <team_name> <player>",
                "                        <gray>- <white>Sets a player as the team leader.",
                "  <gold>/team admin rename <team_name> <new_name>",
                "                                   <gray>- <white>Changes the team name.",
                "  <gold>/team admin delete <team_name> <gray>- <white>Deletes a team."
            ))
        }

        commands.addAll(listOf(
            " ",
            "Help:",
            "  <gold>/team help <gray>- <white>Displays a list of available commands."
        ))

        val itemsPerPage = 12
        val totalPages = (commands.size + itemsPerPage - 1) / itemsPerPage
        val currentPage = page.coerceIn(1, totalPages)

        sender.sendMessage(mH.miniMessageFormat(" <gray>+-------------------------------------------------"))
        sender.sendMessage(mH.miniMessageFormat(" <gray>|    <gold>Available commands for ${plugin.pluginMeta.name}:"))
        sender.sendMessage(mH.miniMessageFormat(" <gray>|"))

        val startIndex = (currentPage - 1) * itemsPerPage
        val endIndex = (currentPage * itemsPerPage).coerceAtMost(commands.size)
        for (i in startIndex until endIndex) {
            sender.sendMessage(mH.miniMessageFormat(" <gray>|  ${commands[i]}"))
        }

        val prevPage = if (currentPage > 1) currentPage - 1 else totalPages
        val nextPage = if (currentPage < totalPages) currentPage + 1 else 1
        sender.sendMessage(mH.miniMessageFormat(" <gray>|"))
        sender.sendMessage(mH.miniMessageFormat(" <gray>|"))
        sender.sendMessage(mH.miniMessageFormat(
            " <gray>| (Page $currentPage/$totalPages) <click:run_command:'/team help $prevPage'><white>[Previous]</white></click>   " +
                    "<click:run_command:'/team help $nextPage'><white>[Next]</white></click>"
        ))
        sender.sendMessage(mH.miniMessageFormat(" <gray>|"))
        sender.sendMessage(mH.miniMessageFormat(" <gray>+-------------------------------------------------"))
    }
}
