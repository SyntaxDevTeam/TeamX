package pl.syntaxdevteam.teamx.gui

import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

internal class TeamMenu(val gui: GUI, size: Int) : InventoryHolder {
    private val contents = Bukkit.createInventory(this, size, gui.getTitle())

    override fun getInventory(): Inventory = contents
}
