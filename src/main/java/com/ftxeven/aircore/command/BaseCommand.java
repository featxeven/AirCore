package com.ftxeven.aircore.command;

import com.ftxeven.aircore.command.Scopes.LiveScope;
import com.ftxeven.aircore.command.Scopes.Scope;
import com.ftxeven.aircore.command.Scopes.ScopeAccess;
import com.ftxeven.aircore.command.player.PlayerTargetResolver;
import com.ftxeven.aircore.command.player.Selectors;
import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.config.MainConfig;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.core.command.DynamicCommand;
import com.ftxeven.aircore.core.command.tabcomplete.TabCompleteEngine;
import com.ftxeven.aircore.module.ModuleManager;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.MiniText;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class BaseCommand implements CommandHandler {

    public record Context(
            ConfigManager configs,
            Messenger messenger,
            ServiceManager services,
            ModuleManager modules,
            PlayerTargetResolver resolver,
            TabCompleteEngine tabCompleteEngine,
            Selectors selectors,
            Feedback feedback,
            Scopes scopes
    ) {}

    protected final Context ctx;
    private final String key;

    protected BaseCommand(Context ctx, String key) {
        this.ctx = ctx;
        this.key = key;
    }

    // Config-driven

    @Override
    public String name() { return config().name(); }

    @Override
    public List<String> aliases() { return config().aliases(); }

    @Override
    public boolean enabled() { return config().enabled(); }

    @Override
    public String usage() { return config().usage(); }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return tabCompleteEngine().complete(sender, config(), permission(), args);
    }

    protected final DynamicCommand config() {
        return configs().commands().findCommandOrDisabled(key);
    }

    protected final Optional<String> resolveAction(String typed) {
        for (Map.Entry<String, String> entry : config().actions().entrySet()) {
            if (entry.getValue().equalsIgnoreCase(typed)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    // Shared dependencies

    protected final ConfigManager configs() { return ctx.configs(); }
    protected final Messenger messenger() { return ctx.messenger(); }
    protected final ServiceManager services() { return ctx.services(); }
    protected final ModuleManager modules() { return ctx.modules(); }
    protected final PlayerTargetResolver resolver() { return ctx.resolver(); }
    protected final TabCompleteEngine tabCompleteEngine() { return ctx.tabCompleteEngine(); }
    protected final Selectors selectors() { return ctx.selectors(); }
    protected final Feedback feedback() { return ctx.feedback(); }
    protected final Scopes scopes() { return ctx.scopes(); }

    // Guards

    protected final boolean checkPermission(CommandSender sender, String permission) {
        return feedback().requirePermission(sender, permission);
    }

    protected final Optional<Player> requirePlayer(CommandSender sender) {
        return feedback().requirePlayer(sender);
    }

    protected final <T> Optional<T> requireFound(Optional<T> resolved, CommandSender sender, String typed) {
        return feedback().requireFound(resolved, sender, typed);
    }

    protected final List<Player> onlineExcept(@Nullable UUID excludedUuid) {
        return scopes().onlineExcept(excludedUuid);
    }

    protected final Optional<List<Player>> requireOtherPlayersOnline(CommandSender sender, @Nullable UUID excludedUuid) {
        return scopes().requireOtherPlayersOnline(sender, excludedUuid);
    }

    protected final boolean isSelf(CommandSender sender, UUID targetUuid) {
        return sender instanceof Player player && player.getUniqueId().equals(targetUuid);
    }

    // Target feedback

    protected final void announce(CommandSender sender, UUID targetUuid, boolean self,
                                  String selfKey, String forKey, String byKey, Map<String, String> placeholders) {
        feedback().announce(sender, targetUuid, self, selfKey, forKey, byKey, placeholders);
    }

    protected final void notifyTarget(CommandSender sender, UUID targetUuid, List<String> template, Map<String, String> placeholders) {
        feedback().notifyTarget(sender, targetUuid, template, placeholders);
    }

    protected final Map<String, String> targetPlaceholders(UUID targetUuid) {
        return feedback().targetPlaceholders(targetUuid);
    }

    protected final Map<String, String> targetPlaceholders(Player target) {
        return feedback().targetPlaceholders(target);
    }

    protected final void sendUsageError(CommandSender sender, String label, String subLabel) {
        String usage = CommandDispatch.format(usage(sender), label, subLabel);
        messenger().send(sender, configs().lang().get("errors.access.incorrect-usage"), Map.of("usage", usage));
    }

    // Command cooldowns

    protected final void completeCooldown(CommandSender sender, String[] args) {
        services().commandCooldowns().complete(name(), sender, args);
    }

    // World restrictions

    protected final boolean isWorldRestrictedFor(CommandSender sender, Player target, MainConfig.RestrictedFeature feature) {
        if (!services().players().isFeatureRestricted(target.getWorld().getName(), feature)) {
            return false;
        }
        String bypass = services().players().bypassPermission(feature);
        return !sender.hasPermission(bypass) && !target.hasPermission(bypass);
    }

    protected final boolean blockedByWorldRestriction(CommandSender sender, @Nullable Player target, boolean self, MainConfig.RestrictedFeature feature) {
        if (target == null || !isWorldRestrictedFor(sender, target, feature)) {
            return false;
        }
        String world = target.getWorld().getName();
        if (self) {
            messenger().send(sender, configs().lang().get("errors.general.world-restricted"), Map.of("world", world));
        } else {
            Map<String, String> placeholders = new LinkedHashMap<>();
            placeholders.put("world", world);
            services().players().formatDisplayName(placeholders, "target", target.getUniqueId());
            messenger().send(sender, configs().lang().get("errors.general.world-restricted-for"), placeholders);
        }
        return true;
    }

    // Safe display of user-typed text

    protected final String escapeUserInput(String raw) {
        return raw == null ? "" : MiniText.mini().escapeTags(raw);
    }

    // Targets and scopes

    protected final void resolveTarget(CommandSender sender, String typed, Consumer<PlayerTargetResolver.Target> onFound) {
        scopes().resolveTarget(sender, typed, onFound);
    }

    protected final void resolveScope(CommandSender sender, @Nullable String typed, ScopeAccess access, Consumer<Scope> onResolved) {
        scopes().resolve(sender, typed, access, onResolved);
    }

    protected final void resolveLiveScope(CommandSender sender, @Nullable String typed, ScopeAccess access, Consumer<LiveScope> onResolved) {
        scopes().resolveLive(sender, typed, access, onResolved);
    }

    protected final void runOnOwner(Scope.Single target, Consumer<Optional<Player>> action) {
        scopes().runOnOwner(target, action);
    }

    protected final void forEachServerMember(BiConsumer<UUID, Optional<Player>> action, Runnable onComplete) {
        scopes().forEachServerMember(action, onComplete);
    }

    protected final void runScope(CommandSender sender, Scope scope, Runnable onDone,
                                  Consumer<Scope.Single> onSingle, Consumer<Player> onLive, Consumer<UUID> onOffline,
                                  Scopes.BulkSummary summary) {
        scopes().run(sender, scope, onDone, onSingle, onLive, onOffline, summary);
    }
}