package pl.syntaxdevteam.teamx.home

import java.util.UUID

data class ClanHome(
    val world: UUID, val x: Double, val y: Double, val z: Double, val yaw: Float, val pitch: Float
)
