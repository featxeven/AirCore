package com.ftxeven.aircore.module.teleport.back;

import com.ftxeven.aircore.model.Position;
import com.ftxeven.aircore.module.teleport.TeleportConfig;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class BackLocationTracker {

    private final Supplier<TeleportConfig> config;
    private final Map<UUID, Deque<Position>> history = new ConcurrentHashMap<>();

    public BackLocationTracker(Supplier<TeleportConfig> config) {
        this.config = config;
    }

    public int count(UUID uuid) {
        Deque<Position> deque = history.get(uuid);
        if (deque == null) {
            return 0;
        }
        synchronized (deque) {
            return deque.size();
        }
    }

    public void push(UUID uuid, Position position) {
        int maxHistory = Math.max(1, config.get().back().maxHistory());
        Deque<Position> deque = history.computeIfAbsent(uuid, ignored -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(position);
            while (deque.size() > maxHistory) {
                deque.pollFirst();
            }
        }
    }

    public Optional<Position> peek(UUID uuid) {
        Deque<Position> deque = history.get(uuid);
        if (deque == null) {
            return Optional.empty();
        }
        synchronized (deque) {
            return Optional.ofNullable(deque.peekLast());
        }
    }

    public void remove(UUID uuid, Position position) {
        Deque<Position> deque = history.get(uuid);
        if (deque == null) {
            return;
        }
        synchronized (deque) {
            deque.remove(position);
            if (deque.isEmpty()) {
                history.remove(uuid, deque);
            }
        }
    }

    public void handleQuit(UUID uuid) {
        history.remove(uuid);
    }
}