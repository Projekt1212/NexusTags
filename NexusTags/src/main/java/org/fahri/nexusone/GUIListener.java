package org.fahri.nexusone.NexusTags;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import me.clip.placeholderapi.PlaceholderAPI;
import java.util.*;

public class GUIListener implements Listener, InventoryHolder {
    private final NexusTags plugin;
    private final Map<UUID, Integer> filterIndex = new HashMap<>(); // 0: All, 1: Owned, 2: Rarity Asc, 3: Rarity Desc
    private final Map<UUID, Integer> currentPage = new HashMap<>();

    public GUIListener(NexusTags plugin) { this.plugin = plugin; }
    @Override public Inventory getInventory() { return null; }

    public void openGUI(Player player, int page) {
        currentPage.put(player.getUniqueId(), page);
        filterIndex.putIfAbsent(player.getUniqueId(), 0);
        renderGUI(player, page);
    }

    private void renderGUI(Player player, int page) {
        ConfigurationSection guiSec = plugin.getGuiConfig().getConfigurationSection("gui");
        if (guiSec == null) return;

        int rows = guiSec.getInt("rows", 6);
        String pageFormat = guiSec.getString("page_format", " - %page%").replace("%page%", String.valueOf(page + 1));
        String title = guiSec.getString("title", "TAG MENU").replace("%page_format%", pageFormat);

        // Buat inventory baru untuk refresh visual total
        Inventory inv = Bukkit.createInventory(this, rows * 9, Utils.parse(player, title));

        renderFiller(inv, rows, guiSec, player);
        renderStaticItems(inv, guiSec, player);

        ConfigurationSection tagsSec = plugin.getTagsConfig().getConfigurationSection("tags");
        if (tagsSec != null) {
            List<String> unlocked = plugin.getDbManager().getUnlockedTags(player.getUniqueId());
            String active = plugin.getDbManager().getActiveTagSync(player.getUniqueId());
            int mode = filterIndex.getOrDefault(player.getUniqueId(), 0);

            List<String> keys = getSortedKeys(player, tagsSec, unlocked, mode);
            List<Integer> slots = getAvailableSlots(guiSec.getInt("start_slot"), guiSec.getInt("end_slot"), rows);

            int ipp = slots.size();
            int start = page * ipp;
            int end = Math.min(start + ipp, keys.size());

            for (int i = start; i < end; i++) {
                String key = keys.get(i);
                boolean isUnlocked = checkAccess(player, tagsSec, key, unlocked);
                List<String> loreTemplate = getLoreTemplate(key.equals(active), isUnlocked, tagsSec, key, player);
                inv.setItem(slots.get(i - start), Utils.createItem(player, tagsSec.getString(key + ".icon", "PAPER"), tagsSec.getString(key + ".display", key), processLore(loreTemplate, tagsSec, key, player)));
            }
            renderNavigation(inv, page, keys.size(), ipp, guiSec, player);
        }
        player.openInventory(inv);
    }

