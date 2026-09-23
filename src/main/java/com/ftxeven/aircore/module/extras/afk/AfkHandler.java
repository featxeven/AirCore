package com.ftxeven.aircore.module.extras.afk;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.config.MainConfig;
import com.ftxeven.aircore.core.command.DynamicCommand;
import com.ftxeven.aircore.module.NameValidator;
import com.ftxeven.aircore.module.chat.filter.ProfanityFilter;
import com.ftxeven.aircore.module.extras.ExtrasConfig;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.service.PlayerService;
import com.ftxeven.aircore.util.Cooldowns;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.MiniText;
import com.ftxeven.aircore.util.Scheduler;
import com.ftxeven.aircore.util.TimeFormatter;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AfkHandler {

    public sealed interface ToggleResult {
        record Enabled() implements ToggleResult {}
        record Disabled() implements ToggleResult {}
        record Restricted(String world) implements ToggleResult {}
    }

    public sealed interface ReasonVerdict {
        record Accepted() implements ReasonVerdict {}
        record TooLong(int length, int max) implements ReasonVerdict {}
        record Disallowed() implements ReasonVerdict {}
    }

    // Where a player last counted as active
    private record Pose(UUID world, double x, double y, double z, float yaw, float pitch) {

        static Pose of(Location location) {
            return new Pose(location.getWorld().getUID(), location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch());
        }

        boolean movedPast(Pose other, double threshold) {
            if (!world.equals(other.world)) {
                return true;
            }
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return dx * dx + dy * dy + dz * dz >= threshold * threshold;
        }

        boolean lookedPast(Pose other, double thresholdDegrees) {
            return Math.max(angleDelta(yaw, other.yaw), Math.abs(pitch - other.pitch)) >= thresholdDegrees;
        }

        private static float angleDelta(float a, float b) {
            float diff = Math.abs(a - b) % 360f;
            return diff > 180f ? 360f - diff : diff;
        }
    }

    private final ConfigManager configs;
    private final Messenger messenger;
    private final PlayerService players;
    private final ProfanityFilter profanity;
    private final AfkActionRunner actions;

    private final Map<UUID, AfkSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastActivityMillis = new ConcurrentHashMap<>();
    private final Map<UUID, Pose> anchors = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> idleChecks = new ConcurrentHashMap<>();
    private final Map<UUID, Cooldowns<UUID>> notified = new ConcurrentHashMap<>(); // initiator -> (target -> last notice)

    public AfkHandler(JavaPlugin plugin, ConfigManager configs, Messenger messenger, PlayerService players,
                      ProfanityFilter profanity) {
        this.configs = configs;
        this.messenger = messenger;
        this.players = players;
        this.profanity = profanity;
        this.actions = new AfkActionRunner(plugin.getLogger(), configs, messenger);
    }

    // Manual toggle (/afk)

    public ToggleResult toggle(Player player, @Nullable String reason) {
        if (isAfk(player.getUniqueId())) {
            endAfk(player);
            return new ToggleResult.Disabled();
        }
        if (players.isFeatureRestricted(player, MainConfig.RestrictedFeature.AFK)) {
            return new ToggleResult.Restricted(player.getWorld().getName());
        }
        beginAfk(player, reason, false);
        return new ToggleResult.Enabled();
    }

    // Reason rules

    public ReasonVerdict validateReason(Player player, String reason) {
        ExtrasConfig.AfkReason settings = configs.extras().afk().reason();

        if (settings.maxLength() > 0 && reason.length() > settings.maxLength()) {
            return new ReasonVerdict.TooLong(reason.length(), settings.maxLength());
        }

        String wordsFile = configs.chat().filters().profanity().wordsFile();
        boolean disallowed = NameValidator.isBlacklisted(player, Permissions.Bypass.AFK_BLACKLIST, settings.blacklist(), reason)
                || profanity.containsExtendedProfanity(player, settings.extendProfanityWords(), wordsFile, reason);
        return disallowed ? new ReasonVerdict.Disallowed() : new ReasonVerdict.Accepted();
    }

    // Info / placeholders

    public boolean isAfk(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    public boolean isAuto(UUID uuid) {
        AfkSession session = sessions.get(uuid);
        return session != null && session.auto();
    }

    public int count() {
        return sessions.size();
    }

    public Optional<String> reason(UUID uuid) {
        return Optional.ofNullable(sessions.get(uuid)).map(this::resolveReason);
    }

    public Optional<Instant> since(UUID uuid) {
        return Optional.ofNullable(sessions.get(uuid)).map(AfkSession::since);
    }

    public Optional<Duration> duration(UUID uuid) {
        return since(uuid).map(start -> Duration.between(start, Instant.now()));
    }

    // Notify a player who interacted with an AFK target

    public boolean notifyIfAfk(CommandSender initiator, UUID targetUuid, ExtrasConfig.AfkNotifyAction action) {
        ExtrasConfig.AfkNotify notice = configs.extras().afk().notice();
        if (!notice.applies(action)) {
            return false;
        }
        AfkSession session = sessions.get(targetUuid);
        if (session == null) {
            return false;
        }

        if (initiator instanceof Player player) {
            UUID initiatorUuid = player.getUniqueId();
            if (initiatorUuid.equals(targetUuid)) {
                return false;
            }
            Cooldowns<UUID> cooldown = notified.computeIfAbsent(initiatorUuid, ignored -> new Cooldowns<>());
            if (cooldown.checkAndStart(targetUuid, notice.cooldown()) > 0) {
                return false;
            }
        }

        messenger.send(initiator, configs.lang().get("extras.afk.interaction"), placeholders(targetUuid, session));
        return true;
    }

    // Activity triggers

    public void handleMove(PlayerMoveEvent event) {
        ExtrasConfig.AfkActivity activity = configs.extras().afk().activity();
        if (!activity.move() && !activity.look()) {
            return;
        }

        Player player = event.getPlayer();
        Pose current = Pose.of(event.getTo());
        Pose anchor = anchors.computeIfAbsent(player.getUniqueId(), ignored -> Pose.of(event.getFrom()));

        boolean moved = activity.move() && anchor.movedPast(current, activity.moveThreshold());
        boolean looked = activity.look() && anchor.lookedPast(current, activity.lookThreshold());
        if (moved || looked) {
            onActivity(player);
        }
    }

    // being moved by something else isn't activity
    public void handleTeleport(PlayerTeleportEvent event) {
        anchors.remove(event.getPlayer().getUniqueId());
    }

    public void handleInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL || !configs.extras().afk().activity().interact()) {
            return; // stepping on a pressure plate is movement, not interaction
        }
        onActivity(event.getPlayer());
    }

    public void handleInteractEntity(PlayerInteractEntityEvent event) {
        if (configs.extras().afk().activity().interact()) {
            onActivity(event.getPlayer());
        }
    }

    public void handleInventoryClick(InventoryClickEvent event) {
        if (configs.extras().afk().activity().inventory() && event.getWhoClicked() instanceof Player player) {
            onActivity(player);
        }
    }

    public void handleChatActivity(Player player) {
        if (configs.extras().afk().activity().chat()) {
            onActivity(player);
        }
    }

    public void handleCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (configs.extras().afk().activity().command() && !isAfkCommand(event.getMessage())) {
            onActivity(event.getPlayer()); // /afk itself never counts
        }
    }

    // Lifecycle

    public void handleJoin(Player player) {
        UUID uuid = player.getUniqueId();
        lastActivityMillis.put(uuid, System.currentTimeMillis());
        Scheduler.runEntityTimer(player, () -> checkIdle(player), 20L, 20L)
                .ifPresent(task -> idleChecks.put(uuid, task));
    }

    public void handleQuit(UUID uuid) {
        AfkSession session = sessions.remove(uuid);
        if (session != null) {
            actions.cancel(session.scheduledActions());
        }
        ScheduledTask idleCheck = idleChecks.remove(uuid);
        if (idleCheck != null) {
            idleCheck.cancel();
        }
        lastActivityMillis.remove(uuid);
        anchors.remove(uuid);
        notified.remove(uuid);
    }

    public void handleWorldChange(Player player) {
        if (isAfk(player.getUniqueId()) && players.isFeatureRestricted(player, MainConfig.RestrictedFeature.AFK)) {
            endAfk(player);
        }
    }

    // Internal

    private void onActivity(Player player) {
        UUID uuid = player.getUniqueId();
        markActive(uuid);
        if (sessions.containsKey(uuid)) {
            endAfk(player);
        }
    }

    private void markActive(UUID uuid) {
        lastActivityMillis.put(uuid, System.currentTimeMillis());
        anchors.remove(uuid);
    }

    private void beginAfk(Player player, @Nullable String reason, boolean auto) {
        UUID uuid = player.getUniqueId();
        List<ScheduledTask> scheduled = actions.schedule(player, () -> placeholdersFor(uuid));
        AfkSession session = new AfkSession(Instant.now(), reason, auto, scheduled);
        sessions.put(uuid, session);
        anchors.remove(uuid);
        announce(player, "extras.afk.enabled", placeholders(uuid, session));
    }

    private void endAfk(Player player) {
        UUID uuid = player.getUniqueId();
        AfkSession session = sessions.remove(uuid);
        if (session == null) {
            return;
        }
        actions.cancel(session.scheduledActions());
        markActive(uuid);
        announce(player, "extras.afk.disabled", placeholders(uuid, session));
    }

    private void announce(Player player, String langKey, Map<String, String> placeholders) {
        List<String> lines = configs.lang().get(langKey);
        if (configs.extras().afk().broadcast()) {
            messenger.broadcast(lines, placeholders);
        } else {
            messenger.send(player, lines, placeholders);
        }
    }

    private void checkIdle(Player player) {
        ExtrasConfig.Afk settings = configs.extras().afk();
        UUID uuid = player.getUniqueId();
        if (!settings.idleEnabled() || sessions.containsKey(uuid)) {
            return;
        }

        long now = System.currentTimeMillis();
        boolean exempt = player.hasPermission(Permissions.Bypass.AFK_IDLE)
                || players.isFeatureRestricted(player, MainConfig.RestrictedFeature.AFK);
        if (exempt) {
            lastActivityMillis.put(uuid, now); // time spent exempt doesn't count against them later
            return;
        }

        Long last = lastActivityMillis.putIfAbsent(uuid, now);
        if (last != null && now - last >= settings.idleTimeout() * 1000L) {
            beginAfk(player, null, true);
        }
    }

    private @Nullable Map<String, String> placeholdersFor(UUID uuid) {
        AfkSession session = sessions.get(uuid);
        return session != null ? placeholders(uuid, session) : null;
    }

    private Map<String, String> placeholders(UUID uuid, AfkSession session) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        players.formatDisplayName(placeholders, "player", uuid);
        placeholders.put("reason", resolveReason(session));

        Duration elapsed = Duration.between(session.since(), Instant.now());
        placeholders.put("duration", TimeFormatter.duration(elapsed, configs.main().formatting(), configs.lang()));
        placeholders.put("duration_raw", String.valueOf(Math.max(0, elapsed.getSeconds())));
        return placeholders;
    }

    private String resolveReason(AfkSession session) {
        String reason = session.reason();
        if (reason == null || reason.isBlank() || !configs.extras().afk().reason().enabled()) {
            return configs.lang().get("placeholders.no-reason").getFirst();
        }
        return MiniText.mini().escapeTags(reason);
    }

    private boolean isAfkCommand(String rawMessage) {
        String withoutSlash = rawMessage.startsWith("/") ? rawMessage.substring(1) : rawMessage;
        int spaceIndex = withoutSlash.indexOf(' ');
        String label = spaceIndex >= 0 ? withoutSlash.substring(0, spaceIndex) : withoutSlash;

        DynamicCommand afkCommand = configs.commands().findCommandOrDisabled("afk");
        if (label.equalsIgnoreCase(afkCommand.name())) {
            return true;
        }
        for (String alias : afkCommand.aliases()) {
            if (label.equalsIgnoreCase(alias)) {
                return true;
            }
        }
        return false;
    }
}