package com.ftxeven.aircore.database.repository;

import com.ftxeven.aircore.model.Variable;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface VariableRepository {

    String GLOBAL_OWNER = "GLOBAL";

    record Change(String owner, String key, @Nullable String value, @Nullable Double numeric) {}

    Map<String, String> loadGlobal();

    Map<String, String> loadPlayer(UUID owner);

    void apply(List<Change> changes);

    int deleteAll(UUID owner);

    int purgeOrphaned(Collection<String> knownKeys);

    int backfillNumeric(String key);

    List<Variable> top(String key, boolean descending, int limit, double minValue);
}