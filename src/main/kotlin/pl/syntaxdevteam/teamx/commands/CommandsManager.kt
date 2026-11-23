package pl.syntaxdevteam.teamx.commands

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import pl.syntaxdevteam.teamx.TeamX

class CommandsManager(private val plugin: TeamX) {
    fun registerCommands() {
        plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val commands = event.registrar()
            commands.register(TeamXCommands(plugin).build(), "TeamX administration", listOf("ltx"))
            commands.register(TeamCommand(plugin).build(), "Manage your TeamX team")
        }
    }
}
