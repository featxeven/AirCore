package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.command.AbstractCommand;
import com.ftxeven.aircore.command.Scopes;
import com.ftxeven.aircore.command.Scopes.Scope;
import com.ftxeven.aircore.command.Scopes.ScopeAccess;
import com.ftxeven.aircore.config.MainConfig;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.core.command.CommandDispatch.Availability;
import com.ftxeven.aircore.core.command.DurationUnits;
import com.ftxeven.aircore.model.PlayerProfile;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.TimeFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public final class PlayertimeCommand extends AbstractCommand {

    private static final String KEY = "playertime";
    private static final String ADD = "add";
    private static final String RESET = "reset";
    private static final long DAY_TICKS = 24000L;
    private static final ScopeAccess ACCESS = ScopeAccess.of(KEY);

    private final DurationUnits durationUnits;

    public PlayertimeCommand(Context ctx, DurationUnits durationUnits) {
        super(ctx, KEY);
        this.durationUnits = durationUnits;
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public int minArgs() { return 1; }

    @Override
    public int maxArgs(CommandSender sender, String[] args) {
        boolean isReset = args.length > 0 && resolveAction(args[0]).filter(RESET::equals).isPresent();
        return CommandDispatch.maxArgs(isReset ? 1 : 2, Availability.ofConfig(ACCESS.canTargetOthers(sender)));
    }

    @Override
    public String usage(CommandSender sender) {
        return CommandDispatch.usage(config(), ACCESS.canTargetOthers(sender));
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Optional<String> action = resolveAction(args[0]);
        if (action.isEmpty()) {
            sendUsageError(sender, label, subLabel);
            return;
        }

        if (action.get().equals(RESET)) {
            String typed = args.length >= 2 ? args[1] : null;
            resolveScope(sender, typed, ACCESS, scope -> handleReset(sender, scope, args));
            return;
        }

        if (args.length < 2) {
            sendUsageError(sender, label, subLabel);
            return;
        }
        boolean isAdd = action.get().equals(ADD);
        OptionalLong parsed = TimeValueParser.resolve(args[1], isAdd, durationUnits, timeFilter());
        if (parsed.isEmpty()) {
            messenger().send(sender, configs().lang().get("errors.general.invalid-time"));
            return;
        }
        long ticks = parsed.getAsLong();
        long displayTicks = isAdd ? ticks : Math.floorMod(ticks, DAY_TICKS);
        String typed = args.length >= 3 ? args[2] : null;

        resolveScope(sender, typed, ACCESS, scope -> handleSetOrAdd(sender, scope, isAdd, displayTicks, ticks, args));
    }

    // Reset

    private void handleReset(CommandSender sender, Scope scope, String[] args) {
        runScope(sender, scope, () -> completeCooldown(sender, args),
                single -> runOnOwner(single, online -> {
                    services().players().updatePlayerTime(single.uuid(), null);
                    online.ifPresent(Player::resetPlayerTime);
                    completeCooldown(sender, args);
                    announce(sender, single.uuid(), single.self(),
                            "utilities.time.player.reset.self", "utilities.time.player.reset.other", "utilities.time.player.reset.by", Map.of());
                }),
                player -> {
                    services().players().updatePlayerTime(player.getUniqueId(), null);
                    player.resetPlayerTime();
                    notifyTarget(sender, player.getUniqueId(), configs().lang().get("utilities.time.player.reset.by"), Map.of());
                },
                uuid -> services().players().updatePlayerTime(uuid, null),
                new Scopes.BulkSummary("utilities.time.player.reset.online", "utilities.time.player.reset.all", Map.of()));
    }

    // Set / add

    private void handleSetOrAdd(CommandSender sender, Scope scope, boolean isAdd, long displayTicks, long ticks, String[] args) {
        String keyBase = "utilities.time.player." + (isAdd ? "add" : "set");
        runScope(sender, scope, () -> completeCooldown(sender, args),
                single -> applySetOrAddOne(sender, single, isAdd, displayTicks, ticks, args),
                player -> applyLive(sender, player, isAdd, displayTicks, ticks),
                uuid -> {
                    long base = isAdd ? offlineTime(uuid) : 0L;
                    services().players().updatePlayerTime(uuid, (int) newTime(isAdd, base, ticks));
                },
                new Scopes.BulkSummary(keyBase + ".online", keyBase + ".all", timePlaceholders(isAdd, displayTicks)));
    }

    private void applySetOrAddOne(CommandSender sender, Scope.Single target, boolean isAdd, long displayTicks, long ticks, String[] args) {
        runOnOwner(target, online -> {
            if (online.isPresent()) {
                Player player = online.get();
                if (blockedByWorldRestriction(sender, player, target.self(), MainConfig.RestrictedFeature.CUSTOM_TIME)) {
                    return;
                }
                long updated = newTime(isAdd, player.getPlayerTime(), ticks);
                player.setPlayerTime(updated, false);
                services().players().updatePlayerTime(target.uuid(), (int) updated);
            } else {
                long base = isAdd ? offlineTime(target.profile()) : 0L;
                services().players().updatePlayerTime(target.uuid(), (int) newTime(isAdd, base, ticks));
            }
            completeCooldown(sender, args);
            announce(sender, target.uuid(), target.self(),
                    "utilities.time.player." + (isAdd ? "add" : "set") + ".self",
                    "utilities.time.player." + (isAdd ? "add" : "set") + ".other",
                    "utilities.time.player." + (isAdd ? "add" : "set") + ".by",
                    timePlaceholders(isAdd, displayTicks));
        });
    }

    private void applyLive(CommandSender sender, Player player, boolean isAdd, long displayTicks, long ticks) {
        if (isWorldRestrictedFor(sender, player, MainConfig.RestrictedFeature.CUSTOM_TIME)) {
            return;
        }
        long updated = newTime(isAdd, player.getPlayerTime(), ticks);
        player.setPlayerTime(updated, false);
        services().players().updatePlayerTime(player.getUniqueId(), (int) updated);
        notifyTarget(sender, player.getUniqueId(), configs().lang().get("utilities.time.player." + (isAdd ? "add" : "set") + ".by"),
                timePlaceholders(isAdd, displayTicks));
    }

    private Map<String, String> timePlaceholders(boolean isAdd, long ticks) {
        String time = isAdd
                ? TimeFormatter.ticks(ticks, configs().main().formatting(), configs().lang())
                : TimeFormatter.clock(ticks, configs().main().formatting());
        return Map.of("time", time, "ticks", TimeFormatter.tickCount(ticks, configs().lang()));
    }

    private long newTime(boolean isAdd, long base, long ticks) {
        return Math.floorMod((isAdd ? base : 0L) + ticks, DAY_TICKS);
    }

    private long offlineTime(PlayerProfile profile) {
        Integer stored = profile.playerTime();
        return stored != null ? stored : 0L;
    }

    private long offlineTime(UUID uuid) {
        return services().players().find(uuid).map(PlayerProfile::playerTime).map(Integer::longValue).orElse(0L);
    }

    private String timeFilter() {
        return DurationUnits.filterFor(config().tabComplete(), 2);
    }
}