package com.ftxeven.aircore.module.extras.block;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.database.cache.CacheManager;
import com.ftxeven.aircore.model.PlayerProfile;
import com.ftxeven.aircore.module.extras.ExtrasConfig;
import com.ftxeven.aircore.permission.OfflinePermissions;
import com.ftxeven.aircore.permission.PermissionTiers;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.service.PlayerService;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class BlockHandler {

    public enum Result { BLOCKED, ALREADY_BLOCKED, LIMIT_REACHED, EXEMPT, UNBLOCKED, NOT_BLOCKED, SELF }

    private final CacheManager cache;
    private final ConfigManager configs;
    private final PlayerService players;

    public BlockHandler(CacheManager cache, ConfigManager configs, PlayerService players) {
        this.cache = cache;
        this.configs = configs;
        this.players = players;
    }

    // Residency

    public CompletableFuture<Void> warm(UUID uuid) {
        return cache.blocks().warm(uuid);
    }

    public void release(UUID uuid) {
        cache.blocks().release(uuid);
    }

    public boolean isBlocked(UUID owner, UUID target) {
        return cache.blocks().isBlocked(owner, target);
    }

    public Set<UUID> blockedBy(UUID owner) {
        return cache.blocks().blockedBy(owner);
    }

    public int countBlocked(UUID owner) {
        return cache.blocks().countBlocked(owner);
    }

    public int configuredMaxBlocked() {
        return configs.extras().block().maxBlocked();
    }

    public int maxBlocked(Permissible permissible) {
        double resolved = PermissionTiers.resolveTier(permissible, Permissions.Bypass.BLOCK_LIMIT, configuredMaxBlocked());
        return (int) resolved;
    }

    public boolean atLimit(UUID owner, Permissible permissible) {
        int limit = maxBlocked(permissible);
        return limit >= 0 && countBlocked(owner) >= limit;
    }

    public void block(UUID owner, UUID target, Player initiator, Consumer<Result> callback) {
        if (owner.equals(target)) {
            callback.accept(Result.SELF);
            return;
        }
        int limit = maxBlocked(initiator); // the initiator's permissions, on the initiator's thread

        OfflinePermissions.has(target, Permissions.Bypass.BLOCK, exempt ->
                Scheduler.runEntity(initiator, () ->
                        callback.accept(exempt ? Result.EXEMPT : finishBlock(owner, target, limit))));
    }

    private Result finishBlock(UUID owner, UUID target, int limit) {
        if (isBlocked(owner, target)) {
            return Result.ALREADY_BLOCKED;
        }
        if (limit >= 0 && countBlocked(owner) >= limit) {
            return Result.LIMIT_REACHED;
        }
        return cache.blocks().block(owner, target) ? Result.BLOCKED : Result.ALREADY_BLOCKED;
    }

    public Result unblock(UUID owner, UUID target) {
        return cache.blocks().unblock(owner, target) ? Result.UNBLOCKED : Result.NOT_BLOCKED;
    }

    public int unblockAll(UUID owner) {
        return cache.blocks().unblockAll(owner);
    }

    public String langKeyFor(Result result) {
        return switch (result) {
            case BLOCKED -> "extras.block.blocked";
            case ALREADY_BLOCKED -> "extras.block.already-blocked";
            case LIMIT_REACHED -> "extras.block.errors.limit-reached";
            case EXEMPT -> "extras.block.errors.cannot";
            case UNBLOCKED -> "extras.block.unblocked";
            case NOT_BLOCKED -> "extras.block.not-blocked";
            case SELF -> "extras.block.errors.self";
        };
    }

    /** For callers already on the initiator's own thread */
    public boolean blocksInteraction(ExtrasConfig.BlockAction action, UUID owner, Player initiator) {
        return blocksInteraction(action, owner, initiator.getUniqueId(), initiator.hasPermission(Permissions.Bypass.BLOCK));
    }

    public boolean blocksInteraction(ExtrasConfig.BlockAction action, UUID owner, UUID initiator, boolean bypasses) {
        if (owner.equals(initiator)) {
            return false;
        }
        if (!configs.extras().block().actions().applies(action)) {
            return false;
        }
        if (bypasses) {
            return false;
        }
        return isBlocked(owner, initiator);
    }

    public List<String> blockedPlayerNames(UUID owner, String limitParam) {
        Set<UUID> blocked = blockedBy(owner);
        Map<UUID, PlayerProfile> profiles = players.findAll(blocked);

        List<String> names = blocked.stream()
                .map(profiles::get)
                .filter(Objects::nonNull)
                .map(PlayerProfile::name)
                .toList();

        return limited(names, limitParam);
    }

    private static List<String> limited(List<String> values, String param) {
        try {
            int limit = Integer.parseInt(param);
            return values.size() > limit ? values.subList(0, limit) : values;
        } catch (NumberFormatException e) {
            return values;
        }
    }
}