package com.ftxeven.aircore.command;

import com.ftxeven.aircore.command.player.PlayerTargetResolver;
import com.ftxeven.aircore.command.player.Selectors;
import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.model.PlayerProfile;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class Scopes {

    /** Who a command acts on once its target argument is resolved. Single may be offline. */
    public sealed interface Scope {
        record Single(UUID uuid, PlayerProfile profile, boolean self) implements Scope {}
        record Online(List<Player> players) implements Scope {}
        record Server() implements Scope {}
    }

    /** Same idea as {@link Scope}, for commands that only ever touch online players. */
    public sealed interface LiveScope {
        record Single(Player player, boolean self) implements LiveScope {}
        record Online(List<Player> players) implements LiveScope {}
    }

    /** which permission nodes unlock which scopes for one command. */
    public record ScopeAccess(String others, @Nullable String all, @Nullable String server) {

        public static ScopeAccess of(String commandKey) {
            return new ScopeAccess(Permissions.Command.others(commandKey), Permissions.Command.all(commandKey), Permissions.Command.server(commandKey));
        }

        public static ScopeAccess othersOnly(String commandKey) {
            return new ScopeAccess(Permissions.Command.others(commandKey), null, null);
        }

        public static ScopeAccess othersAndAll(String commandKey) {
            return new ScopeAccess(Permissions.Command.others(commandKey), Permissions.Command.all(commandKey), null);
        }

        public boolean canTargetOthers(CommandSender sender) {
            return sender.hasPermission(others)
                    || (all != null && sender.hasPermission(all))
                    || (server != null && sender.hasPermission(server));
        }
    }

    /** the one-line result a bulk run sends the sender: which lang key for "online" vs "server", and its placeholders. */
    public record BulkSummary(String onlineKey, String serverKey, Map<String, String> placeholders) {}

    private final ConfigManager configs;
    private final Messenger messenger;
    private final ServiceManager services;
    private final PlayerTargetResolver resolver;
    private final Selectors selectors;
    private final Feedback feedback;

    public Scopes(ConfigManager configs, Messenger messenger, ServiceManager services,
                  PlayerTargetResolver resolver, Selectors selectors, Feedback feedback) {
        this.configs = configs;
        this.messenger = messenger;
        this.services = services;
        this.resolver = resolver;
        this.selectors = selectors;
        this.feedback = feedback;
    }

    // Target lookup

    /** Online players resolve immediately; offline ones are looked up off-thread and delivered back on the sender's thread */
    public void resolveTarget(CommandSender sender, String typed, Consumer<PlayerTargetResolver.Target> onFound) {
        Optional<PlayerTargetResolver.Target> online = resolver.online(typed);
        if (online.isPresent()) {
            onFound.accept(online.get());
            return;
        }
        Scheduler.runAsync(() -> {
            Optional<PlayerTargetResolver.Target> offline = resolver.offline(typed);
            Scheduler.runTargetAware(sender, () -> {
                if (offline.isEmpty()) {
                    messenger.send(sender, configs.lang().get("errors.access.player-never-joined"), Map.of("player", typed));
                    return;
                }
                onFound.accept(offline.get());
            });
        });
    }

    public List<Player> onlineExcept(@Nullable UUID excludedUuid) {
        List<Player> others = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (excludedUuid == null || !online.getUniqueId().equals(excludedUuid)) {
                others.add(online);
            }
        }
        return others;
    }

    public Optional<List<Player>> requireOtherPlayersOnline(CommandSender sender, @Nullable UUID excludedUuid) {
        List<Player> others = onlineExcept(excludedUuid);
        if (others.isEmpty()) {
            messenger.send(sender, configs.lang().get("errors.access.no-players-online"));
            return Optional.empty();
        }
        return Optional.of(others);
    }

    // Scope resolution

    public void resolve(CommandSender sender, @Nullable String typed, ScopeAccess access, Consumer<Scope> onResolved) {
        if (typed == null) {
            feedback.requirePlayer(sender).ifPresent(player -> resolveSelf(player, onResolved));
            return;
        }

        if (access.all() != null && selectors.isAll(typed)) {
            if (feedback.requirePermission(sender, access.all())) {
                onResolved.accept(new Scope.Online(List.copyOf(Bukkit.getOnlinePlayers())));
            }
            return;
        }
        if (access.server() != null && selectors.isServer(typed)) {
            if (feedback.requirePermission(sender, access.server())) {
                onResolved.accept(new Scope.Server());
            }
            return;
        }

        if (!feedback.requirePermission(sender, access.others())) {
            return;
        }
        if (sender instanceof Player player && resolver.matchesSelf(player, typed)) {
            resolveSelf(player, onResolved);
            return;
        }
        resolveTarget(sender, typed, target -> onResolved.accept(new Scope.Single(target.uuid(), target.profile(), false)));
    }

    private void resolveSelf(Player player, Consumer<Scope> onResolved) {
        UUID uuid = player.getUniqueId();
        Optional<PlayerProfile> cached = services.players().peek(uuid);
        if (cached.isPresent()) {
            onResolved.accept(new Scope.Single(uuid, cached.get(), true));
            return;
        }
        Scheduler.runAsync(() -> {
            Optional<PlayerProfile> loaded = services.players().find(uuid);
            Scheduler.runTargetAware(player, () -> loaded.ifPresentOrElse(
                    profile -> onResolved.accept(new Scope.Single(uuid, profile, true)),
                    () -> messenger.send(player, configs.lang().get("errors.database"))));
        });
    }

    public void resolveLive(CommandSender sender, @Nullable String typed, ScopeAccess access, Consumer<LiveScope> onResolved) {
        if (typed == null) {
            feedback.requirePlayer(sender).ifPresent(player -> onResolved.accept(new LiveScope.Single(player, true)));
            return;
        }

        if (access.all() != null && selectors.isAll(typed)) {
            if (feedback.requirePermission(sender, access.all())) {
                onResolved.accept(new LiveScope.Online(List.copyOf(Bukkit.getOnlinePlayers())));
            }
            return;
        }

        if (!feedback.requirePermission(sender, access.others())) {
            return;
        }
        if (sender instanceof Player player && resolver.matchesSelf(player, typed)) {
            onResolved.accept(new LiveScope.Single(player, true));
            return;
        }
        feedback.requireFound(resolver.onlinePlayer(typed), sender, typed)
                .ifPresent(target -> onResolved.accept(new LiveScope.Single(target, false)));
    }

    // Running work against a scope

    /** runs on the target's own thread when they're online, inline otherwise */
    public void runOnOwner(Scope.Single target, Consumer<Optional<Player>> action) {
        Player online = Bukkit.getPlayer(target.uuid());
        if (online != null) {
            Scheduler.runEntity(online, () -> action.accept(Optional.of(online)));
        } else {
            action.accept(Optional.empty());
        }
    }

    /** every stored player: online ones on their own thread, offline ones on the async thread that walks the list */
    public void forEachServerMember(BiConsumer<UUID, Optional<Player>> action, Runnable onComplete) {
        Scheduler.runAsync(() -> {
            for (UUID uuid : services.players().allUuids()) {
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) {
                    Scheduler.runEntity(online, () -> action.accept(uuid, Optional.of(online)));
                } else {
                    action.accept(uuid, Optional.empty());
                }
            }
            Scheduler.runGlobal(onComplete);
        });
    }

    /** The shape shared by /speed, /gamemode, /playertime, /playerweather */
    public void run(CommandSender sender, Scope scope, Runnable onDone,
                    Consumer<Scope.Single> onSingle, Consumer<Player> onLive, Consumer<UUID> onOffline,
                    BulkSummary summary) {
        switch (scope) {
            case Scope.Single single -> onSingle.accept(single);
            case Scope.Online online -> {
                for (Player player : online.players()) {
                    Scheduler.runEntity(player, () -> onLive.accept(player));
                }
                onDone.run();
                messenger.send(sender, configs.lang().get(summary.onlineKey()), summary.placeholders());
            }
            case Scope.Server ignored -> forEachServerMember(
                    (uuid, online) -> online.ifPresentOrElse(onLive, () -> onOffline.accept(uuid)),
                    () -> {
                        onDone.run();
                        messenger.send(sender, configs.lang().get(summary.serverKey()), summary.placeholders());
                    });
        }
    }
}