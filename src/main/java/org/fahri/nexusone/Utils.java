package org.fahri.nexusone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import me.clip.placeholderapi.PlaceholderAPI;
import java.util.ArrayList;
import java.util.List;

public class Utils {
    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer legacy = LegacyComponentSerializer.builder()
            .character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private static final LegacyComponentSerializer sectionSerializer = LegacyComponentSerializer.legacySection();

    public static Component parse(Player player, String text) {
        if (text == null || text.isEmpty()) return Component.empty();

        String processed = (player != null) ? PlaceholderAPI.setPlaceholders(player, text) : text;

        // ANTI-ERROR: Bersihkan simbol § sebelum masuk MiniMessage
        if (processed.contains("§")) {
            processed = LegacyComponentSerializer.legacyAmpersand().serialize(sectionSerializer.deserialize(processed));
        }

        Component mmComp = mm.deserialize(processed);
        String legacyString = legacy.serialize(mmComp);

        return legacy.deserialize(legacyString).decoration(TextDecoration.ITALIC, false);
    }

    public static Component parse(String text) { return parse(null, text); }

    public static String applyLegacy(Player player, String text) {
        if (text == null) return "";
        return sectionSerializer.serialize(parse(player, text));
    }

    public static String applyChatFormat(Player player, String tagDisplay) {
        if (tagDisplay == null) return "";

        // Ambil format simulasi chat dari messages.yml
        String format = NexusTags.getInstance().getMsgsConfig().getString("messages.display_tag_format", "{prefix} &7%player% %tag%");

        String prefix = (player != null) ? PlaceholderAPI.setPlaceholders(player, "%vault_prefix%") : "";
        String playerName = (player != null) ? player.getName() : "Player";

        // Bungkus tag display ke dalam format chat
        String replaced = format.replace("{prefix}", prefix)
                .replace("%player%", playerName)
                .replace("%tag%", tagDisplay);

        return applyLegacy(player, replaced);
    }

    public static ItemStack createItem(Player player, String mat, String name, List<String> lore) {
        Material material = Material.matchMaterial(mat != null ? mat : "PAPER");
        ItemStack item = new ItemStack(material != null ? material : Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(parse(player, name));
            if (lore != null) {
                List<Component> componentLore = new ArrayList<>();
                for (String line : lore) componentLore.add(parse(player, line));
                meta.lore(componentLore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}