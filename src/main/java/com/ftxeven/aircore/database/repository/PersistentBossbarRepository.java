package com.ftxeven.aircore.database.repository;

import java.util.List;
import java.util.Map;

// one row per announcement key that currently has a persist: true bossbar active
public interface PersistentBossbarRepository {

    List<State> findAll();

    void save(State state);

    void clear(String key);

    record State(
            String key, // the announcement's config key
            String text,
            int durationSeconds, // <= 0 means infinite
            String color,
            String overlay,
            boolean countdown,
            double initialProgress,
            long startedAtEpochMillis,
            Map<String, String> placeholders,
            boolean syncOnJoin,
            boolean force,
            List<String> conditions  // re-checked per joining player
    ) {}
}