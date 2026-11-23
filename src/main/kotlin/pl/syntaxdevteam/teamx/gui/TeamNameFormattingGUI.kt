package pl.syntaxdevteam.teamx.gui

import pl.syntaxdevteam.teamx.messages.teamMessage
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import pl.syntaxdevteam.teamx.TeamX

class TeamNameFormattingGUI(private val plugin: TeamX) : GUI {

    private val configs = plugin.config

    private val boldItemName = configs.getString("GUI.item_formats.boldItem.material", "PAPER")!!.uppercase()
    private val boldItemMaterial = Material.matchMaterial(boldItemName) ?: Material.PAPER
    private val boldIndex = configs.getInt("GUI.item_formats.boldItem.index", 11)
    private val boldTitle = configs.getString("GUI.item_formats.boldItem.title", "Bold")!!

    private val italicItemName = configs.getString("GUI.item_formats.italicItem.material", "FEATHER")!!.uppercase()
    private val italicItemMaterial = Material.matchMaterial(italicItemName) ?: Material.FEATHER
    private val italicIndex = configs.getInt("GUI.item_formats.italicItem.index", 13)
    private val italicTitle = configs.getString("GUI.item_formats.italicItem.title", "Italic")!!

    private val underlineItemName = configs.getString("GUI.item_formats.underlineItem.material", "STRING")!!.uppercase()
    private val underlineItemMaterial = Material.matchMaterial(underlineItemName) ?: Material.STRING
    private val underlineIndex = configs.getInt("GUI.item_formats.underlineItem.index", 15)
    private val underlineTitle = configs.getString("GUI.item_formats.underlineItem.title", "Underline")!!

    private val strikethroughItemName = configs.getString("GUI.item_formats.strikethroughItem.material", "BARRIER")!!.uppercase()
    private val strikethroughItemMaterial = Material.matchMaterial(strikethroughItemName) ?: Material.BARRIER
    private val strikethroughIndex = configs.getInt("GUI.item_formats.strikethroughItem.index", 29)
    private val strikethroughTitle = configs.getString("GUI.item_formats.strikethroughItem.title", "Strikethrough")!!

    private val clearFormatItemName = configs.getString("GUI.item_formats.clearFormatItem.material", "MILK_BUCKET")!!.uppercase()
    private val clearFormatItemMaterial = Material.matchMaterial(clearFormatItemName) ?: Material.MILK_BUCKET
    private val clearFormatItemIndex = configs.getInt("GUI.item_formats.clearFormatItem.index", 33)
    private val clearFormatTitle = configs.getString("GUI.item_formats.clearFormatItem.title", "Clear formatting")!!

    private val saveMaterialName = configs.getString("GUI.save.material", "EMERALD_BLOCK")!!.uppercase()
    private val saveMaterial = Material.matchMaterial(saveMaterialName) ?: Material.EMERALD_BLOCK
    private val saveIndex = configs.getInt("GUI.save.index", 48)
    private val saveTitle = configs.getString("GUI.save.title", "Save")!!

    private val backMaterialName = configs.getString("GUI.back.material", "REDSTONE_BLOCK")!!.uppercase()
    private val backMaterial = Material.matchMaterial(backMaterialName) ?: Material.REDSTONE_BLOCK
    private val backIndex = configs.getInt("GUI.back.index", 50)
    private val backTitle = configs.getString("GUI.back.title", "Back")!!

    override fun open(player: Player) {
        val inventory = TeamMenu(this, 54).inventory

        val boldItem = createItem(boldItemMaterial, boldTitle)
        val italicItem = createItem(italicItemMaterial, italicTitle)
        val underlineItem = createItem(underlineItemMaterial, underlineTitle)
        val strikethroughItem = createItem(strikethroughItemMaterial, strikethroughTitle)
        val clearFormatItem = createItem(clearFormatItemMaterial, clearFormatTitle)

        inventory.setItem(boldIndex, boldItem)
        inventory.setItem(italicIndex, italicItem)
        inventory.setItem(underlineIndex, underlineItem)
        inventory.setItem(strikethroughIndex, strikethroughItem)
        inventory.setItem(clearFormatItemIndex, clearFormatItem)

        inventory.setItem(saveIndex, createItem(saveMaterial, saveTitle))
        inventory.setItem(backIndex, createItem(backMaterial, backTitle))

        player.openInventory(inventory)
    }

    override fun handleClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val slot = event.rawSlot
        val topSize = event.view.topInventory.size

        // jeśli kliknięto poza górną częścią GUI, nie rób nic
        if (slot !in 0..<topSize) return

        val clickedItem = event.currentItem ?: return
        val uuid = player.uniqueId
        var teamNameFormat = plugin.cacheHandler.findPlayerCache(uuid.toString()) ?: ""

        // blokujemy tylko klikanie w GUI
        event.isCancelled = true
        plugin.logger.debug("Format passed in handleClick: $teamNameFormat")

        val useMiniMessage = configs.getBoolean("GUI.UseMiniMessage", false)

        when (clickedItem.type) {
            backMaterial -> {
                player.sendMessage(plugin.messageHandler.teamMessage("GUI", "return"))
                InitialSelectionGUI(plugin).open(player)
            }
            boldItemMaterial -> {
                teamNameFormat = if (useMiniMessage) "$teamNameFormat<bold>" else "$teamNameFormat&l"
                plugin.logger.debug("Format passed: $teamNameFormat")
            }
            italicItemMaterial -> {
                teamNameFormat = if (useMiniMessage) "$teamNameFormat<italic>" else "$teamNameFormat&o"
                plugin.logger.debug("Format passed: $teamNameFormat")
            }
            underlineItemMaterial -> {
                teamNameFormat = if (useMiniMessage) "$teamNameFormat<underlined>" else "$teamNameFormat&n"
                plugin.logger.debug("Format passed: $teamNameFormat")
            }
            strikethroughItemMaterial -> {
                teamNameFormat = if (useMiniMessage) "$teamNameFormat<strikethrough>" else "$teamNameFormat&m"
                plugin.logger.debug("Format passed: $teamNameFormat")
            }
            saveMaterial -> {
                plugin.logger.debug("Format passed: $teamNameFormat")
                val teamData = plugin.databaseHandler.getTeamByUUID(uuid.toString())
                val teamId = teamData?.get(0)?.toInt()
                if (teamId != null) {
                    plugin.databaseHandler.updateTeamFormat(teamId, teamNameFormat)
                }
                player.closeInventory()
                plugin.cacheHandler.removePlayerCache(uuid.toString())
                plugin.teamCacheManager.loadPlayerTeamData(uuid)
            }
            else -> {

            }
        }
        player.sendMessage(plugin.messageHandler.teamMessage("GUI", "saved"))
        val teamData = plugin.databaseHandler.getTeamByUUID(uuid.toString())
        val teamId = teamData?.get(0)?.toInt()
        if (teamId != null) {
            plugin.databaseHandler.updateTeamFormat(teamId, teamNameFormat)
        }
        player.closeInventory()
        plugin.cacheHandler.removePlayerCache(uuid.toString())
        plugin.teamCacheManager.loadPlayerTeamData(uuid)
    }

    override fun getTitle(): Component {
        return plugin.messageHandler.stringMessageToComponentNoPrefix("GUI", "title_formatGUI")
    }

    private fun createItem(material: Material, name: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.displayName(plugin.messageHandler.formatMixedTextToMiniMessage(name, TagResolver.empty()))
        item.itemMeta = meta
        return item
    }
}
