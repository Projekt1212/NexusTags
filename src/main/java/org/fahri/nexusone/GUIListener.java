package org.fahri.nexusone;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import me.clip.placeholderapi.PlaceholderAPI;
import java.util.*;

public class GUIListener implements Listener, InventoryHolder {
    private final NexusTags plugin;

    // Variabel static agar data filter dan halaman tidak reset saat GUI ditutup
    private static final Map<UUID, Integer> filterIndex = new HashMap<>();
    private static final Map<UUID, Integer> currentPage = new HashMap<>();
    private static final Map<UUID, String> pendingPurchase = new HashMap<>();

    public GUIListener(NexusTags plugin) { this.plugin = plugin; }

    @Override public Inventory getInventory() { return null; }

    public static Map<UUID, Integer> getFilterIndex() { return filterIndex; }
    public static Map<UUID, Integer> getCurrentPage() { return currentPage; }

    public void openGUI(Player player, int page) {
        UUID uuid = player.getUniqueId();
        filterIndex.putIfAbsent(uuid, 0);
        currentPage.put(uuid, page);
        renderGUI(player, page);
    }

    private void renderGUI(Player player, int page) {
        ConfigurationSection guiSec = plugin.getGuiConfig().getConfigurationSection("gui");
        if (guiSec == null) return;

        int rows = guiSec.getInt("rows", 6);
        String pageFormat = guiSec.getString("page_format", " - %page%").replace("%page%", String.valueOf(page + 1));
        String title = guiSec.getString("title", "TAG MENU").replace("%page_format%", pageFormat);

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
                inv.setItem(slots.get(i - start), Utils.createItem(player,
                        tagsSec.getString(key + ".icon", "PAPER"),
                        tagsSec.getString(key + ".display", key),
                        processLore(loreTemplate, tagsSec, key, player)));
            }
            renderNavigation(inv, page, keys.size(), ipp, guiSec, player);
        }
        player.openInventory(inv);
    }

    public void openConfirmationGUI(Player player, String tagId) {
        ConfigurationSection conf = plugin.getConfirmationConfig().getConfigurationSection("gui");
        Inventory inv = Bukkit.createInventory(this, conf.getInt("rows", 3) * 9, Utils.parse(player, conf.getString("title")));

        ItemStack filler = Utils.createItem(player, conf.getString("filler.material"), conf.getString("filler.name"), null);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        ConfigurationSection tagSec = plugin.getTagsConfig().getConfigurationSection("tags." + tagId);
        String display = tagSec.getString("display", tagId);
        // Memanggil preview lengkap (format chat)
        String preview = PlaceholderAPI.setPlaceholders(player, "%nexustags_preview_" + tagId + "%");

        inv.setItem(conf.getInt("tag_preview_slot"), Utils.createItem(player, tagSec.getString("icon", "PAPER"), display, tagSec.getStringList("description")));

        List<String> confirmLore = new ArrayList<>();
        for (String s : conf.getStringList("confirm.lore")) {
            confirmLore.add(s.replace("%tag_display%", display)
                    .replace("%price%", String.valueOf(tagSec.getInt("price")))
                    .replace("%preview%", preview));
        }
        inv.setItem(conf.getInt("confirm.slot"), Utils.createItem(player, conf.getString("confirm.material"), conf.getString("confirm.name"), confirmLore));
        inv.setItem(conf.getInt("cancel.slot"), Utils.createItem(player, conf.getString("cancel.material"), conf.getString("cancel.name"), conf.getStringList("cancel.lore")));

        pendingPurchase.put(player.getUniqueId(), tagId);
        player.openInventory(inv);
    }

    public void openTagEditor(Player player, TagEditorSession session) {
        Inventory inv = Bukkit.createInventory(null, 36, Utils.parse("&0Editor: " + session.tagId));

        // Item Preview
        inv.setItem(13, Utils.createItem(player, session.icon, session.display, session.description));

        // Tombol Edit Display
        inv.setItem(10, Utils.createItem(player, "NAME_TAG", "&eUbah Display", List.of("&7Sekarang: " + session.display)));

        // Tombol Edit Price
        inv.setItem(11, Utils.createItem(player, "GOLD_INGOT", "&eUbah Harga", List.of("&7Sekarang: " + session.price, "&8(-1 untuk hanya perm)")));

        // Tombol Edit Permission
        inv.setItem(12, Utils.createItem(player, "BARRIER", "&eUbah Permission", List.of("&7Sekarang: " + session.permission, "&8(Kosongkan untuk hanya harga)")));

        // Tombol Simpan & Buang
        inv.setItem(30, Utils.createItem(player, "RED_WOOL", "&cDiscard Changes", null));
        inv.setItem(32, Utils.createItem(player, "LIME_WOOL", "&aConfirm & Save", null));

        player.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GUIListener)) return;
        e.setCancelled(true);
        Player p = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();
        UUID uuid = p.getUniqueId();

        // Cek Judul Dinamis Konfirmasi
        String currentTitle = e.getView().getTitle();
        String formattedConfTitle = Utils.applyLegacy(p, plugin.getConfirmationConfig().getString("gui.title"));

        if (currentTitle.equals(formattedConfTitle)) {
            String tid = pendingPurchase.get(uuid);
            if (tid == null) return;
            ConfigurationSection conf = plugin.getConfirmationConfig().getConfigurationSection("gui");

            if (slot == conf.getInt("confirm.slot")) {
                processPurchase(p, tid);
                pendingPurchase.remove(uuid);
            } else if (slot == conf.getInt("cancel.slot")) {
                pendingPurchase.remove(uuid);
                openGUI(p, 0);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            }
            return;
        }

        ConfigurationSection gui = plugin.getGuiConfig().getConfigurationSection("gui");
        if (slot == gui.getInt("controls.close.slot")) p.closeInventory();
        else if (slot == gui.getInt("navigation.previous_page.slot") && currentPage.getOrDefault(uuid, 0) > 0) {
            currentPage.put(uuid, currentPage.get(uuid) - 1);
            renderGUI(p, currentPage.get(uuid));
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        } else if (slot == gui.getInt("navigation.next_page.slot")) {
            ConfigurationSection tagsSec = plugin.getTagsConfig().getConfigurationSection("tags");
            int mode = filterIndex.getOrDefault(uuid, 0);
            List<String> keys = getSortedKeys(p, tagsSec, plugin.getDbManager().getUnlockedTags(uuid), mode);
            List<Integer> slots = getAvailableSlots(gui.getInt("start_slot"), gui.getInt("end_slot"), gui.getInt("rows"));
            if ((currentPage.getOrDefault(uuid, 0) + 1) * slots.size() < keys.size()) {
                currentPage.put(uuid, currentPage.get(uuid) + 1);
                renderGUI(p, currentPage.get(uuid));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            }
        } else if (slot == gui.getInt("controls.filter_hopper.slot")) {
            filterIndex.put(uuid, (filterIndex.getOrDefault(uuid, 0) + 1) % 4);
            currentPage.put(uuid, 0);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            renderGUI(p, 0);
        } else {
            handleTagSelection(p, slot, currentPage.getOrDefault(uuid, 0), gui);
        }
    }

    private void handleTagSelection(Player p, int slot, int page, ConfigurationSection guiSec) {
        ConfigurationSection tagsSec = plugin.getTagsConfig().getConfigurationSection("tags");
        List<String> u = plugin.getDbManager().getUnlockedTags(p.getUniqueId());
        String activeTag = plugin.getDbManager().getActiveTagSync(p.getUniqueId());
        List<String> filtered = getSortedKeys(p, tagsSec, u, filterIndex.getOrDefault(p.getUniqueId(), 0));
        List<Integer> slots = getAvailableSlots(guiSec.getInt("start_slot"), guiSec.getInt("end_slot"), guiSec.getInt("rows"));

        if (slots.contains(slot)) {
            int idx = (page * slots.size()) + slots.indexOf(slot);
            if (idx >= filtered.size()) return;
            String tid = filtered.get(idx);
            String display = tagsSec.getString(tid + ".display", tid);

            if (tid.equals(activeTag)) {
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                p.sendMessage(Utils.parse(p, "&aKamu sudah memakai tag ini!"));
                return;
            }

            if (checkAccess(p, tagsSec, tid, u)) {
                plugin.getDbManager().setTag(p.getUniqueId(), tid);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                for (String s : plugin.getMsgsConfig().getStringList("messages.tag_equipped")) p.sendMessage(Utils.parse(p, s.replace("%tag%", display)));
                renderGUI(p, page);
            } else if (tagsSec.contains(tid + ".price")) {
                int pr = tagsSec.getInt(tid + ".price", 0);
                double bal = Double.parseDouble(PlaceholderAPI.setPlaceholders(p, "%nexogems_balance%"));
                if (bal >= pr) openConfirmationGUI(p, tid);
                else {
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    for (String s : plugin.getMsgsConfig().getStringList("messages.buy_failed")) p.sendMessage(Utils.parse(p, s.replace("%missing%", String.valueOf(pr - bal))));
                }
            } else {
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                for (String s : plugin.getMsgsConfig().getStringList("messages.no_permission_tag")) p.sendMessage(Utils.parse(p, s));
            }
        }
    }

    private void processPurchase(Player p, String tid) {
        ConfigurationSection tagsSec = plugin.getTagsConfig().getConfigurationSection("tags");
        int pr = tagsSec.getInt(tid + ".price", 0);
        double bal = Double.parseDouble(PlaceholderAPI.setPlaceholders(p, "%nexogems_balance%"));
        String display = tagsSec.getString(tid + ".display", tid);

        if (bal >= pr) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "nb withdraw " + p.getName() + " " + pr + " Buy tag " + tid);
            plugin.getDbManager().addUnlockedTag(p.getUniqueId(), tid);
            plugin.getDbManager().setTag(p.getUniqueId(), tid);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            for (String s : plugin.getMsgsConfig().getStringList("messages.buy_success")) p.sendMessage(Utils.parse(p, s.replace("%tag%", display).replace("%price%", String.valueOf(pr))));
            openGUI(p, 0);
        } else {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            for (String s : plugin.getMsgsConfig().getStringList("messages.buy_failed")) p.sendMessage(Utils.parse(p, s.replace("%missing%", String.valueOf(pr - bal))));
        }
    }

    private void renderNavigation(Inventory inv, int page, int size, int ipp, ConfigurationSection s, Player p) {
        ConfigurationSection c = s.getConfigurationSection("controls.filter_hopper");
        int m = filterIndex.getOrDefault(p.getUniqueId(), 0);
        String n; List<String> l;
        if (m == 1) { n = c.getString("name_owned"); l = c.getStringList("lore_owned"); }
        else if (m == 2) { n = c.getString("name_rarity_asc"); l = c.getStringList("lore_rarity_asc"); }
        else if (m == 3) { n = c.getString("name_rarity_desc"); l = c.getStringList("lore_rarity_desc"); }
        else { n = c.getString("name_all"); l = c.getStringList("lore_all"); }
        inv.setItem(c.getInt("slot"), Utils.createItem(p, c.getString("material"), n, l));

        ConfigurationSection nav = s.getConfigurationSection("navigation");
        if (page > 0) inv.setItem(nav.getInt("previous_page.slot"), Utils.createItem(p, nav.getString("previous_page.material"), nav.getString("previous_page.name"), null));
        if ((page + 1) * ipp < size) inv.setItem(nav.getInt("next_page.slot"), Utils.createItem(p, nav.getString("next_page.material"), nav.getString("next_page.name"), null));
        inv.setItem(s.getInt("controls.close.slot"), Utils.createItem(p, s.getString("controls.close.material"), s.getString("controls.close.name"), null));
    }

    private List<String> getSortedKeys(Player p, ConfigurationSection t, List<String> u, int m) {
        List<String> k = new ArrayList<>(t.getKeys(false));
        if (m == 1) k.removeIf(id -> !checkAccess(p, t, id, u));
        k.sort((a, b) -> {
            int wA = plugin.getTagsConfig().getInt("rarities." + t.getString(a + ".rarity", "COMMON").toUpperCase() + ".weight", 0);
            int wB = plugin.getTagsConfig().getInt("rarities." + t.getString(b + ".rarity", "COMMON").toUpperCase() + ".weight", 0);
            int r = (m == 2) ? Integer.compare(wA, wB) : Integer.compare(wB, wA);
            return (r != 0) ? r : a.compareToIgnoreCase(b);
        });
        return k;
    }

    private boolean checkAccess(Player p, ConfigurationSection t, String k, List<String> u) {
        return (t.contains(k + ".permission") && p.hasPermission(t.getString(k + ".permission"))) || u.contains(k) || (t.getInt(k + ".price", -1) == 0);
    }

    private List<String> getLoreTemplate(boolean active, boolean unlocked, ConfigurationSection t, String k, Player p) {
        ConfigurationSection d = plugin.getGuiConfig().getConfigurationSection("tag_display");
        if (active) return d.getStringList("lore_active");
        if (unlocked) return d.getStringList("lore_unlocked");
        if (t.contains(k + ".permission") && !p.hasPermission(t.getString(k + ".permission"))) return d.getStringList("lore_no_permission");
        int pr = t.getInt(k + ".price", 0);
        double bal = Double.parseDouble(PlaceholderAPI.setPlaceholders(p, "%nexogems_balance%"));
        return (bal >= pr) ? d.getStringList("lore_locked_affordable") : d.getStringList("lore_locked_expensive");
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

    private void saveTagToConfig(TagEditorSession s) {
        FileConfiguration cfg = plugin.getTagsConfig();
        String path = "tags." + s.tagId;
        cfg.set(path + ".display", s.display);
        cfg.set(path + ".icon", s.icon);
        cfg.set(path + ".description", s.description.isEmpty() ? List.of("&7Default description") : s.description);
        cfg.set(path + ".rarity", s.rarity);

        if (s.price == -1) cfg.set(path + ".price", null);
        else cfg.set(path + ".price", s.price);

        if (s.permission.isEmpty()) cfg.set(path + ".permission", null);
        else cfg.set(path + ".permission", s.permission);

        plugin.getConfigManager().saveTagsConfig();
        plugin.reloadPlugin();
    }

    private boolean isEdgeSlot(int s, int r) { int c = s % 9; int row = s / 9; return c == 0 || c == 8 || row == 0 || row == r - 1; }
    private List<Integer> getAvailableSlots(int st, int e, int r) { List<Integer> l = new ArrayList<>(); for (int i = st; i <= e; i++) if (!isEdgeSlot(i, r)) l.add(i); return l; }
}