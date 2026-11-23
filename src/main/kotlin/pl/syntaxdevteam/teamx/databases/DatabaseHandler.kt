package pl.syntaxdevteam.teamx.databases

import pl.syntaxdevteam.syntaxdb.Database
import pl.syntaxdevteam.syntaxdb.config.DatabaseType
import net.kyori.adventure.text.Component
import pl.syntaxdevteam.teamx.TeamX
import java.io.File
import java.io.IOException
import java.sql.*

class DatabaseHandler(private val plugin: TeamX) {
    private val logger = plugin.logger
    private val dbType = when (val configured = (plugin.config.getString("database.type") ?: "sqlite").trim().lowercase()) {
        "sqlite" -> DatabaseType.SQLITE
        "mysql", "mariadb" -> DatabaseType.MYSQL
        "postgresql" -> DatabaseType.POSTGRESQL
        else -> error("Unsupported TeamX database type '$configured'. Use sqlite, mysql, mariadb or postgresql.")
    }
    private val database = Database.create(dbType) {
        val config = plugin.config
        val dbName = config.getString("database.sql.dbname") ?: plugin.name
        if (dbType == DatabaseType.SQLITE) {
            file = File(plugin.dataFolder, "$dbName.db").path
            property("journal_mode", "WAL")
        } else {
            host = config.getString("database.sql.host", "localhost") ?: "localhost"
            port = config.getString("database.sql.port")?.toIntOrNull()?.takeIf { it > 0 }
            database = dbName
            username = config.getString("database.sql.username")
            password = config.getString("database.sql.password")
        }
        pool { poolName = "${plugin.name}-database" }
    }

    fun openConnection() = database.start()

    fun closeConnection() = database.close()

    /**
     * Metoda do tworzenia tabel w bazie danych.
     * Uwzględnia wszystkie wymagane różnice parametrów dla różnych baz danych.
     * Tworzy następujące tabele:
     *
     *  **teams**
     *   - id (INT AUTO_INCREMENT PRIMARY KEY)
     *   - name (VARCHAR(32) UNIQUE NOT NULL)
     *   - leader_uuid (CHAR(36) NOT NULL)
     *   - format (VARCHAR(10) UNIQUE)
     *   - created_at (TIMESTAMP DEFAULT CURRENT_TIMESTAMP)
     *   - description (TEXT DEFAULT NULL)
     *
     *  **team_members**
     *   - id (INT AUTO_INCREMENT PRIMARY KEY)
     *   - team_id (INT NOT NULL)
     *   - player_uuid (CHAR(36) NOT NULL)
     *   - role (ENUM('LEADER', 'MANAGER', 'MEMBER') NOT NULL DEFAULT 'MEMBER')
     *   - joined_at (TIMESTAMP DEFAULT CURRENT_TIMESTAMP)
     *   - FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
     *   - UNIQUE (player_uuid)
     *
     */
    fun createTables() {
        database.withConnection { conn ->
            logger.debug("Database connection established from createTables")
            conn.createStatement().use { statement ->
                val createTeamsTable = when (dbType) {
                    DatabaseType.SQLITE -> """
                    CREATE TABLE IF NOT EXISTS teams (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT UNIQUE NOT NULL,
                        leader_uuid CHAR(36) NOT NULL,
                        format TEXT UNIQUE,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        description TEXT DEFAULT NULL
                    );
                """.trimIndent()

                    DatabaseType.POSTGRESQL -> """
                    CREATE TABLE IF NOT EXISTS teams (
                        id SERIAL PRIMARY KEY,
                        name VARCHAR(32) UNIQUE NOT NULL,
                        leader_uuid CHAR(36) NOT NULL,
                        format VARCHAR(128) UNIQUE,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        description TEXT DEFAULT NULL
                    );
                """.trimIndent()

                    else -> """
                    CREATE TABLE IF NOT EXISTS teams (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        name VARCHAR(32) UNIQUE NOT NULL,
                        leader_uuid CHAR(36) NOT NULL,
                        format VARCHAR(128) UNIQUE,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        description TEXT DEFAULT NULL
                    );
                """.trimIndent()
                }
                statement.executeUpdate(createTeamsTable)
                logger.debug("Table 'teams' created successfully.")

                val createTeamMembersTable = when (dbType) {
                    DatabaseType.SQLITE -> """
                    CREATE TABLE IF NOT EXISTS team_members (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        team_id INT NOT NULL,
                        player_uuid CHAR(36) NOT NULL,
                        role TEXT CHECK(role IN ('LEADER', 'MANAGER', 'MEMBER')) NOT NULL DEFAULT 'MEMBER',
                        joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
                        UNIQUE (player_uuid)
                    );
                """.trimIndent()

                    DatabaseType.POSTGRESQL -> """
                    CREATE TABLE IF NOT EXISTS team_members (
                        id SERIAL PRIMARY KEY,
                        team_id INT NOT NULL,
                        player_uuid CHAR(36) NOT NULL,
                        role VARCHAR(10) CHECK(role IN ('LEADER', 'MANAGER', 'MEMBER')) NOT NULL DEFAULT 'MEMBER',
                        joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
                        UNIQUE (player_uuid)
                    );
                """.trimIndent()

                    else -> """
                    CREATE TABLE IF NOT EXISTS team_members (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        team_id INT NOT NULL,
                        player_uuid CHAR(36) NOT NULL,
                        role ENUM('LEADER', 'MANAGER', 'MEMBER') NOT NULL DEFAULT 'MEMBER',
                        joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
                        UNIQUE (player_uuid)
                    );

                """.trimIndent()
                }
                statement.executeUpdate(createTeamMembersTable)
                logger.debug("Table 'team_members' created successfully.")
            }
        }
        logger.debug("Table creation operations completed.")
    }

