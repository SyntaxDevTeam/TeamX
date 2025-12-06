package pl.syntaxdevteam.teamx.databases

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.kyori.adventure.text.Component
import pl.syntaxdevteam.teamx.TeamX
import java.io.File
import java.io.IOException
import java.sql.*

class DatabaseHandler(private val plugin: TeamX) {
    private var dataSource: HikariDataSource? = null
    private var logger = plugin.logger
    private val dbType = plugin.config.getString("database.type")?.lowercase() ?: "sqlite"

    init {
        setupDataSource()
    }

    /**
     * Configures the HikariCP data source for database connections.
     *
     * This method sets up the data source based on the database type defined in the plugin configuration.
     * It supports the following database types:
     * - MySQL/MariaDB
     * - PostgreSQL
     * - SQLite
     * - H2
     *
     * For SQLite databases, specific pool settings are applied to minimize resource usage.
     * For other database types, the pool is configured with more robust settings suitable for production.
     *
     * @throws IllegalArgumentException If the database type is unsupported.
     * @throws Exception If an error occurs during data source initialization.
     */

    private fun setupDataSource() {
        val hikariConfig = HikariConfig()
        val dbName = plugin.config.getString("database.sql.dbname") ?: plugin.name
        val user = plugin.config.getString("database.sql.username") ?: "ROOT"
        val password = plugin.config.getString("database.sql.password") ?: "U5eV3ryStr0ngP4ssw0rd"
        when (dbType) {
            "mysql", "mariadb" -> {
                hikariConfig.jdbcUrl =
                    "jdbc:mysql://${plugin.config.getString("database.sql.host")}:${plugin.config.getString("database.sql.port")}/$dbName"
                hikariConfig.username = user
                hikariConfig.password = password
                hikariConfig.driverClassName = "com.mysql.cj.jdbc.Driver"
            }

            "postgresql" -> {
                hikariConfig.jdbcUrl =
                    "jdbc:postgresql://${plugin.config.getString("database.sql.host")}:${plugin.config.getString("database.sql.port")}/$dbName"
                hikariConfig.username = user
                hikariConfig.password = password
                hikariConfig.driverClassName = "org.postgresql.Driver"
            }

            "sqlite" -> {
                hikariConfig.jdbcUrl = "jdbc:sqlite:${plugin.dataFolder}/$dbName.db"
                hikariConfig.driverClassName = "org.sqlite.JDBC"
            }

            "h2" -> {
                hikariConfig.jdbcUrl = "jdbc:h2:./${plugin.dataFolder}/$dbName"
                hikariConfig.username = user
                hikariConfig.password = password
                hikariConfig.driverClassName = "org.h2.Driver"
            }

            else -> throw IllegalArgumentException("Unsupported database type: $dbType")
        }
        if (dbType == "sqlite") {
            hikariConfig.maximumPoolSize = 2
            hikariConfig.minimumIdle = 1
            hikariConfig.connectionTimeout = 30000
            hikariConfig.idleTimeout = 10000
            hikariConfig.maxLifetime = 60000
            hikariConfig.keepaliveTime = 30000
        } else {
            hikariConfig.maximumPoolSize = 10
            hikariConfig.minimumIdle = 2
            hikariConfig.connectionTimeout = 30000
            hikariConfig.idleTimeout = 600000
            hikariConfig.maxLifetime = 1800000
            hikariConfig.keepaliveTime = 900000
            hikariConfig.leakDetectionThreshold = 2000
        }

        logger.debug("Setting up data source for database type: $dbType")
        try {
            dataSource = HikariDataSource(hikariConfig)
            logger.debug("HikariCP data source initialized successfully for $dbType.")
        } catch (e: Exception) {
            logger.err("Failed to initialize HikariCP data source: ${e.message}")
            throw e
        }
    }

    /**
     * Ensures that the database connection is open.
     *
     * If the data source is not initialized, this method will invoke `setupDataSource`
     * to configure and start the connection pool.
     */
    fun openConnection() {
        if (dataSource == null) {
            setupDataSource()
        }
    }

