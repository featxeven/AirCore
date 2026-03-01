package com.ftxeven.aircore.core.service;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.*;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;

public final class AnnouncementService {
    private final AirCore plugin;
    private final Random random = new Random();

    public AnnouncementService(AirCore plugin) {
        this.plugin = plugin;
    }

    public void stopAllTasks() {
        plugin.scheduler().cancelAll();
    }

    public void scheduleAnnouncement(ConfigurationSection sec, int interval) {
        plugin.scheduler().runAsyncTimer(() -> broadcast(sec, null), interval * 20L, interval * 20L);
    }

    public void broadcast(ConfigurationSection sec, String args) {
        ConfigurationSection componentsSec = sec.getConfigurationSection("components");
        if (componentsSec == null) return;

        List<String> conditions = sec.getStringList("conditions");
        boolean ignoreToggle = sec.getBoolean("ignore-toggle", false);
        boolean pickRandom = sec.getBoolean("pick-random", false);
        Map<String, String> context = args != null ? Map.of("args", args) : Collections.emptyMap();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!ignoreToggle && !plugin.core().toggles().isEnabled(player.getUniqueId(), ToggleService.Toggle.ANNOUNCEMENTS)) continue;
            if (!checkConditions(player, conditions, context)) continue;

            List<String> toRun = new ArrayList<>();
            List<String> pool = new ArrayList<>();

            for (String k : componentsSec.getKeys(false)) {
                if (componentsSec.isConfigurationSection(k)) {
                    if (componentsSec.getBoolean(k + ".forced", false)) {
                        toRun.add(k);
                        continue;
                    }
                }
                pool.add(k);
            }

            if (pickRandom && !pool.isEmpty()) {
                toRun.add(pool.get(random.nextInt(pool.size())));
            } else {
                toRun.addAll(pool);
            }

            for (String compKey : toRun) {
                render(player, compKey, componentsSec, context);
            }
        }
    }

    private void render(Player player, String type, ConfigurationSection components, Map<String, String> context) {
        boolean isSection = components.isConfigurationSection(type);
        ConfigurationSection data = isSection ? components.getConfigurationSection(type) : null;

        switch (type.toLowerCase()) {
            case "chat" -> {
                List<String> lines;
                if (isSection) {
                    assert data != null;
                    lines = data.getStringList("lines");
                } else {
                    lines = components.getStringList(type);
                }

                for (String line : lines) {
                    if (line == null || line.isEmpty()) {
                        player.sendMessage(net.kyori.adventure.text.Component.empty());
                        continue;
                    }

                    player.sendMessage(MessageUtil.mini(player, line, context));
                }
            }
            case "actionbar" -> {
                if (data == null) return;
                ActionbarUtil.send(plugin, player, data.getString("text"), context);
            }
            case "title" -> {
                if (data == null) return;
                TitleUtil.send(player, data.getString("main"), data.getString("sub"),
                        data.getInt("fadeIn", 10), data.getInt("stay", 70), data.getInt("fadeOut", 20), context);
            }
            case "bossbar" -> {
                if (data == null) return;
                BossbarUtil.send(player, data.getString("text", ""), context,
                        data.getInt("duration", 100), parseColor(data.getString("color", "WHITE")),
                        data.getString("overlay", "PROGRESS"), (float) data.getDouble("progress", 1.0),
                        data.getBoolean("countdown", false));
            }
            case "sound" -> {
                if (data == null) return;
                SoundUtil.play(player, data.getString("key"),
                        (float) data.getDouble("volume", 1.0), (float) data.getDouble("pitch", 1.0));
            }
        }
    }

    private BossBar.Color parseColor(String colorName) {
        try {
            return BossBar.Color.valueOf(colorName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BossBar.Color.WHITE;
        }
    }

    private boolean checkConditions(Player p, List<String> conditions, Map<String, String> context) {
        if (conditions.isEmpty()) return true;

        for (String cond : conditions) {
            String parsed = PlainTextComponentSerializer.plainText().serialize(MessageUtil.mini(p, cond, context));
            if (!evaluateRaw(parsed)) return false;
        }
        return true;
    }

    private boolean evaluateRaw(String parsed) {
        String[] operators = {"==", "!=", "=~", "!~", ">=", "<=", ">", "<", "|-", "-|", "<>", "><"};
        String foundOp = null;
        int opIndex = -1;

        for (String op : operators) {
            int index = parsed.indexOf(" " + op + " ");
            if (index != -1) {
                foundOp = op;
                opIndex = index;
                break;
            }
        }

        if (foundOp == null) return Boolean.parseBoolean(parsed.trim());

        String left = parsed.substring(0, opIndex).trim();
        String right = parsed.substring(opIndex + foundOp.length() + 1).trim().replace("'", "");

        return switch (foundOp) {
            case "==" -> left.equals(right);
            case "!=" -> !left.equals(right);
            case "=~" -> left.equalsIgnoreCase(right);
            case "!~" -> !left.equalsIgnoreCase(right);
            case "|-" -> left.startsWith(right);
            case "-|" -> left.endsWith(right);
            case "<>" -> left.contains(right);
            case "><" -> !left.contains(right);
            case ">"  -> compare(left, right) > 0;
            case "<"  -> compare(left, right) < 0;
            case ">=" -> compare(left, right) >= 0;
            case "<=" -> compare(left, right) <= 0;
            default -> false;
        };
    }

    private double compare(String left, String right) {
        try { return Double.parseDouble(left) - Double.parseDouble(right); }
        catch (NumberFormatException e) { return left.compareTo(right); }
    }

    public void clearVisuals() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.clearTitle();
            player.sendActionBar(net.kyori.adventure.text.Component.empty());
            BossbarUtil.hideAll(player);
        }
    }
}