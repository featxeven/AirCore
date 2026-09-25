package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.command.player.PlayerTargetResolver;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.TimeFormatter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SeenCommand extends BaseCommand {

    private static final String KEY = "seen";

    public SeenCommand(Context ctx) {
        super(ctx, KEY);
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public int minArgs() { return 1; }

    @Override
    public int maxArgs() { return 1; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        resolveTarget(sender, args[0], target -> {
            completeCooldown(sender, args);
            if (Bukkit.getPlayer(target.uuid()) != null) {
                sendOnline(sender, target);
            } else {
                sendOffline(sender, target);
            }
        });
    }

    private void sendOnline(CommandSender sender, PlayerTargetResolver.Target target) {
        messenger().send(sender, configs().lang().get("utilities.seen.online"), targetPlaceholders(target));
    }

    private void sendOffline(CommandSender sender, PlayerTargetResolver.Target target) {
        Instant lastSeen = target.profile().lastSeenAt();

        Map<String, String> placeholders = targetPlaceholders(target);
        placeholders.put("date", TimeFormatter.date(lastSeen, configs().main().formatting()));
        placeholders.put("time", TimeFormatter.time(lastSeen, configs().main().formatting()));
        placeholders.put("ago", TimeFormatter.duration(
                Duration.between(lastSeen, Instant.now()), configs().main().formatting(), configs().lang()));

        messenger().send(sender, configs().lang().get("utilities.seen.offline"), placeholders);
    }

    private Map<String, String> targetPlaceholders(PlayerTargetResolver.Target target) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        services().players().formatDisplayName(placeholders, "target", target.profile());
        return placeholders;
    }
}