    /**
     * Closes the HikariCP connection pool.
     *
     * This method gracefully shuts down the data source, releasing all resources associated with
     * the connection pool. It also logs the pool's statistics, such as total, active, and idle connections.
     *
     * Any errors encountered during shutdown are logged but do not interrupt the process.
     */
    fun closeConnection() {
        try {
            dataSource?.close()
            logger.info("HikariCP pool shut down. Total=${dataSource?.hikariPoolMXBean?.totalConnections}, Active=${dataSource?.hikariPoolMXBean?.activeConnections}, Idle=${dataSource?.hikariPoolMXBean?.idleConnections}")
        } catch (e: SQLException) {
            logger.err("Error while closing HikariCP pool: ${e.message}")
        }
    }

    /**
     * Obtains a connection from the HikariCP data source.
     *
     * This method retrieves a connection from the pool and, if the database type is SQLite,
     * enables Write-Ahead Logging (WAL) mode to improve performance.
     *
     * Connections must be properly closed after use to avoid resource leaks.
     *
     * @return A valid `Connection` object, or `null` if the connection could not be established.
     */
    private fun getConnection(): Connection? {
        return try {
            val connection = dataSource?.connection
            if (connection != null && dbType == "sqlite") {
                enableSQLiteWAL(connection)
            }
            logger.debug("Connection obtained: Active=${dataSource?.hikariPoolMXBean?.activeConnections}, Idle=${dataSource?.hikariPoolMXBean?.idleConnections}")
            connection
        } catch (e: SQLException) {
            logger.err("Failed to get connection. ${e.message}")
            null
        }
    }

