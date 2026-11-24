package pl.syntaxdevteam.teamx.cache
import java.util.UUID
import pl.syntaxdevteam.teamx.home.ClanHome
import pl.syntaxdevteam.teamx.clans.ClanMember

data class CachedTeamData(
    val teamId: Int,
    val name: String,
    val format: String,
    val leaderName: String,
    val description: String,
    val createdAt: String,
    val memberCount: Int,
    val members: List<String>,
    val fullName: String,
    val tag: String,
    val leader: UUID,
    val roster: List<ClanMember>,
    val home: ClanHome?
)