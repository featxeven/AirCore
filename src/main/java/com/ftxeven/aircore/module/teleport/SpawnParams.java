package com.ftxeven.aircore.module.teleport;

import com.ftxeven.aircore.core.command.FlagTokens;
import com.ftxeven.aircore.core.command.FlagTokens.Flag;
import com.ftxeven.aircore.core.command.FlagTokens.Token;
import com.ftxeven.aircore.database.repository.LocationRepository;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record SpawnParams(boolean firstJoin, @Nullable String group) {

    public sealed interface Result {
        record Success(SpawnParams params) implements Result {}
        record UnknownParam(String token) implements Result {}
        record InvalidValue(String param, String value) implements Result {}
        record ConflictingParams() implements Result {}
    }

    private static final String FIRST_JOIN = "first_join";
    private static final String GROUP = "group";

    private static final List<Flag> FLAGS = List.of(Flag.bool(FIRST_JOIN), Flag.any(GROUP));

    public static Result parse(String[] tokens) {
        boolean firstJoin = false;
        String group = null;

        for (String raw : tokens) {
            if (!(FlagTokens.read(raw, FLAGS) instanceof Token.Pair(String key, String value))) {
                return new Result.UnknownParam(raw);
            }

            switch (key) {
                case FIRST_JOIN -> {
                    Boolean parsed = FlagTokens.parseBoolean(value);
                    if (parsed == null) return new Result.InvalidValue(key, value);
                    firstJoin = parsed;
                }
                case GROUP -> {
                    if (value.isBlank()) return new Result.InvalidValue(key, value);
                    group = value;
                }
            }
        }

        if (firstJoin && group != null) {
            return new Result.ConflictingParams();
        }
        return new Result.Success(new SpawnParams(firstJoin, group));
    }

    public static List<String> suggestTokens(String[] priorTokens, String currentToken, List<String> knownGroups) {
        return FlagTokens.suggestTokens(FLAGS, priorTokens, currentToken,
                key -> key.equals(GROUP) ? knownGroups : List.of());
    }

    public String resolveKey() {
        if (firstJoin) {
            return LocationRepository.SPAWN_FIRST_JOIN;
        }
        if (group != null) {
            return LocationRepository.spawnGroup(group);
        }
        return LocationRepository.SPAWN_DEFAULT;
    }
}