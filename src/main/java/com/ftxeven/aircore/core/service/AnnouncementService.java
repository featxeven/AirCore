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
import java.util.concurrent.ThreadLocalRandom;

public final class AnnouncementService {
    private final AirCore plugin;

    public AnnouncementService(AirCore plugin) {
        this.plugin = plugin;
    }

    public void stopAllTasks() {
        plugin.scheduler().cancelAll();
    }

    public void scheduleAnnouncement(ConfigurationSection sec, int repeatInterval) {
        plugin.scheduler().runDelayed(() -> broadcast(sec, null), repeatInterval * 20L);
    }

    public void broadcast(ConfigurationSection sec, String args) {
        if (sec == null || !sec.getBoolean("enabled", true)) return;

        ConfigurationSection sequenceSec = sec.getConfigurationSection("sequence");
        if (sequenceSec == null) return;

        List<String> stepKeys = new ArrayList<>(sequenceSec.getKeys(false));
        int stepInterval = sec.getInt("interval", 0);
        boolean ignoreToggle = sec.getBoolean("ignore-toggle", false);
        boolean pickRandom = sec.getBoolean("pick-random", false);
        Map<String, String> context = args != null ? Map.of("args", args) : Collections.emptyMap();

        if (pickRandom && !stepKeys.isEmpty()) {
            String selected = stepKeys.get(ThreadLocalRandom.current().nextInt(stepKeys.size()));
            broadcastStep(sequenceSec.getConfigurationSection(selected), ignoreToggle, context);
            scheduleNextLoop(sec);
        } else {
            executeStep(sequenceSec, stepKeys, 0, stepInterval, ignoreToggle, context);
        }
    }

    private void executeStep(ConfigurationSection sequence, List<String> keys, int index, int interval, boolean ignoreToggle, Map<String, String> context) {
        if (index >= keys.size()) {
            scheduleNextLoop(sequence.getParent());
            return;
        }

        broadcastStep(sequence.getConfigurationSection(keys.get(index)), ignoreToggle, context);

        if (index + 1 < keys.size()) {
            plugin.scheduler().runDelayed(
                    () -> executeStep(sequence, keys, index + 1, interval, ignoreToggle, context),
                    Math.max(1, (long) interval * 20L)
            );
        } else {
            scheduleNextLoop(sequence.getParent());
        }
    }

    private void scheduleNextLoop(ConfigurationSection rootSec) {
        if (rootSec == null || !rootSec.getBoolean("enabled", true)) return;
        int repeatInterval = rootSec.getInt("interval", 0);
        if (repeatInterval > 0) {
            plugin.scheduler().runDelayed(() -> broadcast(rootSec, null), repeatInterval * 20L);
        }
    }

    private void broadcastStep(ConfigurationSection step, boolean ignoreToggle, Map<String, String> context) {
        if (step == null) return;
        ConfigurationSection components = step.getConfigurationSection("components");
        if (components == null) return;

        ConfigurationSection root = step.getParent() != null ? step.getParent().getParent() : null;
        List<String> globalConditions = root != null ? root.getStringList("conditions") : Collections.emptyList();
        List<String> stepConditions = step.getStringList("conditions");

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!ignoreToggle && !plugin.core().toggles().isEnabled(player.getUniqueId(), ToggleService.Toggle.ANNOUNCEMENTS)) continue;
            if (!checkConditions(player, globalConditions, context)) continue;
            if (!stepConditions.isEmpty() && !checkConditions(player, stepConditions, context)) continue;

            components.getKeys(false).forEach(type -> render(player, type, components, context));
        }
    }

    private void render(Player player, String type, ConfigurationSection components, Map<String, String> context) {
        boolean isSection = components.isConfigurationSection(type);
        ConfigurationSection data = isSection ? components.getConfigurationSection(type) : null;

        switch (type.toLowerCase()) {
            case "chat" -> {
                List<String> lines = isSection && data != null ? data.getStringList("lines") : components.getStringList(type);
                lines.forEach(line -> player.sendMessage(MessageUtil.mini(player, line, context)));
            }
            case "actionbar" -> {
                String text = isSection && data != null ? data.getString("text") : components.getString(type);
                if (text != null && !text.isBlank()) ActionbarUtil.send(plugin, player, text, context);
            }
            case "title" -> {
                if (data == null) return;
                TitleUtil.send(player, data.getString("main", ""), data.getString("sub", ""),
                        data.getInt("fadeIn", 10), data.getInt("stay", 70), data.getInt("fadeOut", 20), context);
            }
            case "bossbar" -> {
                if (data == null) return;
                BossbarUtil.send(player, data.getString("text", ""), context, data.getInt("duration", 100),
                        parseColor(data.getString("color", "WHITE")), data.getString("overlay", "PROGRESS"),
                        (float) data.getDouble("progress", 1.0), data.getBoolean("countdown", false));
            }
            case "sound" -> {
                if (data == null) return;
                String key = data.getString("key");
                if (key != null && !key.isBlank()) SoundUtil.play(player, key, (float) data.getDouble("volume", 1.0), (float) data.getDouble("pitch", 1.0));
            }
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

    private double compare(String l, String r) {
        try { return Double.parseDouble(l) - Double.parseDouble(r); }
        catch (NumberFormatException e) { return l.compareTo(r); }
    }

    private BossBar.Color parseColor(String c) {
        try { return BossBar.Color.valueOf(c.toUpperCase()); }
        catch (Exception e) { return BossBar.Color.WHITE; }
    }

    public void clearVisuals() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.clearTitle();
            p.sendActionBar(Component.empty());
            BossbarUtil.hideAll(p);
        }
    }
}