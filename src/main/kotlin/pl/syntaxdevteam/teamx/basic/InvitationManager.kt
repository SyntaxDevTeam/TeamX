package pl.syntaxdevteam.teamx.basic

import pl.syntaxdevteam.teamx.messages.teamMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import pl.syntaxdevteam.teamx.TeamX
import java.util.*

/**
 * Klasa zarządzająca zaproszeniami do drużyny.
 *
 * Klasa ta odpowiada za:
 * - wysyłanie zaproszeń (przechowywanie zaproszenia w pamięci i wysyłanie wiadomości do zapraszanego gracza),
 * - obsługę akceptacji i odrzucania zaproszeń (usuwanie zaproszenia i wykonywanie akcji, np. dodawanie gracza do drużyny oraz powiadamianie lidera),
 * - automatyczne wygaszanie zaproszeń po upływie określonego czasu (120 sekund).
 *
 * @param plugin instancja głównej klasy pluginu (TeamX), która umożliwia dostęp do np. messageHandler, uuidManager czy databaseHandler.
 */
class InvitationManager(private val plugin: TeamX) {
    /**
     * Przechowuje aktywne zaproszenia, gdzie kluczem jest unikalny identyfikator zapraszanego gracza.
     */
    private val invitations = mutableMapOf<UUID, TeamInvitation>()
    private val mH = plugin.messageHandler

    /**
     * Wysyła zaproszenie do drużyny.
     *
     * Metoda tworzy nowe zaproszenie o podanych parametrach:
     * - zapisywane jest ono w mapie [invitations] z kluczem unikalnym dla zapraszanego gracza,
     * - wysyłana jest wiadomość do zapraszanego gracza informująca o otrzymaniu zaproszenia,
     * - zaplanowane jest automatyczne wygaszenie zaproszenia po 120 sekundach.
     *
     * @param teamId identyfikator drużyny, do której gracz jest zapraszany
     * @param leader gracz wysyłający zaproszenie (lider drużyny)
     * @param invited gracz zapraszany
     */
    fun sendInvitation(teamId: String, leader: Player, invited: Player) {
        val invitation = TeamInvitation(teamId, leader.uniqueId, invited.uniqueId)
        invitations[invited.uniqueId] = invitation

        // Informujemy zapraszanego gracza o otrzymaniu zaproszenia
        invited.sendMessage(mH.teamMessage("invitation", "received", mapOf("leader" to leader.name)))

        // Ustawiamy automatyczne wygaszenie zaproszenia po 120 sekundach
        scheduleExpiration(invited.uniqueId)
    }

    /**
     * Akceptuje zaproszenie.
     *
     * Metoda wykonuje następujące kroki:
     * - usuwa zaproszenie z mapy [invitations],
     * - pobiera UUID gracza (przez uuidManager) oraz nazwę drużyny (z bazy danych, przez databaseHandler),
     * - wysyła wiadomość do zapraszanego gracza potwierdzającą akceptację zaproszenia,
     * - dodaje gracza do drużyny przy użyciu metody [addPlayerToTeam] (przez databaseHandler),
     * - wysyła powiadomienie do lidera o tym, że gracz przyjął zaproszenie.
     *
     * Jeśli zaproszenie nie istnieje (np. wygasło lub zostało wcześniej obsłużone), zapraszany otrzyma odpowiedni komunikat.
     *
     * @param invited gracz, który przyjmuje zaproszenie
     */
    fun acceptInvitation(invited: Player) {
        val invitation = invitations.remove(invited.uniqueId)
        val uuid = invited.uniqueId
        val name = plugin.databaseHandler.getTeamByUUID(invitation?.leaderUUID.toString())
        val teamName = name?.get(1)
        if (invitation != null) {
            invited.sendMessage(mH.teamMessage("invitation", "accepted", mapOf("team_name" to teamName!!)))
            plugin.databaseHandler.addPlayerToTeam(invitation.teamId.toInt(), uuid.toString())

            Bukkit.getPlayer(invitation.leaderUUID)?.sendMessage(
                mH.teamMessage("invitation", "player_accepted", mapOf("player" to invited.name))
            )
        } else {
            invited.sendMessage(mH.teamMessage("invitation", "no_invitation"))
        }
    }

    /**
     * Odrzuca zaproszenie.
     *
     * Metoda wykonuje następujące kroki:
     * - usuwa zaproszenie z mapy [invitations],
     * - wysyła wiadomość do gracza informującą o odrzuceniu zaproszenia,
     * - wysyła powiadomienie do lidera (jeśli jest online), że gracz odrzucił zaproszenie.
     *
     * Jeśli zaproszenie nie istnieje, gracz otrzymuje komunikat o braku aktywnego zaproszenia.
     *
     * @param invited gracz, który odrzuca zaproszenie
     */
    fun declineInvitation(invited: Player) {
        val invitation = invitations.remove(invited.uniqueId)
        if (invitation != null) {
            invited.sendMessage(mH.teamMessage("invitation", "denied"))
            // Powiadom lidera, że gracz odrzucił zaproszenie
            Bukkit.getPlayer(invitation.leaderUUID)?.sendMessage(
                plugin.messageHandler.teamMessage("invitation", "player_denied", mapOf("player" to invited.name))
            )
        } else {
            invited.sendMessage(plugin.messageHandler.teamMessage("invitation", "no_invitation"))
        }
    }

    /**
     * Ustawia automatyczne wygaszenie zaproszenia.
     *
     * Metoda planuje zadanie, które po upływie 120 sekund:
     * - sprawdza, czy zaproszenie nadal istnieje w mapie [invitations],
     * - usuwa zaproszenie, jeśli nadal jest aktywne,
     * - wysyła wiadomość do zapraszanego gracza informującą, że zaproszenie wygasło.
     *
     * @param invitedUUID unikalny identyfikator gracza, dla którego zaproszenie ma wygasnąć
     */
    private fun scheduleExpiration(invitedUUID: UUID) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val invitation = invitations.remove(invitedUUID)
            if (invitation != null) {
                Bukkit.getPlayer(invitedUUID)?.sendMessage(mH.teamMessage("invitation", "expired"))
            }
        }, 120 * 20L)
    }
}
