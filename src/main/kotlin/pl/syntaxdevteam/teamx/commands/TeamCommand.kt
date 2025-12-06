package pl.syntaxdevteam.teamx.commands

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.jetbrains.annotations.NotNull
import pl.syntaxdevteam.teamx.TeamX
import pl.syntaxdevteam.teamx.basic.TextSanitizer
import pl.syntaxdevteam.teamx.gui.InitialSelectionGUI
import pl.syntaxdevteam.teamx.hooks.CleanerXHook

@Suppress("UnstableApiUsage")
class TeamCommand(private val plugin: TeamX) : BasicCommand {
    private val mH = plugin.messageHandler
    private val db = plugin.databaseHandler
    private val maxNameLength = plugin.config.getInt("team.max-name-length", 16)

    override fun execute(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>) {
        val player = stack.sender.name
        val uuid = plugin.uuidManager.getUUID(player)

        if (!stack.sender.hasPermission("teamx.cmd.team")) {
            stack.sender.sendMessage(mH.getMessage("error", "no_permission"))
            return
        }

        if (args.isEmpty()) {
            val page = args.getOrNull(1)?.toIntOrNull() ?: 1
            sendHelp(stack, page)
            return
        }

        when {
            args[0].equals("create", ignoreCase = true) -> {
                if (!(stack.sender.hasPermission("teamx.cmd.team.premium") || stack.sender.hasPermission("teamx.cmd.team.create"))) {
                    stack.sender.sendMessage(mH.getMessage("error", "no_permission"))
                    return
                }
                if (args.size < 2) {
                    stack.sender.sendMessage(mH.getMessage("team", "create_usage"))
                    return
                }

                val name: String = args[1]
                if (db.isTeamNameExists(name)) {
                    stack.sender.sendMessage(mH.getMessage("team", "create_exists", mapOf("team_name" to name)))
                    return
                }


                if (!isTextLengthValid(name)) {
                    stack.sender.sendMessage(mH.getMessage("error", "name_too_long", mapOf("limit" to maxNameLength.toString())))
                    return
                }

                val plainName = mH.getPlainText(Component.text(name))
                val cleanerXApi = CleanerXHook.getApi()
                if (cleanerXApi != null && cleanerXApi.containsBannedWord(plainName)) {
                    stack.sender.sendMessage(mH.getMessage("error", "banned_word"))
                    return
                }

                val existingTeam = db.getTeamByUUID(uuid.toString())
                if (existingTeam != null) {
                    stack.sender.sendMessage(mH.getMessage("error", "already_in_team"))
                    return
                }

                val description: String = args.slice(2 until args.size).joinToString(" ")
                val cleanName = TextSanitizer.stripFormatting(plainName).replace("_", " ")
                db.addTeam(cleanName, uuid.toString(), description)
                stack.sender.sendMessage(mH.getMessage("team", "created", mapOf("team_name" to cleanName)))
                plugin.logger.debug("Player ${stack.sender.name} created a team:")
                plugin.logger.debug("Name: $name | Clean name: $cleanName")
                plugin.logger.debug("Description: $description")
                plugin.teamCacheManager.loadPlayerTeamData(uuid)

            }
            args[0].equals("rename", ignoreCase = true) -> {
                if (!(stack.sender.hasPermission("teamx.cmd.team.premium") || stack.sender.hasPermission("teamx.cmd.team.rename"))) {
                    stack.sender.sendMessage(mH.getMessage("error", "no_permission"))
                    return
                }
                if (args.size < 2) {
                    stack.sender.sendMessage(mH.getMessage("team", "rename_usage"))
                    return
                }
                val newName: String = args[1]
                val teamData = db.getTeamByUUID(uuid.toString())
                if (teamData == null) {
                    stack.sender.sendMessage(mH.getMessage("error", "no_have_team"))
                    return
                }
                val teamId = teamData[0].toInt()
                if (db.isTeamNameExists(newName)) {
                    stack.sender.sendMessage(mH.getMessage("team", "rename_exists"))
                    return
                }
                if (db.updateTeamName(teamId, TextSanitizer.stripFormatting(newName))) {
                    stack.sender.sendMessage(mH.getMessage("team", "renamed", mapOf("team_name" to newName)))
                    plugin.logger.debug("Team ID $teamId has been renamed to $newName")
                } else {
                    stack.sender.sendMessage(mH.getMessage("team", "rename_failed"))
                }
                plugin.teamCacheManager.loadPlayerTeamData(uuid)
            }
            args[0].equals("description", ignoreCase = true) -> {
                if (!(stack.sender.hasPermission("teamx.cmd.team.premium") || stack.sender.hasPermission("teamx.cmd.team.description"))) {
                    stack.sender.sendMessage(mH.getMessage("error", "no_permission"))
                    return
                }
                if (args.size < 2) {
                    stack.sender.sendMessage(mH.getMessage("team", "description_usage"))
                    return
                }
                val description: String = args.slice(1 until args.size).joinToString(" ")
                val teamData = db.getTeamByUUID(uuid.toString())
                if (teamData == null) {
                    stack.sender.sendMessage(mH.getMessage("error", "no_have_team"))
                    return
                }
                val teamId = teamData[0].toInt()
                if (db.updateTeamDescription(teamId, description)) {
                    stack.sender.sendMessage(mH.getMessage("team", "description_updated"))
                    plugin.logger.debug("Team ID $teamId description has been updated to $description")
                } else {
                    stack.sender.sendMessage(mH.getMessage("team", "description_failed"))
                }
                plugin.teamCacheManager.loadPlayerTeamData(uuid)
            }
            args[0].equals("format", ignoreCase = true) -> {
                if (!(stack.sender.hasPermission("teamx.cmd.team.premium") || stack.sender.hasPermission("teamx.cmd.team.format"))) {
                    stack.sender.sendMessage(mH.getMessage("error", "no_permission"))
                    return
                }

                val sender = stack.sender
                if (sender !is Player) {
                    sender.sendRichMessage("Only players can use this command!")
                    return
                }

                val teamData = db.getTeamByUUID(uuid.toString())
                if (teamData == null) {
                    stack.sender.sendMessage(mH.getMessage("error", "no_have_team"))
                    return
                }

                val teamOwnerUUID = teamData[2]
                if (teamOwnerUUID != uuid.toString()) {
                    stack.sender.sendMessage(mH.getMessage("error", "not_team_owner"))
                    return
                }

                InitialSelectionGUI(plugin).open(sender)
            }
            args[0].equals("delete", ignoreCase = true) -> {
                if (!(stack.sender.hasPermission("teamx.cmd.team.premium") || stack.sender.hasPermission("teamx.cmd.team.delete"))) {
                    stack.sender.sendMessage(mH.getMessage("error", "no_permission"))
                    return
                }
                val teamData = db.getTeamByUUID(uuid.toString())
                if (teamData == null) {
                    stack.sender.sendMessage(mH.getMessage("error", "no_have_team"))
                    return
                }
                val teamId = teamData[0].toInt()
                val teamName = teamData[1]
                val teamLeaderUUID = teamData[2]

                if (teamLeaderUUID != uuid.toString()) {
                    stack.sender.sendMessage(mH.getMessage("error", "not_team_owner"))
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
                    stack.sender.sendMessage(mH.getMessage("team", "deleted", mapOf("team_name" to teamName)))
                } else {
                    stack.sender.sendMessage(mH.getMessage("team", "delete_failed", mapOf("team_name" to teamName)))
                }
                plugin.teamCacheManager.loadPlayerTeamData(uuid)
            }
            args[0].equals("kick", ignoreCase = true) -> {
                if (!(stack.sender.hasPermission("teamx.cmd.team.premium") || stack.sender.hasPermission("teamx.cmd.team.kick"))) {
                    stack.sender.sendMessage(mH.getMessage("error", "no_permission"))
                    return
                }

                if (args.size < 2) {
                    stack.sender.sendMessage(mH.getMessage("team", "kick_usage"))
                    return
                }

                val playerName = args[1]
                val playerUUID = plugin.uuidManager.getUUID(playerName)
                val teamData = db.getTeamByUUID(playerUUID.toString())

                if (teamData == null) {
                    stack.sender.sendMessage(mH.getMessage("error", "player_not_in_team", mapOf("player" to playerName)))
                    return
                }

                val teamId = teamData[0].toInt()
                val teamName = teamData[1]
                val teamLeaderUUID = teamData[2]

                if (teamLeaderUUID != uuid.toString()) {
                    stack.sender.sendMessage(mH.getMessage("error", "not_team_owner"))
                    return
                }

                if (db.removePlayerFromTeam(teamId, playerUUID.toString())) {
                    stack.sender.sendMessage(mH.getMessage("team", "player_removed", mapOf("player" to playerName, "team_name" to teamName)))
                    plugin.server.getPlayer(playerUUID)?.sendMessage(mH.getMessage("team", "you_kicked", mapOf("team_name" to teamName)))
                } else {
                    stack.sender.sendMessage(mH.getMessage("team", "remove_failed", mapOf("player" to playerName, "team_name" to teamName)))
                }
                plugin.teamCacheManager.loadPlayerTeamData(uuid)
            }
            args[0].equals("invite", ignoreCase = true) -> {
                if (!(stack.sender.hasPermission("teamx.cmd.team.premium") || stack.sender.hasPermission("teamx.cmd.team.invite"))) {
                    stack.sender.sendMessage(mH.getMessage("error", "no_permission"))
                    return
                }

                if (args.size < 2) {
                    stack.sender.sendMessage(mH.getMessage("invitation", "usage"))
                    return
                }

                val invitedPlayerName = args[1]
                val invitedPlayer = plugin.server.getPlayer(invitedPlayerName)
                if (invitedPlayer == null) {
                    stack.sender.sendMessage(mH.getMessage("error", "player_not_found", mapOf("player" to invitedPlayerName)))
                    return
                }

                val invitedPlayerUUID = plugin.uuidManager.getUUID(invitedPlayerName)
                val existingTeam = db.getTeamByUUID(invitedPlayerUUID.toString())
                if (existingTeam != null) {
                    stack.sender.sendMessage(mH.getMessage("error", "player_already_in_team", mapOf("player" to invitedPlayerName)))
                    return
                }

                val teamData = db.getTeamByUUID(uuid.toString())
                if (teamData == null) {
                    stack.sender.sendMessage(mH.getMessage("error", "no_have_team"))
                    return
                }

                val teamId = teamData[0]
                val leader = stack.sender as Player
                plugin.invitationManager.sendInvitation(teamId, leader, invitedPlayer)
                stack.sender.sendMessage(mH.getMessage("invitation", "sent", mapOf("player" to invitedPlayerName)))
            }

            args[0].equals("accept", ignoreCase = true) -> {
                if (!(stack.sender.hasPermission("teamx.cmd.basic") || stack.sender.hasPermission("teamx.cmd.team.accept"))) {
                    stack.sender.sendMessage(plugin.messageHandler.getMessage("error", "no_permission"))
                    return
                }

                val sender = stack.sender
                if (sender !is Player) {
                    sender.sendMessage(mH.getMessage("error", "console"))
                    return
                }

                val existingTeam = db.getTeamByUUID(uuid.toString())
                if (existingTeam != null) {
                    stack.sender.sendMessage(mH.getMessage("error", "already_in_team"))
                    return
                }

                plugin.invitationManager.acceptInvitation(sender)
                plugin.teamCacheManager.loadPlayerTeamData(uuid)
            }
            args[0].equals("deny", ignoreCase = true) -> {
                if (!(stack.sender.hasPermission("teamx.cmd.basic") || stack.sender.hasPermission("teamx.cmd.team.deny"))) {
                    stack.sender.sendMessage(plugin.messageHandler.getMessage("error", "no_permission"))
                    return
                }

                val sender = stack.sender
                if (sender !is Player) {
                    sender.sendMessage(mH.getMessage("error", "console"))
                    return
                }

                val existingTeam = db.getTeamByUUID(uuid.toString())
                if (existingTeam != null) {
                    stack.sender.sendMessage(mH.getMessage("error", "already_in_team"))
                    return
                }

                plugin.invitationManager.declineInvitation(sender)
            }
            args[0].equals("leave", ignoreCase = true) -> {
                if (!(stack.sender.hasPermission("teamx.cmd.basic") || stack.sender.hasPermission("teamx.cmd.team.leave"))) {
                    stack.sender.sendMessage(mH.getMessage("error", "no_permission"))
                    return
                }

                val sender = stack.sender
                if (sender !is Player) {
                    sender.sendMessage(mH.getMessage("error", "console"))
                    return
                }

                val teamData = db.getTeamByUUID(uuid.toString())
                if (teamData == null) {
                    stack.sender.sendMessage(mH.getMessage("error", "no_have_team"))
                    return
                }

                val teamId = teamData[0].toInt()
                val teamName = teamData[1]
                if (db.removePlayerFromTeam(teamId, uuid.toString())) {
                    stack.sender.sendMessage(mH.getMessage("team", "player_leaved", mapOf("player" to sender.name, "team_name" to teamName)))
                } else {
                    stack.sender.sendMessage(mH.getMessage("team", "leave_failed", mapOf("player" to sender.name, "team_name" to teamName)))
                }
                plugin.teamCacheManager.loadPlayerTeamData(uuid)
            }
            args[0].equals("admin", ignoreCase = true) -> {
                if (!stack.sender.hasPermission("teamx.cmd.team.admin")) {
                    stack.sender.sendMessage(mH.getMessage("error", "no_permission"))
                    return
                }
                if (args.size < 3) {
                    stack.sender.sendMessage(mH.getMessage("team", "admin_usage"))
                    return
                }
                val action = args[1]
                val teamName = args[2]
                val teamData = db.getTeamByName(teamName)
                if (teamData == null) {
                    stack.sender.sendMessage(mH.getMessage("error", "not_found", mapOf("team_name" to teamName)))
                    return
                }
                val teamId = teamData[0].toInt()

                when (action.lowercase()) {
                    "adduser" -> {
                        if (args.size < 4) {
                            stack.sender.sendMessage(mH.getMessage("team", "admin_usage"))
                            return
                        }
                        val playerName = args[3]
                        val playerUUID = plugin.uuidManager.getUUID(playerName)
                        if (db.addPlayerToTeam(teamId, playerUUID.toString())) {
                            stack.sender.sendMessage(mH.getMessage("team", "player_added", mapOf("player" to playerName, "team_name" to teamName)))
                        } else {
                            stack.sender.sendMessage(mH.getMessage("team", "add_failed", mapOf("player" to playerName, "team_name" to teamName)))
                        }
                        plugin.teamCacheManager.refreshCacheForOnlinePlayers()
                    }
                    "removeuser" -> {
                        if (args.size < 4) {
                            stack.sender.sendMessage(mH.getMessage("team", "admin_usage"))
                            return
                        }
                        val playerName = args[3]
                        val playerUUID = plugin.uuidManager.getUUID(playerName)
                        if (db.removePlayerFromTeam(teamId, playerUUID.toString())) {
                            stack.sender.sendMessage(mH.getMessage("team", "player_removed", mapOf("player" to playerName, "team_name" to teamName)))
                        } else {
                            stack.sender.sendMessage(mH.getMessage("team", "remove_failed", mapOf("player" to playerName, "team_name" to teamName)))
                        }
                        plugin.teamCacheManager.refreshCacheForOnlinePlayers()
                    }
                    "setleader" -> {
                        if (args.size < 4) {
                            stack.sender.sendMessage(mH.getMessage("team", "admin_usage"))
                            return
                        }
                        val playerName = args[3]
                        val playerUUID = plugin.uuidManager.getUUID(playerName)
                        if (db.setTeamLeader(teamId, playerUUID.toString())) {

                            stack.sender.sendMessage(mH.getMessage("team", "leader_set", mapOf("player" to playerName, "team_name" to teamName)))
                        } else {
                            stack.sender.sendMessage(mH.getMessage("team", "set_leader_failed", mapOf("player" to playerName, "team_name" to teamName)))
                        }
                        plugin.teamCacheManager.refreshCacheForOnlinePlayers()
                    }
                    "rename" -> {
                        if (args.size < 4) {
                            stack.sender.sendMessage(mH.getMessage("team", "admin_usage"))
                            return
                        }
                        val newName = args[3]
                        if (db.isTeamNameExists(newName)) {
                            stack.sender.sendMessage(mH.getMessage("team", "rename_exists"))
                            return
                        }
                        if (db.updateTeamName(teamId, TextSanitizer.stripFormatting(newName))) {
                            stack.sender.sendMessage(mH.getMessage("team", "renamed", mapOf("team_name" to newName)))
                            plugin.logger.debug("Team ID $teamId has been renamed to $newName")
                        } else {
                            stack.sender.sendMessage(mH.getMessage("team", "rename_failed"))
                        }
                        plugin.teamCacheManager.refreshCacheForOnlinePlayers()
                    }
                    "delete" -> {
                        val teamMembers = db.getTeamMembers(teamId)
                        var success = true

                        for (memberUuid in teamMembers) {
                            if (!db.removePlayerFromTeam(teamId, memberUuid)) {
                                success = false
                            }
                        }

                        if (success && db.removeTeam(teamId, false)) {
                            stack.sender.sendMessage(mH.getMessage("team", "deleted", mapOf("team_name" to teamName)))
                        } else {
                            stack.sender.sendMessage(mH.getMessage("team", "delete_failed", mapOf("team_name" to teamName)))
                        }
                        plugin.teamCacheManager.refreshCacheForOnlinePlayers()
                    }
                    else -> {
                        stack.sender.sendMessage(mH.getMessage("team", "admin_usage"))
                    }
                }
            }
            args[0].equals("help", ignoreCase = true) -> {
                if (!stack.sender.hasPermission("teamx.cmd.team.help")) {
                    stack.sender.sendMessage(mH.getMessage("error", "no_permission"))
                    return
                }
                val page = args.getOrNull(1)?.toIntOrNull() ?: 1
                sendHelp(stack, page)
            }
        }
    }

