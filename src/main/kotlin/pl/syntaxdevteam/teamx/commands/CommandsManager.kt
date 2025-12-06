package pl.syntaxdevteam.teamx.commands

import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.Plugin
import pl.syntaxdevteam.teamx.TeamX

@Suppress("UnstableApiUsage")
class CommandsManager(private val plugin: TeamX) {

    fun registerCommands() {
        val manager: LifecycleEventManager<Plugin> = plugin.lifecycleManager
        manager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val commands: Commands = event.registrar()
            commands.register(
                "teamx",
                "TeamX plugin command. Type /ltx help to check available commands",
                TeamXCommands(plugin)
            )
            commands.register(
                "ltx",
                "TeamX plugin command. Type /ltx help to check available commands",
                TeamXCommands(plugin)
            )
            commands.register(
                "team",
                "TeamX plugin command. Type /ltx help to check available commands",
                TeamCommand(plugin)
            )
        }
    }
}