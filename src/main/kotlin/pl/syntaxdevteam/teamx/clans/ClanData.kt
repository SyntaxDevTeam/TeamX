package pl.syntaxdevteam.teamx.clans

import java.util.UUID

enum class ClanRank { LEADER, MANAGER, ELITE, MEMBER }
enum class Specialization { ASSASSIN, BUILDER, MINER, ALCHEMIST }

data class ClanMember(val uuid: UUID, val rank: ClanRank, val specialization: Specialization?)
