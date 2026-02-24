package org.fahri.nexusone.NexusTags;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;

public class ConfigManager {
    private final NexusTags plugin;
    private FileConfiguration tagsConfig;
    private FileConfiguration guiConfig;
    private FileConfiguration msgsConfig;
    private FileConfiguration confirmationConfig; // Tambahkan ini

    private File tagsFile;
    private File guiFile;
    private File msgsFile;
    private File confirmationFile; // Tambahkan ini

    public ConfigManager(NexusTags plugin) {
        this.plugin = plugin;
        setupConfigs();
    }

    public void setupConfigs() {
        tagsFile = new File(plugin.getDataFolder(), "tags.yml");
        guiFile = new File(plugin.getDataFolder(), "gui.yml");
        msgsFile = new File(plugin.getDataFolder(), "messages.yml");
        confirmationFile = new File(plugin.getDataFolder(), "confirmation.yml"); // Tambahkan ini

        if (!tagsFile.exists()) plugin.saveResource("tags.yml", false);
        if (!guiFile.exists()) plugin.saveResource("gui.yml", false);
        if (!msgsFile.exists()) plugin.saveResource("messages.yml", false);
        if (!confirmationFile.exists()) plugin.saveResource("confirmation.yml", false); // Tambahkan ini

        tagsConfig = YamlConfiguration.loadConfiguration(tagsFile);
        guiConfig = YamlConfiguration.loadConfiguration(guiFile);
        msgsConfig = YamlConfiguration.loadConfiguration(msgsFile);
        confirmationConfig = YamlConfiguration.loadConfiguration(confirmationFile); // Tambahkan ini
    }

    public FileConfiguration getTagsConfig() { return tagsConfig; }
    public FileConfiguration getGuiConfig() { return guiConfig; }
    public FileConfiguration getMsgsConfig() { return msgsConfig; }
    public FileConfiguration getConfirmationConfig() { return confirmationConfig; } // Tambahkan ini

    public void saveTags() {
        try { tagsConfig.save(tagsFile); } catch (IOException e) { e.printStackTrace(); }
    }
}
