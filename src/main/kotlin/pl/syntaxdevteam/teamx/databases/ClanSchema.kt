package pl.syntaxdevteam.teamx.databases

import java.sql.Connection
import java.util.Locale

internal object ClanSchema {
    private val tagPattern = Regex("[A-Za-z0-9]{1,8}")

    fun normalizeTag(input: String): String? =
        input.takeIf(tagPattern::matches)?.uppercase(Locale.ROOT)

    fun allocateTag(name: String, reserved: MutableSet<String>): String {
        val stem = name.uppercase(Locale.ROOT).filter { it in 'A'..'Z' || it in '0'..'9' }
            .take(8).ifEmpty { "TEAM" }
        if (reserved.add(stem)) return stem
        var number = 2L
        while (true) {
            val suffix = number++.toString(36).uppercase(Locale.ROOT)
            val candidate = stem.take((8 - suffix.length).coerceAtLeast(0)) + suffix
            if (reserved.add(candidate)) return candidate
        }
    }

    fun migrate(connection: Connection) {
        addColumn(connection, "teams", "tag", "VARCHAR(8)")
        addColumn(connection, "team_members", "elite", "SMALLINT NOT NULL DEFAULT 0")
        addColumn(connection, "team_members", "specialization", "VARCHAR(12)")
        connection.createStatement().use {
            it.executeUpdate("""
                CREATE TABLE IF NOT EXISTS team_homes (
                    team_id INT PRIMARY KEY,
                    world_uuid CHAR(36) NOT NULL,
                    x DOUBLE PRECISION NOT NULL,
                    y DOUBLE PRECISION NOT NULL,
                    z DOUBLE PRECISION NOT NULL,
                    yaw REAL NOT NULL,
                    pitch REAL NOT NULL,
                    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
                )
            """.trimIndent())
        }

        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT leader_uuid FROM teams GROUP BY leader_uuid HAVING COUNT(*) > 1").use {
                check(!it.next()) { "A legacy founder leads multiple clans. Assign distinct leaders before migrating." }
            }
        }
        val reserved = reservedTags(connection)
        val missing = connection.createStatement().use { statement ->
            statement.executeQuery("SELECT id, name FROM teams WHERE tag IS NULL ORDER BY id").use { rows ->
                buildList { while (rows.next()) add(rows.getInt(1) to rows.getString(2)) }
            }
        }
        connection.prepareStatement("UPDATE teams SET tag = ? WHERE id = ? AND tag IS NULL").use { update ->
            for ((id, name) in missing) {
                update.setString(1, allocateTag(name, reserved))
                update.setInt(2, id)
                update.executeUpdate()
            }
        }
        val indexed = connection.metaData.getIndexInfo(connection.catalog, schema(connection), "teams", true, false).use {
            generateSequence { if (it.next()) it.getString("INDEX_NAME").orEmpty() else null }
                .any { name -> name.equals("teamx_unique_tag", ignoreCase = true) }
        }
        if (!indexed) connection.createStatement().use {
            it.executeUpdate("CREATE UNIQUE INDEX teamx_unique_tag ON teams (tag)")
        }

        // Legacy founders could exist only in teams. Keep the original clan IDs and reconcile their rows.
        connection.createStatement().use {
            it.executeUpdate("""
                DELETE FROM team_members WHERE EXISTS (
                    SELECT 1 FROM teams WHERE teams.leader_uuid = team_members.player_uuid
                    AND teams.id <> team_members.team_id
                )
            """.trimIndent())
            it.executeUpdate("""
                UPDATE team_members SET role = 'MEMBER' WHERE role = 'LEADER' AND NOT EXISTS (
                    SELECT 1 FROM teams WHERE teams.id = team_members.team_id
                    AND teams.leader_uuid = team_members.player_uuid
                )
            """.trimIndent())
            it.executeUpdate("""
                UPDATE team_members SET role = 'LEADER', elite = 0 WHERE role <> 'LEADER' AND EXISTS (
                    SELECT 1 FROM teams WHERE teams.id = team_members.team_id
                    AND teams.leader_uuid = team_members.player_uuid
                )
            """.trimIndent())
            it.executeUpdate("""
                INSERT INTO team_members (team_id, player_uuid, role)
                SELECT id, leader_uuid, 'LEADER' FROM teams WHERE NOT EXISTS (
                    SELECT 1 FROM team_members WHERE team_members.player_uuid = teams.leader_uuid
                )
            """.trimIndent())
        }
    }

    fun reservedTags(connection: Connection): MutableSet<String> =
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT tag FROM teams WHERE tag IS NOT NULL").use { rows ->
                buildSet { while (rows.next()) add(rows.getString(1).uppercase(Locale.ROOT)) }.toMutableSet()
            }
        }

    private fun schema(connection: Connection): String? =
        if (connection.metaData.databaseProductName == "PostgreSQL") connection.schema else null

    private fun addColumn(connection: Connection, table: String, name: String, definition: String) {
        val exists = connection.metaData.getColumns(connection.catalog, schema(connection), table, name).use { it.next() }
        if (!exists) connection.createStatement().use {
            it.executeUpdate("ALTER TABLE $table ADD COLUMN $name $definition")
        }
    }
}
