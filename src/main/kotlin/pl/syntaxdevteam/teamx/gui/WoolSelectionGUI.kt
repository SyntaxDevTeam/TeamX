package pl.syntaxdevteam.teamx.gui

import pl.syntaxdevteam.teamx.messages.teamMessage
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import pl.syntaxdevteam.teamx.TeamX
import java.util.*

class WoolSelectionGUI(private val plugin: TeamX) : GUI {
    private var selectedColor: String = ""
    private val backMaterialName = plugin.config.getString("GUI.back.material", "REDSTONE_BLOCK")!!
    private val backMaterial = Material.matchMaterial(backMaterialName) ?: Material.REDSTONE_BLOCK
    private val backIndex = plugin.config.getInt("GUI.back.index", 49)
    private val backTitle = plugin.config.getString("GUI.back.title", "Back")!!

    private val woolColorMap = mapOf(
        "WHITE" to Material.WHITE_WOOL,
        "ORANGE" to Material.ORANGE_WOOL,
        "MAGENTA" to Material.MAGENTA_WOOL,
        "LIGHT_BLUE" to Material.LIGHT_BLUE_WOOL,
        "YELLOW" to Material.YELLOW_WOOL,
        "LIME" to Material.LIME_WOOL,
        "PINK" to Material.PINK_WOOL,
        "GRAY" to Material.GRAY_WOOL,
        "LIGHT_GRAY" to Material.LIGHT_GRAY_WOOL,
        "CYAN" to Material.CYAN_WOOL,
        "PURPLE" to Material.PURPLE_WOOL,
        "BLUE" to Material.BLUE_WOOL,
        "BROWN" to Material.BROWN_WOOL,
        "GREEN" to Material.GREEN_WOOL,
        "RED" to Material.RED_WOOL,
        "BLACK" to Material.BLACK_WOOL
    )

    override fun open(player: Player) {
        val inventory = TeamMenu(this, 54).inventory

        for ([index, entry] in woolColorMap.entries.withIndex()) {
            inventory.setItem(
                index,
                createItem(entry.value, "${getMiniMessageColor(entry.key)}${formatColorName(entry.key)}")
            )
        }

        inventory.setItem(backIndex, createItem(backMaterial, backTitle))
        player.openInventory(inventory)
    }

    override fun handleClick(event: InventoryClickEvent) {
        val view = event.view
        val topSize = view.topInventory.size    // np. 54 dla tego GUI
        val slot = event.rawSlot                // numer slotu w całym widoku

        // 1) klik poza górnym GUI (dolna część ekwipunku lub creative‑tab) → allow
        if (slot !in 0..<topSize) return

        // 2) anuluj tylko jeśli klik w rzeczywistą pozycję w GUI
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val clicked = event.currentItem ?: return

        when (clicked.type) {
            backMaterial -> {
                player.sendMessage(plugin.messageHandler.teamMessage("GUI", "return"))
                InitialSelectionGUI(plugin).open(player)
            }
            else -> {
                val woolEntry = woolColorMap.entries.find { it.value == clicked.type } ?: return
                selectedColor = if (plugin.config.getBoolean("GUI.UseMiniMessage", false))
                    getMiniMessageColor(woolEntry.key)
                else
                    getColorCode(woolEntry.key)

                player.sendMessage(plugin.messageHandler.teamMessage("GUI", "color_selected"))
                plugin.logger.debug("Selected color: $selectedColor")
                plugin.cacheHandler.savePlayerCache(
                    player.uniqueId.toString(),
                    selectedColor
                )
                TeamNameFormattingGUI(plugin).open(player)
            }
        }
    }

    private fun getColorCode(color: String): String {
        return when (color) {
            "WHITE" -> "&f"
            "ORANGE" -> "&6"
            "MAGENTA" -> "&d"
            "LIGHT_BLUE" -> "&b"
            "YELLOW" -> "&e"
            "LIME" -> "&a"
            "PINK" -> "&d"
            "GRAY" -> "&8"
            "LIGHT_GRAY" -> "&7"
            "CYAN" -> "&3"
            "PURPLE" -> "&5"
            "BLUE" -> "&9"
            "BROWN" -> "&4"
            "GREEN" -> "&2"
            "RED" -> "&c"
            "BLACK" -> "&0"
            else -> ""
        }
    }

    private fun getMiniMessageColor(color: String): String {
        return when (color) {
            "WHITE" -> "<white>"
            "ORANGE" -> "<gold>"
            "MAGENTA" -> "<light_purple>"
            "LIGHT_BLUE" -> "<aqua>"
            "YELLOW" -> "<yellow>"
            "LIME" -> "<green>"
            "PINK" -> "<light_purple>"
            "GRAY" -> "<gray>"
            "LIGHT_GRAY" -> "<color:#C0C0C0>"
            "CYAN" -> "<dark_aqua>"
            "PURPLE" -> "<dark_purple>"
            "BLUE" -> "<blue>"
            "BROWN" -> "<color:#8B4513>"
            "GREEN" -> "<dark_green>"
            "RED" -> "<red>"
            "BLACK" -> "<black>"
            else -> ""
        }
    }

    override fun getTitle(): Component {
        return plugin.messageHandler.stringMessageToComponentNoPrefix("GUI", "title_colorGUI")
    }

    private fun createItem(material: Material, name: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.displayName(plugin.messageHandler.formatMixedTextToMiniMessage(name, TagResolver.empty()))
        item.itemMeta = meta
        return item
    }

    private fun formatColorName(color: String): String {
        return color.lowercase(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }.replace("_", " ")
    }
}
