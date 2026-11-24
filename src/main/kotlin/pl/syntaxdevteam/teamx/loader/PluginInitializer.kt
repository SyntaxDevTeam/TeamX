package pl.syntaxdevteam.teamx.loader

import org.bukkit.event.HandlerList
import pl.syntaxdevteam.core.SyntaxCore
import pl.syntaxdevteam.core.config.BaseConfigMigration
import pl.syntaxdevteam.core.config.ConfigManager
import pl.syntaxdevteam.core.config.ConfigMigrationContext
import pl.syntaxdevteam.message.SyntaxMessages
import pl.syntaxdevteam.teamx.TeamX
import pl.syntaxdevteam.teamx.invitations.InvitationManager
import pl.syntaxdevteam.teamx.placeholders.PlaceholderHandler
import pl.syntaxdevteam.teamx.cache.TeamCacheManager
import pl.syntaxdevteam.teamx.databases.DatabaseHandler
import pl.syntaxdevteam.teamx.gui.ClanMenus
import pl.syntaxdevteam.teamx.chat.ClanChat
import pl.syntaxdevteam.teamx.chat.ClanCommandLogFilter
import pl.syntaxdevteam.teamx.home.ClanTeleport
import pl.syntaxdevteam.teamx.messages.ClanMessages
import pl.syntaxdevteam.teamx.ping.ClanPing
import pl.syntaxdevteam.teamx.clans.ClanOperations
import pl.syntaxdevteam.teamx.hooks.HookHandler

class PluginInitializer(private val plugin: TeamX) {
    private var operations: ClanOperations? = null
    private var clanCommandLogFilter: ClanCommandLogFilter? = null
    private var menus: ClanMenus? = null
    private var database: DatabaseHandler? = null
    private var unregisterPlaceholders: (() -> Unit)? = null

    fun onEnable() {
        setupConfig()
        val storage = DatabaseHandler(plugin)
        database = storage
        storage.openConnection()
        storage.createTables()
        plugin.databaseHandler = storage

        plugin.messageHandler = SyntaxMessages.initialize(plugin)
        plugin.hookHandler = HookHandler(plugin)
        plugin.operations = ClanOperations(plugin).also { operations = it }
        plugin.clanMessages = ClanMessages(plugin)
        plugin.clanChat = ClanChat(plugin)
        runCatching { ClanCommandLogFilter().also { clanCommandLogFilter = it }.install() }
            .onSuccess { if (!it) plugin.logger.warning("TeamX could not filter clan chat from the server command log.") }
            .onFailure { plugin.logger.warning("TeamX could not filter clan chat from the server command log: ${it.message}") }
        plugin.clanPing = ClanPing(plugin)
        plugin.clanTeleport = ClanTeleport(plugin)
        plugin.menus = ClanMenus(plugin).also { menus = it }
        plugin.invitationManager = InvitationManager(plugin)
        plugin.teamCacheManager = TeamCacheManager(plugin)

        plugin.server.pluginManager.registerEvents(plugin.clanChat, plugin)
        plugin.server.pluginManager.registerEvents(plugin.clanPing, plugin)
        plugin.server.pluginManager.registerEvents(plugin.clanTeleport, plugin)
        plugin.server.pluginManager.registerEvents(plugin.teamCacheManager, plugin)
        plugin.teamCacheManager.initialize()
        if (plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            val expansion = PlaceholderHandler(plugin)
            if (expansion.register()) {
                unregisterPlaceholders = { expansion.unregister() }
            }
        }
    }

    fun onDisable() {
        val server = plugin.server
        runCatching { unregisterPlaceholders?.invoke() }
            .onFailure { plugin.logger.warning("Failed to unregister PlaceholderAPI expansion: ${it.message}") }
        unregisterPlaceholders = null
        clanCommandLogFilter?.close()
        menus?.close()
        operations?.close()
        HandlerList.unregisterAll(plugin)
        server.asyncScheduler.cancelTasks(plugin)
        server.servicesManager.unregisterAll(plugin)
        try {
            database?.closeConnection()
        } finally {
            database = null
            SyntaxMessages.reset()
            SyntaxCore.close()
        }
    }

    private fun setupConfig() {
        ConfigManager(plugin, plugin.logger, initialVersion = 100, targetVersion = 102)
            .registerMigration(object : BaseConfigMigration(100, 101) {
                override fun migrate(context: ConfigMigrationContext) {
                    context.moveValue("checkForUpdates", "update.check-for-updates", removeSource = true)
                    context.moveValue("autoDownloadUpdates", "update.auto-download", removeSource = true)
                }
            })
            .registerMigration(object : BaseConfigMigration(101, 102) {
                override fun migrate(context: ConfigMigrationContext) = Unit
            }).load()
        plugin.reloadConfig()
    }
}
