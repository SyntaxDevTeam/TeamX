package pl.syntaxdevteam.teamx.gui

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent

class GUIHandler : Listener {
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val menu = event.view.topInventory.holder as? TeamMenu ?: return
        event.isCancelled = true
        if (event.clickedInventory == event.view.topInventory) menu.gui.handleClick(event)
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder is TeamMenu) event.isCancelled = true
    }
}
