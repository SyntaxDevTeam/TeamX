package pl.syntaxdevteam.teamx.commands

import pl.syntaxdevteam.teamx.messages.teamMessage
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventOwner
import org.jetbrains.annotations.NotNull
import pl.syntaxdevteam.teamx.TeamX

class TeamXCommands(private val plugin: TeamX) : BasicCommand {
    private val mH get() = plugin.messageHandler

    override fun execute(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>) {
        if (!plugin.isEnabled) return
        if (!stack.sender.hasPermission("teamx.cmd.ltx")) {
            stack.sender.sendMessage(plugin.messageHandler.teamMessage("error", "no_permission"))
            return
        }
        val pluginMeta = (plugin as LifecycleEventOwner).pluginMeta
        if (args.isNotEmpty()) {
            when {
                args[0].equals("help", ignoreCase = true) -> {

                    val page = args.getOrNull(1)?.toIntOrNull() ?: 1
                    sendHelp(stack, page)

                }

                args[0].equals("version", ignoreCase = true) -> {

                    stack.sender.sendMessage(
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

                args[0].equals("reload", ignoreCase = true) -> {

                    plugin.onReload()
                    stack.sender.sendMessage(mH.teamMessage("reload", "success"))
                    plugin.teamCacheManager.refreshCacheForOnlinePlayers()
                }
                args[0].equals("export", ignoreCase = true) -> {
                    if (stack.sender.hasPermission("consentchecker.cmd.export")) {
                        plugin.databaseHandler.exportDatabase()
                    } else {
                        stack.sender.sendMessage(mH.teamMessage("error", "no_permission"))
                    }
                }
                args[0].equals("import", ignoreCase = true) -> {
                    if (stack.sender.hasPermission("consentchecker.cmd.import")) {
                        plugin.databaseHandler.importDatabase()
                    } else {
                        stack.sender.sendMessage(mH.teamMessage("error", "no_permission"))
                    }
                }
            }
        } else {
            stack.sender.sendMessage(mH.miniMessageFormat("<green>Type </green><gold>/ltx help</gold> <green>to see available commands</green>"))
        }
    }

    private fun sendHelp(stack: CommandSourceStack, page: Int) {
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

        stack.sender.sendMessage(mH.miniMessageFormat(" <gray>+-------------------------------------------------"))
        stack.sender.sendMessage(mH.miniMessageFormat(" <gray>|    <gold>Available commands for ${plugin.pluginMeta.name}:"))
        stack.sender.sendMessage(mH.miniMessageFormat(" <gray>|"))

        val startIndex = (currentPage - 1) * itemsPerPage
        val endIndex = (currentPage * itemsPerPage).coerceAtMost(commands.size)
        for (i in startIndex until endIndex) {
            stack.sender.sendMessage(mH.miniMessageFormat(" <gray>|  ${commands[i]}"))
        }

        val prevPage = if (currentPage > 1) currentPage - 1 else totalPages
        val nextPage = if (currentPage < totalPages) currentPage + 1 else 1
        stack.sender.sendMessage(mH.miniMessageFormat(" <gray>|"))
        stack.sender.sendMessage(mH.miniMessageFormat(" <gray>|"))
        stack.sender.sendMessage(mH.miniMessageFormat(
            " <gray>| (Page $currentPage/$totalPages) <click:run_command:'/ftx help $prevPage'><white>[Previous]</white></click>   " +
                    "<click:run_command:'/ftx help $nextPage'><white>[Next]</white></click>"
        ))
        stack.sender.sendMessage(mH.miniMessageFormat(" <gray>|"))
        stack.sender.sendMessage(mH.miniMessageFormat(" <gray>+-------------------------------------------------"))
    }

    override fun suggest(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>): List<String> {
        return if (stack.sender.hasPermission("teamx.cmd.ltx")) {

            when (args.size) {
                1 -> listOf("help", "version", "reload", "export", "import")
                else -> emptyList()
            }
        } else {
            emptyList()
        }
    }
}