    private fun isTextLengthValid(text: String): Boolean {
        plugin.logger.debug("Text length: ${text.length}")
        plugin.logger.debug("Max length: $maxNameLength")
        return text.length <= maxNameLength
    }

    private fun sendHelp(stack: CommandSourceStack, page: Int) {
        val sender = stack.sender
        val isPremium = sender.hasPermission("teamx.cmd.team.premium")
        val isAdmin = sender.hasPermission("teamx.cmd.team.admin")

        val commands = mutableListOf(
            "  <gold>/team create <name> <description> ",
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

    override fun suggest(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>): List<String> {
        if (!stack.sender.hasPermission("teamx.cmd.team")) {
            return emptyList()
        }

        if (args.isEmpty()) {
            val suggestions = mutableListOf<String>()

            // Komendy premium
            if (stack.sender.hasPermission("teamx.cmd.team.premium") ||
                stack.sender.hasPermission("teamx.cmd.team.create")) {
                suggestions.add("create")
            }
            if (stack.sender.hasPermission("teamx.cmd.team.premium") ||
                stack.sender.hasPermission("teamx.cmd.team.rename")) {
                suggestions.add("rename")
            }
            if (stack.sender.hasPermission("teamx.cmd.team.premium") ||
                stack.sender.hasPermission("teamx.cmd.team.description")) {
                suggestions.add("description")
            }
            if (stack.sender.hasPermission("teamx.cmd.team.premium") ||
                stack.sender.hasPermission("teamx.cmd.team.format")) {
                suggestions.add("format")
            }
            if (stack.sender.hasPermission("teamx.cmd.team.premium") ||
                stack.sender.hasPermission("teamx.cmd.team.delete")) {
                suggestions.add("delete")
            }
            if (stack.sender.hasPermission("teamx.cmd.team.premium") ||
                stack.sender.hasPermission("teamx.cmd.team.kick")) {
                suggestions.add("kick")
            }
            if (stack.sender.hasPermission("teamx.cmd.team.premium") ||
                stack.sender.hasPermission("teamx.cmd.team.invite")) {
                suggestions.add("invite")
            }

            if (stack.sender.hasPermission("teamx.cmd.basic") ||
                stack.sender.hasPermission("teamx.cmd.team.accept")) {
                suggestions.add("accept")
            }
            if (stack.sender.hasPermission("teamx.cmd.basic") ||
                stack.sender.hasPermission("teamx.cmd.team.deny")) {
                suggestions.add("deny")
            }
            if (stack.sender.hasPermission("teamx.cmd.basic") ||
                stack.sender.hasPermission("teamx.cmd.team.leave")) {
                suggestions.add("leave")
            }

            if (stack.sender.hasPermission("teamx.cmd.team.admin")) {
                suggestions.add("admin")
            }

            suggestions.add("help")

            return suggestions
        }
        if (args.size == 1) {
            val suggestions = mutableListOf<String>()
            val arg = args[0].lowercase()

            if ((stack.sender.hasPermission("teamx.cmd.team.premium") ||
                        stack.sender.hasPermission("teamx.cmd.team.create")) && "create".startsWith(arg)) {
                suggestions.add("create")
            }
            if ((stack.sender.hasPermission("teamx.cmd.team.premium") ||
                        stack.sender.hasPermission("teamx.cmd.team.rename")) && "rename".startsWith(arg)) {
                suggestions.add("rename")
            }
            if ((stack.sender.hasPermission("teamx.cmd.team.premium") ||
                        stack.sender.hasPermission("teamx.cmd.team.description")) && "description".startsWith(arg)) {
                suggestions.add("description")
            }
            if ((stack.sender.hasPermission("teamx.cmd.team.premium") ||
                        stack.sender.hasPermission("teamx.cmd.team.format")) && "format".startsWith(arg)) {
                suggestions.add("format")
            }
            if ((stack.sender.hasPermission("teamx.cmd.team.premium") ||
                        stack.sender.hasPermission("teamx.cmd.team.delete")) && "delete".startsWith(arg)) {
                suggestions.add("delete")
            }
            if ((stack.sender.hasPermission("teamx.cmd.team.premium") ||
                        stack.sender.hasPermission("teamx.cmd.team.kick")) && "kick".startsWith(arg)) {
                suggestions.add("kick")
            }
            if ((stack.sender.hasPermission("teamx.cmd.team.premium") ||
                        stack.sender.hasPermission("teamx.cmd.team.invite")) && "invite".startsWith(arg)) {
                suggestions.add("invite")
            }
            if ((stack.sender.hasPermission("teamx.cmd.basic") ||
                        stack.sender.hasPermission("teamx.cmd.team.accept")) && "accept".startsWith(arg)) {
                suggestions.add("accept")
            }
            if ((stack.sender.hasPermission("teamx.cmd.basic") ||
                        stack.sender.hasPermission("teamx.cmd.team.deny")) && "deny".startsWith(arg)) {
                suggestions.add("deny")
            }
            if ((stack.sender.hasPermission("teamx.cmd.basic") ||
                        stack.sender.hasPermission("teamx.cmd.team.leave")) && "leave".startsWith(arg)) {
                suggestions.add("leave")
            }
            if (stack.sender.hasPermission("teamx.cmd.team.admin") && "admin".startsWith(arg)) {
                suggestions.add("admin")
            }
            if ("help".startsWith(arg)) {
                suggestions.add("help")
            }

            return suggestions
        }

        if (args.size == 2) {
            when (args[0].lowercase()) {
                "kick", "invite" -> {
                    return plugin.server.onlinePlayers.map { it.name }
                        .filter { it.startsWith(args[1], ignoreCase = true) }
                }
            }
        }

        if (args[0].equals("admin", ignoreCase = true)) {
            if (args.size == 2) {
                val adminSubCommands = listOf("adduser", "removeuser", "addmanager", "removemanager", "setleader", "rename", "delete")
                return adminSubCommands.filter { it.startsWith(args[1].lowercase()) }
            }
            if (args.size == 3) {
                return plugin.databaseHandler.getTeamList()
                    .filter { it.startsWith(args[2], ignoreCase = true) }
            }
            if (args.size == 4) {
                val subCommand = args[1].lowercase()
                if (subCommand in listOf("adduser", "removeuser", "addmanager", "removemanager", "setleader")) {
                    return plugin.server.onlinePlayers.map { it.name }
                        .filter { it.startsWith(args[3], ignoreCase = true) }
                }
                return emptyList()
            }
        }

        return emptyList()
    }
}