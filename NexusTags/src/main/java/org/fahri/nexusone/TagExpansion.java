package org.fahri.nexusone.NexusTags;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.text.NumberFormat;
import java.util.Locale;

public class TagExpansion extends PlaceholderExpansion {
    private final NexusTags plugin;
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacySection();

    public TagExpansion(NexusTags plugin) { this.plugin = plugin; }

    @Override public @NotNull String getIdentifier() { return "nexustags"; }
    @Override public @NotNull String getAuthor() { return "Fahri"; }
    @Override public @NotNull String getVersion() { return "1.4"; }
    @Override public boolean persist() { return true; }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        // Placeholder Statistik
        if (params.equalsIgnoreCase("unlocked_tag")) {
            return String.valueOf(plugin.getDbManager().getUnlockedTags(player.getUniqueId()).size());
        }
        if (params.equalsIgnoreCase("unlocked_tag_formatted")) {
            int count = plugin.getDbManager().getUnlockedTags(player.getUniqueId()).size();
            return "(" + NumberFormat.getNumberInstance(Locale.US).format(count) + ")";
        }

        // Placeholder Preview (Dinamis per Tag)
        if (params.startsWith("preview_")) {
            String tagId = params.replace("preview_", "");
            String format = plugin.getMsgsConfig().getString("messages.display_tag_format", "&7%player% (%tag%) &8> &fChat!");
            String tagDisplay = plugin.getTagsConfig().getString("tags." + tagId + ".display", tagId);
            return Utils.applyLegacy(player, format.replace("%tag%", tagDisplay));
        }

        // Placeholder Rarity
        if (params.startsWith("rarity_")) {
            String tagId = params.replace("rarity_", "");
            String rKey = plugin.getTagsConfig().getString("tags." + tagId + ".rarity", "COMMON");
            return Utils.applyLegacy(player, plugin.getTagsConfig().getString("rarities." + rKey + ".display", "&7COMMON"));
        }

        String activeTag = plugin.getDbManager().getActiveTagSync(player.getUniqueId());
        if (activeTag == null || activeTag.isEmpty() || activeTag.equalsIgnoreCase("none")) return "";

        if (params.equalsIgnoreCase("active")) return activeTag;
        if (params.equalsIgnoreCase("active_formatted")) return activeTag.replace("_", " ");
        if (params.equalsIgnoreCase("tag")) {
            String display = plugin.getTagsConfig().getString("tags." + activeTag + ".display");
            return legacySerializer.serialize(Utils.parse(player, display != null ? display : activeTag));
        }
        return null;
    }
}