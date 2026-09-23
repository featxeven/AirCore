package com.ftxeven.aircore.migration.model;

import com.ftxeven.aircore.command.player.ToggleKey;
import com.ftxeven.aircore.model.PlayerProfile;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public final class LegacyToggles {

    private final Map<ToggleKey, Boolean> values = new EnumMap<>(ToggleKey.class);

    public LegacyToggles set(ToggleKey key, @Nullable Boolean value) {
        if (value != null) {
            values.put(key, value);
        }
        return this;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public PlayerProfile.Toggles merge(PlayerProfile.Toggles defaults) {
        PlayerProfile.Toggles result = defaults;
        for (Map.Entry<ToggleKey, Boolean> entry : values.entrySet()) {
            result = entry.getKey().with(result, entry.getValue());
        }
        return result;
    }
}