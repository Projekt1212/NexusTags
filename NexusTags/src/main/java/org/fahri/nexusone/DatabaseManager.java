package org.fahri.nexusone.NexusTags;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.*;
import java.util.*;

public class DatabaseManager {
    private final NexusTags plugin;
    private HikariDataSource ds;

    public DatabaseManager(NexusTags plugin) {
        this.plugin = plugin;
        setupDatabase();
    }

    private void setupDatabase() {
        String host = plugin.getConfig().getString("database.host", "localhost");
        int port = plugin.getConfig().getInt("database.port", 3306);
        String dbName = plugin.getConfig().getString("database.database", "nexus_tags");
        String user = plugin.getConfig().getString("database.username", "root");
        String pass = plugin.getConfig().getString("database.password", "");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + dbName);
        config.setUsername(user);
        config.setPassword(pass);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.ds = new HikariDataSource(config);

        try (Connection conn = ds.getConnection();
             PreparedStatement st1 = conn.prepareStatement("CREATE TABLE IF NOT EXISTS nexustags_unlocked (uuid VARCHAR(36), tag_id VARCHAR(64), PRIMARY KEY(uuid, tag_id))");
             PreparedStatement st2 = conn.prepareStatement("CREATE TABLE IF NOT EXISTS nexustags_active (uuid VARCHAR(36) PRIMARY KEY, tag_id VARCHAR(64))");
             PreparedStatement st3 = conn.prepareStatement("CREATE TABLE IF NOT EXISTS nexustags_configs (config_name VARCHAR(64) PRIMARY KEY, content LONGTEXT)")) {
            st1.execute();
            st2.execute();
            st3.execute();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // FIX: Menambahkan metode yang hilang untuk menghapus tag
    public void removeUnlockedTag(UUID uuid, String tagId) {
        try (Connection conn = ds.getConnection();
             PreparedStatement st = conn.prepareStatement("DELETE FROM nexustags_unlocked WHERE uuid = ? AND tag_id = ?")) {
            st.setString(1, uuid.toString());
            st.setString(2, tagId);
            st.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void saveConfigToCloud(String name, String content) {
        try (Connection conn = ds.getConnection();
             PreparedStatement st = conn.prepareStatement("REPLACE INTO nexustags_configs (config_name, content) VALUES (?, ?)")) {
            st.setString(1, name);
            st.setString(2, content);
            st.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public String loadConfigFromCloud(String name) {
        try (Connection conn = ds.getConnection();
             PreparedStatement st = conn.prepareStatement("SELECT content FROM nexustags_configs WHERE config_name = ?")) {
            st.setString(1, name);
            ResultSet rs = st.executeQuery();
            if (rs.next()) return rs.getString("content");
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public List<String> getUnlockedTags(UUID uuid) {
        List<String> tags = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement st = conn.prepareStatement("SELECT tag_id FROM nexustags_unlocked WHERE uuid = ?")) {
            st.setString(1, uuid.toString());
            ResultSet rs = st.executeQuery();
            while (rs.next()) tags.add(rs.getString("tag_id"));
        } catch (SQLException e) { e.printStackTrace(); }
        return tags;
    }

    public void addUnlockedTag(UUID uuid, String tagId) {
        try (Connection conn = ds.getConnection();
             PreparedStatement st = conn.prepareStatement("INSERT IGNORE INTO nexustags_unlocked (uuid, tag_id) VALUES (?, ?)")) {
            st.setString(1, uuid.toString());
            st.setString(2, tagId);
            st.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void setTag(UUID uuid, String tagId) {
        try (Connection conn = ds.getConnection();
             PreparedStatement st = conn.prepareStatement("REPLACE INTO nexustags_active (uuid, tag_id) VALUES (?, ?)")) {
            st.setString(1, uuid.toString());
            st.setString(2, tagId);
            st.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public String getActiveTagSync(UUID uuid) {
        try (Connection conn = ds.getConnection();
             PreparedStatement st = conn.prepareStatement("SELECT tag_id FROM nexustags_active WHERE uuid = ?")) {
            st.setString(1, uuid.toString());
            ResultSet rs = st.executeQuery();
            if (rs.next()) return rs.getString("tag_id");
        } catch (SQLException e) { e.printStackTrace(); }
        return "none";
    }

    public void close() { if (ds != null) ds.close(); }
}