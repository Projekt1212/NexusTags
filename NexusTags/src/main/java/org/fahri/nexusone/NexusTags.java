package org.fahri.nexusone.NexusTags;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class NexusTags extends JavaPlugin {
    private static NexusTags instance;
    private DatabaseManager dbManager;
    private ConfigManager configManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reloadConfig();

        this.configManager = new ConfigManager(this);
        this.dbManager = new DatabaseManager(this);

        getCommand("tag").setExecutor(new TagCommand(this));
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new TagExpansion(this).register();
        }
        getLogger().info("NexusTags v1.0 - Stable Version Enabled!");
    }

    @Override
    public void onDisable() {
        if (dbManager != null) dbManager.close();
    }

    public void reloadPlugin() {
        reloadConfig();
        this.configManager = new ConfigManager(this);
    }

    public static NexusTags getInstance() { return instance; }
    public DatabaseManager getDbManager() { return dbManager; }
    public FileConfiguration getTagsConfig() { return configManager.getTagsConfig(); }
    public FileConfiguration getGuiConfig() { return configManager.getGuiConfig(); }
    public FileConfiguration getMsgsConfig() { return configManager.getMsgsConfig(); }
}