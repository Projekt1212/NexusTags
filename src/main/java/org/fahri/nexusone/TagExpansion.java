package org.fahri.nexusone.NexusTags;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
    public @NotNull String getVersion() { return "1.1"; }

    @Override
    public boolean persist() { return true; }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        // Tag Aktif Terformat
        if (params.equalsIgnoreCase("active_formatted")) {
            String active = plugin.getDbManager().getActiveTagSync(player.getUniqueId());
            if (active == null || active.isEmpty() || active.equalsIgnoreCase("none")) {
                return "Tidak ada";
            }
            String display = plugin.getTagsConfig().getString("tags." + active + ".display", active);
            return Utils.applyLegacy(player, display);
        }

        // Jumlah Tag Dimiliki (Akurat: DB + Gratis + Permission) - Tanpa Kurung
        if (params.equalsIgnoreCase("unlocked_tag") || params.equalsIgnoreCase("unlocked_tag_formatted")) {
            ConfigurationSection tagsSec = plugin.getTagsConfig().getConfigurationSection("tags");
            if (tagsSec == null) return "0";

            List<String> dbUnlocked = plugin.getDbManager().getUnlockedTags(player.getUniqueId());
            int count = 0;

            for (String key : tagsSec.getKeys(false)) {
                boolean isFree = tagsSec.getInt(key + ".price", -1) == 0;
                boolean hasPerm = tagsSec.contains(key + ".permission") && player.hasPermission(tagsSec.getString(key + ".permission"));

                if (dbUnlocked.contains(key) || isFree || hasPerm) {
                    count++;
                }
            }
            return String.valueOf(count);
        }

        // Rarity: Hanya menampilkan teks rarity (Warna saja)
        if (params.startsWith("rarity_")) {
            String tagId = params.replace("rarity_", "");
            String rarity = plugin.getTagsConfig().getString("tags." + tagId + ".rarity", "COMMON").toUpperCase();
            String rarityDisplay = plugin.getTagsConfig().getString("rarities." + rarity + ".display", rarity);
            return Utils.applyLegacy(player, rarityDisplay);
        }

        // Preview: Simulasi Chat lengkap (Prefix + Nama + Tag) dari messages.display_tag_format
        if (params.startsWith("preview_")) {
            String tagId = params.replace("preview_", "");
            String tagDisplay = plugin.getTagsConfig().getString("tags." + tagId + ".display", tagId);
            return Utils.applyChatFormat(player, tagDisplay);
        }

        return null;
    }
}
