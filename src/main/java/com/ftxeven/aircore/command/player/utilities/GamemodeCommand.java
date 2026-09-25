package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.command.Scopes;
import com.ftxeven.aircore.command.Scopes.Scope;
import com.ftxeven.aircore.command.Scopes.ScopeAccess;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.core.command.CommandDispatch.Availability;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class GamemodeCommand extends BaseCommand {

    private static final String KEY = "gamemode";
    private static final ScopeAccess ACCESS = ScopeAccess.of(KEY);

    public GamemodeCommand(Context ctx) {
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
        Optional<String> action = resolveAction(args[0]);
        if (action.isEmpty()) {
            sendUsageError(sender, label, subLabel);
            return;
        }
        GameMode gameMode = GameMode.valueOf(action.get().toUpperCase(Locale.ROOT));
        String typed = args.length > 1 ? args[1] : null;

        Scopes.BulkSummary summary = new Scopes.BulkSummary("utilities.gamemode.online", "utilities.gamemode.all",
                gamemodePlaceholders(gameMode));

        resolveScope(sender, typed, ACCESS, scope -> runScope(sender, scope, () -> completeCooldown(sender, args),
                single -> applyOne(sender, single, gameMode, args),
                player -> applyLive(sender, player, gameMode),
                uuid -> applyOffline(uuid, gameMode),
                summary));
    }

    private void applyOne(CommandSender sender, Scope.Single target, GameMode gameMode, String[] args) {
        runOnOwner(target, online -> {
            boolean already = online.map(p -> p.getGameMode() == gameMode).orElseGet(() -> target.profile().gameMode() == gameMode);
            if (already) {
                sendAlready(sender, target.uuid(), target.self(), gameMode);
                return;
            }
            online.ifPresent(p -> p.setGameMode(gameMode));
            services().players().updateGameMode(target.uuid(), gameMode);
            completeCooldown(sender, args);
            announce(sender, target.uuid(), target.self(),
                    "utilities.gamemode.self", "utilities.gamemode.other", "utilities.gamemode.by", gamemodePlaceholders(gameMode));
        });
    }

    private void applyLive(CommandSender sender, Player player, GameMode gameMode) {
        if (player.getGameMode() == gameMode) {
            return;
        }
        player.setGameMode(gameMode);
        services().players().updateGameMode(player.getUniqueId(), gameMode);
        notifyTarget(sender, player.getUniqueId(), configs().lang().get("utilities.gamemode.by"), gamemodePlaceholders(gameMode));
    }

    private void applyOffline(UUID uuid, GameMode gameMode) {
        boolean already = services().players().peek(uuid).map(profile -> profile.gameMode() == gameMode).orElse(false);
        if (!already) {
            services().players().updateGameMode(uuid, gameMode);
        }
    }

    private void sendAlready(CommandSender sender, UUID targetUuid, boolean self, GameMode gameMode) {
        Map<String, String> placeholders = new LinkedHashMap<>(gamemodePlaceholders(gameMode));
        if (self) {
            messenger().send(sender, configs().lang().get("utilities.gamemode.errors.same"), placeholders);
            return;
        }
        services().players().formatDisplayName(placeholders, "target", targetUuid);
        messenger().send(sender, configs().lang().get("utilities.gamemode.errors.same-for"), placeholders);
    }

    private Map<String, String> gamemodePlaceholders(GameMode gameMode) {
        return Map.of("gamemode", gamemodeLabel(gameMode));
    }

    private String gamemodeLabel(GameMode gameMode) {
        return configs().lang().get("utilities.gamemode.values." + gameMode.name().toLowerCase(Locale.ROOT)).getFirst();
    }
}