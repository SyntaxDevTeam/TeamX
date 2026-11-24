package pl.syntaxdevteam.teamx.gui

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.BaseGui
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import dev.triumphteam.gui.guis.PaginatedGui
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import pl.syntaxdevteam.teamx.TeamX
import pl.syntaxdevteam.teamx.clans.ClanMember
import pl.syntaxdevteam.teamx.clans.ClanRank
import pl.syntaxdevteam.teamx.clans.Specialization
import pl.syntaxdevteam.teamx.cache.CachedTeamData
import pl.syntaxdevteam.teamx.messages.teamMessage
import java.util.Locale

class ClanMenus(private val plugin: TeamX) {
    private val mH get() = plugin.messageHandler

    fun open(player: Player) {
        val clan = plugin.teamCacheManager.get(player.uniqueId)
        val gui = menu("panel")
        if (clan == null) {
            button(gui, "create", 10, Material.WRITABLE_BOOK) { suggest(player, "/team create ") }
            button(gui, "invitations", 12, Material.PAPER) { invitations(player) }
            button(gui, "list", 14, Material.BOOK) { list(player) }
        } else {
            val count = clan.roster.count { plugin.server.getPlayer(it.uuid) != null }
            val home = clan.home
            val location = home?.let {
                "${plugin.server.getWorld(it.world)?.name ?: it.world} · ${Location.locToBlock(it.x)} ${Location.locToBlock(it.y)} ${Location.locToBlock(it.z)}"
            }
            button(gui, "overview", 4, Material.WHITE_BANNER, title = Component.text("[${clan.tag}] ${clan.name}"),
                lore = listOf(Component.text(clan.description), text("founder_info", mapOf("player" to clan.leaderName)),
                    text("online", mapOf("count" to "$count/${clan.roster.size}")),
                    location?.let { text("location", mapOf("location" to it)) } ?: text("no_home")))
            button(gui, "members", 10, Material.PLAYER_HEAD) { members(player, clan) }
            button(gui, "settings", 12, Material.COMPARATOR) { settings(player, clan) }
            button(gui, "home", 14, Material.ENDER_PEARL) { command(player, "home") }
            button(gui, "ping", 16, Material.COMPASS) { command(player, "ping") }
            button(gui, "chat", 22, Material.FEATHER) { command(player, "chat") }
        }
        gui.open(player)
    }

    private fun invitations(player: Player) {
        val gui = menu("invitations")
        val invitation = plugin.invitationManager.current(player.uniqueId)
        if (invitation == null) button(gui, "no_invitation", 13, Material.PAPER)
        else {
            button(gui, "accept", 11, Material.LIME_WOOL) { command(player, "accept", ::open) }
            button(gui, "deny", 15, Material.RED_WOOL) { command(player, "deny", ::invitations) }
        }
        back(gui, 22) { open(player) }
        gui.open(player)
    }

    private fun list(player: Player) {
        plugin.operations.submit(player) {
            val clans = plugin.databaseHandler.clanList()
            plugin.operations.onPlayer(player) {
                val gui = paginated("list") { open(player) }
                clans.forEach { (name, tag) -> gui.addItem(item(Material.BOOK, Component.text("[$tag] $name"))) }
                gui.open(player)
            }
        }
    }

    private fun members(player: Player, clan: CachedTeamData) {
        if (plugin.teamCacheManager.get(player.uniqueId)?.teamId != clan.teamId) return open(player)
        val gui = paginated("members") { open(player) }
        val rank = clan.roster.find { it.uuid == player.uniqueId }?.rank
        if (rank == ClanRank.LEADER || rank == ClanRank.MANAGER) {
            button(gui, "invite", 4, Material.WRITABLE_BOOK) { suggest(player, "/team invite ") }
        }
        for (member in clan.roster) {
            val name = plugin.server.getOfflinePlayer(member.uuid).name ?: member.uuid.toString()
            gui.addItem(item(Material.PLAYER_HEAD, Component.text(name),
                listOf(text(member.rank.name.lowercase()), text(member.specialization?.name?.lowercase() ?: "none"))) {
                memberSettings(player, clan.teamId, member, name)
            })
        }
        gui.open(player)
    }

