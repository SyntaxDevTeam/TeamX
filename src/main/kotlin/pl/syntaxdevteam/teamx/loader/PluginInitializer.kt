package pl.syntaxdevteam.teamx.loader

import pl.syntaxdevteam.teamx.TeamX
import pl.syntaxdevteam.teamx.basic.InvitationManager
import pl.syntaxdevteam.teamx.basic.PlaceholderHandler
import pl.syntaxdevteam.teamx.cache.TeamCacheManager
import pl.syntaxdevteam.teamx.commands.CommandsManager
import pl.syntaxdevteam.teamx.common.*
import pl.syntaxdevteam.teamx.databases.DatabaseHandler
import pl.syntaxdevteam.teamx.cache.ColorCacheHandler
import pl.syntaxdevteam.teamx.gui.GUIHandler
import pl.syntaxdevteam.teamx.hooks.CleanerXHook
import pl.syntaxdevteam.teamx.hooks.HookHandler

@Suppress("UnstableApiUsage")
class PluginInitializer(private val plugin: TeamX) {

    fun onEnable() {
        setUpLogger()
        setupConfig()
        setupUUID()
        setupDatabase()
        setupHandlers()
        registerEvents()
        registerCommands()
        checkForUpdates()
    }

    fun onDisable() {
        plugin.logger.err(plugin.pluginMeta.name + " " + plugin.pluginMeta.version + " has been disabled ☹️")
    }

    private fun setUpLogger() {
        plugin.pluginConfig = plugin.getConfig()
        plugin.logger = Logger(plugin, plugin.pluginConfig.getBoolean("debug"))
    }

    private fun setupConfig() {
        plugin.saveDefaultConfig()
        plugin.configHandler = ConfigHandler(plugin)
        plugin.configHandler.verifyAndUpdateConfig()
    }

    private fun setupUUID() {
        plugin.uuidManager = UUIDManager(plugin)
    }

    private fun setupDatabase() {
        plugin.databaseHandler = DatabaseHandler(plugin)
        plugin.databaseHandler.openConnection()
        plugin.databaseHandler.createTables()
    }

    private fun setupHandlers() {
        plugin.messageHandler = MessageHandler(plugin).apply { initial() }
        plugin.pluginsManager = PluginManager(plugin)
        plugin.hookHandler = HookHandler(plugin)
        plugin.cacheHandler = ColorCacheHandler(plugin)
        plugin.invitationManager = InvitationManager(plugin)
        plugin.teamCacheManager = TeamCacheManager(plugin)
    }

    private fun registerCommands() {
        plugin.commandsManager = CommandsManager(plugin)
        plugin.commandsManager.registerCommands()
    }

    private fun registerEvents() {
        plugin.server.pluginManager.registerEvents(GUIHandler(plugin), plugin)
        plugin.server.pluginManager.registerEvents(plugin.teamCacheManager, plugin)
        if (plugin.hookHandler.checkPlaceholderAPI()) {
            PlaceholderHandler(plugin).register()
        }
        val cleanerXApi = CleanerXHook.getApi()
        if (cleanerXApi != null) {
            plugin.logger.info("CleanerX API detected - integration enabled.")
        } else {
            plugin.logger.info("CleanerX API not detected - skipping integration.")
        }
    }

    private fun checkForUpdates() {
        plugin.statsCollector = StatsCollector(plugin)
        plugin.updateChecker = UpdateChecker(plugin)
        plugin.updateChecker.checkForUpdates()
    }
}