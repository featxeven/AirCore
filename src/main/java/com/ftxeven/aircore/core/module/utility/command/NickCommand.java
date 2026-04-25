package com.ftxeven.aircore.core.module.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NickCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERM = "aircore.command.nick";
    private static final String PERM_OTHERS = "aircore.command.nick.others";

    public NickCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            if (args.length == 0) { sender.sendMessage("Usage: /nick <player> [nick]"); return true; }
            handleConsole(sender, args);
            return true;
        }

        if (!player.hasPermission(PERM)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM));
            return true;
        }

        boolean hasOthers = player.hasPermission(PERM_OTHERS);
        int maxArgs = hasOthers ? 2 : 1;

        if (plugin.config().errorOnExcessArgs() && args.length > maxArgs) {
            String usage = hasOthers
                    ? plugin.commandConfig().getUsage("nick", "others", label)
                    : plugin.commandConfig().getUsage("nick", label);
            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", usage));
            return true;
        }

        if (args.length == 0) {
            clearNick(player, player);
            return true;
        }

        if (args.length == 1) {
            if (args[0].equalsIgnoreCase(player.getName())) { clearNick(player, player); return true; }
            setNick(player, player, label, args[0]);
            return true;
        }

        OfflinePlayer target = resolve(player, args[1]);
        if (target == null) return true;

        if (args[0].equalsIgnoreCase(target.getName())) clearNick(player, target);
        else setNick(player, target, label, args[0]);
        return true;
    }

    private void handleConsole(CommandSender sender, String[] args) {
        OfflinePlayer target = resolveConsole(sender, args[0]);
        if (target == null) return;

        if (args.length == 1) {
            plugin.utility().nicks().clear(target.getUniqueId());
            sender.sendMessage("Cleared nick for " + target.getName());
            notifyTarget(target, null, consoleName());
            return;
        }

        String raw = args[1];
        String visible = stripFormatting(raw);
        if (visible.isBlank()) { sender.sendMessage("That nickname would appear empty."); return; }

        int max = plugin.config().nicknameMaxLength();
        if (visible.length() > max) { sender.sendMessage("Nickname too long (" + visible.length() + "/" + max + ")"); return; }

        String regex = plugin.config().nicknameValidationRegex();
        if (!regex.isBlank() && !visible.matches(regex)) {
            sender.sendMessage("Nickname contains invalid characters.");
            return;
        }

        plugin.utility().nicks().set(target.getUniqueId(), raw);
        sender.sendMessage("Set nick for " + target.getName() + ": " + raw);
        if (target.isOnline() && target.getPlayer() != null && plugin.config().consoleToPlayerFeedback()) {
            MessageUtil.send(target.getPlayer(), "utilities.nick.set-by", Map.of("player", consoleName(), "nick", raw));
        }
    }

    private void clearNick(CommandSender sender, OfflinePlayer target) {
        plugin.utility().nicks().clear(target.getUniqueId());
        String targetName = target.getName() != null ? target.getName() : "Unknown";

        if (sender instanceof Player p) {
            if (p.getUniqueId().equals(target.getUniqueId())) {
                MessageUtil.send(p, "utilities.nick.reset", Map.of());
            } else {
                MessageUtil.send(p, "utilities.nick.reset-for", Map.of("player", targetName));
                notifyTarget(target, null, p.getName());
            }
        }
    }

    private void setNick(CommandSender sender, OfflinePlayer target, String label, String rawNick) {
        String sanitized = (sender instanceof Player p)
                ? plugin.chat().formats().sanitizeForNick(p, rawNick)
                : rawNick;

        String visible = stripFormatting(sanitized);

        if (visible.isBlank()) {
            String usage = (sender instanceof Player p && p.hasPermission(PERM_OTHERS))
                    ? plugin.commandConfig().getUsage("nick", "others", label)
                    : plugin.commandConfig().getUsage("nick", label);
            if (sender instanceof Player p) MessageUtil.send(p, "errors.incorrect-usage", Map.of("usage", usage));
            return;
        }

        int max = plugin.config().nicknameMaxLength();
        if (visible.length() > max) {
            if (sender instanceof Player p) {
                MessageUtil.send(p, "utilities.nick.errors.too-long", Map.of(
                        "max", String.valueOf(max),
                        "length", String.valueOf(visible.length())
                ));
            }
            return;
        }

        String regex = plugin.config().nicknameValidationRegex();
        if (!regex.isBlank() && !visible.matches(regex)) {
            if (sender instanceof Player p) MessageUtil.send(p, "utilities.nick.errors.invalid-name", Map.of());
            return;
        }

        plugin.utility().nicks().set(target.getUniqueId(), sanitized);
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        String senderName = (sender instanceof Player p) ? p.getName() : consoleName();

        if (sender instanceof Player p) {
            if (p.getUniqueId().equals(target.getUniqueId())) {
                MessageUtil.send(p, "utilities.nick.set", Map.of("nick", sanitized));
            } else {
                MessageUtil.send(p, "utilities.nick.set-for", Map.of("player", targetName, "nick", sanitized));
                notifyTarget(target, sanitized, senderName);
            }
        }
    }

    private String stripFormatting(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]+>", "")
                .replaceAll("(?i)&[0-9a-fk-or]", "")
                .replaceAll("(?i)&&?#[0-9a-f]{6}", "")
                .trim();
    }

    private void notifyTarget(OfflinePlayer target, String nick, String senderName) {
        if (!target.isOnline() || target.getPlayer() == null) return;
        if (nick == null) MessageUtil.send(target.getPlayer(), "utilities.nick.reset-by", Map.of("player", senderName));
        else MessageUtil.send(target.getPlayer(), "utilities.nick.set-by", Map.of("player", senderName, "nick", nick));
    }

    private String consoleName() { return String.valueOf(plugin.lang().get("general.console-name")); }

    private OfflinePlayer resolve(Player sender, String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online;
        UUID uuid = plugin.database().records().uuidFromName(name);
        if (uuid != null) return Bukkit.getOfflinePlayer(uuid);
        MessageUtil.send(sender, "errors.player-never-joined", Map.of());
        return null;
    }

    private OfflinePlayer resolveConsole(CommandSender sender, String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online;
        UUID uuid = plugin.database().records().uuidFromName(name);
        if (uuid != null) return Bukkit.getOfflinePlayer(uuid);
        sender.sendMessage("Player not found");
        return null;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission(PERM)) return Collections.emptyList();
        if (args.length == 2 && sender.hasPermission(PERM_OTHERS)) {
            String input = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(input))
                    .limit(20).toList();
        }
        return Collections.emptyList();
    }
}