package com.ftxeven.aircore.module.chat.filter;

import com.ftxeven.aircore.core.command.CommandExecution;
import com.ftxeven.aircore.module.chat.ChatConfig;
import com.ftxeven.aircore.service.PlayerService;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class WarningLadder {

    private record State(int count, long lastViolationMillis, int highestStepFired) {}

    private final Supplier<ChatConfig> config;
    private final Messenger messenger;
    private final PlayerService players;
    private final ConcurrentHashMap<UUID, State> state = new ConcurrentHashMap<>();

    public WarningLadder(Supplier<ChatConfig> config, Messenger messenger, PlayerService players) {
        this.config = config;
        this.messenger = messenger;
        this.players = players;
    }

    public void recordViolation(Player player, String filterName, boolean blocked) {
        ChatConfig.Warnings warnings = config.get().warnings();
        if (!warnings.enabled() || warnings.steps().isEmpty()) {
            return;
        }
        if (!blocked && !warnings.countReplacements()) {
            return;
        }
        if (player.hasPermission(Permissions.Bypass.WARNINGS)) {
            return;
        }

        int weight = warnings.weightFor(filterName);
        if (weight <= 0) {
            return; // filter excluded from the ladder via weights: <filter>: 0
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long resetAfterMillis = warnings.resetAfter() * 1000L;

        State current = state.compute(uuid, (ignored, previous) -> {
            boolean expired = previous != null && resetAfterMillis > 0
                    && now - previous.lastViolationMillis() > resetAfterMillis;
            int base = (previous == null || expired) ? 0 : previous.count();
            int highestFired = (previous == null || expired) ? 0 : previous.highestStepFired();
            int next = base + weight;
            if (warnings.maxCount() >= 0) {
                next = Math.min(next, warnings.maxCount());
            }
            return new State(next, now, highestFired);
        });

        resolveStep(warnings, current.count(), current.highestStepFired())
                .ifPresent(step -> fire(player, step, current, warnings));
    }

    private Optional<Map.Entry<Integer, ChatConfig.WarningStep>> resolveStep(ChatConfig.Warnings warnings, int count, int highestFired) {
        Integer next = warnings.steps().keySet().stream()
                .filter(threshold -> threshold > highestFired && threshold <= count)
                .min(Integer::compareTo)
                .orElse(null);
        if (next != null) {
            return Optional.of(Map.entry(next, warnings.steps().get(next)));
        }

        if (warnings.repeatLastStep() <= 0) {
            return Optional.empty();
        }
        int highestDefined = Collections.max(warnings.steps().keySet());
        if (highestFired < highestDefined) {
            return Optional.empty(); // hasn't reached the top of the ladder yet
        }
        if (count > highestDefined && (count - highestDefined) % warnings.repeatLastStep() == 0) {
            return Optional.of(Map.entry(highestDefined, warnings.steps().get(highestDefined)));
        }
        return Optional.empty();
    }

    private void fire(Player player, Map.Entry<Integer, ChatConfig.WarningStep> step, State current, ChatConfig.Warnings warnings) {
        int count = current.count();
        ChatConfig.WarningStep definition = step.getValue();

        List<Integer> ordered = warnings.steps().keySet().stream().sorted().toList();
        int stepNumber = ordered.indexOf(step.getKey()) + 1;
        int totalSteps = ordered.size();

        Map<String, String> placeholders = new HashMap<>();
        players.formatDisplayName(placeholders, "player", player.getUniqueId());
        placeholders.put("count", String.valueOf(count));
        placeholders.put("step", String.valueOf(stepNumber));
        placeholders.put("total_steps", String.valueOf(totalSteps));

        if (!definition.message().isEmpty()) {
            messenger.send(player, definition.message(), placeholders);
        }

        if (!definition.command().isEmpty()) {
            for (String template : definition.command()) {
                String command = template.replace("%player%", player.getName()).replace("%count%", String.valueOf(count));
                CommandExecution.asConsole(command);
            }
        }

        if (!definition.notifyStaff().isEmpty()) {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission(Permissions.Access.NOTIFY_WARNINGS)) {
                    messenger.send(staff, definition.notifyStaff(), placeholders);
                }
            }
        }

        UUID uuid = player.getUniqueId();
        state.put(uuid, definition.resetOnTrigger()
                ? new State(0, System.currentTimeMillis(), 0)
                : new State(current.count(), current.lastViolationMillis(), step.getKey()));
    }

    public void handleQuit(UUID uuid) {
        state.remove(uuid);
    }
}