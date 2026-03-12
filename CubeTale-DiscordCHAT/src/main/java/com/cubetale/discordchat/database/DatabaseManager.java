package com.cubetale.discordchat.database;

import com.cubetale.discordchat.CubeTaleDiscordChat;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class DatabaseManager {

    private final CubeTaleDiscordChat plugin;
    private HikariDataSource dataSource;
    private String dbType;

    public DatabaseManager(CubeTaleDiscordChat plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        dbType = plugin.getConfigManager().getDatabaseType();

        try {
            HikariConfig config = new HikariConfig();
            config.setPoolName("CubeTale-DiscordCHAT-Pool");

            if (dbType.equals("MYSQL")) {
                setupMySQL(config);
            } else {
                setupSQLite(config);
            }

            config.setMaximumPoolSize(plugin.getConfigManager().getMysqlPoolSize());
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);

            dataSource = new HikariDataSource(config);

            createTables();
            plugin.getPluginLogger().info("Database initialized successfully (" + dbType + ").");
            return true;
        } catch (Exception e) {
            plugin.getPluginLogger().severe("Failed to initialize database: " + e.getMessage());
            plugin.getPluginLogger().log(java.util.logging.Level.SEVERE, "Database error", e);
            return false;
        }
    }

    private void setupMySQL(HikariConfig config) {
        String host = plugin.getConfigManager().getMysqlHost();
        int port = plugin.getConfigManager().getMysqlPort();
        String database = plugin.getConfigManager().getMysqlDatabase();
        String username = plugin.getConfigManager().getMysqlUsername();
        String password = plugin.getConfigManager().getMysqlPassword();

        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf-8");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setUsername(username);
        config.setPassword(password);
    }

    private void setupSQLite(HikariConfig config) {
        File dbFile = new File(plugin.getDataFolder(), plugin.getConfigManager().getSqliteFile());
        if (!dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
        }
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1);
    }

    private void createTables() throws SQLException {
        String linkedAccountsTable;
        String verificationCodesTable;

        if (dbType.equals("MYSQL")) {
            linkedAccountsTable = "CREATE TABLE IF NOT EXISTS `linked_accounts` ("
                    + "`id` INT AUTO_INCREMENT PRIMARY KEY,"
                    + "`minecraft_uuid` VARCHAR(36) NOT NULL UNIQUE,"
                    + "`minecraft_name` VARCHAR(16) NOT NULL,"
                    + "`discord_id` VARCHAR(20) NOT NULL UNIQUE,"
                    + "`discord_tag` VARCHAR(37) NOT NULL,"
                    + "`linked_at` BIGINT NOT NULL,"
                    + "INDEX `idx_minecraft_uuid` (`minecraft_uuid`),"
                    + "INDEX `idx_discord_id` (`discord_id`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

            verificationCodesTable = "CREATE TABLE IF NOT EXISTS `verification_codes` ("
                    + "`id` INT AUTO_INCREMENT PRIMARY KEY,"
                    + "`minecraft_uuid` VARCHAR(36) NOT NULL,"
                    + "`code` VARCHAR(10) NOT NULL,"
                    + "`created_at` BIGINT NOT NULL,"
                    + "`expires_at` BIGINT NOT NULL,"
                    + "INDEX `idx_code` (`code`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
        } else {
            linkedAccountsTable = "CREATE TABLE IF NOT EXISTS linked_accounts ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "minecraft_uuid TEXT NOT NULL UNIQUE,"
                    + "minecraft_name TEXT NOT NULL,"
                    + "discord_id TEXT NOT NULL UNIQUE,"
                    + "discord_tag TEXT NOT NULL,"
                    + "linked_at INTEGER NOT NULL"
                    + ");";

            verificationCodesTable = "CREATE TABLE IF NOT EXISTS verification_codes ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "minecraft_uuid TEXT NOT NULL,"
                    + "code TEXT NOT NULL,"
                    + "created_at INTEGER NOT NULL,"
                    + "expires_at INTEGER NOT NULL"
                    + ");";
        }

        try (Connection conn = getConnection()) {
            conn.createStatement().execute(linkedAccountsTable);
            conn.createStatement().execute(verificationCodesTable);
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // --- Linked accounts operations ---

    public boolean isLinked(UUID minecraftUUID) {
        String sql = "SELECT 1 FROM linked_accounts WHERE minecraft_uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, minecraftUUID.toString());
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getPluginLogger().warning("DB error checking link: " + e.getMessage());
            return false;
        }
    }

    public boolean isDiscordLinked(String discordId) {
        String sql = "SELECT 1 FROM linked_accounts WHERE discord_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, discordId);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getPluginLogger().warning("DB error checking discord link: " + e.getMessage());
            return false;
        }
    }

    public String getDiscordId(UUID minecraftUUID) {
        String sql = "SELECT discord_id FROM linked_accounts WHERE minecraft_uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, minecraftUUID.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("discord_id");
        } catch (SQLException e) {
            plugin.getPluginLogger().warning("DB error getting discord id: " + e.getMessage());
        }
        return null;
    }

    public String getDiscordTag(UUID minecraftUUID) {
        String sql = "SELECT discord_tag FROM linked_accounts WHERE minecraft_uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, minecraftUUID.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("discord_tag");
        } catch (SQLException e) {
            plugin.getPluginLogger().warning("DB error getting discord tag: " + e.getMessage());
        }
        return null;
    }

    public UUID getMinecraftUUID(String discordId) {
        String sql = "SELECT minecraft_uuid FROM linked_accounts WHERE discord_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, discordId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return UUID.fromString(rs.getString("minecraft_uuid"));
        } catch (SQLException e) {
            plugin.getPluginLogger().warning("DB error getting minecraft uuid: " + e.getMessage());
        }
        return null;
    }

    public boolean linkAccount(UUID minecraftUUID, String minecraftName, String discordId, String discordTag) {
        String sql = dbType.equals("MYSQL")
                ? "INSERT INTO linked_accounts (minecraft_uuid, minecraft_name, discord_id, discord_tag, linked_at) VALUES (?, ?, ?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE minecraft_name=VALUES(minecraft_name), discord_id=VALUES(discord_id), discord_tag=VALUES(discord_tag), linked_at=VALUES(linked_at)"
                : "INSERT OR REPLACE INTO linked_accounts (minecraft_uuid, minecraft_name, discord_id, discord_tag, linked_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, minecraftUUID.toString());
            ps.setString(2, minecraftName);
            ps.setString(3, discordId);
            ps.setString(4, discordTag);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getPluginLogger().warning("DB error linking account: " + e.getMessage());
            return false;
        }
    }

    public boolean unlinkAccount(UUID minecraftUUID) {
        String sql = "DELETE FROM linked_accounts WHERE minecraft_uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, minecraftUUID.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getPluginLogger().warning("DB error unlinking account: " + e.getMessage());
            return false;
        }
    }

    // --- Verification codes operations ---

    public void saveVerificationCode(UUID minecraftUUID, String code, long expiresAt) {
        // Remove old codes first
        deleteVerificationCodes(minecraftUUID);
        String sql = "INSERT INTO verification_codes (minecraft_uuid, code, created_at, expires_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, minecraftUUID.toString());
            ps.setString(2, code);
            ps.setLong(3, System.currentTimeMillis());
            ps.setLong(4, expiresAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getPluginLogger().warning("DB error saving verification code: " + e.getMessage());
        }
    }

    public UUID getUUIDForCode(String code) {
        String sql = "SELECT minecraft_uuid, expires_at FROM verification_codes WHERE code = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                long expiresAt = rs.getLong("expires_at");
                if (System.currentTimeMillis() > expiresAt) {
                    deleteExpiredCodes();
                    return null;
                }
                return UUID.fromString(rs.getString("minecraft_uuid"));
            }
        } catch (SQLException e) {
            plugin.getPluginLogger().warning("DB error getting uuid for code: " + e.getMessage());
        }
        return null;
    }

    public void deleteVerificationCodes(UUID minecraftUUID) {
        String sql = "DELETE FROM verification_codes WHERE minecraft_uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, minecraftUUID.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getPluginLogger().warning("DB error deleting verification codes: " + e.getMessage());
        }
    }

    public void deleteExpiredCodes() {
        String sql = "DELETE FROM verification_codes WHERE expires_at < ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getPluginLogger().warning("DB error deleting expired codes: " + e.getMessage());
        }
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getPluginLogger().info("Database connection pool closed.");
        }
    }
}