    /**
     * Dodaje nowy zespół do bazy danych.
     * Prosty wpis z nazwą, UUID lidera, formatem i opisem.
     * Zwraca `true` jeśli operacja zakończyła się sukcesem.
     * Zwraca `false` jeśli operacja zakończyła się niepowodzeniem.
     *
     * @param name
     * @param leaderUuid
     * @param description
     * @return
     */
    fun addTeam(name: String, leaderUuid: String, description: String?): Boolean {
        val cleanName = plugin.messageHandler.getPlainText(Component.text(name))
        return try {
            database.withConnection { conn ->
                logger.debug("Database connection established from addTeam")
                val query = """
                INSERT INTO teams (name, leader_uuid, format, created_at, description)
                VALUES (?, ?, NULL, CURRENT_TIMESTAMP, ?)
            """.trimIndent()
                conn.prepareStatement(query).use { preparedStatement ->
                    preparedStatement.setString(1, cleanName)
                    preparedStatement.setString(2, leaderUuid)
                    preparedStatement.setString(3, description)
                    preparedStatement.executeUpdate()
                    plugin.logger.debug("Team $name added to the database.")
                    true
                }
            }
        } catch (e: SQLException) {
            plugin.logger.err("Failed to add team $name. ${e.message}")
            false
        }
    }

    /**
     * Aktualizuje nazwę zespołu w bazie danych.
     * Zwraca `true` jeśli operacja zakończyła się sukcesem.
     * Zwraca `false` jeśli operacja zakończyła się niepowodzeniem.
     *
     * @param teamId
     * @param newName
     * @return
     */
    fun updateTeamName(teamId: Int, newName: String): Boolean {
        val query = "UPDATE teams SET name = ? WHERE id = ?"
        return try {
            database.withConnection { conn ->
                conn.prepareStatement(query).use { preparedStatement ->
                    preparedStatement.setString(1, newName)
                    preparedStatement.setInt(2, teamId)
                    val rowsAffected = preparedStatement.executeUpdate()
                    rowsAffected > 0
                }
            }
        } catch (e: SQLException) {
            plugin.logger.err("Failed to update team name for team ID: $teamId. ${e.message}")
            false
        }
    }

