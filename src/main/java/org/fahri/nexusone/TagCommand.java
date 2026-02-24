package org.fahri.nexusone;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TagCommand implements CommandExecutor, TabCompleter {
    private final NexusTags plugin;

    public TagCommand(NexusTags plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Player player = (sender instanceof Player) ? (Player) sender : null;

        // 1. Perintah Dasar /tag (Buka GUI)
        if (args.length == 0) {
            if (player == null) {
                sender.sendMessage("Hanya pemain yang bisa membuka menu!");
                return true;
            }
            // Menggunakan instance GUIListener dari plugin
            new GUIListener(plugin).openGUI(player, 0);
            return true;
        }

        // 2. Perintah /tag help
        if (args[0].equalsIgnoreCase("help")) {
            sendMsg(sender, "messages.usage_help");
            return true;
        }

        if (args[0].equalsIgnoreCase("create") || args[0].equalsIgnoreCase("edit")) {
            if (!(sender instanceof Player)) return true;
            if (args.length < 2) {
                sender.sendMessage(Utils.parse("&cFormat: /tag " + args[0] + " <tagId>"));
                return true;
            }

            Player p = (Player) sender;
            String tid = args[1];

            TagEditorSession session = plugin.getEditorSessions().get(p.getUniqueId());

            if (session == null || !session.tagId.equals(tid)) {
                session = new TagEditorSession(tid);
                // Jika mode edit, ambil data lama
                if (args[0].equalsIgnoreCase("edit") && plugin.getTagsConfig().contains("tags." + tid)) {
                    session.display = plugin.getTagsConfig().getString("tags." + tid + ".display");
                    session.price = plugin.getTagsConfig().getInt("tags." + tid + ".price", 0);
                    session.permission = plugin.getTagsConfig().getString("tags." + tid + ".permission", "");
                    session.icon = plugin.getTagsConfig().getString("tags." + tid + ".icon", "PAPER");
                    session.description = plugin.getTagsConfig().getStringList("tags." + tid + ".description");
                }
                plugin.getEditorSessions().put(p.getUniqueId(), session);
            }

            new GUIListener(plugin).openTagEditor(p, session);
            return true;
        }

        // 3. Perintah /tag reload
        if (args[0].equalsIgnoreCase("reload")) {
            if (player != null && !player.hasPermission("nexustags.admin")) {
                sendMsg(sender, "messages.no_permission");
                return true;
            }
            plugin.reloadPlugin(); // Menggunakan metode reload yang kita buat di NexusTags.java
            sendMsg(sender, "messages.reload_success");
            return true;
        }

        // 4. Perintah /tag dbmigrate <push/pull> (FITUR BARU)
        if (args[0].equalsIgnoreCase("dbmigrate")) {
            if (player != null && !player.hasPermission("nexustags.admin")) {
                sendMsg(sender, "messages.no_permission");
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage(Utils.parse(player, "&eGunakan: &f/tag dbmigrate <push/pull>"));
                return true;
            }

            if (args[1].equalsIgnoreCase("push")) {
                // Upload config lokal ke MySQL
                plugin.getDbManager().saveConfigToCloud("tags.yml", plugin.getTagsConfig().saveToString());
                plugin.getDbManager().saveConfigToCloud("gui.yml", plugin.getGuiConfig().saveToString());
                plugin.getDbManager().saveConfigToCloud("messages.yml", plugin.getMsgsConfig().saveToString());
                sender.sendMessage(Utils.parse(player, "&a[NexusTags] Config lokal berhasil di-PUSH ke MySQL!"));
            }
            else if (args[1].equalsIgnoreCase("pull")) {
                // Download config dari MySQL ke lokal
                try {
                    String[] files = {"tags.yml", "gui.yml", "messages.yml"};
                    for (String fileName : files) {
                        String content = plugin.getDbManager().loadConfigFromCloud(fileName);
                        if (content != null) {
                            Files.write(new File(plugin.getDataFolder(), fileName).toPath(), content.getBytes());
                        }
                    }
                    plugin.reloadPlugin(); // Reload otomatis setelah ditarik
                    sender.sendMessage(Utils.parse(player, "&b[NexusTags] Config berhasil di-PULL dari MySQL dan di-reload!"));
                } catch (Exception e) {
                    sender.sendMessage(Utils.parse(player, "&cGagal melakukan pull data dari database!"));
                    e.printStackTrace();
                }
            }
            return true;
        }

        // 5. Perintah /tag unlock <player> <tag_id>
        if (args[0].equalsIgnoreCase("unlock")) {
            if (player != null && !player.hasPermission("nexustags.admin")) {
                sendMsg(sender, "messages.no_permission");
                return true;
            }

            if (args.length < 3) {
                sendMsg(sender, "messages.usage_tag_unlock");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            String tagId = args[2];

            if (target == null) {
                sender.sendMessage(Utils.parse(player, "&cPlayer tidak ditemukan!"));
                return true;
            }

            if (!plugin.getTagsConfig().contains("tags." + tagId)) {
                sender.sendMessage(Utils.parse(player, "&cTag ID '" + tagId + "' tidak valid!"));
                return true;
            }

            plugin.getDbManager().addUnlockedTag(target.getUniqueId(), tagId);
            sender.sendMessage(Utils.parse(player, "&aBerhasil membuka tag &f" + tagId + " &auntuk &e" + target.getName()));
            return true;
        }

        // 6. Perintah /tag remove <player> <tag_id>
        if (args[0].equalsIgnoreCase("remove")) {
            if (player != null && !player.hasPermission("nexustags.admin")) {
                sendMsg(sender, "messages.no_permission");
                return true;
            }

            if (args.length < 3) {
                sendMsg(sender, "messages.usage_tag_remove");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            String tagId = args[2];

            if (target == null) {
                sender.sendMessage(Utils.parse(player, "&cPlayer tidak ditemukan!"));
                return true;
            }

            plugin.getDbManager().removeUnlockedTag(target.getUniqueId(), tagId);
            sender.sendMessage(Utils.parse(player, "&eBerhasil menghapus tag &f" + tagId + " &edari &c" + target.getName()));
            return true;
        }

        // Default: Jika sub-perintah salah, kirim Help
        sendMsg(sender, "messages.usage_help");
        return true;
    }

    private void sendMsg(CommandSender sender, String path) {
        Player p = (sender instanceof Player) ? (Player) sender : null;
        List<String> msgList = plugin.getMsgsConfig().getStringList(path);

        if (!msgList.isEmpty()) {
            for (String line : msgList) {
                sender.sendMessage(Utils.parse(p, line));
            }
        } else {
            String singleMsg = plugin.getMsgsConfig().getString(path);
            if (singleMsg != null) {
                sender.sendMessage(Utils.parse(p, singleMsg));
            }
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            subs.add("help");
            subs.add("reload");
            if (sender.hasPermission("nexustags.admin")) {
                subs.add("unlock");
                subs.add("remove");
                subs.add("dbmigrate"); // Tab complete untuk fitur baru
                subs.add("create");
                subs.add("edit");
            }
            return subs.stream().filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("dbmigrate") && sender.hasPermission("nexustags.admin")) {
                List<String> options = new ArrayList<>();
                options.add("push");
                options.add("pull");
                return options.stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("unlock") || args[0].equalsIgnoreCase("remove")) {
                return null; // Daftar player online
            }
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("unlock") || args[0].equalsIgnoreCase("remove"))) {
            if (plugin.getTagsConfig().getConfigurationSection("tags") == null) return Collections.emptyList();
            return new ArrayList<>(plugin.getTagsConfig().getConfigurationSection("tags").getKeys(false));
        }

        return Collections.emptyList();
    }
}