    private fun memberSettings(player: Player, teamId: Int, member: ClanMember, name: String) {
        val clan = plugin.teamCacheManager.get(player.uniqueId)?.takeIf { it.teamId == teamId } ?: return open(player)
        val target = clan.roster.find { it.uuid == member.uuid } ?: return members(player, clan)
        val rank = clan.roster.find { it.uuid == player.uniqueId }?.rank
        val gui = menu("members", 4)
        button(gui, "members", 4, Material.PLAYER_HEAD, title = Component.text(name),
            lore = listOf(text(target.rank.name.lowercase()), text(target.specialization?.name?.lowercase() ?: "none")))
        val argument = target.uuid.toString()
        fun reopen(viewer: Player) = memberSettings(viewer, teamId, target, name)
        if (rank == ClanRank.LEADER) {
            if (target.rank != ClanRank.LEADER) {
                listOf(ClanRank.MANAGER, ClanRank.ELITE, ClanRank.MEMBER).forEachIndexed { index, choice ->
                    button(gui, choice.name.lowercase(), 11 + index, Material.IRON_INGOT) {
                        command(player, "rank $argument ${choice.name.lowercase()}", ::reopen)
                    }
                }
            }
            (Specialization.entries.map { it.name.lowercase() } + "none").forEachIndexed { index, choice ->
                button(gui, choice, 20 + index, Material.BOOK) {
                    command(player, "specialization $argument $choice", ::reopen)
                }
            }
        }
        if (member.uuid != player.uniqueId && target.rank != ClanRank.LEADER &&
            (rank == ClanRank.LEADER || rank == ClanRank.MANAGER && target.rank != ClanRank.MANAGER)
        ) button(gui, "kick", 15, Material.BARRIER) {
            confirm(player, "kick", { reopen(player) }) {
                command(player, "kick $argument") { viewer -> refreshMembers(viewer) }
            }
        }
        back(gui, 31) { refreshMembers(player) }
        gui.open(player)
    }

    private fun refreshMembers(player: Player) {
        val clan = plugin.teamCacheManager.get(player.uniqueId)
        if (clan == null) open(player) else members(player, clan)
    }

    private fun settings(player: Player, clan: CachedTeamData) {
        val gui = menu("settings", 4)
        val rank = clan.roster.find { it.uuid == player.uniqueId }?.rank
        if (rank == ClanRank.LEADER) {
            button(gui, "rename", 10, Material.NAME_TAG, lore = feeLore(player, "rename")) { suggest(player, "/team rename ") }
            button(gui, "tag", 12, Material.PAPER, lore = feeLore(player, "tag")) { suggest(player, "/team tag ") }
            button(gui, "format", 14, Material.GLOW_INK_SAC) { command(player, "format") }
            button(gui, "delete", 16, Material.LAVA_BUCKET) {
                confirm(player, "delete", { settings(player, clan) }) {
                    if (plugin.teamCacheManager.get(player.uniqueId)?.teamId == clan.teamId) {
                        command(player, "delete", ::open)
                    } else open(player)
                }
            }
        }
        if (rank == ClanRank.LEADER || rank == ClanRank.MANAGER) {
            button(gui, "description", 19, Material.WRITABLE_BOOK) { suggest(player, "/team description ") }
            button(gui, "sethome", 21, Material.LODESTONE) { command(player, "sethome", ::open) }
            button(gui, "delhome", 23, Material.BARRIER) {
                confirm(player, "delhome", { settings(player, clan) }) { command(player, "delhome", ::open) }
            }
        }
        if (rank != ClanRank.LEADER) {
            button(gui, "leave", 16, Material.BARRIER) {
                confirm(player, "leave", { settings(player, clan) }) { command(player, "leave", ::open) }
            }
        }
        back(gui, 31) { open(player) }
        gui.open(player)
    }

    private fun feeLore(player: Player, operation: String): List<Component> {
        if (player.hasPermission("teamx.fees.bypass") || player.hasPermission("teamx.cmd.team.admin")) return emptyList()
        val amount = plugin.config.getDouble("team.fees.$operation")
        return if (amount > 0) listOf(text("fee", mapOf("amount" to amount.toString()))) else emptyList()
    }

    private fun confirm(player: Player, action: String, back: () -> Unit, confirm: () -> Unit) {
        val gui = menu("confirm")
        button(gui, action, 11, Material.LIME_WOOL) { confirm() }
        back(gui, 15, back)
        gui.open(player)
    }

    fun openFormat(player: Player) {
        val clan = plugin.teamCacheManager.get(player.uniqueId)
        if (clan == null || clan.leader != player.uniqueId) {
            player.sendMessage(mH.teamMessage("team", "not_team_owner"))
            return
        }
        formatOptions(player, clan, clan.format)
    }

