package org.fahri.nexusone;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class TagExpansion extends PlaceholderExpansion {
    private final NexusTags plugin;

    public TagExpansion(NexusTags plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() { return "nexustags"; }

    @Override
    public @NotNull String getAuthor() { return "Fahri"; }

    @Override
    public @NotNull String getVersion() { return "1.2"; }

    @Override
    public boolean persist() { return true; }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        // %nexustags_tag% -> Display name tag aktif (e.g. &a&lTERRA)
        if (params.equalsIgnoreCase("tag")) {
            String active = plugin.getDbManager().getActiveTagSync(player.getUniqueId());
            if (active == null || active.isEmpty() || active.equalsIgnoreCase("none")) return "";
            String display = plugin.getTagsConfig().getString("tags." + active + ".display", active);
            return Utils.applyLegacy(player, display);
        }

        // %nexustags_active% -> TagId asli (e.g. TERRA)
        if (params.equalsIgnoreCase("active")) {
            String active = plugin.getDbManager().getActiveTagSync(player.getUniqueId());
            return (active == null || active.equalsIgnoreCase("none")) ? "" : active;
        }

        // %nexustags_active_formatted% -> TagId dengan "_" menjadi spasi
        if (params.equalsIgnoreCase("active_formatted")) {
            String active = plugin.getDbManager().getActiveTagSync(player.getUniqueId());
            if (active == null || active.isEmpty() || active.equalsIgnoreCase("none")) return "";
            return active.replace("_", " ");
        }

        // %nexustags_unlocked_tag% & %nexustags_unlocked_tag_formatted%
        if (params.equalsIgnoreCase("unlocked_tag") || params.equalsIgnoreCase("unlocked_tag_formatted")) {
            ConfigurationSection tagsSec = plugin.getTagsConfig().getConfigurationSection("tags");
            if (tagsSec == null) return "0";

            List<String> dbUnlocked = plugin.getDbManager().getUnlockedTags(player.getUniqueId());
            int count = 0;
            for (String key : tagsSec.getKeys(false)) {
                boolean isFree = tagsSec.getInt(key + ".price", -1) == 0;
                boolean hasPerm = tagsSec.contains(key + ".permission") && player.hasPermission(tagsSec.getString(key + ".permission"));
                if (dbUnlocked.contains(key) || isFree || hasPerm) count++;
            }

            if (params.endsWith("_formatted")) {
                return NumberFormat.getInstance(Locale.US).format(count);
            }
            return String.valueOf(count);
        }

        if (params.equalsIgnoreCase("total_tag")) {
            return String.valueOf(getTotalTags());
        }

        // %nexustags_total_tag_formatted% - Total tag dengan format (1,287)
        if (params.equalsIgnoreCase("total_tag_formatted")) {
            return NumberFormat.getInstance(Locale.US).format(getTotalTags());
        }

        // %nexustags_rarity_<tag_id>%
        if (params.startsWith("rarity_")) {
            String tagId = params.replace("rarity_", "");
            String rarity = plugin.getTagsConfig().getString("tags." + tagId + ".rarity", "COMMON").toUpperCase();
            String rarityDisplay = plugin.getTagsConfig().getString("rarities." + rarity + ".display", rarity);
            return Utils.applyLegacy(player, rarityDisplay);
        }

        // %nexustags_preview_<tag_id>%
        if (params.startsWith("preview_")) {
            String tagId = params.replace("preview_", "");
            String tagDisplay = plugin.getTagsConfig().getString("tags." + tagId + ".display", tagId);
            return Utils.applyChatFormat(player, tagDisplay);
        }

        return null;
    }

    private int getTotalTags() {
        ConfigurationSection section = plugin.getTagsConfig().getConfigurationSection("tags");
        if (section == null) return 0;
        return section.getKeys(false).size();
    }

}