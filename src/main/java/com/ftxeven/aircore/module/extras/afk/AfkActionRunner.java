package com.ftxeven.aircore.module.extras.afk;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.config.MessageComponents;
import com.ftxeven.aircore.core.command.CommandExecution;
import com.ftxeven.aircore.core.condition.ConditionEvaluator;
import com.ftxeven.aircore.module.extras.ExtrasConfig;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.MiniText;
import com.ftxeven.aircore.util.Placeholders;
import com.ftxeven.aircore.util.Scheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

final class AfkActionRunner {

    private static final String BOSSBAR_KEY_PREFIX = "afk:";

    private final ConfigManager configs;
    private final Messenger messenger;
    private final ConditionEvaluator conditions;

    AfkActionRunner(Logger logger, ConfigManager configs, Messenger messenger) {
        this.configs = configs;
        this.messenger = messenger;
        this.conditions = new ConditionEvaluator(logger::warning);
    }

    // 'placeholders' yields the session's placeholders when a step is due, or null if the session already ended
    List<ScheduledTask> schedule(Player player, Supplier<Map<String, String>> placeholders) {
        if (player.hasPermission(Permissions.Bypass.AFK_ACTIONS)) {
            return List.of();
        }
        List<ExtrasConfig.AfkAction> actionsAfter = configs.extras().afk().actionsAfter();
        if (actionsAfter.isEmpty()) {
            return List.of();
        }

        List<ScheduledTask> tasks = new ArrayList<>(actionsAfter.size());
        for (ExtrasConfig.AfkAction action : actionsAfter) {
            Scheduler.runEntityLater(player, () -> fire(player, action, placeholders), action.seconds() * 20L)
                    .ifPresent(tasks::add);
        }
        return List.copyOf(tasks);
    }

    void cancel(List<ScheduledTask> tasks) {
        for (ScheduledTask task : tasks) {
            task.cancel();
        }
    }

    private void fire(Player player, ExtrasConfig.AfkAction action, Supplier<Map<String, String>> source) {
        Map<String, String> placeholders = source.get();
        if (placeholders == null || !player.isOnline()) {
            return; // session ended (or the player left) before this step was due
        }
        if (!conditions.evaluate(action.conditions(), Placeholders.resolver(player, placeholders))) {
            return;
        }

        messenger.send(player, action.message(), placeholders, BOSSBAR_KEY_PREFIX + action.seconds());
        runCommands(player, action.command(), placeholders);
    }

    private void runCommands(Player player, List<MessageComponents.CommandRun> commands, Map<String, String> placeholders) {
        if (commands.isEmpty()) {
            return;
        }

        Map<String, String> plain = new LinkedHashMap<>(placeholders);
        plain.put("player", player.getName());
        plain.put("reason", MiniText.plain(placeholders.get("reason")));

        for (MessageComponents.CommandRun command : commands) {
            String resolved = Placeholders.apply(player, command.command(), plain);
            switch (command.runAs()) {
                case CONSOLE -> CommandExecution.asConsole(resolved);
                case PLAYER -> CommandExecution.asPlayer(player, resolved);
            }
        }
    }
}