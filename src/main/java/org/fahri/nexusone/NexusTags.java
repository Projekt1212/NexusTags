package org.fahri.nexusone;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.Bukkit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NexusTags extends JavaPlugin implements Listener {
    private static NexusTags instance;
    private DatabaseManager dbManager;
    private ConfigManager configManager;
    private GUIListener guiListener;
    private final Map<UUID, TagEditorSession> editorSessions = new HashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.configManager = new ConfigManager(this);
        this.configManager.setupConfigs();
        this.dbManager = new DatabaseManager(this);
        this.guiListener = new GUIListener(this);

        getCommand("tag").setExecutor(new TagCommand(this));
        getServer().getPluginManager().registerEvents(guiListener, this);
        getServer().getPluginManager().registerEvents(this, this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new TagExpansion(this).register();
        }
        getLogger().info("NexusTags v1.0 Enabled!");
    }

    @Override
    public void onDisable() {
        if (dbManager != null) dbManager.close();
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        TagEditorSession session = editorSessions.get(e.getPlayer().getUniqueId());
        if (session == null || session.lastEditAction.isEmpty()) return;

        e.setCancelled(true);
        String msg = e.getMessage();

        if (session.lastEditAction.equals("DISPLAY")) session.display = msg;
        else if (session.lastEditAction.equals("PRICE")) {
            try { session.price = Integer.parseInt(msg); } catch (Exception ex) { e.getPlayer().sendMessage("§cInput harus angka!"); }
        }
        else if (session.lastEditAction.equals("PERM")) session.permission = msg.equalsIgnoreCase("none") ? "" : msg;

        session.lastEditAction = "";
        Bukkit.getScheduler().runTask(this, () -> guiListener.openTagEditor(e.getPlayer(), session));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        editorSessions.remove(e.getPlayer().getUniqueId());
        GUIListener.getFilterIndex().remove(e.getPlayer().getUniqueId());
        GUIListener.getCurrentPage().remove(e.getPlayer().getUniqueId());
    }

    public void reloadPlugin() {
        this.configManager.reloadConfigs();
    }

    public static NexusTags getInstance() { return instance; }
    public DatabaseManager getDbManager() { return dbManager; }
    public ConfigManager getConfigManager() { return configManager; }
    public GUIListener getGuiListener() { return guiListener; }
    public Map<UUID, TagEditorSession> getEditorSessions() { return editorSessions; }

    public FileConfiguration getTagsConfig() { return configManager.getTagsConfig(); }
    public FileConfiguration getGuiConfig() { return configManager.getGuiConfig(); }
    public FileConfiguration getMsgsConfig() { return configManager.getMsgsConfig(); }
    public FileConfiguration getConfirmationConfig() { return configManager.getConfirmationConfig(); }
}