    /**
     * Enables Write-Ahead Logging (WAL) mode for SQLite.
     *
     * WAL mode improves SQLite's performance by allowing concurrent reads and writes.
     * This method executes the `PRAGMA journal_mode=WAL;` statement on the provided connection.
     *
     * @param connection The active SQLite connection.
     */
    private fun enableSQLiteWAL(connection: Connection) {
        logger.debug("SQLite connection detected! I'm enabling WAL mode")
        try {
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA journal_mode=WAL;")
                logger.debug("SQLite WAL mode enabled.")
            }
        } catch (e: SQLException) {
            logger.err("Failed to enable SQLite WAL mode. ${e.message}")
        }
    }

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
        getConnection()?.use { conn ->
            logger.debug("Database connection established from createTables")
            conn.createStatement().use { statement ->
                try {
                    val createTeamsTable = when (dbType) {
                        "sqlite" -> """
                        CREATE TABLE IF NOT EXISTS teams (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            name TEXT UNIQUE NOT NULL,
                            leader_uuid CHAR(36) NOT NULL,
                            format TEXT UNIQUE,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            description TEXT DEFAULT NULL
                        );
                    """.trimIndent()

                        "postgresql" -> """
                        CREATE TABLE IF NOT EXISTS teams (
                            id SERIAL PRIMARY KEY,
                            name VARCHAR(32) UNIQUE NOT NULL,
                            leader_uuid CHAR(36) NOT NULL,
                            format VARCHAR(128) UNIQUE,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            description TEXT DEFAULT NULL
                        );
                    """.trimIndent()

                        "h2" -> """
                        CREATE TABLE IF NOT EXISTS teams (
                            id INT AUTO_INCREMENT PRIMARY KEY,
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
                        "sqlite" -> """
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

                        "postgresql" -> """
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

                        "h2" -> """
                        CREATE TABLE IF NOT EXISTS team_members (
                            id INT AUTO_INCREMENT PRIMARY KEY,
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
                } catch (ex: SQLException) {
                    logger.err("Error creating tables: ${ex.message}")
                }
            }
        } ?: logger.err("No database connection available.")
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
        val format = plugin.config.getString("team.default-format") ?: ""
        val cleanName = plugin.messageHandler.getPlainText(Component.text(name))
        return try {
            getConnection()?.use { conn ->
                logger.debug("Database connection established from addTeam")
                val query = """
                INSERT INTO teams (name, leader_uuid, format, created_at, description)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?)
            """.trimIndent()
                conn.prepareStatement(query).use { preparedStatement ->
                    preparedStatement.setString(1, cleanName)
                    preparedStatement.setString(2, leaderUuid)
                    preparedStatement.setString(3, format)
                    preparedStatement.setString(4, description)
                    preparedStatement.executeUpdate()
                    plugin.logger.debug("Team $name added to the database.")
                    true
                }
            } ?: throw SQLException("No connection available")
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
            getConnection()?.use { conn ->
                conn.prepareStatement(query).use { preparedStatement ->
                    preparedStatement.setString(1, newName)
                    preparedStatement.setInt(2, teamId)
                    val rowsAffected = preparedStatement.executeUpdate()
                    rowsAffected > 0
                }
            } ?: false
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
            getConnection()?.use { conn ->
                conn.prepareStatement(query).use { preparedStatement ->
                    preparedStatement.setString(1, newFormat)
                    preparedStatement.setInt(2, teamId)
                    val rowsAffected = preparedStatement.executeUpdate()
                    rowsAffected > 0
                }
            } ?: false
        } catch (e: SQLException) {
            plugin.logger.err("Failed to update team name for team ID: $teamId. ${e.message}")
            false
        }
    }

    fun updateTeamDescription(teamId: Int, newDescription: String): Boolean {
        val query = "UPDATE teams SET description = ? WHERE id = ?"
        return try {
            getConnection()?.use { conn ->
                conn.prepareStatement(query).use { preparedStatement ->
                    preparedStatement.setString(1, newDescription)
                    preparedStatement.setInt(2, teamId)
                    val rowsAffected = preparedStatement.executeUpdate()
                    rowsAffected > 0
                }
            } ?: false
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
            getConnection()?.use { conn ->
                conn.prepareStatement(query).use { preparedStatement ->
                    preparedStatement.setString(1, teamName)
                    val resultSet = preparedStatement.executeQuery()
                    resultSet.next() && resultSet.getInt(1) > 0
                }
            } ?: false
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
            getConnection()?.use { conn ->
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
            } ?: throw SQLException("No connection available")
        } catch (e: SQLException) {
            plugin.logger.err("Failed to remove team with identifier: $teamIdentifier. ${e.message}")
            false
        }
    }

    fun getTeamList(): List<String> {
        val query = "SELECT name FROM teams"
        val teamNames = mutableListOf<String>()

        return try {
            getConnection()?.use { conn ->
                conn.prepareStatement(query).use { preparedStatement ->
                    val resultSet = preparedStatement.executeQuery()
                    while (resultSet.next()) {
                        teamNames.add(resultSet.getString("name"))
                    }
                }
                teamNames
            } ?: emptyList()
        } catch (e: SQLException) {
            plugin.logger.err("Failed to retrieve team names. ${e.message}")
            emptyList()
        }
    }

    fun getTeamByUUID(playerUuid: String): Array<String>? {
        return try {
            getConnection()?.use { conn ->
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
                            resultSet.getString("format"),
                            resultSet.getString("created_at"),
                            resultSet.getString("description")
                        )
                        plugin.logger.debug("Team data retrieved for player UUID: $playerUuid")
                        teamData
                    } else {
                        plugin.logger.debug("No team found for player UUID: $playerUuid")
                        null
                    }
                }
            } ?: throw SQLException("No connection available")
        } catch (e: SQLException) {
            plugin.logger.debug("Failed to retrieve team for player UUID: $playerUuid. ${e.message}")
            null
        }
    }

    fun getTeamByName(name: String): Array<String>? {
        val query = "SELECT * FROM teams WHERE name = ?"
        return try {
            getConnection()?.use { conn ->
                conn.prepareStatement(query).use { preparedStatement ->
                    preparedStatement.setString(1, name)
                    val resultSet = preparedStatement.executeQuery()
                    if (resultSet.next()) {
                        val teamData = arrayOf(
                            resultSet.getString("id"),
                            resultSet.getString("name"),
                            resultSet.getString("leader_uuid"),
                            resultSet.getString("created_at"),
                            resultSet.getString("description")
                        )
                        plugin.logger.debug("Team data retrieved for team name: $name")
                        teamData
                    } else {
                        plugin.logger.warning("No team found with name: $name")
                        null
                    }
                }
            } ?: throw SQLException("No connection available")
        } catch (e: SQLException) {
            plugin.logger.debug("Failed to retrieve team for name: $name. ${e.message}")
            null
        }
    }

    fun addPlayerToTeam(teamId: Int, playerUuid: String): Boolean {
        val query = "INSERT INTO team_members (team_id, player_uuid, joined_at) VALUES (?, ?, CURRENT_TIMESTAMP)"
        return try {
            getConnection()?.use { conn ->
                conn.prepareStatement(query).use { preparedStatement ->
                    preparedStatement.setInt(1, teamId)
                    preparedStatement.setString(2, playerUuid)
                    preparedStatement.executeUpdate()
                    plugin.logger.debug("Player $playerUuid added to team ID $teamId.")
                    true
                }
            } ?: throw SQLException("No connection available")
        } catch (e: SQLException) {
            plugin.logger.err("Failed to add player $playerUuid to team ID $teamId. ${e.message}")
            false
        }
    }

    fun removePlayerFromTeam(teamId: Int, playerUuid: String): Boolean {
        val query = "DELETE FROM team_members WHERE team_id = ? AND player_uuid = ?"
        return try {
            getConnection()?.use { conn ->
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
            } ?: throw SQLException("No connection available")
        } catch (e: SQLException) {
            plugin.logger.err("Failed to remove player $playerUuid from team ID $teamId. ${e.message}")
            false
        }
    }

    fun getMemberCountByUUID(leaderUuid: String): Int {
        val teamId = getTeamIdByLeaderUUID(leaderUuid) ?: return 0
        val query = "SELECT COUNT(*) FROM team_members WHERE team_id = ?"

        return try {
            getConnection()?.use { conn ->
                conn.prepareStatement(query).use { preparedStatement ->
                    preparedStatement.setInt(1, teamId)
                    val resultSet = preparedStatement.executeQuery()
                    if (resultSet.next()) {
                        resultSet.getInt(1)+1
                    } else {
                        0
                    }
                }
            } ?: 1
        } catch (e: SQLException) {
            plugin.logger.err("Failed to get member count for team with leader UUID: $leaderUuid. ${e.message}")
            0
        }
    }

    fun setTeamLeader(teamId: Int, newLeaderUuid: String): Boolean {
        val query = "UPDATE teams SET leader_uuid = ? WHERE id = ?"
        return try {
            getConnection()?.use { conn ->
                conn.prepareStatement(query).use { preparedStatement ->
                    preparedStatement.setString(1, newLeaderUuid)
                    preparedStatement.setInt(2, teamId)
                    val rowsAffected = preparedStatement.executeUpdate()
                    rowsAffected > 0
                }
            } ?: false
        } catch (e: SQLException) {
            plugin.logger.err("Failed to set team leader for team ID: $teamId. ${e.message}")
            false
        }
    }


    fun getMemberUUIDsByUUID(leaderUuid: String): List<String> {
        val teamId = getTeamIdByLeaderUUID(leaderUuid) ?: return emptyList()
        val query = "SELECT player_uuid FROM team_members WHERE team_id = ?"

        return try {
            getConnection()?.use { conn ->
                conn.prepareStatement(query).use { preparedStatement ->
                    preparedStatement.setInt(1, teamId)
                    val resultSet = preparedStatement.executeQuery()
                    val uuids = mutableListOf<String>()
                    while (resultSet.next()) {
                        uuids.add(resultSet.getString("player_uuid"))
                    }
                    uuids
                }
            } ?: emptyList()
        } catch (e: SQLException) {
            plugin.logger.err("Failed to get member UUIDs for team with leader UUID: $leaderUuid. ${e.message}")
            emptyList()
        }
    }

    private fun getTeamIdByLeaderUUID(leaderUuid: String): Int? {
        val query = "SELECT id FROM teams WHERE leader_uuid = ?"

        return try {
            getConnection()?.use { conn ->
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
            getConnection()?.use { conn ->
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
            getConnection()?.use { conn ->
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
            getConnection()?.use { conn ->
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
            getConnection()?.use { conn ->
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