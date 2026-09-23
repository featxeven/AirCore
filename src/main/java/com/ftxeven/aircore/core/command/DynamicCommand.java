package com.ftxeven.aircore.core.command;

import com.ftxeven.aircore.core.command.tabcomplete.TabPosition;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record DynamicCommand(
        boolean enabled,
        String name,
        List<String> aliases,
        String usage,
        String usageOthers,
        Map<String, String> actions,
        Map<String, String> gui,
        Map<Integer, TabPosition> tabComplete
) {
    public DynamicCommand {
        aliases = List.copyOf(aliases);
        gui = Map.copyOf(gui);
    }

    public static DynamicCommand disabled(String key) {
        return new DynamicCommand(false, key, List.of(), "", "", Map.of(), Map.of(), Map.of());
    }

    public Optional<String> gui(String key, String[] args) {
        String path = gui.get(key);
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        String resolved = CommandArgs.substitute(path, args);
        return resolved.isBlank() ? Optional.empty() : Optional.of(resolved);
    }
}