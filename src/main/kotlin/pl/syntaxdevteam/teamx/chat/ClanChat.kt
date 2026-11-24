package pl.syntaxdevteam.teamx.chat

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import pl.syntaxdevteam.teamx.TeamX
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

class ClanChat(private val plugin: TeamX) : Listener {
    private val chatMode = ConcurrentHashMap.newKeySet<UUID>()

    private fun enabled() = plugin.config.getBoolean("team.chat.enabled", true)

    fun toggleChat(player: Player) {
        if (chatMode.remove(player.uniqueId)) return plugin.clanMessages.tell(player, "chat_off")
        if (!enabled()) return plugin.clanMessages.tell(player, "disabled")
        if (plugin.teamCacheManager.get(player.uniqueId) == null) return plugin.clanMessages.tell(player, "membership_required")
        chatMode.add(player.uniqueId)
        plugin.clanMessages.tell(player, "chat_on")
    }

    fun chat(player: Player, text: String) {
        if (!enabled()) return plugin.clanMessages.tell(player, "disabled")
        if (text.isBlank()) return
        plugin.clanMessages.broadcast(player, "chat_message", mapOf("message" to text))
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        if (player.uniqueId !in chatMode) return
        val text = PlainTextComponentSerializer.plainText().serialize(event.message())
        val route = !event.isCancelled
        event.isCancelled = true
        event.viewers().clear()
        event.message(Component.empty())
        if (route) chat(player, text)
    }

    @Suppress("DEPRECATION")
    @EventHandler(priority = EventPriority.LOWEST)
    fun onLegacyChat(event: org.bukkit.event.player.AsyncPlayerChatEvent) {
        if (event.player.uniqueId !in chatMode) return
        val text = event.message
        val route = !event.isCancelled
        event.isCancelled = true
        event.recipients.clear()
        event.message = ""
        if (route) chat(event.player, text)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        chatMode.remove(event.player.uniqueId)
    }
}
