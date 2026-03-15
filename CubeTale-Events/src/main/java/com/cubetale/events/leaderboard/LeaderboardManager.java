package com.cubetale.events.leaderboard;

import com.cubetale.events.CubeTaleEvents;
import com.cubetale.events.event.EventType;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.*;
import java.util.*;

public class LeaderboardManager {

    private final CubeTaleEvents plugin;
    private HikariDataSource dataSource;

    public LeaderboardManager(CubeTaleEvents plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        if (!plugin.getEventsConfig().isLeaderboardEnabled()) return;
        try {
            setupDataSource();
            createTables();
            plugin.getLogger().info("[Events] Leaderboard database initialized.");
        } catch (Exception e) {
            plugin.getLogger().severe("[Events] Failed to initialize leaderboard database: " + e.getMessage());
        }
    }

    private void setupDataSource() {
        HikariConfig config = new HikariConfig();
        String type = plugin.getEventsConfig().getLeaderboardDatabase().toUpperCase();

        if ("MYSQL".equals(type)) {
            config.setJdbcUrl("jdbc:mysql://" + plugin.getEventsConfig().getMysqlHost() + ":"
                    + plugin.getEventsConfig().getMysqlPort() + "/" + plugin.getEventsConfig().getMysqlDatabase()
                    + "?useSSL=false&allowPublicKeyRetrieval=true");
            config.setUsername(plugin.getEventsConfig().getMysqlUser());
            config.setPassword(plugin.getEventsConfig().getMysqlPassword());
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setMaximumPoolSize(plugin.getEventsConfig().getMysqlPoolSize());
        } else {
            File dbFile = new File(plugin.getDataFolder(), plugin.getEventsConfig().getSqliteFile());
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
            config.setMaximumPoolSize(1);
        }

        config.setPoolName("CubeTaleEvents-DB");
        config.setConnectionTimeout(10000);
        dataSource = new HikariDataSource(config);
    }

    private void createTables() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS event_stats (
                    uuid        VARCHAR(36)  NOT NULL,
                    player_name VARCHAR(64)  NOT NULL,
                    event_type  VARCHAR(32)  NOT NULL,
                    wins        INT          NOT NULL DEFAULT 0,
                    plays       INT          NOT NULL DEFAULT 0,
                    PRIMARY KEY (uuid, event_type)
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS event_wins_total (
                    uuid        VARCHAR(36)  NOT NULL PRIMARY KEY,
                    player_name VARCHAR(64)  NOT NULL,
                    total_wins  INT          NOT NULL DEFAULT 0,
                    total_plays INT          NOT NULL DEFAULT 0
                )
            """);
        }
    }

    public void addWin(UUID uuid, EventType type) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String name = getNameFor(uuid);
            try (Connection conn = dataSource.getConnection()) {
                upsertStat(conn, uuid.toString(), name, type.name(), true);
                upsertTotal(conn, uuid.toString(), name, true);
            } catch (Exception e) {
                plugin.getLogger().warning("[Events] DB error adding win: " + e.getMessage());
            }
        });
    }

    public void addPlay(UUID uuid, EventType type) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String name = getNameFor(uuid);
            try (Connection conn = dataSource.getConnection()) {
                upsertStat(conn, uuid.toString(), name, type.name(), false);
                upsertTotal(conn, uuid.toString(), name, false);
            } catch (Exception e) {
                plugin.getLogger().warning("[Events] DB error adding play: " + e.getMessage());
            }
        });
    }

    private void upsertStat(Connection conn, String uuid, String name, String type, boolean win) throws SQLException {
        String sql = """
            INSERT INTO event_stats (uuid, player_name, event_type, wins, plays)
            VALUES (?, ?, ?, ?, 1)
            ON CONFLICT(uuid, event_type) DO UPDATE SET
                player_name = excluded.player_name,
                wins  = wins  + excluded.wins,
                plays = plays + 1
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, name);
            ps.setString(3, type);
            ps.setInt(4, win ? 1 : 0);
            ps.executeUpdate();
        }
    }

    private void upsertTotal(Connection conn, String uuid, String name, boolean win) throws SQLException {
        String sql = """
            INSERT INTO event_wins_total (uuid, player_name, total_wins, total_plays)
            VALUES (?, ?, ?, 1)
            ON CONFLICT(uuid) DO UPDATE SET
                player_name  = excluded.player_name,
                total_wins   = total_wins  + excluded.total_wins,
                total_plays  = total_plays + 1
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, name);
            ps.setInt(3, win ? 1 : 0);
            ps.executeUpdate();
        }
    }

    public record PlayerStat(String name, int wins, int plays) {}

    public List<PlayerStat> getTopWinners(int limit) {
        List<PlayerStat> list = new ArrayList<>();
        if (dataSource == null) return list;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT player_name, total_wins, total_plays FROM event_wins_total ORDER BY total_wins DESC LIMIT ?")) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new PlayerStat(rs.getString("player_name"),
                        rs.getInt("total_wins"), rs.getInt("total_plays")));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Events] DB error fetching leaderboard: " + e.getMessage());
        }
        return list;
    }

    public PlayerStat getPlayerStat(UUID uuid) {
        if (dataSource == null) return new PlayerStat("?", 0, 0);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT player_name, total_wins, total_plays FROM event_wins_total WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new PlayerStat(rs.getString("player_name"),
                        rs.getInt("total_wins"), rs.getInt("total_plays"));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Events] DB error fetching player stat: " + e.getMessage());
        }
        return new PlayerStat(getNameFor(uuid), 0, 0);
    }

    public int getTotalWins(UUID uuid) {
        return getPlayerStat(uuid).wins();
    }

    private String getNameFor(UUID uuid) {
        var p = Bukkit.getPlayer(uuid);
        if (p != null) return p.getName();
        var op = Bukkit.getOfflinePlayer(uuid);
        return op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }
}
