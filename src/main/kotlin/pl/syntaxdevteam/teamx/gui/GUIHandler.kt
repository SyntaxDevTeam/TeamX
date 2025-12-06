package pl.syntaxdevteam.teamx.gui

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import pl.syntaxdevteam.teamx.TeamX

class GUIHandler(private val plugin: TeamX) : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val title = event.view.title()

        when (title) {
            plugin.messageHandler.getLogMessage("GUI", "title_initialGUI") -> InitialSelectionGUI(plugin).handleClick(event)
            plugin.messageHandler.getLogMessage("GUI", "title_colorGUI") -> WoolSelectionGUI(plugin).handleClick(event)
            plugin.messageHandler.getLogMessage("GUI", "title_formatGUI") -> TeamNameFormattingGUI(plugin).handleClick(event)
        }
    }
}