    private List<String> getSortedKeys(Player p, ConfigurationSection t, List<String> u, int mode) {
        List<String> keys = new ArrayList<>(t.getKeys(false));
        if (mode == 1) keys.removeIf(k -> !checkAccess(p, t, k, u));

        keys.sort((a, b) -> {
            String rA = t.getString(a + ".rarity", "COMMON").toUpperCase();
            String rB = t.getString(b + ".rarity", "COMMON").toUpperCase();
            int wA = plugin.getTagsConfig().getInt("rarities." + rA + ".weight", 0);
            int wB = plugin.getTagsConfig().getInt("rarities." + rB + ".weight", 0);

            int res;
            if (mode == 2) res = Integer.compare(wA, wB);
            else res = Integer.compare(wB, wA); // Default/Mode 3 (Desc)

            return (res != 0) ? res : a.compareToIgnoreCase(b);
        });
        return keys;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GUIListener)) return;
        e.setCancelled(true);
        Player p = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();
        ConfigurationSection gui = plugin.getGuiConfig().getConfigurationSection("gui");

        if (slot == gui.getInt("controls.close.slot")) p.closeInventory();
        else if (slot == gui.getInt("navigation.previous_page.slot") && currentPage.getOrDefault(p.getUniqueId(), 0) > 0) {
            renderGUI(p, currentPage.get(p.getUniqueId()) - 1);
            currentPage.put(p.getUniqueId(), currentPage.get(p.getUniqueId()) - 1);
        } else if (slot == gui.getInt("navigation.next_page.slot")) {
            renderGUI(p, currentPage.get(p.getUniqueId()) + 1);
            currentPage.put(p.getUniqueId(), currentPage.get(p.getUniqueId()) + 1);
        } else if (slot == gui.getInt("controls.filter_hopper.slot")) {
            int next = (filterIndex.getOrDefault(p.getUniqueId(), 0) + 1) % 4;
            filterIndex.put(p.getUniqueId(), next);
            currentPage.put(p.getUniqueId(), 0);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            renderGUI(p, 0);
        } else {
            handleTagSelection(p, slot, currentPage.getOrDefault(p.getUniqueId(), 0), gui);
        }
    }

    private void handleTagSelection(Player p, int slot, int page, ConfigurationSection guiSec) {
        ConfigurationSection tagsSec = plugin.getTagsConfig().getConfigurationSection("tags");
        List<String> u = plugin.getDbManager().getUnlockedTags(p.getUniqueId());

        // Ambil Tag yang sedang aktif saat ini
        String activeTag = plugin.getDbManager().getActiveTagSync(p.getUniqueId());

        int m = filterIndex.getOrDefault(p.getUniqueId(), 0);
        List<String> filtered = getSortedKeys(p, tagsSec, u, m);
        List<Integer> slots = getAvailableSlots(guiSec.getInt("start_slot"), guiSec.getInt("end_slot"), guiSec.getInt("rows"));

        if (slots.contains(slot)) {
            int idx = (page * slots.size()) + slots.indexOf(slot);
            if (idx >= filtered.size()) return;

            String tid = filtered.get(idx);
            String display = tagsSec.getString(tid + ".display", tid);

            // LOGIKA BARU: Cek jika tag yang diklik adalah tag yang sedang dipakai
            if (tid.equals(activeTag)) {
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                // Kamu bisa ganti pesannya langsung di sini atau ambil dari config
                p.sendMessage(Utils.parse(p, "&aKamu sudah memakai tag ini!"));
                return; // Stop eksekusi agar tidak memproses "Equip" lagi
            }

            // 1. JIKA SUDAH PUNYA AKSES
            if (checkAccess(p, tagsSec, tid, u)) {
                plugin.getDbManager().setTag(p.getUniqueId(), tid);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                for (String s : plugin.getMsgsConfig().getStringList("messages.tag_equipped")) {
                    p.sendMessage(Utils.parse(p, s.replace("%tag%", display)));
                }
                renderGUI(p, page);
            }
            // 2. JIKA BELUM PUNYA DAN BERBAYAR
// 2. JIKA BELUM PUNYA DAN BERBAYAR
            else if (tagsSec.contains(tid + ".price")) {
                int pr = tagsSec.getInt(tid + ".price", 0);
                double bal = Double.parseDouble(PlaceholderAPI.setPlaceholders(p, "%nexogems_balance%"));

                if (bal >= pr) {
                    // Perintah disesuaikan: nb withdraw <player> <price> Buy tag <tag>
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "nb withdraw " + p.getName() + " " + pr + " Buy tag " + tid);

                    plugin.getDbManager().addUnlockedTag(p.getUniqueId(), tid);
                    plugin.getDbManager().setTag(p.getUniqueId(), tid);

                    for (String s : plugin.getMsgsConfig().getStringList("messages.buy_success")) {
                        p.sendMessage(Utils.parse(p, s.replace("%tag%", display).replace("%price%", String.valueOf(pr))));
                    }
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                    renderGUI(p, page);
                } else {
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    for (String s : plugin.getMsgsConfig().getStringList("messages.buy_failed")) {
                        p.sendMessage(Utils.parse(p, s.replace("%missing%", String.valueOf(pr - bal))));
                    }
                }
            }
            // 3. JIKA TIDAK ADA PERMS
            else {
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                for (String s : plugin.getMsgsConfig().getStringList("messages.no_permission_tag")) {
                    p.sendMessage(Utils.parse(p, s));
                }
            }
        }
    }

    private void renderNavigation(Inventory inv, int page, int size, int ipp, ConfigurationSection s, Player p) {
        ConfigurationSection c = s.getConfigurationSection("controls.filter_hopper");
        int m = filterIndex.getOrDefault(p.getUniqueId(), 0);
        String name; List<String> lore;
        if (m == 1) { name = c.getString("name_owned"); lore = c.getStringList("lore_owned"); }
        else if (m == 2) { name = c.getString("name_rarity_asc"); lore = c.getStringList("lore_rarity_asc"); }
        else if (m == 3) { name = c.getString("name_rarity_desc"); lore = c.getStringList("lore_rarity_desc"); }
        else { name = c.getString("name_all"); lore = c.getStringList("lore_all"); }

        inv.setItem(c.getInt("slot"), Utils.createItem(p, c.getString("material"), name, lore));
        ConfigurationSection nav = s.getConfigurationSection("navigation");
        if (page > 0) inv.setItem(nav.getInt("previous_page.slot"), Utils.createItem(p, nav.getString("previous_page.material"), nav.getString("previous_page.name"), null));
        if ((page + 1) * ipp < size) inv.setItem(nav.getInt("next_page.slot"), Utils.createItem(p, nav.getString("next_page.material"), nav.getString("next_page.name"), null));
        inv.setItem(s.getInt("controls.close.slot"), Utils.createItem(p, s.getString("controls.close.material"), s.getString("controls.close.name"), null));
    }

    // 1. Perbaikan logika akses: Jika harga 0, otomatis true
    private boolean checkAccess(Player p, ConfigurationSection t, String k, List<String> u) {
        // Jika player punya permission, langsung unlock
        if (t.contains(k + ".permission") && p.hasPermission(t.getString(k + ".permission"))) return true;

        // Jika di database sudah ada, langsung unlock
        if (u.contains(k)) return true;

        // FITUR BARU: Jika harga di config adalah 0, otomatis unlock
        if (t.contains(k + ".price") && t.getInt(k + ".price") == 0) return true;

        return false;
    }

    // 2. Perbaikan Lore: Supaya tidak muncul tulisan "KLIK UNTUK MEMBELI" pada barang gratis
    private List<String> getLoreTemplate(boolean active, boolean unlocked, ConfigurationSection t, String k, Player p) {
        ConfigurationSection d = plugin.getGuiConfig().getConfigurationSection("tag_display");

        if (active) return d.getStringList("lore_active");

        // Jika sudah unlocked (termasuk yang harganya 0), pakai lore_unlocked
        if (unlocked) return d.getStringList("lore_unlocked");

        if (t.contains(k + ".permission") && !p.hasPermission(t.getString(k + ".permission"))) {
            return d.getStringList("lore_no_permission");
        }

        // Cek harga untuk menentukan lore beli
        int price = t.getInt(k + ".price", 0);
        double bal = Double.parseDouble(PlaceholderAPI.setPlaceholders(p, "%nexogems_balance%"));
        return (bal >= price) ? d.getStringList("lore_locked_affordable") : d.getStringList("lore_locked_expensive");
    }

    private List<String> processLore(List<String> temp, ConfigurationSection sec, String key, Player p) {
        List<String> res = new ArrayList<>();
        for (String s : temp) {
            String line = s.replace("%price%", String.valueOf(sec.getInt(key + ".price")))
                    .replace("%rarity%", PlaceholderAPI.setPlaceholders(p, "%nexustags_rarity_" + key + "%"))
                    .replace("%preview%", PlaceholderAPI.setPlaceholders(p, "%nexustags_preview_" + key + "%"));
            if (line.contains("%description%")) res.addAll(sec.getStringList(key + ".description"));
            else res.add(line);
        }
        return res;
    }

    private void renderFiller(Inventory inv, int rows, ConfigurationSection s, Player p) {
        ConfigurationSection f = s.getConfigurationSection("filler");
        if (f != null && f.getBoolean("enabled", true)) {
            for (int i = 0; i < inv.getSize(); i++) if (isEdgeSlot(i, rows)) inv.setItem(i, Utils.createItem(p, f.getString("material"), f.getString("name"), null));
        }
    }

    private void renderStaticItems(Inventory inv, ConfigurationSection s, Player p) {
        ConfigurationSection items = s.getConfigurationSection("items");
        if (items != null) {
            for (String k : items.getKeys(false)) {
                ConfigurationSection it = items.getConfigurationSection(k);
                inv.setItem(it.getInt("slot"), Utils.createItem(p, it.getString("material"), it.getString("name"), it.getStringList("lore")));
            }
        }
    }

    private boolean isEdgeSlot(int s, int r) { int c = s % 9; int row = s / 9; return c == 0 || c == 8 || row == 0 || row == r - 1; }
    private List<Integer> getAvailableSlots(int st, int e, int r) { List<Integer> l = new ArrayList<>(); for (int i = st; i <= e; i++) if (!isEdgeSlot(i, r)) l.add(i); return l; }
}