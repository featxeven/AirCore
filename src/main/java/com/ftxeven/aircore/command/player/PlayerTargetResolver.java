package com.ftxeven.aircore.command.player;

import com.ftxeven.aircore.config.MainConfig;
import com.ftxeven.aircore.model.PlayerProfile;
import com.ftxeven.aircore.service.PlayerService;
import com.ftxeven.aircore.util.MiniText;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class PlayerTargetResolver {

    private static final Pattern LEGACY_CODE = Pattern.compile("[&§][0-9a-fk-orA-FK-OR]");
    private static final Pattern NON_WORD = Pattern.compile("[^a-zA-Z0-9_]");

    public record Target(UUID uuid, PlayerProfile profile) {}

    private final PlayerService players;
    private final Supplier<MainConfig> mainConfig;

    public PlayerTargetResolver(PlayerService players, Supplier<MainConfig> mainConfig) {
        this.players = players;
        this.mainConfig = mainConfig;
    }

    public boolean matchesSelf(Player sender, String typed) {
        if (typed == null || typed.equalsIgnoreCase(sender.getName())) {
            return true;
        }
        return nicknameLookupAllowed() && players.peek(sender.getUniqueId())
                .map(profile -> matchesNickname(profile, typed))
                .orElse(false);
    }

    public Optional<Target> online(String typed) {
        if (typed == null) {
            return Optional.empty();
        }
        if (nicknameLookupAllowed()) {
            Player byNickname = findOnlineByNickname(typed);
            if (byNickname != null) {
                return toTarget(byNickname.getUniqueId());
            }
        }
        Player byName = findOnlineByName(typed);
        return byName != null ? toTarget(byName.getUniqueId()) : Optional.empty();
    }

    public Optional<Player> onlinePlayer(String typed) {
        return online(typed).map(target -> Bukkit.getPlayer(target.uuid()));
    }

    public Optional<Target> offline(String typed) {
        Optional<Target> found = online(typed);
        if (found.isPresent()) {
            return found;
        }

        if (nicknameLookupAllowed()) {
            Optional<Target> byNickname = players.findByNickname(typed)
                    .map(profile -> new Target(profile.uuid(), profile));
            if (byNickname.isPresent()) {
                return byNickname;
            }
        }

        return players.findByRealName(typed).map(profile -> new Target(profile.uuid(), profile));
    }

    private Optional<Target> toTarget(UUID uuid) {
        return players.peek(uuid).map(profile -> new Target(uuid, profile));
    }

    private Player findOnlineByName(String typed) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(typed)) {
                return online;
            }
        }
        return null;
    }

    private Player findOnlineByNickname(String typed) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (players.peek(online.getUniqueId()).map(profile -> matchesNickname(profile, typed)).orElse(false)) {
                return online;
            }
        }
        return null;
    }

    private boolean matchesNickname(PlayerProfile profile, String typed) {
        String nickname = profile.nickname();
        return nickname != null && !nickname.isEmpty() && normalize(nickname).equals(normalize(typed));
    }

    private boolean nicknameLookupAllowed() {
        return mainConfig.get().general().allowNicknameLookup();
    }

    private String normalize(String value) {
        String stripped = LEGACY_CODE.matcher(MiniText.mini().stripTags(value)).replaceAll("");
        return NON_WORD.matcher(stripped).replaceAll("").toLowerCase(Locale.ROOT);
    }
}