package pl.syntaxdevteam.teamx.hooks

import net.milkbowl.vault.economy.Economy
import org.bukkit.entity.Player
import pl.syntaxdevteam.teamx.TeamX
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class IdentityPayment(private val plugin: TeamX, private val player: Player, private val amount: Double) {
    private var chargedBy: Economy? = null
    var error = "payment_failed"
        private set

    fun withdraw(): Boolean = onPlayer {
        if (!plugin.server.pluginManager.isPluginEnabled("Vault") &&
            !plugin.server.pluginManager.isPluginEnabled("VaultUnlocked")
        ) {
            error = "economy_unavailable"
            return@onPlayer false
        }
        val economy = plugin.server.servicesManager.load(Economy::class.java)
        if (economy == null) {
            error = "economy_unavailable"
            return@onPlayer false
        }
        if (!economy.withdrawPlayer(player, amount).transactionSuccess()) return@onPlayer false
        chargedBy = economy
        true
    }

    fun refund() {
        val provider = chargedBy ?: return
        if (!onPlayer { provider.depositPlayer(player, amount).transactionSuccess() }) {
            plugin.logger.err("Vault refund failed for ${player.uniqueId} (amount: $amount).")
        }
    }

    private fun onPlayer(action: () -> Boolean): Boolean {
        val future = FutureTask(action)
        if (!plugin.isEnabled) return false
        val scheduled = player.scheduler.run(plugin, { future.run() }, { future.cancel(false) }) ?: return false
        return try {
            future.get(5, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            // Cancel only if the scheduler has not started the operation.
            if (scheduled.cancel() == io.papermc.paper.threadedregions.scheduler.ScheduledTask.CancelledState.CANCELLED_BY_CALLER) {
                false
            } else {
                future.get()
            }
        } catch (_: java.util.concurrent.CancellationException) {
            false
        }
    }
}
