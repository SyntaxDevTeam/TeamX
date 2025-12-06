package pl.syntaxdevteam.teamx

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin
import pl.syntaxdevteam.teamx.basic.InvitationManager
import pl.syntaxdevteam.teamx.cache.TeamCacheManager
import pl.syntaxdevteam.teamx.commands.CommandsManager
import pl.syntaxdevteam.teamx.common.*
import pl.syntaxdevteam.teamx.databases.DatabaseHandler
import pl.syntaxdevteam.teamx.cache.ColorCacheHandler
import pl.syntaxdevteam.teamx.hooks.HookHandler
import pl.syntaxdevteam.teamx.loader.PluginInitializer
import java.io.File
import java.util.*

class TeamX : JavaPlugin() {
    private lateinit var pluginInitializer: PluginInitializer

    lateinit var uuidManager: UUIDManager
    lateinit var configHandler: ConfigHandler
    lateinit var pluginConfig: FileConfiguration
    lateinit var logger: Logger
    lateinit var pluginsManager: PluginManager
    lateinit var statsCollector: StatsCollector
    lateinit var databaseHandler: DatabaseHandler
    lateinit var messageHandler: MessageHandler
    lateinit var updateChecker: UpdateChecker
    lateinit var commandsManager: CommandsManager
    lateinit var hookHandler: HookHandler
    lateinit var cacheHandler: ColorCacheHandler
    lateinit var invitationManager: InvitationManager
    lateinit var teamCacheManager: TeamCacheManager

    override fun onEnable() {
        pluginInitializer = PluginInitializer(this)
        pluginInitializer.onEnable()
    }

    override fun onDisable() {
        databaseHandler.closeConnection()
        pluginInitializer.onDisable()
    }
    fun onReload() {
        super.reloadConfig()
        logger.success("Config reloaded.")
    }

    fun getPluginFile(): File {
        return this.file
    }

    fun getServerName(): String {
        val properties = Properties()
        val file = File("server.properties")
        if (file.exists()) {
            properties.load(file.inputStream())
            val serverName = properties.getProperty("server-name")
            if (serverName != null) {
                return serverName
            } else {
                logger.debug("Property 'server-name' not found in server.properties file.")
            }
        } else {
            logger.debug("The server.properties file does not exist.")
        }
        return "Unknown Server"
    }
}
