package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.command.AbstractCommand;
import com.ftxeven.aircore.command.Scopes;
import com.ftxeven.aircore.command.Scopes.Scope;
import com.ftxeven.aircore.command.Scopes.ScopeAccess;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.core.command.CommandDispatch.Availability;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SpeedCommand extends AbstractCommand {

    private static final String KEY = "speed";
    private static final ScopeAccess ACCESS = ScopeAccess.of(KEY);

    private static final int MIN_LEVEL = 1;
    private static final int MAX_LEVEL = 10;
    private static final float DEFAULT_WALK_SPEED = 0.2f;
    private static final float DEFAULT_FLY_SPEED = 0.1f;
    private static final float MAX_SPEED = 1.0f;

    private enum SpeedType { WALK, FLY, BOTH }

    public SpeedCommand(Context ctx) {
        super(ctx, KEY);
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public int minArgs(CommandSender sender) {
        return sender instanceof Player ? 1 : 2;
    }

    @Override
    public int maxArgs(CommandSender sender) {
        return CommandDispatch.maxArgs(2, Availability.ofConfig(ACCESS.canTargetOthers(sender)));
    }

    @Override
    public String usage(CommandSender sender) {
        return CommandDispatch.usage(config(), ACCESS.canTargetOthers(sender));
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Optional<Integer> parsedLevel = parseLevel(args[0]);
        if (parsedLevel.isEmpty()) {
            messenger().send(sender, configs().lang().get("utilities.speed.errors.range"),
                    Map.of("min", String.valueOf(MIN_LEVEL), "max", String.valueOf(MAX_LEVEL)));
            return;
        }
        int level = parsedLevel.get();

        String secondArg = args.length > 1 ? args[1] : null;
        Optional<String> maybeType = resolveAction(secondArg);
        String typed = maybeType.isPresent() ? (args.length > 2 ? args[2] : null) : secondArg;
        SpeedType requestedType = maybeType.map(t -> t.equals("fly") ? SpeedType.FLY : SpeedType.WALK).orElse(null);

        Scopes.BulkSummary summary = new Scopes.BulkSummary("utilities.speed.online", "utilities.speed.all",
                speedPlaceholders(level, requestedType != null ? requestedType : SpeedType.BOTH));

        resolveScope(sender, typed, ACCESS, scope -> runScope(sender, scope, () -> completeCooldown(sender, args),
                single -> applyOne(sender, single, level, requestedType, args),
                player -> applyLive(sender, player, level, requestedType),
                uuid -> applyOffline(uuid, level, requestedType),
                summary));
    }

    // Single target (self or explicit)

    private void applyOne(CommandSender sender, Scope.Single target, int level, SpeedType requestedType, String[] args) {
        runOnOwner(target, online -> {
            SpeedType type = requestedType != null ? requestedType
                    : online.map(p -> p.isFlying() ? SpeedType.FLY : SpeedType.WALK).orElse(SpeedType.BOTH);
            float walkValue = walkSpeedFor(level);
            float flyValue = flySpeedFor(level);

            boolean unchanged = online
                    .map(p -> matches(type, walkValue, flyValue, p.getWalkSpeed(), p.getFlySpeed()))
                    .orElseGet(() -> matches(type, walkValue, flyValue, target.profile().walkSpeed(), target.profile().flySpeed()));
            if (unchanged) {
                sendAlready(sender, target.uuid(), target.self(), type);
                return;
            }

            online.ifPresent(p -> applyLiveSpeed(p, type, walkValue, flyValue));
            persistSpeed(target.uuid(), type, walkValue, flyValue);
            completeCooldown(sender, args);
            announce(sender, target.uuid(), target.self(),
                    "utilities.speed.self", "utilities.speed.other", "utilities.speed.by", speedPlaceholders(level, type));
        });
    }

    // Bulk: one online player, already on its own thread

    private void applyLive(CommandSender sender, Player player, int level, SpeedType requestedType) {
        SpeedType type = requestedType != null ? requestedType : (player.isFlying() ? SpeedType.FLY : SpeedType.WALK);
        float walkValue = walkSpeedFor(level);
        float flyValue = flySpeedFor(level);
        if (matches(type, walkValue, flyValue, player.getWalkSpeed(), player.getFlySpeed())) {
            return;
        }
        applyLiveSpeed(player, type, walkValue, flyValue);
        persistSpeed(player.getUniqueId(), type, walkValue, flyValue);
        notifyTarget(sender, player.getUniqueId(), configs().lang().get("utilities.speed.by"), speedPlaceholders(level, type));
    }

    // Bulk: a stored player who isn't online

    private void applyOffline(UUID uuid, int level, SpeedType requestedType) {
        SpeedType type = requestedType != null ? requestedType : SpeedType.BOTH;
        float walkValue = walkSpeedFor(level);
        float flyValue = flySpeedFor(level);
        boolean unchanged = services().players().peek(uuid)
                .map(profile -> matches(type, walkValue, flyValue, profile.walkSpeed(), profile.flySpeed()))
                .orElse(false);
        if (!unchanged) {
            persistSpeed(uuid, type, walkValue, flyValue);
        }
    }

    // Shared

    private boolean matches(SpeedType type, float walkValue, float flyValue, float currentWalk, float currentFly) {
        return switch (type) {
            case WALK -> currentWalk == walkValue;
            case FLY -> currentFly == flyValue;
            case BOTH -> currentWalk == walkValue && currentFly == flyValue;
        };
    }

    private void applyLiveSpeed(Player player, SpeedType type, float walkValue, float flyValue) {
        if (type != SpeedType.FLY) player.setWalkSpeed(walkValue);
        if (type != SpeedType.WALK) player.setFlySpeed(flyValue);
    }

    private void persistSpeed(UUID uuid, SpeedType type, float walkValue, float flyValue) {
        if (type != SpeedType.FLY) services().players().updateWalkSpeed(uuid, walkValue);
        if (type != SpeedType.WALK) services().players().updateFlySpeed(uuid, flyValue);
    }

    private void sendAlready(CommandSender sender, UUID targetUuid, boolean self, SpeedType type) {
        if (self) {
            messenger().send(sender, configs().lang().get("utilities.speed.errors.same"), Map.of("type", typeLabel(type)));
            return;
        }
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("type", typeLabel(type));
        services().players().formatDisplayName(placeholders, "target", targetUuid);
        messenger().send(sender, configs().lang().get("utilities.speed.errors.same-for"), placeholders);
    }

    private Map<String, String> speedPlaceholders(int level, SpeedType type) {
        return Map.of("speed", String.valueOf(level), "type", typeLabel(type));
    }

    private String typeLabel(SpeedType type) {
        String key = switch (type) {
            case WALK -> "walk";
            case FLY -> "fly";
            case BOTH -> "both";
        };
        return configs().lang().get("utilities.speed.values." + key).getFirst();
    }

    private static float walkSpeedFor(int level) { return scaled(level, DEFAULT_WALK_SPEED); }
    private static float flySpeedFor(int level) { return scaled(level, DEFAULT_FLY_SPEED); }

    private static float scaled(int level, float base) {
        return base + (level - MIN_LEVEL) * (MAX_SPEED - base) / (MAX_LEVEL - MIN_LEVEL);
    }

    private Optional<Integer> parseLevel(String raw) {
        try {
            int level = Integer.parseInt(raw);
            return (level >= MIN_LEVEL && level <= MAX_LEVEL) ? Optional.of(level) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}