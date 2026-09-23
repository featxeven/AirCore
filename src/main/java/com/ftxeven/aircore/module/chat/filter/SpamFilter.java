package com.ftxeven.aircore.module.chat.filter;

import com.ftxeven.aircore.module.chat.ChatConfig;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpamFilter {

    private static final String LANG_KEY = "chat.errors.filters.spam";

    private record Entry(long timestampMillis, String message) {}

    private final ConcurrentHashMap<UUID, Deque<Entry>> history = new ConcurrentHashMap<>();

    public FilterVerdict apply(ChatConfig.SpamFilter config, Player sender, String body) {
        if (!config.enabled() || bypassed(sender)) {
            return FilterVerdict.allow(body);
        }

        long now = System.currentTimeMillis();
        long windowMillis = config.window() * 1000L;
        Deque<Entry> recent = history.computeIfAbsent(sender.getUniqueId(), ignored -> new ArrayDeque<>());

        synchronized (recent) {
            prune(recent, now, windowMillis);

            if (config.similarityCheck()) {
                for (Entry entry : recent) {
                    if (similarity(entry.message(), body) >= config.similarityThreshold()) {
                        return FilterVerdict.block(LANG_KEY);
                    }
                }
            }

            if (recent.size() >= config.maxMessages()) {
                return FilterVerdict.block(LANG_KEY);
            }

            recent.addLast(new Entry(now, body));
        }
        return FilterVerdict.allow(body);
    }

    private void prune(Deque<Entry> recent, long now, long windowMillis) {
        while (!recent.isEmpty() && now - recent.peekFirst().timestampMillis() > windowMillis) {
            recent.pollFirst();
        }
    }

    private int similarity(String a, String b) {
        String left = a.toLowerCase(Locale.ROOT);
        String right = b.toLowerCase(Locale.ROOT);
        int maxLength = Math.max(left.length(), right.length());
        if (maxLength == 0) {
            return 100;
        }
        int distance = levenshtein(left, right);
        return (int) Math.round((1.0 - ((double) distance / maxLength)) * 100);
    }

    private int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    public void handleQuit(UUID uuid) {
        history.remove(uuid);
    }

    private boolean bypassed(Player sender) {
        return sender.hasPermission(Permissions.Bypass.filter("spam"));
    }
}