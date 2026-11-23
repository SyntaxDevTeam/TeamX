package pl.syntaxdevteam.teamx.loader

import org.bukkit.event.HandlerList
import pl.syntaxdevteam.core.SyntaxCore
import pl.syntaxdevteam.core.config.BaseConfigMigration
import pl.syntaxdevteam.core.config.ConfigManager
import pl.syntaxdevteam.core.config.ConfigMigrationContext
import pl.syntaxdevteam.core.platform.ServerEnvironment
import pl.syntaxdevteam.message.SyntaxMessages
import pl.syntaxdevteam.teamx.TeamX
import pl.syntaxdevteam.teamx.basic.InvitationManager
import pl.syntaxdevteam.teamx.basic.PlaceholderHandler
import pl.syntaxdevteam.teamx.cache.ColorCacheHandler
import pl.syntaxdevteam.teamx.cache.TeamCacheManager
import pl.syntaxdevteam.teamx.databases.DatabaseHandler
import pl.syntaxdevteam.teamx.gui.GUIHandler
import pl.syntaxdevteam.teamx.gui.TeamMenu
import pl.syntaxdevteam.teamx.hooks.HookHandler

class PluginInitializer(private val plugin: TeamX) {
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
        plugin.cacheHandler = ColorCacheHandler(plugin)
        plugin.invitationManager = InvitationManager(plugin)
        plugin.teamCacheManager = TeamCacheManager(plugin)

        plugin.server.pluginManager.registerEvents(GUIHandler(), plugin)
        plugin.server.pluginManager.registerEvents(plugin.teamCacheManager, plugin)
        plugin.teamCacheManager.refreshCacheForOnlinePlayers()
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
        if (!ServerEnvironment.isFoliaBased()) {
            server.onlinePlayers.forEach { player ->
                if (player.openInventory.topInventory.holder is TeamMenu) player.closeInventory()
            }
        }
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
        ConfigManager(plugin, plugin.logger, initialVersion = 100, targetVersion = 101)
            .registerMigration(object : BaseConfigMigration(100, 101) {
                override fun migrate(context: ConfigMigrationContext) {
                    context.moveValue("checkForUpdates", "update.check-for-updates", removeSource = true)
                    context.moveValue("autoDownloadUpdates", "update.auto-download", removeSource = true)
                }
            }).load()
        plugin.reloadConfig()
    }
}
