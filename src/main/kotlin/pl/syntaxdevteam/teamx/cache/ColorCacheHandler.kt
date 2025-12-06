package pl.syntaxdevteam.teamx.cache

import pl.syntaxdevteam.teamx.TeamX

class ColorCacheHandler(private val plugin: TeamX) {
    private val cache: MutableMap<String, String> = mutableMapOf()

    fun savePlayerCache(playerUUID: String, color: String) {
        cache[playerUUID] = color
        plugin.logger.debug("Color data saved -> $playerUUID|$color")
    }

    fun findPlayerCache(playerUUID: String): String? {
        return cache[playerUUID]
    }

    fun removePlayerCache(playerUUID: String) {
        cache.remove(playerUUID)
        plugin.logger.debug("Removed all cache entries for player: $playerUUID")
    }
}