    private fun formatOptions(player: Player, clan: CachedTeamData, draft: String) {
        val gui = legacyMenu("title_initialGUI")
        legacyButton(gui, "colors", 11, Material.PURPLE_WOOL) { colors(player, clan, draft) }
        legacyButton(gui, "format", 15, Material.GLOW_INK_SAC) { decorations(player, clan, draft) }
        legacyButton(gui, "back", 50, Material.REDSTONE_BLOCK) { settings(player, clan) }
        gui.open(player)
    }

    private fun colors(player: Player, clan: CachedTeamData, draft: String) {
        val gui = legacyMenu("title_colorGUI")
        val useMiniMessage = plugin.config.getBoolean("GUI.UseMiniMessage")
        val materialSuffix = plugin.config.getString("GUI.item_colors.material", "WOOL").orEmpty().uppercase(Locale.ROOT)
        colors.forEachIndexed { index, (color, legacy, mini) ->
            val material = Material.matchMaterial("${color}_$materialSuffix") ?: Material.matchMaterial("${color}_WOOL") ?: Material.WHITE_WOOL
            val name = mH.miniMessageFormat("<$mini>${color.lowercase().replace('_', ' ')}")
            gui.setItem(index, item(material, name) {
                val format = if (useMiniMessage) "<$mini>" else "&$legacy"
                decorations(player, clan, format)
            })
        }
        legacyButton(gui, "back", 50, Material.REDSTONE_BLOCK) { formatOptions(player, clan, draft) }
        gui.open(player)
    }

    private fun decorations(player: Player, clan: CachedTeamData, draft: String) {
        val gui = legacyMenu("title_formatGUI")
        val useMiniMessage = plugin.config.getBoolean("GUI.UseMiniMessage")
        fun toggle(mini: String, legacy: String): String {
            val code = if (useMiniMessage) "<$mini>" else "&$legacy"
            return if (code in draft) draft.replace(code, "") else draft + code
        }
        val preview = mH.formatMixedTextToMiniMessage(draft + MiniMessage.miniMessage().escapeTags(clan.name), TagResolver.empty())
        gui.setItem(4, item(Material.NAME_TAG, preview))
        legacyButton(gui, "item_formats.boldItem", 11, Material.PAPER) { decorations(player, clan, toggle("bold", "l")) }
        legacyButton(gui, "item_formats.italicItem", 13, Material.FEATHER) { decorations(player, clan, toggle("italic", "o")) }
        legacyButton(gui, "item_formats.underlineItem", 15, Material.STRING) { decorations(player, clan, toggle("underlined", "n")) }
        legacyButton(gui, "item_formats.strikethroughItem", 29, Material.BARRIER) {
            decorations(player, clan, toggle("strikethrough", "m"))
        }
        legacyButton(gui, "item_formats.clearFormatItem", 31, Material.MILK_BUCKET) { decorations(player, clan, "") }
        legacyButton(gui, "save", 48, Material.EMERALD_BLOCK) { saveFormat(player, clan.teamId, draft) }
        legacyButton(gui, "back", 50, Material.REDSTONE_BLOCK) { formatOptions(player, clan, draft) }
        gui.open(player)
    }

    private fun saveFormat(player: Player, teamId: Int, draft: String) {
        if (!player.hasPermission("teamx.cmd.team") ||
            !(player.hasPermission("teamx.cmd.team.creator") || player.hasPermission("teamx.cmd.team.format"))
        ) return plugin.clanMessages.tell(player, "failed")
        plugin.operations.submit(player) {
            val current = plugin.databaseHandler.getTeamByUUID(player.uniqueId.toString())
            val saved = current?.get(0)?.toInt() == teamId && current[2] == player.uniqueId.toString() &&
                plugin.databaseHandler.updateTeamFormat(teamId, draft)
            if (saved) plugin.teamCacheManager.refreshCacheForOnlinePlayers()
            if (saved) player.sendMessage(mH.teamMessage("GUI", "saved")) else plugin.clanMessages.tell(player, "failed")
            plugin.operations.onPlayer(player) { open(player) }
        }
    }

    private fun menu(key: String, rows: Int = 3) = Gui.gui().title(text(key)).rows(rows).disableAllInteractions().create()

