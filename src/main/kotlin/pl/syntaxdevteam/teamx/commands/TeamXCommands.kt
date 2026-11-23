package pl.syntaxdevteam.teamx.commands

import com.mojang.brigadier.arguments.IntegerArgumentType
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.command.CommandSender
import pl.syntaxdevteam.teamx.TeamX
import pl.syntaxdevteam.teamx.messages.teamMessage

class TeamXCommands(private val plugin: TeamX) {
    private val mH get() = plugin.messageHandler

    fun build() = Commands.literal("teamx")
        .requires { plugin.isEnabled && it.sender.hasPermission("teamx.cmd.ltx") }
        .executes {
            it.source.sender.sendMessage(mH.miniMessageFormat("<green>Type </green><gold>/ltx help</gold> <green>to see available commands</green>"))
            1
        }
        .then(Commands.literal("help")
            .executes {
                sendHelp(it.source.sender, 1)
                1
            }
            .then(Commands.argument("page", IntegerArgumentType.integer())
                .executes {
                    sendHelp(it.source.sender, IntegerArgumentType.getInteger(it, "page"))
                    1
                }))
        .then(Commands.literal("version").executes {
            sendVersion(it.source.sender)
            1
        })
        .then(Commands.literal("reload").executes {
            plugin.onReload()
            it.source.sender.sendMessage(mH.teamMessage("reload", "success"))
            plugin.teamCacheManager.refreshCacheForOnlinePlayers()
            1
        })
        .then(Commands.literal("export")
            .requires { it.sender.hasPermission("consentchecker.cmd.export") }
            .executes {
                plugin.databaseHandler.exportDatabase()
                1
            })
        .then(Commands.literal("import")
            .requires { it.sender.hasPermission("consentchecker.cmd.import") }
            .executes {
                plugin.databaseHandler.importDatabase()
                1
            })
        .build()

    private fun sendVersion(sender: CommandSender) {
        val pluginMeta = plugin.pluginMeta
        sender.sendMessage(
            mH.miniMessageFormat(
                "\n <gray>+-------------------------------------------------\n" +
                        " <gray>|\n" +
                        " <gray>|   <gold>→ <bold>" + pluginMeta.name + "</bold> ←</gold>\n" +
                        " <gray>|   <white>Author: <bold><gold>" + pluginMeta.authors + "</gold></bold>\n" +
                        " <gray>|   <white>Website: <bold><gold><click:open_url:'" + pluginMeta.website + "'>" + pluginMeta.website + "</click></gold></bold>\n" +
                        " <gray>|   <white>Version: <bold><gold>" + pluginMeta.version + "</gold></bold>\n" +
                        " <gray>|" +
                        "\n<gray>+-------------------------------------------------"
            )
        )
    }

    private fun sendHelp(sender: CommandSender, page: Int) {
        val commands = listOf(
            "  <gold>/ltx help <gray>- <white>Displays this prompt.",
            "  <gold>/ltx version <gray>- <white>Shows plugin info.",
            "  <gold>/ltx reload <gray>- <white>Reloads the configuration file.",
            "  <gold>/team help <gray>- <white>Displays a list of available commands.",
            " ",
            " ",
            " ",
            " ",
            " ",
            " ",
            " ",
            " "
        )

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
            " <gray>| (Page $currentPage/$totalPages) <click:run_command:'/ltx help $prevPage'><white>[Previous]</white></click>   " +
                    "<click:run_command:'/ltx help $nextPage'><white>[Next]</white></click>"
        ))
        sender.sendMessage(mH.miniMessageFormat(" <gray>|"))
        sender.sendMessage(mH.miniMessageFormat(" <gray>+-------------------------------------------------"))
    }
}