    /**
     * Aktualizuje format zespołu w bazie danych.
     * Zwraca `true` jeśli operacja zakończyła się sukcesem.
     * Zwraca `false` jeśli operacja zakończyła się niepowodzeniem.
     *
     * @param teamId
     * @param newFormat
     * @return
     */
    fun updateTeamFormat(teamId: Int, newFormat: String): Boolean {
        val query = "UPDATE teams SET format = ? WHERE id = ?"
        return try {
            database.withConnection { conn ->
                conn.prepareStatement(query).use { preparedStatement ->
                    preparedStatement.setString(1, newFormat)
                    preparedStatement.setInt(2, teamId)
                    val rowsAffected = preparedStatement.executeUpdate()
                    rowsAffected > 0
                }
            }
        } catch (e: SQLException) {
            plugin.logger.err("Failed to update team name for team ID: $teamId. ${e.message}")
            false
        }
    }

    fun updateTeamDescription(teamId: Int, newDescription: String): Boolean {
        val query = "UPDATE teams SET description = ? WHERE id = ?"
        return try {
            database.withConnection { conn ->
                conn.prepareStatement(query).use { preparedStatement ->
                    preparedStatement.setString(1, newDescription)
                    preparedStatement.setInt(2, teamId)
                    val rowsAffected = preparedStatement.executeUpdate()
                    rowsAffected > 0
                }
            }
        } catch (e: SQLException) {
            plugin.logger.err("Failed to update team description for team ID: $teamId. ${e.message}")
            false
        }
    }

    /**
     * Sprawdza, czy nazwa zespołu już istnieje w bazie danych.
     * Zwraca `true` jeśli nazwa zespołu już istnieje.
     * Zwraca `false` jeśli nazwa zespołu nie istnieje.
     *
     * @param teamName
     * @return
     */
    fun isTeamNameExists(teamName: String): Boolean {
        val query = "SELECT COUNT(*) FROM teams WHERE name = ?"
        return try {
            database.withConnection { conn ->
                conn.prepareStatement(query).use { preparedStatement ->
                    preparedStatement.setString(1, teamName)
                    val resultSet = preparedStatement.executeQuery()
                    resultSet.next() && resultSet.getInt(1) > 0
                }
            }
        } catch (e: SQLException) {
            plugin.logger.err("Failed to check if team name exists: ${e.message}")
            false
        }
    }

    /**
     * Usuwa zespół z bazy danych.
     * Możliwe jest usunięcie zespołu na podstawie nazwy lub identyfikatora.
     * Zwraca `true` jeśli operacja zakończyła się sukcesem.
     * Zwraca `false` jeśli operacja zakończyła się niepowodzeniem.
     *
     * @param teamIdentifier
     * @param byName
     * @return
     */
    fun removeTeam(teamIdentifier: Int, byName: Boolean = true): Boolean {
        return try {
            database.withConnection { conn ->
                logger.debug("Database connection established from removeTeam")
                val query = if (byName) {
                    "DELETE FROM teams WHERE name = ?"
                } else {
                    "DELETE FROM teams WHERE id = ?"
                }
                conn.prepareStatement(query).use { preparedStatement ->
                    if (byName) {
                        preparedStatement.setString(1, teamIdentifier.toString())
                    } else {
                        preparedStatement.setInt(1, teamIdentifier)
                    }
                    val rowsAffected = preparedStatement.executeUpdate()
                    if (rowsAffected > 0) {
                        plugin.logger.debug("Team $teamIdentifier removed from the database.")
                        true
                    } else {
                        plugin.logger.warning("No team found with identifier: $teamIdentifier.")
                        false
                    }
                }
            }
        } catch (e: SQLException) {
            plugin.logger.err("Failed to remove team with identifier: $teamIdentifier. ${e.message}")
            false
        }
    }

