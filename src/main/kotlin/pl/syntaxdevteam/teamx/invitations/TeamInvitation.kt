package pl.syntaxdevteam.teamx.invitations

import java.util.*

/**
 * Reprezentuje zaproszenie do drużyny.
 *
 * @property teamId identyfikator drużyny (np. numer lub unikalny identyfikator zapisanego teamu)
 * @property leaderUUID unikalny identyfikator gracza, który wysłał zaproszenie (lidera drużyny)
 * @property invitedUUID unikalny identyfikator gracza, który został zaproszony
 * @property timestamp znacznik czasu utworzenia zaproszenia; domyślnie ustawiany na aktualny czas
 */
data class TeamInvitation(
    val teamId: String,
    val leaderUUID: UUID,
    val invitedUUID: UUID,
    val timestamp: Long = System.currentTimeMillis()
)