package com.ftxeven.aircore.module.afk;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.config.MessageComponents;
import com.ftxeven.aircore.core.command.CommandExecution;
import com.ftxeven.aircore.core.condition.ConditionEvaluator;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.MiniText;
import com.ftxeven.aircore.util.Placeholders;
import com.ftxeven.aircore.util.Scheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

final class AfkActionRunner {

    private static final String BOSSBAR_KEY_PREFIX = "afk:";

    // a subtitle needs a title to ride on, so a subtitle-only step gets an empty one with the usual timings
    private static final MessageComponents.Timed BLANK_TITLE = new MessageComponents.Timed("", 10, 40, 10);

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
        List<AfkConfig.Action> actionsAfter = configs.afk().actionsAfter();
        if (actionsAfter.isEmpty()) {
            return List.of();
        }

        List<ScheduledTask> tasks = new ArrayList<>(actionsAfter.size());
        for (AfkConfig.Action action : actionsAfter) {
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

    private void fire(Player player, AfkConfig.Action action, Supplier<Map<String, String>> source) {
        Map<String, String> placeholders = source.get();
        if (placeholders == null || !player.isOnline()) {
            return; // session ended (or the player left) before this step was due
        }
        if (!conditions.evaluate(action.conditions(), Placeholders.resolver(player, placeholders))) {
            return;
        }

        MessageComponents.Bundle message = bundleOf(action);
        if (message != null) {
            messenger.send(player, message, placeholders, BOSSBAR_KEY_PREFIX + action.seconds());
        }
        runCommand(player, action.command(), placeholders);
    }

    // null when the step is command-only
    private static @Nullable MessageComponents.Bundle bundleOf(AfkConfig.Action action) {
        AfkConfig.ActionTitle title = action.title();
        AfkConfig.ActionSound sound = action.sound();
        boolean hasSubtitle = !action.subtitle().isBlank();

        if (action.chat().isEmpty() && title == null && !hasSubtitle && sound == null) {
            return null;
        }

        MessageComponents.Timed timedTitle = null;
        if (title != null) {
            timedTitle = new MessageComponents.Timed(title.text(), title.fadeIn(), title.stay(), title.fadeOut());
        } else if (hasSubtitle) {
            timedTitle = BLANK_TITLE;
        }

        return new MessageComponents.Bundle(
                action.chat(),
                timedTitle,
                hasSubtitle ? action.subtitle() : null,
                null, // actionbar
                null, // bossbar
                sound != null ? new MessageComponents.Sound(sound.key(), sound.volume(), sound.pitch()) : null
        );
    }

    private void runCommand(Player player, @Nullable AfkConfig.ActionCommand command, Map<String, String> placeholders) {
        if (command == null) {
            return;
        }

        // commands get the real name and the unescaped reason, not the MiniMessage-flavoured display values
        Map<String, String> plain = new LinkedHashMap<>(placeholders);
        plain.put("player", player.getName());
        plain.put("reason", MiniText.plain(placeholders.get("reason")));

        String resolved = Placeholders.apply(player, command.command(), plain);
        switch (command.runAs()) {
            case CONSOLE -> CommandExecution.asConsole(resolved);
            case PLAYER -> CommandExecution.asPlayer(player, resolved);
        }
    }
}