package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.command.Scopes.LiveScope;
import com.ftxeven.aircore.command.Scopes.ScopeAccess;
import com.ftxeven.aircore.config.MainConfig;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.core.command.CommandDispatch.Availability;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Queue;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public final class NearCommand extends BaseCommand {

    private static final String KEY = "near";
    private static final String LANG = "utilities.near.";
    private static final ScopeAccess ACCESS = ScopeAccess.othersOnly(KEY);

    // digits with an optional sign and fraction; anything else typed is read as a player
    private static final Pattern NUMBER = Pattern.compile("[+-]?\\d+(\\.\\d+)?");
    // closer than this (squared) on the horizontal plane reads as "here" instead of a heading
    private static final double HERE_DISTANCE_SQUARED = 1.0D;
    // a candidate whose entity retires mid-search never reports back, so the search closes on its own
    private static final long SEARCH_TIMEOUT_TICKS = 40L;

    private record Hit(Player player, double distance, double bearing, boolean here, boolean afk) {}

    public NearCommand(Context ctx) {
        super(ctx, KEY);
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public int minArgs(CommandSender sender) {
        return sender instanceof Player ? 0 : 1;
    }

    @Override
    public int maxArgs(CommandSender sender) {
        return CommandDispatch.maxArgs(0, Availability.OPTIONAL, Availability.ofPermission(sender, ACCESS.others()));
    }

    @Override
    public String usage(CommandSender sender) {
        return CommandDispatch.usage(config(), ACCESS.canTargetOthers(sender));
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        MainConfig.Near near = configs().main().utilityDefaults().near();

        String radiusArg = null;
        String playerArg = null;
        for (String arg : args) {
            if (radiusArg == null && NUMBER.matcher(arg).matches()) {
                radiusArg = arg;
            } else if (playerArg == null) {
                playerArg = arg;
            } else {
                sendUsageError(sender, label, subLabel);
                return;
            }
        }

        OptionalDouble radius = resolveRadius(sender, near, radiusArg);
        if (radius.isEmpty()) {
            return;
        }
        double resolvedRadius = radius.getAsDouble();

        resolveLiveScope(sender, playerArg, ACCESS, scope -> {
            if (scope instanceof LiveScope.Single single) {
                search(sender, single, resolvedRadius, near, args);
            }
        });
    }

    // Radius

    private OptionalDouble resolveRadius(CommandSender sender, MainConfig.Near near, String raw) {
        if (raw == null) {
            return OptionalDouble.of(near.defaultRadius());
        }

        double radius = Double.parseDouble(raw);
        if (radius < near.minRadius()) {
            messenger().send(sender, configs().lang().get(LANG + "errors.radius-too-small"),
                    Map.of("min", String.valueOf(near.minRadius())));
            return OptionalDouble.empty();
        }
        if (!near.radiusUnlimited() && radius > near.maxRadius()
                && !sender.hasPermission(Permissions.Bypass.NEAR_RADIUS)) {
            messenger().send(sender, configs().lang().get(LANG + "errors.radius-too-large"),
                    Map.of("max", String.valueOf(near.maxRadius())));
            return OptionalDouble.empty();
        }
        if (radius > near.hardMaxRadius()) {
            messenger().send(sender, configs().lang().get(LANG + "errors.radius-hard-limit"),
                    Map.of("max", String.valueOf(near.hardMaxRadius())));
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(radius);
    }

    // Search

    private void search(CommandSender sender, LiveScope.Single scope, double radius, MainConfig.Near near, String[] args) {
        Player center = scope.player();
        boolean self = scope.self();

        // what the viewer can see is the viewer's own state, so it's read here on the viewer's thread
        List<Player> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(center.getUniqueId())) {
                continue;
            }
            if (near.hide().vanished() && sender instanceof Player viewer && !viewer.canSee(online)) {
                continue;
            }
            candidates.add(online);
        }

        // the origin is read on the center's own thread, then every candidate reports from theirs
        Scheduler.runEntity(center, () -> {
            if (blockedByWorldRestriction(sender, center, self, MainConfig.RestrictedFeature.NEAR)) {
                return;
            }
            completeCooldown(sender, args);
            collect(sender, center, self, center.getLocation(), radius, near, candidates);
        });
    }

    private void collect(CommandSender sender, Player center, boolean self, Location origin, double radius,
                         MainConfig.Near near, List<Player> candidates) {
        if (candidates.isEmpty()) {
            finish(sender, center, self, radius, near, List.of());
            return;
        }

        Queue<Hit> hits = new ConcurrentLinkedQueue<>();
        AtomicInteger pending = new AtomicInteger(candidates.size());
        AtomicBoolean finished = new AtomicBoolean();
        Runnable complete = () -> {
            if (finished.compareAndSet(false, true)) {
                finish(sender, center, self, radius, near, List.copyOf(hits));
            }
        };

        Scheduler.runGlobalLater(complete, SEARCH_TIMEOUT_TICKS);

        for (Player candidate : candidates) {
            Scheduler.runEntity(candidate, () -> {
                try {
                    measure(candidate, origin, radius, near).ifPresent(hits::add);
                } finally {
                    if (pending.decrementAndGet() == 0) {
                        complete.run();
                    }
                }
            });
        }
    }

    // Runs on the candidate's own thread
    private Optional<Hit> measure(Player candidate, Location origin, double radius, MainConfig.Near near) {
        Location at = candidate.getLocation();
        if (!Objects.equals(at.getWorld(), origin.getWorld())) {
            return Optional.empty();
        }

        double dx = at.getX() - origin.getX();
        double dz = at.getZ() - origin.getZ();
        double dy = near.ignoreHeight() ? 0.0D : at.getY() - origin.getY();
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared > radius * radius) {
            return Optional.empty();
        }

        if (near.hide().spectators() && candidate.getGameMode() == GameMode.SPECTATOR) {
            return Optional.empty();
        }
        if (candidate.hasPermission(Permissions.Bypass.NEAR_HIDDEN)) {
            return Optional.empty();
        }
        boolean afk = modules().afk().isAfk(candidate.getUniqueId());
        if (afk && near.hide().afk()) {
            return Optional.empty();
        }

        boolean here = dx * dx + dz * dz < HERE_DISTANCE_SQUARED;
        return Optional.of(new Hit(candidate, Math.sqrt(distanceSquared), Headings.bearingTo(dx, dz), here, afk));
    }

    // Output

    private void finish(CommandSender sender, Player center, boolean self, double radius,
                        MainConfig.Near near, List<Hit> found) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        if (!self) {
            placeholders.putAll(targetPlaceholders(center));
        }
        placeholders.put("radius", formatRadius(radius));
        placeholders.put("count", String.valueOf(found.size()));

        if (found.isEmpty()) {
            messenger().send(sender, configs().lang().get(LANG + "list." + (self ? "empty" : "empty-other")), placeholders);
            return;
        }

        List<Hit> sorted = new ArrayList<>(found);
        sorted.sort(comparator(near.sort()));

        int shown = near.resultsUnlimited() ? sorted.size() : Math.min(sorted.size(), near.maxResults());
        StringJoiner entries = new StringJoiner(text(LANG + "list.separator"));
        for (Hit hit : sorted.subList(0, shown)) {
            entries.add(renderEntry(hit, near));
        }
        if (shown < sorted.size()) {
            entries.add(fill(text(LANG + "list.more"), Map.of("remaining", String.valueOf(sorted.size() - shown))));
        }
        placeholders.put("players", entries.toString());

        messenger().send(sender, configs().lang().get(LANG + "list." + (self ? "header" : "header-other")), placeholders);
    }

    private String renderEntry(Hit hit, MainConfig.Near near) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        services().players().formatDisplayName(placeholders, "player", hit.player());
        placeholders.put("distance", formatDistance(hit.distance(), near.distanceDecimals()));
        placeholders.put("direction", hit.here()
                ? text(LANG + "values.here")
                : text("utilities.compass.values." + Headings.key(hit.bearing())));

        return fill(text(LANG + "list." + (hit.afk() ? "entry-afk" : "entry")), placeholders);
    }

    private static Comparator<Hit> comparator(MainConfig.NearSort sort) {
        Comparator<Hit> byName = Comparator.comparing(hit -> hit.player().getName(), String.CASE_INSENSITIVE_ORDER);
        return switch (sort) {
            case DISTANCE -> Comparator.comparingDouble(Hit::distance).thenComparing(byName);
            case NAME -> byName;
        };
    }

    // Helpers

    private String text(String key) {
        return configs().lang().get(key).getFirst();
    }

    private static String fill(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> value : values.entrySet()) {
            result = result.replace("%" + value.getKey() + "%", value.getValue());
        }
        return result;
    }

    private static String formatDistance(double distance, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", distance);
    }

    private static String formatRadius(double radius) {
        return BigDecimal.valueOf(radius).stripTrailingZeros().toPlainString();
    }
}