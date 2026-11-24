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
import pl.syntaxdevteam.teamx.clans.TextSanitizer
import net.kyori.adventure.text.event.ClickEvent
import pl.syntaxdevteam.teamx.clans.ClanRank
import pl.syntaxdevteam.teamx.clans.Specialization
import pl.syntaxdevteam.teamx.databases.ClanSchema
import pl.syntaxdevteam.teamx.hooks.IdentityPayment
import pl.syntaxdevteam.teamx.hooks.CleanerXHook
import pl.syntaxdevteam.teamx.hooks.PlotsXHook
import pl.syntaxdevteam.teamx.messages.teamMessage
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class TeamCommand(private val plugin: TeamX) {
    private val mH get() = plugin.messageHandler
    private val db get() = plugin.databaseHandler
    // Accessed only by the clan database worker.
    private data class FeeConfirmation(
        val clanId: Int, val operation: String, val value: String, val amount: Double, val expires: Long
    )

    private val feeConfirmations = mutableMapOf<UUID, FeeConfirmation>()
    private val creatorPermission = "teamx.cmd.team.creator"
    private val maxNameLength get() = plugin.config.getInt("team.max-name-length", 16)

    fun build(): LiteralCommandNode<CommandSourceStack> = Commands.literal("team")
        .requires { plugin.isEnabled && it.sender.hasPermission("teamx.cmd.team") }
        .executes { if (it.source.sender is Player) runAsPlayer(it, plugin.menus::open) else showHelp(it, 1) }
        .then(helpCommand())
        .then(plotsCommand())
        .then(createCommand())
        .then(identityCommand("rename"))
        .then(identityCommand("tag"))
        .then(memberCommand("rank", ClanRank.entries.filter { it != ClanRank.LEADER }.map { it.name.lowercase() }))
        .then(memberCommand("specialization", Specialization.entries.map { it.name.lowercase() } + "none"))
        .then(basic("home").executes { runAsPlayer(it, plugin.clanTeleport::teleport) })
        .then(creator("sethome").executes { runAsPlayer(it) { player -> plugin.clanTeleport.setHome(player, true) } })
        .then(creator("delhome").executes { runAsPlayer(it) { player -> plugin.clanTeleport.setHome(player, false) } })
        .then(basic("ping").executes { runAsPlayer(it, plugin.clanPing::ping) })
        .then(basic("chat").executes { runAsPlayer(it, plugin.clanChat::toggleChat) }
            .then(Commands.argument("message", StringArgumentType.greedyString()).executes { context ->
                runAsPlayer(context) { plugin.clanChat.chat(it, text(context, "message")) }
            }))
        .then(descriptionCommand())
        .then(creator("format").executes { runAsPlayer(it, plugin.menus::openFormat) })
        .then(creator("delete").executes { runDatabase(it, ::deleteTeam) })
        .then(kickCommand())
        .then(inviteCommand())
        .then(basic("accept").executes { runDatabase(it, ::acceptInvitation) })
        .then(basic("deny").executes { runDatabase(it, ::declineInvitation) })
        .then(basic("leave").executes { runDatabase(it, ::leaveTeam) })
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

    private fun createCommand(): LiteralArgumentBuilder<CommandSourceStack> = creator("create")
        .executes { usage(it, "team", "create_usage") }
        .then(
            Commands.argument("name", StringArgumentType.string())
                .executes { context -> runDatabase(context) { createTeam(it, text(context, "name"), "") } }
                .then(
                    Commands.argument("description", StringArgumentType.greedyString()).executes { context ->
                        runDatabase(context) { player ->
                            createTeam(player, text(context, "name"), text(context, "description"))
                        }
                    }
                )
        )

    private fun identityCommand(name: String): LiteralArgumentBuilder<CommandSourceStack> = creator(name)
        .executes { usage(it, if (name == "rename") "team" else "clan", "${name}_usage") }
        .then(Commands.argument("value", StringArgumentType.string())
            .executes { identity(it, name, false) }
            .then(Commands.literal("confirm").executes { identity(it, name, true) }))

    private fun identity(context: CommandContext<CommandSourceStack>, operation: String, confirmed: Boolean): Int =
        runAsPlayer(context) { player ->
            val bypass = player.hasPermission("teamx.fees.bypass") || player.hasPermission("teamx.cmd.team.admin")
            val input = text(context, "value")
            val path = "team.fees.$operation"
            val fee = if (bypass) 0.0 else plugin.config.getDouble(path)
            plugin.operations.submit(player) {
                val team = plugin.operations.managedClan(player) ?: return@submit
                val value = if (operation == "tag") ClanSchema.normalizeTag(input) else validateTeamName(player, input)
                if (value == null) {
                    if (operation == "tag") tell(player, "tag_usage")
                    return@submit
                }
                if (!fee.isFinite() || fee < 0) return@submit tell(player, "failed")
                val id = team[0].toInt()
                if (fee > 0) {
                    val now = System.nanoTime()
                    feeConfirmations.entries.removeIf { now - it.value.expires >= 0 }
                    val pending = feeConfirmations.remove(player.uniqueId)
                    val expected = FeeConfirmation(id, operation, value, fee, now + TimeUnit.SECONDS.toNanos(30))
                    if (!confirmed || pending?.copy(expires = expected.expires) != expected) {
                        feeConfirmations[player.uniqueId] = expected
                        val command = "/team $operation ${StringArgumentType.escapeIfRequired(value)} confirm"
                        player.sendMessage(mH.teamMessage("clan", "confirm_fee", mapOf("amount" to fee.toString()))
                            .clickEvent(ClickEvent.runCommand(command)))
                        return@submit
                    }
                }
                val payment = if (fee > 0) IdentityPayment(plugin, player, fee) else null
                var attempted = false
                val charge: (() -> Boolean)? = payment?.let {
                    { attempted = true; it.withdraw() }
                }
                val updated = try {
                    if (operation == "tag") db.updateTeamTag(id, value, charge)
                    else db.updateTeamName(id, value, charge)
                } catch (failure: Exception) {
                    payment?.refund()
                    throw failure
                }
                if (!updated) {
                    payment?.refund()
                    tell(player, if (attempted) payment?.error ?: "failed" else "identity_failed")
                    return@submit
                }
                plugin.teamCacheManager.refreshCacheForOnlinePlayers()
                player.sendMessage(mH.teamMessage("clan", "identity_updated", mapOf("value" to value)))
            }
        }

    private fun memberCommand(name: String, options: List<String>) = creator(name)
        .executes { usage(it, "clan", "${name}_usage") }
        .then(playerArgument().then(Commands.argument("value", StringArgumentType.word())
            .suggests { _, builder -> suggestNames(builder, options) }
            .executes { context ->
                runDatabase(context) { player ->
                    val team = plugin.operations.managedClan(player) ?: return@runDatabase
                    val option = text(context, "value").lowercase()
                    if (option !in options) return@runDatabase tell(player, "${name}_usage")
                    val target = resolvePlayer(player, text(context, "player")) ?: return@runDatabase
                    val id = team[0].toInt()
                    val changed = if (name == "rank") db.setRank(id, target, ClanRank.valueOf(option.uppercase()))
                    else db.setSpecialization(id, target, option.takeUnless { it == "none" }?.uppercase()?.let(Specialization::valueOf))
                    if (changed) plugin.teamCacheManager.refreshCacheForOnlinePlayers()
                    tell(player, if (changed) "member_updated" else "failed")
                }
            }))

    private fun tell(sender: CommandSender, key: String) = sender.sendMessage(mH.teamMessage("clan", key))

    private fun resolvePlayer(sender: CommandSender, name: String): UUID? {
        val uuid = runCatching { UUID.fromString(name) }.getOrNull()
            ?: plugin.server.getPlayerExact(name)?.uniqueId
            ?: plugin.server.getOfflinePlayerIfCached(name)?.uniqueId
            ?: plugin.uuidManager.getUUID(name).takeIf { plugin.server.getOfflinePlayer(it).hasPlayedBefore() }
        if (uuid == null) sender.sendMessage(mH.teamMessage("error", "player_not_found", mapOf("player" to name)))
        return uuid
    }

    private fun descriptionCommand(): LiteralArgumentBuilder<CommandSourceStack> = creator("description")
        .executes { usage(it, "team", "description_usage") }
        .then(
            Commands.argument("description", StringArgumentType.greedyString()).executes { context ->
                runDatabase(context) { updateDescription(it, text(context, "description")) }
            }
        )

    private fun kickCommand(): LiteralArgumentBuilder<CommandSourceStack> = creator("kick")
        .executes { usage(it, "team", "kick_usage") }
        .then(
            playerArgument().executes { context ->
                runDatabase(context) { kickPlayer(it, text(context, "player")) }
            }
        )

    private fun inviteCommand(): LiteralArgumentBuilder<CommandSourceStack> = creator("invite")
        .executes { usage(it, "invitation", "usage") }
        .then(
            playerArgument().executes { context ->
                runDatabase(context) { invitePlayer(it, text(context, "player")) }
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
        .then(Commands.literal("tag")
            .executes(::adminUsage)
            .then(teamArgument().then(Commands.argument("tag", StringArgumentType.word()).executes { context ->
                withTeam(context) { sender, id, _ ->
                    val tag = ClanSchema.normalizeTag(text(context, "tag"))
                    if (tag == null) tell(sender, "tag_usage")
                    else if (!db.updateTeamTag(id, tag)) tell(sender, "identity_failed")
                    else {
                        plugin.teamCacheManager.refreshCacheForOnlinePlayers()
                        sender.sendMessage(mH.teamMessage("clan", "identity_updated", mapOf("value" to tag)))
                    }
                }
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

    private fun creator(name: String) = permitted(name, creatorPermission)

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
            CompletableFuture.supplyAsync { suggestNames(builder, db.getTeamList()).join() }
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
        plugin.operations.submit(sender) {
            val team = db.getTeamByName(teamName)
            if (team == null) {
                sender.sendMessage(mH.teamMessage("error", "not_found", mapOf("team_name" to teamName)))
            } else {
                action(sender, team[0].toInt(), teamName)
            }
        }
        return 1
    }

    private fun runDatabase(context: CommandContext<CommandSourceStack>, action: (Player) -> Unit): Int =
        runAsPlayer(context) { player -> plugin.operations.submit(player) { action(player) } }

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
        plugin.teamCacheManager.refreshCacheForOnlinePlayers()
    }

    private fun updateDescription(sender: Player, description: String) {
        val teamData = plugin.operations.managedClan(sender, manager = true) ?: return
        val teamId = teamData[0].toInt()
        if (db.updateTeamDescription(teamId, description)) {
            sender.sendMessage(mH.teamMessage("team", "description_updated"))
            plugin.logger.debug("Team ID $teamId description has been updated to $description")
        } else {
            sender.sendMessage(mH.teamMessage("team", "description_failed"))
        }
        plugin.teamCacheManager.refreshCacheForOnlinePlayers()
    }

    private fun deleteTeam(sender: Player) {
        val team = plugin.operations.managedClan(sender) ?: return
        adminDeleteTeam(sender, team[0].toInt(), team[1])
    }

    private fun kickPlayer(sender: Player, playerName: String) {
        val team = plugin.operations.managedClan(sender, manager = true) ?: return
        val target = resolvePlayer(sender, playerName) ?: return
        val id = team[0].toInt()
        val members = db.members(id)
        val member = members.find { it.uuid == target }
        if (target == sender.uniqueId || member == null || member.rank == ClanRank.LEADER ||
            (team[2] != sender.uniqueId.toString() && member.rank == ClanRank.MANAGER)
        ) return tell(sender, "failed")
        if (!db.removePlayerFromTeam(id, target.toString())) return tell(sender, "failed")
        plugin.teamCacheManager.refreshCacheForOnlinePlayers()
        sender.sendMessage(mH.teamMessage("team", "player_removed", mapOf("player" to playerName, "team_name" to team[1])))
        plugin.server.getPlayer(target)?.sendMessage(mH.teamMessage("team", "you_kicked", mapOf("team_name" to team[1])))
    }

    private fun invitePlayer(sender: Player, invitedPlayerName: String) {
        val teamData = plugin.operations.managedClan(sender, manager = true) ?: return
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
        plugin.teamCacheManager.refreshCacheForOnlinePlayers()
    }

    private fun adminAddPlayer(sender: CommandSender, teamId: Int, teamName: String, playerName: String) {
        val playerUUID = resolvePlayer(sender, playerName) ?: return
        if (db.addPlayerToTeam(teamId, playerUUID.toString())) {
            sender.sendMessage(mH.teamMessage("team", "player_added", mapOf("player" to playerName, "team_name" to teamName)))
        } else {
            sender.sendMessage(mH.teamMessage("team", "add_failed", mapOf("player" to playerName, "team_name" to teamName)))
        }
        plugin.teamCacheManager.refreshCacheForOnlinePlayers()
    }

    private fun adminRemovePlayer(sender: CommandSender, teamId: Int, teamName: String, playerName: String) {
        val playerUUID = resolvePlayer(sender, playerName) ?: return
        if (db.removePlayerFromTeam(teamId, playerUUID.toString())) {
            sender.sendMessage(mH.teamMessage("team", "player_removed", mapOf("player" to playerName, "team_name" to teamName)))
        } else {
            sender.sendMessage(mH.teamMessage("team", "remove_failed", mapOf("player" to playerName, "team_name" to teamName)))
        }
        plugin.teamCacheManager.refreshCacheForOnlinePlayers()
    }

    private fun adminSetLeader(sender: CommandSender, teamId: Int, teamName: String, playerName: String) {
        val playerUUID = resolvePlayer(sender, playerName) ?: return
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
        if (db.removeTeam(teamId)) {
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
        val hasBasic = sender.hasPermission("teamx.cmd.basic")
        val isAdmin = sender.hasPermission("teamx.cmd.team.admin")

        val commands = mutableListOf(
            "  <gold>/team <gray>- <white>Open the clan panel.",
            "  <gold>/team plots [page]",
            "  <gold>/team create <name> [description]",
            "  <gold>/team rename <name> [confirm]",
            "  <gold>/team tag <tag> [confirm]",
            "  <gold>/team description <text>",
            "  <gold>/team format",
            "  <gold>/team delete",
            "  <gold>/team invite <player>",
            "  <gold>/team kick <player>",
            "  <gold>/team rank <player> <manager|elite|member>",
            "  <gold>/team specialization <player> <assassin|builder|miner|alchemist|none>",
            "  <gold>/team sethome",
            "  <gold>/team delhome"
        )
        if (hasBasic || listOf("home", "chat", "ping", "accept", "deny", "leave")
                .any { sender.hasPermission("teamx.cmd.team.$it") }) {
            commands.addAll(listOf(
                "  <gold>/team home",
                "  <gold>/team chat [message]",
                "  <gold>/team ping",
                "  <gold>/team accept",
                "  <gold>/team deny",
                "  <gold>/team leave"
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
                "  <gold>/team admin tag <team_name> <tag>",
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
