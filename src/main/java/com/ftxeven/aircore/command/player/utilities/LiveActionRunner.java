package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.command.Feedback;
import com.ftxeven.aircore.command.Scopes.LiveScope;
import com.ftxeven.aircore.command.player.utilities.LiveActionCommand.Keys;
import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class LiveActionRunner {

    private final ConfigManager configs;
    private final Messenger messenger;
    private final Feedback feedback;

    public LiveActionRunner(ConfigManager configs, Messenger messenger, Feedback feedback) {
        this.configs = configs;
        this.messenger = messenger;
        this.feedback = feedback;
    }

    public void run(CommandSender sender, LiveScope scope, Keys keys, Function<Player, Optional<String>> apply, Runnable onDone) {
        switch (scope) {
            case LiveScope.Single single -> runOne(sender, single, keys, apply, onDone);
            case LiveScope.Online online -> runAll(sender, online.players(), keys, apply, onDone);
        }
    }

    private void runOne(CommandSender sender, LiveScope.Single single, Keys keys, Function<Player, Optional<String>> apply, Runnable onDone) {
        Player target = single.player();
        boolean self = single.self();
        Scheduler.runEntity(target, () -> {
            Optional<String> skipped = apply.apply(target);
            if (skipped.isPresent()) {
                String errorKey = keys.errorBase() + skipped.get() + (self ? "" : "-for");
                messenger.send(sender, configs.lang().get(errorKey), self ? Map.of() : feedback.targetPlaceholders(target));
                return;
            }
            onDone.run();
            feedback.announce(sender, target.getUniqueId(), self, keys.self(), keys.other(), keys.by(), Map.of());
        });
    }

    private void runAll(CommandSender sender, List<Player> targets, Keys keys, Function<Player, Optional<String>> apply, Runnable onDone) {
        for (Player online : targets) {
            Scheduler.runEntity(online, () -> {
                if (apply.apply(online).isEmpty()) {
                    feedback.notifyTarget(sender, online.getUniqueId(), configs.lang().get(keys.by()), Map.of());
                }
            });
        }
        onDone.run();
        messenger.send(sender, configs.lang().get(keys.all()));
    }
}