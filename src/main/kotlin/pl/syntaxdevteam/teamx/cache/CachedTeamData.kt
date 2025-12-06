package pl.syntaxdevteam.teamx.cache

data class CachedTeamData(
    val teamId: Int,
    val name: String,
    val format: String,
    val leaderName: String,
    val description: String,
    val createdAt: String,
    val memberCount: Int,
    val members: List<String>,
    val fullName: String
)