    private fun legacyMenu(key: String) = Gui.gui().title(mH.stringMessageToComponentNoPrefix("GUI", key))
        .rows(6).disableAllInteractions().create()

    private fun paginated(key: String, back: () -> Unit): PaginatedGui {
        val gui = Gui.paginated().title(text(key)).rows(6).pageSize(if (key == "members") 36 else 45)
            .disableAllInteractions().create()
        button(gui, "previous", 45, Material.ARROW) { gui.previous() }
        button(gui, "next", 53, Material.ARROW) { gui.next() }
        back(gui, 49, back)
        return gui
    }

    private fun back(gui: BaseGui, slot: Int, action: () -> Unit) =
        button(gui, "back", slot, Material.REDSTONE_BLOCK, action = action)

    private fun button(gui: BaseGui, key: String, slot: Int, material: Material, title: Component = text(key),
                       lore: List<Component> = emptyList(), action: (() -> Unit)? = null) {
        val path = "GUI.panel.$key"
        val configuredSlot = if (gui is PaginatedGui) slot else plugin.config.getInt("$path.index", slot)
        gui.setItem(configuredSlot.coerceIn(0, gui.rows * 9 - 1),
            item(configMaterial(path, material), title, lore, action))
    }

    private fun legacyButton(gui: Gui, key: String, slot: Int, material: Material, action: () -> Unit) {
        val path = "GUI.$key"
        val title = plugin.config.getString("$path.title")?.let { mH.formatMixedTextToMiniMessage(it, TagResolver.empty()) }
            ?: text(key.substringAfterLast('.'))
        gui.setItem(plugin.config.getInt("$path.index", slot).coerceIn(0, 53),
            item(configMaterial(path, material), title, action = action))
    }

    private fun configMaterial(path: String, fallback: Material) =
        plugin.config.getString("$path.material")?.let(Material::matchMaterial)?.takeIf { it.isItem && !it.isAir }
            ?: fallback

    private fun item(material: Material, title: Component, lore: List<Component> = emptyList(),
                     action: (() -> Unit)? = null): GuiItem {
        val stack = ItemStack(material)
        val meta = stack.itemMeta
        meta.displayName(title)
        if (lore.isNotEmpty()) meta.lore(lore)
        stack.itemMeta = meta
        return ItemBuilder.from(stack).asGuiItem { event ->
            val player = event.whoClicked as? Player ?: return@asGuiItem
            if (action == null || !event.isLeftClick) return@asGuiItem
            val view = event.view.topInventory
            plugin.operations.onPlayer(player) {
                if (player.openInventory.topInventory == view) action()
            }
        }
    }

    private fun text(key: String, values: Map<String, String> = emptyMap()): Component =
        if (values.isEmpty()) mH.stringMessageToComponentNoPrefix("panel", key)
        else mH.teamMessage("panel", key, values)

    private fun suggest(player: Player, command: String) {
        player.closeInventory()
        player.sendMessage(mH.teamMessage("clan", "input", mapOf("command" to command.trimEnd()))
            .clickEvent(ClickEvent.suggestCommand(command)))
    }

    private fun command(player: Player, command: String, reopen: ((Player) -> Unit)? = null) {
        player.performCommand("team $command")
        if (reopen != null) plugin.operations.submit(player) { plugin.operations.onPlayer(player) { reopen(player) } }
    }

    fun close() {
        for (player in plugin.server.onlinePlayers) {
            if (Bukkit.isOwnedByCurrentRegion(player) && player.openInventory.topInventory.holder is BaseGui) {
                player.closeInventory()
            }
        }
    }

    private companion object {
        val colors = listOf(
            Triple("WHITE", "f", "white"), Triple("ORANGE", "6", "gold"), Triple("MAGENTA", "d", "light_purple"),
            Triple("LIGHT_BLUE", "b", "aqua"), Triple("YELLOW", "e", "yellow"), Triple("LIME", "a", "green"),
            Triple("PINK", "d", "light_purple"), Triple("GRAY", "8", "gray"), Triple("LIGHT_GRAY", "7", "gray"),
            Triple("CYAN", "3", "dark_aqua"), Triple("PURPLE", "5", "dark_purple"), Triple("BLUE", "9", "blue"),
            Triple("BROWN", "4", "color:#8B4513"), Triple("GREEN", "2", "dark_green"), Triple("RED", "c", "red"),
            Triple("BLACK", "0", "black")
        )
    }
}
