package com.ftxeven.aircore.module.homes;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.database.cache.HomeCache;
import com.ftxeven.aircore.database.query.HomeFacetCounts;
import com.ftxeven.aircore.database.query.HomeQuery;
import com.ftxeven.aircore.database.query.PageResult;
import com.ftxeven.aircore.model.Home;
import com.ftxeven.aircore.model.Position;
import com.ftxeven.aircore.module.NameValidator;
import com.ftxeven.aircore.module.Positions;
import com.ftxeven.aircore.module.chat.ChatModule;
import com.ftxeven.aircore.permission.PermissionTiers;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class HomesModule {

    private final ConfigManager configs;
    private final HomeCache homes;
    private final HomeNameValidator naming;

    public HomesModule(ConfigManager configs, HomeCache homes, Supplier<ChatModule> chatModule) {
        this.configs = configs;
        this.homes = homes;
        this.naming = new HomeNameValidator(configs, chatModule);
    }

    public HomeNameValidator naming() {
        return naming;
    }

    // Lookup

    public Optional<Home> find(UUID owner, String name) {
        return homes.find(owner, name);
    }

    public Optional<Home> resolve(UUID owner, String identifier) {
        Optional<Home> byName = find(owner, identifier);
        return byName.isPresent() ? byName : findByIndex(owner, identifier);
    }

    public Optional<Home> findByIndex(UUID owner, String indexToken) {
        try {
            return findByIndex(owner, Integer.parseInt(indexToken));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public Optional<Home> findByIndex(UUID owner, int index) {
        if (index < 1) {
            return Optional.empty();
        }
        List<Home> ordered = findAll(owner);
        return index <= ordered.size() ? Optional.of(ordered.get(index - 1)) : Optional.empty();
    }

    public List<Home> findAll(UUID owner) {
        return homes.findAll(owner);
    }

    public int count(UUID owner) {
        return homes.count(owner);
    }

    // Paged/filtered browsing (GUI)

    public PageResult<Home> query(HomeQuery query) {
        return homes.query(query);
    }

    public HomeFacetCounts facets(HomeQuery query) {
        return homes.facets(query);
    }

    // Residency

    public CompletableFuture<Void> warm(UUID owner) {
        return homes.warm(owner);
    }

    public void handleJoin(UUID uuid) {
        homes.warm(uuid);
    }

    public void handleQuit(UUID uuid) {
        homes.release(uuid);
    }

    // Resolves a home by name for a given owner
    public Function<String, Optional<Home>> homeResolver(UUID owner) {
        return name -> find(owner, name);
    }

    // Limits

    public int limitFor(Permissible sender) {
        int configured = configs.homes().general().maxHomes();
        return (int) PermissionTiers.resolveTier(sender, Permissions.Bypass.LIMIT, configured);
    }

    public boolean blocksWorld(Permissible sender, String world) {
        if (sender.hasPermission(Permissions.Bypass.HOME_DISABLED_WORLDS)) {
            return false;
        }
        return configs.homes().general().disabledWorlds().contains(world);
    }

    // Setting

    public sealed interface SetVerdict {
        record Created(String name) implements SetVerdict {}
        record Overwritten(String name) implements SetVerdict {}
        record AlreadyExists(String name) implements SetVerdict {}
        record LimitReached(int count, int limit) implements SetVerdict {}
        record BlockedWorld() implements SetVerdict {}
        record InvalidName(NameValidator.Reason reason) implements SetVerdict {}
    }

    public SetVerdict set(Player player, @Nullable String requestedName) {
        if (blocksWorld(player, player.getWorld().getName())) {
            return new SetVerdict.BlockedWorld();
        }

        UUID uuid = player.getUniqueId();
        String resolvedName;

        if (configs.homes().naming().enabled()) {
            NameValidator.Verdict verdict = naming.validate(player, requestedName == null ? "" : requestedName);
            if (verdict instanceof NameValidator.Verdict.Reject(NameValidator.Reason reason)) {
                return new SetVerdict.InvalidName(reason);
            }
            resolvedName = ((NameValidator.Verdict.Allow) verdict).name();
        } else {
            resolvedName = nextNumberedName(uuid);
        }

        Optional<Home> existing = homes.find(uuid, resolvedName);
        Position position = Positions.of(player.getLocation());

        if (existing.isPresent()) {
            Home current = existing.get();
            if (!configs.homes().behaviour().overwriteOnSet()) {
                return new SetVerdict.AlreadyExists(current.name());
            }
            homes.save(current.withPosition(position));
            return new SetVerdict.Overwritten(current.name());
        }

        int limit = limitFor(player);
        int current = homes.count(uuid);
        if (limit >= 0 && current >= limit) {
            return new SetVerdict.LimitReached(current, limit);
        }

        homes.save(new Home(uuid, resolvedName, position, null, false, Instant.now()));
        return new SetVerdict.Created(resolvedName);
    }

    // numbers homes 1, 2, 3... when naming.enabled is false
    private String nextNumberedName(UUID owner) {
        Set<String> taken = homes.findAll(owner).stream().map(Home::name).collect(Collectors.toSet());
        int candidate = 1;
        while (taken.contains(String.valueOf(candidate))) {
            candidate++;
        }
        return String.valueOf(candidate);
    }

    // Customizing

    public boolean setFavorite(UUID owner, String name, boolean favorite) {
        return homes.setFavorite(owner, name, favorite);
    }

    public boolean setIcon(UUID owner, String name, @Nullable String icon) {
        return homes.setIcon(owner, name, icon);
    }

    // Deleting

    public boolean delete(UUID owner, String name) {
        return homes.delete(owner, name);
    }

    public int deleteAll(UUID owner) {
        return homes.deleteAll(owner);
    }

    public Optional<Position> bedFallback(Player player) {
        if (!configs.homes().behaviour().teleportToBed()) {
            return Optional.empty();
        }
        Location bed = player.getBedSpawnLocation();
        return bed != null ? Optional.of(Positions.of(bed)) : Optional.empty();
    }
}