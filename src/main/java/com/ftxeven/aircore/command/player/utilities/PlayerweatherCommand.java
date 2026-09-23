package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.command.AbstractCommand;
import com.ftxeven.aircore.command.Scopes;
import com.ftxeven.aircore.command.Scopes.Scope;
import com.ftxeven.aircore.command.Scopes.ScopeAccess;
import com.ftxeven.aircore.config.MainConfig;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.core.command.CommandDispatch.Availability;
import com.ftxeven.aircore.model.PlayerProfile;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PlayerweatherCommand extends AbstractCommand {

    private static final String KEY = "playerweather";
    private static final String RESET = "reset";
    private static final ScopeAccess ACCESS = ScopeAccess.of(KEY);

    public PlayerweatherCommand(Context ctx) {
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
        return CommandDispatch.maxArgs(1, Availability.ofConfig(ACCESS.canTargetOthers(sender)));
    }

    @Override
    public String usage(CommandSender sender) {
        return CommandDispatch.usage(config(), ACCESS.canTargetOthers(sender));
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Optional<String> resolved = resolveAction(args[0]);
        if (resolved.isEmpty()) {
            sendUsageError(sender, label, subLabel);
            return;
        }
        String action = resolved.get();
        PlayerProfile.PersonalWeather weather = toPersonalWeather(action);
        String typed = args.length > 1 ? args[1] : null;

        resolveScope(sender, typed, ACCESS, scope -> runScope(sender, scope, () -> completeCooldown(sender, args),
                single -> applyOne(sender, single, action, weather, args),
                player -> applyLive(sender, player, weather, action),
                uuid -> services().players().updatePlayerWeather(uuid, weather),
                bulkSummary(action)));
    }

    private void applyOne(CommandSender sender, Scope.Single target, String action, PlayerProfile.PersonalWeather weather, String[] args) {
        runOnOwner(target, online -> {
            if (weather != null && online.isPresent()
                    && blockedByWorldRestriction(sender, online.get(), target.self(), MainConfig.RestrictedFeature.CUSTOM_WEATHER)) {
                return;
            }
            services().players().updatePlayerWeather(target.uuid(), weather);
            online.ifPresent(p -> applyToPlayer(p, weather));
            completeCooldown(sender, args);
            announceResult(sender, target.uuid(), target.self(), action, weather);
        });
    }

    private void applyLive(CommandSender sender, Player player, PlayerProfile.PersonalWeather weather, String action) {
        if (weather != null && isWorldRestrictedFor(sender, player, MainConfig.RestrictedFeature.CUSTOM_WEATHER)) {
            return;
        }
        services().players().updatePlayerWeather(player.getUniqueId(), weather);
        applyToPlayer(player, weather);
        notifyResult(sender, player.getUniqueId(), action);
    }

    private void applyToPlayer(Player player, PlayerProfile.PersonalWeather weather) {
        if (weather != null) {
            player.setPlayerWeather(weather.toBukkit());
        } else {
            player.resetPlayerWeather();
        }
    }

    private void notifyResult(CommandSender sender, UUID uuid, String action) {
        boolean reset = action.equals(RESET);
        String key = "utilities.weather.player." + (reset ? "reset" : "set") + ".by";
        notifyTarget(sender, uuid, configs().lang().get(key), reset ? Map.of() : Map.of("weather", weatherLabel(action)));
    }

    private Scopes.BulkSummary bulkSummary(String action) {
        boolean reset = action.equals(RESET);
        String prefix = "utilities.weather.player." + (reset ? "reset" : "set");
        return new Scopes.BulkSummary(prefix + ".online", prefix + ".all",
                reset ? Map.of() : Map.of("weather", weatherLabel(action)));
    }

    private void announceResult(CommandSender sender, UUID targetUuid, boolean self, String action, PlayerProfile.PersonalWeather weather) {
        if (weather == null) {
            announce(sender, targetUuid, self,
                    "utilities.weather.player.reset.self", "utilities.weather.player.reset.other", "utilities.weather.player.reset.by", Map.of());
            return;
        }
        announce(sender, targetUuid, self,
                "utilities.weather.player.set.self", "utilities.weather.player.set.other", "utilities.weather.player.set.by",
                Map.of("weather", weatherLabel(action)));
    }

    private PlayerProfile.PersonalWeather toPersonalWeather(String action) {
        if (action.equals(RESET)) return null;
        return action.equals("clear") ? PlayerProfile.PersonalWeather.CLEAR : PlayerProfile.PersonalWeather.THUNDER;
    }

    private String weatherLabel(String action) {
        return configs().lang().get("utilities.weather.values." + action).getFirst();
    }
}