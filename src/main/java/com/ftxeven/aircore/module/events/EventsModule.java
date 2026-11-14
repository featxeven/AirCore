package com.ftxeven.aircore.module.events;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.model.PlayerProfile;
import com.ftxeven.aircore.module.GroupResolver;
import com.ftxeven.aircore.service.PlayerService;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.util.Messenger;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EventsModule {

    private final EventsConfig config;
    private final ConfigManager configs;
    private final Messenger messenger;
    private final GroupResolver groups;
    private final PlayerService players;

    private final DeathMessageResolver deathResolver;

    public EventsModule(ConfigManager configs, Messenger messenger, GroupResolver groups, ServiceManager services) {
        this.config = configs.events();
        this.configs = configs;
        this.messenger = messenger;
        this.groups = groups;
        this.players = services.players();

        this.deathResolver = new DeathMessageResolver(configs.lang(), players);
    }

    // Join

    public void handleJoin(PlayerJoinEvent event) {
        if (joinEnabled()) {
            event.joinMessage(null);
        }
    }

    public void announceJoin(Player player, PlayerProfile profile, boolean firstJoin) {
        if (!joinEnabled()) {
            return;
        }
        sendMotd(player, profile, firstJoin);
        broadcastJoin(player, profile, firstJoin);
    }

    // Quit

    public void handleQuit(PlayerQuitEvent event) {
        if (!leaveEnabled()) {
            return;
        }
        event.quitMessage(null);
        broadcastLeave(event.getPlayer());
    }

    // Death

    public void handleDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!deathEnabled() || config.death().disabledWorlds().contains(player.getWorld().getName())) {
            return;
        }

        DeathMessageResolver.Result result = deathResolver.resolve(player);

        Map<String, String> placeholders = new HashMap<>(result.placeholders());
        players.formatDisplayName(placeholders, "player", player);

        event.deathMessage(null);
        messenger.broadcast(player, configs.lang().get(result.langKey()), placeholders);
    }

    // Internal

    private boolean joinEnabled() {
        return config.enabled() && config.join().enabled();
    }

    private boolean leaveEnabled() {
        return config.enabled() && config.leave().enabled();
    }

    private boolean deathEnabled() {
        return config.enabled() && config.death().enabled();
    }

    private void sendMotd(Player player, PlayerProfile profile, boolean firstJoin) {
        List<String> lines = firstJoin
                ? config.join().motd().firstJoin()
                : GroupResolver.resolve(config.join().motd().returning(), groups.primaryGroup(player));
        if (lines == null || lines.isEmpty()) {
            return;
        }
        Map<String, String> placeholders = new HashMap<>();
        players.formatDisplayName(placeholders, profile);
        messenger.send(player, lines, placeholders);
    }

    private void broadcastJoin(Player player, PlayerProfile profile, boolean firstJoin) {
        String template = firstJoin
                ? config.join().broadcast().firstJoin()
                : GroupResolver.resolve(config.join().broadcast().returning(), groups.primaryGroup(player));
        if (template == null || template.isEmpty()) {
            return;
        }
        Map<String, String> placeholders = new HashMap<>();
        players.formatDisplayName(placeholders, profile);
        placeholders.put("join_number", String.valueOf(profile.joinNumber()));
        messenger.broadcast(player, List.of(template), placeholders);
    }

    private void broadcastLeave(Player player) {
        String template = GroupResolver.resolve(config.leave().broadcast(), groups.primaryGroup(player));
        if (template == null || template.isEmpty()) {
            return;
        }
        Map<String, String> placeholders = new HashMap<>();
        players.formatDisplayName(placeholders, "player", player);
        messenger.broadcast(player, List.of(template), placeholders);
    }
}