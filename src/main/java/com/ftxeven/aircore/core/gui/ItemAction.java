package com.ftxeven.aircore.core.gui;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.*;

public final class ItemAction {
    private final AirCore plugin;
    private static final Pattern DELAY = Pattern.compile("\\s*<delay:(\\d+)>\\s*$");
    private final Map<String, List<ActionData>> cache = new ConcurrentHashMap<>();
    private final Set<String> warned = Collections.synchronizedSet(new HashSet<>());

    public ItemAction(AirCore plugin) { this.plugin = plugin; }

    public void executeAll(List<String> raw, Player p, Map<String, String> ph) {
        if (p == null || raw == null || raw.isEmpty()) return;
        Map<String, String> placeholders = ph == null ? Map.of() : ph;

        String key = String.join("||", raw);
        List<ActionData> actions = cache.computeIfAbsent(key, k ->
                raw.stream().filter(Objects::nonNull).map(this::parseSingle).filter(Objects::nonNull).toList());

        for (ActionData ad : actions) {
            if (ad.delay <= 0) run(p, ad, placeholders);
            else plugin.scheduler().runDelayed(() -> run(p, ad, placeholders), ad.delay);
        }
    }

    private ActionData parseSingle(String raw) {
        String trimmed = raw.trim();
        int delay = 0;
        Matcher m = DELAY.matcher(trimmed);
        if (m.find()) {
            delay = Integer.parseInt(m.group(1));
            trimmed = trimmed.substring(0, m.start()).trim();
        }
        if (trimmed.isEmpty()) return null;

        String lower = trimmed.toLowerCase();
        if (lower.startsWith("[player]")) return new ActionData(Type.PLAYER_CMD, trimmed.substring(8).trim(), delay, 0, 0);
        if (lower.startsWith("[console]")) return new ActionData(Type.CONSOLE_CMD, trimmed.substring(9).trim(), delay, 0, 0);
        if (lower.startsWith("[message]")) return new ActionData(Type.MESSAGE, trimmed.substring(9).trim(), delay, 0, 0);
        if (lower.equals("[close]")) return new ActionData(Type.CLOSE, "", delay, 0, 0);
        if (lower.equals("[refresh]")) return new ActionData(Type.REFRESH, "", delay, 0, 0); // New Action
        if (lower.startsWith("[sound]")) {
            String[] pts = trimmed.substring(7).trim().split("\\s+");
            return new ActionData(Type.SOUND, pts[0], delay, pts.length > 1 ? f(pts[1]) : 1f, pts.length > 2 ? f(pts[2]) : 1f);
        }

        if (warned.add(trimmed)) plugin.getLogger().warning("Unknown action: " + trimmed);
        return null;
    }

    private void run(Player p, ActionData ad, Map<String, String> ph) {
        String payload = (ad.type == Type.CLOSE || ad.type == Type.REFRESH) ? "" : resolve(p, ad.payload, ph);
        switch (ad.type) {
            case PLAYER_CMD -> p.performCommand(payload);
            case CONSOLE_CMD -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), payload);
            case MESSAGE -> p.sendMessage(MessageUtil.miniRaw(payload));
            case SOUND -> SoundUtil.play(p, ad.payload, ad.vol, ad.pitch);
            case CLOSE -> p.closeInventory();
            case REFRESH -> {
                Inventory top = p.getOpenInventory().getTopInventory();
                plugin.gui().refresh(p, top, ph);
            }
        }
    }

    private String resolve(Player p, String txt, Map<String, String> ph) {
        for (var entry : ph.entrySet()) txt = txt.replace("%" + entry.getKey() + "%", entry.getValue());
        return PlaceholderUtil.apply(p, txt);
    }

    private float f(String s) { try { return Float.parseFloat(s); } catch (Exception e) { return 1f; } }

    private record ActionData(Type type, String payload, int delay, float vol, float pitch) {}
    private enum Type { PLAYER_CMD, CONSOLE_CMD, MESSAGE, SOUND, CLOSE, REFRESH }
}