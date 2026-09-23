package com.ftxeven.aircore.module.chat.pm;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ReplyTracker {

    private record Entry(UUID partner, long lastActivityMillis) {}

    private final Map<UUID, Entry> targets = new ConcurrentHashMap<>();

    public void remember(UUID a, UUID b) {
        long now = System.currentTimeMillis();
        targets.put(a, new Entry(b, now));
        targets.put(b, new Entry(a, now));
    }

    public Optional<UUID> get(UUID player, int expiresAfterSeconds) {
        Entry entry = targets.get(player);
        if (entry == null) {
            return Optional.empty();
        }
        if (expiresAfterSeconds > 0) {
            double elapsed = (System.currentTimeMillis() - entry.lastActivityMillis()) / 1000.0;
            if (elapsed >= expiresAfterSeconds) {
                targets.remove(player);
                return Optional.empty();
            }
        }
        return Optional.of(entry.partner());
    }

    public void clear(UUID player) {
        targets.remove(player);
    }
}