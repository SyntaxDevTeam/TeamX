package pl.syntaxdevteam.teamx.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import pl.syntaxdevteam.teamx.TeamX

class InitialSelectionGUI(private val plugin: TeamX) : GUI {

    private val colorTitleMaterialName = plugin.config.getString("GUI.colors.title", "§a§lChoose your color")!!
    private val colorMaterialName = plugin.config.getString("GUI.colors.material", colorTitleMaterialName)!!
    private val colorMaterial = Material.matchMaterial(colorMaterialName) ?: Material.PURPLE_WOOL
    private val colorIndex = plugin.config.getInt("GUI.colors.index", 11)

    private val formatTitleMaterialName = plugin.config.getString("GUI.format.title", "§a§lChoose your format")!!
    private val formatMaterialName = plugin.config.getString("GUI.format.material", formatTitleMaterialName)!!
    private val formatMaterial = Material.matchMaterial(formatMaterialName) ?: Material.PAPER
    private val formatIndex = plugin.config.getInt("GUI.format.index", 15)

    override fun open(player: Player) {
        val inventory = Bukkit.createInventory(null, 27, getTitle())

        val colorItem = createItem(colorMaterial, colorTitleMaterialName)
        val formatItem = createItem(formatMaterial, formatTitleMaterialName)

        inventory.setItem(colorIndex, colorItem)
        inventory.setItem(formatIndex, formatItem)

        player.openInventory(inventory)
    }

    override fun handleClick(event: InventoryClickEvent) {
        val view = event.view
        val topSize = view.topInventory.size
        val slot = event.rawSlot

        if (slot !in 0..<topSize) return

        if (slot != colorIndex && slot != formatIndex) return

        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return

        when (slot) {
            colorIndex -> {
                player.sendMessage(plugin.messageHandler.getMessage("GUI", "opening_color"))
                WoolSelectionGUI(plugin).open(player)
            }
            formatIndex -> {
                player.sendMessage(plugin.messageHandler.getMessage("GUI", "opening_format"))
                TeamNameFormattingGUI(plugin).open(player)
            }
        }

    }

    override fun getTitle(): Component {
        return plugin.messageHandler.getLogMessage("GUI", "title_initialGUI")
    }

    private fun createItem(material: Material, name: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.displayName(plugin.messageHandler.formatMixedTextToMiniMessage(name, TagResolver.empty()))
        item.itemMeta = meta
        return item
    }
}