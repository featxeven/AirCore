package com.ftxeven.aircore.service;

import com.ftxeven.aircore.config.CommandsConfig;
import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.database.cache.CacheManager;
import com.ftxeven.aircore.model.CooldownScope;
import com.ftxeven.aircore.permission.PermissionTiers;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;

public final class CommandCooldownService {

    private final CacheManager cache;
    private final ConfigManager configs;

    public CommandCooldownService(CacheManager cache, ConfigManager configs) {
        this.cache = cache;
        this.configs = configs;
    }

    public sealed interface Verdict {
        record Allowed() implements Verdict {}
        record Blocked(Instant expiresAt, @Nullable String customMessage) implements Verdict {}
    }

    public Verdict check(String commandName, CommandSender sender, String[] args) {
        String commandKey = commandName.toLowerCase(Locale.ROOT);
        CommandsConfig.CommandCooldown cooldownConfig = configs.commands().cooldowns().get(commandKey);
        if (cooldownConfig == null || !(sender instanceof Player player)) {
            return new Verdict.Allowed();
        }

        Optional<String> arg = keyArg(cooldownConfig, args);
        if (arg.isEmpty()) {
            return new Verdict.Allowed(); // strict cooldown, invoked with arguments
        }

        int seconds = effectiveSeconds(player, commandKey, cooldownConfig.seconds());
        if (seconds <= 0) {
            return new Verdict.Allowed();
        }

        return cache.cooldowns().peek(CooldownScope.COMMAND, player.getUniqueId(), commandKey, arg.get(), Instant.now())
                .<Verdict>map(active -> new Verdict.Blocked(active.expiresAt(), cooldownConfig.message()))
                .orElseGet(Verdict.Allowed::new);
    }

    public void complete(String commandName, CommandSender sender, String[] args) {
        String commandKey = commandName.toLowerCase(Locale.ROOT);
        CommandsConfig.CommandCooldown cooldownConfig = configs.commands().cooldowns().get(commandKey);
        if (cooldownConfig == null || !(sender instanceof Player player)) {
            return;
        }

        Optional<String> arg = keyArg(cooldownConfig, args);
        if (arg.isEmpty()) {
            return;
        }

        int seconds = effectiveSeconds(player, commandKey, cooldownConfig.seconds());
        if (seconds <= 0) {
            return;
        }

        cache.cooldowns().start(CooldownScope.COMMAND, player.getUniqueId(), commandKey, arg.get(), Instant.now().plusSeconds(seconds));
    }

    private Optional<String> keyArg(CommandsConfig.CommandCooldown cooldown, String[] args) {
        if (cooldown.strict() && args.length > 0) {
            return Optional.empty();
        }
        if (!cooldown.perArg() || args.length == 0) {
            return Optional.of("");
        }
        return Optional.of(normalize(args[0]));
    }

    private String normalize(String rawArg) {
        String lower = rawArg.trim().toLowerCase(Locale.ROOT);
        return lower.length() > 64 ? lower.substring(0, 64) : lower;
    }

    private int effectiveSeconds(Player player, String commandKey, int configuredSeconds) {
        String bypass = Permissions.Bypass.command(commandKey);
        if (player.hasPermission(bypass) || player.hasPermission(Permissions.Bypass.command("*"))) {
            return -1;
        }

        OptionalDouble tier = PermissionTiers.resolve(player, bypass, PermissionTiers.Pick.LOWEST);
        return tier.isPresent() ? Math.clamp((int) tier.getAsDouble(), 0, configuredSeconds) : configuredSeconds;
    }
}