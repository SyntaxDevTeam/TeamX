package pl.syntaxdevteam.teamx

import org.bukkit.plugin.java.JavaPlugin
import pl.syntaxdevteam.core.SyntaxCore
import pl.syntaxdevteam.core.update.HangarSource
import pl.syntaxdevteam.message.MessageHandler
import pl.syntaxdevteam.teamx.basic.InvitationManager
import pl.syntaxdevteam.teamx.cache.ColorCacheHandler
import pl.syntaxdevteam.teamx.cache.TeamCacheManager
import pl.syntaxdevteam.teamx.commands.CommandsManager
import pl.syntaxdevteam.teamx.databases.DatabaseHandler
import pl.syntaxdevteam.teamx.hooks.HookHandler
import pl.syntaxdevteam.teamx.loader.PluginInitializer

class TeamX : JavaPlugin() {
    private var initializer: PluginInitializer? = null
    private var commandsRegistered = false

    val logger get() = SyntaxCore.logger
    val uuidManager get() = SyntaxCore.uuidManager

    lateinit var databaseHandler: DatabaseHandler
    lateinit var messageHandler: MessageHandler
    lateinit var hookHandler: HookHandler
    lateinit var cacheHandler: ColorCacheHandler
    lateinit var invitationManager: InvitationManager
    lateinit var teamCacheManager: TeamCacheManager

    override fun onEnable() {
        val startup = PluginInitializer(this)
        initializer = startup
        try {
            SyntaxCore.registerUpdateSources(HangarSource(pluginMeta.name))
            SyntaxCore.init(this, versionType = "paper")
            startup.onEnable()
            if (!commandsRegistered) {
                CommandsManager(this).registerCommands()
                commandsRegistered = true
            }
            if (config.getBoolean("update.check-for-updates", true)) SyntaxCore.updateChecker.checkAsync()
        } catch (failure: Throwable) {
            initializer = null
            runCatching { startup.onDisable() }.onFailure { failure.addSuppressed(it) }
            throw failure
        }
    }

    override fun onDisable() {
        val active = initializer
        initializer = null
        active?.onDisable()
    }

    fun onReload() {
        super.reloadConfig()
        messageHandler.reloadMessages()
        logger.success("Config reloaded.")
    }
}