    fun getTeamList(): List<String> {
        val query = "SELECT name FROM teams"
        val teamNames = mutableListOf<String>()

        return try {
            database.withConnection { conn ->
                conn.prepareStatement(query).use { preparedStatement ->
                    val resultSet = preparedStatement.executeQuery()
                    while (resultSet.next()) {
                        teamNames.add(resultSet.getString("name"))
                    }
                }
                teamNames
            }
        } catch (e: SQLException) {
            plugin.logger.err("Failed to retrieve team names. ${e.message}")
            emptyList()
        }
    }

    fun getTeamByUUID(playerUuid: String): Array<String>? {
        return try {
            database.withConnection { conn ->
                logger.debug("Database connection established from getTeamByUUID")

                val teamIdQuery = "SELECT team_id FROM team_members WHERE player_uuid = ?"
                val teamId = conn.prepareStatement(teamIdQuery).use { preparedStatement ->
                    preparedStatement.setString(1, playerUuid)
                    val resultSet = preparedStatement.executeQuery()
                    if (resultSet.next()) {
                        resultSet.getInt("team_id")
                    } else {
                        null
                    }
                }

                val teamQuery = if (teamId != null) {
                    "SELECT * FROM teams WHERE id = ?"
                } else {
                    "SELECT * FROM teams WHERE leader_uuid = ?"
                }

                conn.prepareStatement(teamQuery).use { preparedStatement ->
                    if (teamId != null) {
                        preparedStatement.setInt(1, teamId)
                    } else {
                        preparedStatement.setString(1, playerUuid)
                    }
                    val resultSet = preparedStatement.executeQuery()
                    if (resultSet.next()) {
                        val teamData = arrayOf(
                            resultSet.getString("id"),
                            resultSet.getString("name"),
                            resultSet.getString("leader_uuid"),
                            resultSet.getString("format")
                                ?: plugin.config.getString("team.default-format")
                                ?: plugin.config.getString("default-format").orEmpty(),
                            resultSet.getString("created_at"),
                            resultSet.getString("description").orEmpty()
                        )
                        plugin.logger.debug("Team data retrieved for player UUID: $playerUuid")
                        teamData
                    } else {
                        plugin.logger.debug("No team found for player UUID: $playerUuid")
                        null
                    }
                }
            }
        } catch (e: SQLException) {
            plugin.logger.debug("Failed to retrieve team for player UUID: $playerUuid. ${e.message}")
            null
        }
    }

    fun getTeamByName(name: String): Array<String>? {
        val query = "SELECT * FROM teams WHERE name = ?"
        return try {
            database.withConnection { conn ->
                conn.prepareStatement(query).use { preparedStatement ->
                    preparedStatement.setString(1, name)
                    val resultSet = preparedStatement.executeQuery()
                    if (resultSet.next()) {
                        val teamData = arrayOf(
                            resultSet.getString("id"),
                            resultSet.getString("name"),
                            resultSet.getString("leader_uuid"),
                            resultSet.getString("created_at"),
                            resultSet.getString("description").orEmpty()
                        )
                        plugin.logger.debug("Team data retrieved for team name: $name")
                        teamData
                    } else {
                        plugin.logger.warning("No team found with name: $name")
                        null
                    }
                }
            }
        } catch (e: SQLException) {
            plugin.logger.debug("Failed to retrieve team for name: $name. ${e.message}")
            null
        }
    }

    fun addPlayerToTeam(teamId: Int, playerUuid: String): Boolean {
        val query = "INSERT INTO team_members (team_id, player_uuid, joined_at) VALUES (?, ?, CURRENT_TIMESTAMP)"
        return try {
            database.withConnection { conn ->
                conn.prepareStatement(query).use { preparedStatement ->
                    preparedStatement.setInt(1, teamId)
                    preparedStatement.setString(2, playerUuid)
                    preparedStatement.executeUpdate()
                    plugin.logger.debug("Player $playerUuid added to team ID $teamId.")
                    true
                }
            }
        } catch (e: SQLException) {
            plugin.logger.err("Failed to add player $playerUuid to team ID $teamId. ${e.message}")
            false
        }
    }

