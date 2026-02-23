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
    private File tagsFile;
    private File guiFile;
    private File msgsFile;

    public ConfigManager(NexusTags plugin) {
        this.plugin = plugin;
        setupConfigs();
    }

    public void setupConfigs() {
        // Inisialisasi file konfigurasi kustom
        tagsFile = new File(plugin.getDataFolder(), "tags.yml");
        guiFile = new File(plugin.getDataFolder(), "gui.yml");
        msgsFile = new File(plugin.getDataFolder(), "messages.yml");

        // Simpan default dari JAR jika file belum ada
        if (!tagsFile.exists()) plugin.saveResource("tags.yml", false);
        if (!guiFile.exists()) plugin.saveResource("gui.yml", false);
        if (!msgsFile.exists()) plugin.saveResource("messages.yml", false);

        // Load konten YAML
        tagsConfig = YamlConfiguration.loadConfiguration(tagsFile);
        guiConfig = YamlConfiguration.loadConfiguration(guiFile);
        msgsConfig = YamlConfiguration.loadConfiguration(msgsFile);
    }

    public FileConfiguration getTagsConfig() { return tagsConfig; }
    public FileConfiguration getGuiConfig() { return guiConfig; }
    public FileConfiguration getMsgsConfig() { return msgsConfig; }

    public void saveTags() {
        try { tagsConfig.save(tagsFile); } catch (IOException e) { e.printStackTrace(); }
    }
}