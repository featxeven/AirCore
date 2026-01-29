package com.ftxeven.aircore.module.gui;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import com.ftxeven.aircore.util.PlaceholderUtil;
import com.ftxeven.aircore.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ItemAction {
    private final AirCore plugin;
    private static final Pattern DELAY = Pattern.compile("\\s*<delay:(\\d+)>\\s*$");

    private final Map<String, List<ParsedAction>> parsedCache = new ConcurrentHashMap<>();
    private final Set<String> warnedUnknown = Collections.synchronizedSet(new HashSet<>());

    public ItemAction(AirCore plugin) {
        this.plugin = plugin;
    }

    public void executeAll(List<String> rawActions,
                           Player viewer,
                           Map<String, String> placeholders) {
        if (viewer == null || rawActions == null || rawActions.isEmpty()) return;

        Map<String, String> ph = (placeholders == null) ? Collections.emptyMap() : placeholders;

        String cacheKey = buildActionsKey(rawActions);

        List<ParsedAction> actions = parsedCache.computeIfAbsent(cacheKey, k -> {
            List<ParsedAction> parsed = new ArrayList<>(rawActions.size());
            for (String raw : rawActions) {
                if (raw == null) continue;
                ParsedAction pa = parseSingle(raw);
                if (pa != null) parsed.add(pa);
            }
            return parsed;
        });

        for (ParsedAction pa : actions) {
            if (pa.delay <= 0) {
                runAction(viewer, pa, ph);
            } else {
                plugin.scheduler().runDelayed(() -> runAction(viewer, pa, ph), pa.delay);
            }
        }
    }

    private String buildActionsKey(List<String> raw) {
        return String.join("||", raw.stream().map(s -> s == null ? "" : s.trim()).toList());
    }

    private ParsedAction parseSingle(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();

        // extract delay
        Matcher m = DELAY.matcher(trimmed);
        int delay = 0;
        if (m.find()) {
            try {
                delay = Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
                plugin.getLogger().warning("Invalid delay tag in action: " + raw);
            }
            trimmed = trimmed.substring(0, m.start()).trim();
        }

        if (trimmed.isBlank()) return null;

        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("[player]")) {
            return new ParsedAction(ActionType.PLAYER_CMD, trimmed.substring(8).trim(), delay);
        }
        if (lower.startsWith("[console]")) {
            return new ParsedAction(ActionType.CONSOLE_CMD, trimmed.substring(9).trim(), delay);
        }
        if (lower.startsWith("[message]")) {
            return new ParsedAction(ActionType.MESSAGE, trimmed.substring(9).trim(), delay);
        }
        if (lower.startsWith("[sound]")) {
            String payload = trimmed.substring(7).trim();
            String[] parts = payload.split("\\s+");
            String name = parts.length >= 1 ? parts[0] : "";
            float vol = parts.length >= 2 ? parseFloatSafe(parts[1], payload) : 1f;
            float pitch = parts.length >= 3 ? parseFloatSafe(parts[2], payload) : 1f;
            return new ParsedAction(ActionType.SOUND, name, delay, vol, pitch);
        }
        if (lower.equals("[close]")) {
            return new ParsedAction(ActionType.CLOSE, "", delay);
        }

        if (warnedUnknown.add(trimmed)) {
            plugin.getLogger().warning("Unknown action type: " + trimmed);
        }
        return new ParsedAction(ActionType.UNKNOWN, trimmed, delay);
    }

    private void runAction(Player player, ParsedAction pa, Map<String, String> placeholders) {
        if (player == null || pa == null) return;

        switch (pa.type) {
            case PLAYER_CMD -> runCommand(pa.payload, player, placeholders, false);
            case CONSOLE_CMD -> runCommand(pa.payload, player, placeholders, true);
            case MESSAGE -> {
                String resolved = resolvePlaceholders(player, pa.payload, placeholders);
                player.sendMessage(MessageUtil.miniRaw(resolved));
            }
            case SOUND -> {
                if (pa.payload == null || pa.payload.isBlank()) {
                    plugin.getLogger().warning("Missing sound name in action payload.");
                } else {
                    SoundUtil.play(player, pa.payload, pa.soundVol, pa.soundPitch);
                }
            }
            case CLOSE -> player.closeInventory();
            case UNKNOWN -> { /* already warned at parse time */ }
        }
    }


    private void runCommand(String payload, Player player, Map<String, String> placeholders, boolean console) {
        String cmd = resolvePlaceholders(player, payload, placeholders);
        if (console) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        } else {
            player.performCommand(cmd);
        }
    }

    private String resolvePlaceholders(Player viewer, String text, Map<String, String> custom) {
        if (text == null) return "";
        String result = text;

        if (custom != null) {
            for (Map.Entry<String, String> entry : custom.entrySet()) {
                result = result.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }

        return PlaceholderUtil.apply(viewer, result);
    }

    private float parseFloatSafe(String s, String context) {
        try {
            return Float.parseFloat(s);
        } catch (Exception e) {
            if (warnedUnknown.add("float:" + context)) {
                plugin.getLogger().warning("Invalid float value in action: " + context);
            }
            return 1.0f;
        }
    }

    private static final class ParsedAction {
        final ActionType type;
        final String payload;
        final int delay;
        final float soundVol;
        final float soundPitch;

        ParsedAction(ActionType type, String payload, int delay) {
            this(type, payload, delay, 1f, 1f);
        }

        ParsedAction(ActionType type, String payload, int delay, float vol, float pitch) {
            this.type = type;
            this.payload = payload;
            this.delay = Math.max(0, delay);
            this.soundVol = vol;
            this.soundPitch = pitch;
        }
    }

    private enum ActionType {
        PLAYER_CMD,
        CONSOLE_CMD,
        MESSAGE,
        SOUND,
        CLOSE,
        UNKNOWN
    }
}