    fun removePlayerFromTeam(teamId: Int, playerUuid: String): Boolean {
        val query = "DELETE FROM team_members WHERE team_id = ? AND player_uuid = ?"
        return try {
            database.withConnection { conn ->
                conn.prepareStatement(query).use { preparedStatement ->
                    preparedStatement.setInt(1, teamId)
                    preparedStatement.setString(2, playerUuid)
                    val rowsAffected = preparedStatement.executeUpdate()
                    if (rowsAffected > 0) {
                        plugin.logger.debug("Player $playerUuid removed from team ID $teamId.")
                        true
                    } else {
                        plugin.logger.debug("No player $playerUuid found in team ID $teamId.")
                        false
                    }
                }
            }
        } catch (e: SQLException) {
            plugin.logger.err("Failed to remove player $playerUuid from team ID $teamId. ${e.message}")
            false
        }
    }

    fun getMemberCountByUUID(leaderUuid: String): Int {
        val teamId = getTeamIdByLeaderUUID(leaderUuid) ?: return 0
        val query = "SELECT COUNT(*) FROM team_members WHERE team_id = ?"

        return try {
            database.withConnection { conn ->
                conn.prepareStatement(query).use { preparedStatement ->
                    preparedStatement.setInt(1, teamId)
                    val resultSet = preparedStatement.executeQuery()
                    if (resultSet.next()) {
                        resultSet.getInt(1)+1
                    } else {
                        0
                    }
                }
            }
        } catch (e: SQLException) {
            plugin.logger.err("Failed to get member count for team with leader UUID: $leaderUuid. ${e.message}")
            0
        }
    }

    fun setTeamLeader(teamId: Int, newLeaderUuid: String): Boolean {
        val query = "UPDATE teams SET leader_uuid = ? WHERE id = ?"
        return try {
            database.withConnection { conn ->
                conn.prepareStatement(query).use { preparedStatement ->
                    preparedStatement.setString(1, newLeaderUuid)
                    preparedStatement.setInt(2, teamId)
                    val rowsAffected = preparedStatement.executeUpdate()
                    rowsAffected > 0
                }
            }
        } catch (e: SQLException) {
            plugin.logger.err("Failed to set team leader for team ID: $teamId. ${e.message}")
            false
        }
    }


    fun getMemberUUIDsByUUID(leaderUuid: String): List<String> {
        val teamId = getTeamIdByLeaderUUID(leaderUuid) ?: return emptyList()
        val query = "SELECT player_uuid FROM team_members WHERE team_id = ?"

        return try {
            database.withConnection { conn ->
                conn.prepareStatement(query).use { preparedStatement ->
                    preparedStatement.setInt(1, teamId)
                    val resultSet = preparedStatement.executeQuery()
                    val uuids = mutableListOf<String>()
                    while (resultSet.next()) {
                        uuids.add(resultSet.getString("player_uuid"))
                    }
                    uuids
                }
            }
        } catch (e: SQLException) {
            plugin.logger.err("Failed to get member UUIDs for team with leader UUID: $leaderUuid. ${e.message}")
            emptyList()
        }
    }

    private fun getTeamIdByLeaderUUID(leaderUuid: String): Int? {
        val query = "SELECT id FROM teams WHERE leader_uuid = ?"

        return try {
            database.withConnection { conn ->
                conn.prepareStatement(query).use { preparedStatement ->
                    preparedStatement.setString(1, leaderUuid)
                    val resultSet = preparedStatement.executeQuery()
                    if (resultSet.next()) {
                        resultSet.getInt("id")
                    } else {
                        null
                    }
                }
            }
        } catch (e: SQLException) {
            plugin.logger.err("Failed to get team ID for leader UUID: $leaderUuid. ${e.message}")
            null
        }
    }

