package org.fahri.nexusone;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;

public class ConfigManager {
    private final NexusTags plugin;
    private FileConfiguration tagsConfig, guiConfig, msgsConfig, confirmationConfig;
    private File tagsFile, guiFile, msgsFile, confirmationFile;

    public ConfigManager(NexusTags plugin) {
        this.plugin = plugin;
    }

    public void setupConfigs() {
        tagsFile = new File(plugin.getDataFolder(), "tags.yml");
        guiFile = new File(plugin.getDataFolder(), "gui.yml");
        msgsFile = new File(plugin.getDataFolder(), "messages.yml");
        confirmationFile = new File(plugin.getDataFolder(), "confirmation.yml");

        if (!tagsFile.exists()) plugin.saveResource("tags.yml", false);
        if (!guiFile.exists()) plugin.saveResource("gui.yml", false);
        if (!msgsFile.exists()) plugin.saveResource("messages.yml", false);
        if (!confirmationFile.exists()) plugin.saveResource("confirmation.yml", false);

        reloadConfigs();
    }

    public void reloadConfigs() {
        tagsConfig = YamlConfiguration.loadConfiguration(tagsFile);
        guiConfig = YamlConfiguration.loadConfiguration(guiFile);
        msgsConfig = YamlConfiguration.loadConfiguration(msgsFile);
        confirmationConfig = YamlConfiguration.loadConfiguration(confirmationFile);
    }

    public void saveTagsConfig() {
        try {
            tagsConfig.save(tagsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public FileConfiguration getTagsConfig() { return tagsConfig; }
    public FileConfiguration getGuiConfig() { return guiConfig; }
    public FileConfiguration getMsgsConfig() { return msgsConfig; }
    public FileConfiguration getConfirmationConfig() { return confirmationConfig; }
}