    fun getLeaderByTeamId(teamId: Int): String? {
        val query = "SELECT leader_uuid FROM teams WHERE id = ?"
        return try {
            database.withConnection { conn ->
                conn.prepareStatement(query).use { preparedStatement ->
                    preparedStatement.setInt(1, teamId)
                    val resultSet = preparedStatement.executeQuery()
                    if (resultSet.next()) {
                        resultSet.getString("leader_uuid")
                    } else {
                        null
                    }
                }
            }
        } catch (e: SQLException) {
            plugin.logger.err("Failed to get leader UUID for team ID: $teamId. ${e.message}")
            null
        }
    }
    
    fun getTeamMembers(teamId: Int): List<String> {
        val query = "SELECT player_uuid FROM team_members WHERE team_id = ?"
        val members = mutableListOf<String>()

        return try {
            database.withConnection { conn ->
                conn.prepareStatement(query).use { preparedStatement ->
                    preparedStatement.setInt(1, teamId)
                    val resultSet = preparedStatement.executeQuery()
                    while (resultSet.next()) {
                        members.add(resultSet.getString("player_uuid"))
                    }
                }
            }
            members
        } catch (e: SQLException) {
            plugin.logger.err("Failed to get team members for team ID: $teamId. ${e.message}")
            emptyList()
        }
    }

    /**
     * Exports the database to a SQL dump file.
     *
     * This method retrieves the data from the database tables and writes it to a SQL dump file.
     * The dump file contains valid SQL statements to recreate the tables and insert the data.
     *
     * The dump file is saved in the `dump` directory inside the plugin's data folder.
     */
    fun exportDatabase() {
        val tables = listOf("teams", "team_members")
        try {
            database.withConnection { conn ->
                val dumpDir = File(plugin.dataFolder, "dump")
                if (!dumpDir.exists()) {
                    dumpDir.mkdirs()
                }
                val writer = File(dumpDir, "backup.sql").bufferedWriter()
                for (table in tables) {
                    val resultSet = conn.createStatement().executeQuery("SELECT * FROM $table")
                    val metaData = resultSet.metaData
                    val columnCount = metaData.columnCount

                    if (!resultSet.isBeforeFirst) {
                        continue
                    }

                    writer.write("INSERT INTO $table VALUES\n")
                    var first = true
                    while (resultSet.next()) {
                        if (!first) {
                            writer.write(",\n")
                        }
                        first = false
                        writer.write("(")
                        for (i in 1..columnCount) {
                            val value = resultSet.getObject(i)
                            if (value == null) {
                                writer.write("NULL")
                            } else {
                                writer.write("'${value.toString().replace("'", "''")}'")
                            }
                            if (i < columnCount) writer.write(", ")
                        }
                        writer.write(")")
                    }
                    writer.write(";\n")
                }
                writer.close()
                plugin.logger.success("Database exported to ${dumpDir}/backup.sql")
            }
        } catch (e: SQLException) {
            plugin.logger.err("Failed to export database. ${e.message}")
        } catch (e: IOException) {
            plugin.logger.err("Failed to write to file. ${e.message}")
        }
    }

    /**
     * Imports the database from a SQL dump file.
     *
     * This method reads the SQL dump file line by line and executes the SQL statements to recreate
     * the database tables and insert the data. The dump file must contain valid SQL statements
     * separated by semicolons.
     */
    fun importDatabase() {
        val filePath = File(plugin.dataFolder, "dump/backup.sql").absolutePath
        try {
            database.withConnection { conn ->
                val lines = File(filePath).readLines()
                val statement = conn.createStatement()
                val sql = StringBuilder()
                for (line in lines) {
                    sql.append(line)
                    if (line.trim().endsWith(";")) {
                        statement.execute(sql.toString())
                        sql.setLength(0)
                    }
                }
                plugin.logger.success("Database imported from $filePath")
            }
        } catch (e: SQLException) {
            plugin.logger.err("Failed to import database. ${e.message}")
        } catch (e: IOException) {
            plugin.logger.err("Failed to read from file. ${e.message}")
        }
    }
}