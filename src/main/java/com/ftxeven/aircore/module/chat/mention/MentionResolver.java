package com.ftxeven.aircore.module.chat.mention;

import com.ftxeven.aircore.model.PlayerProfile;
import com.ftxeven.aircore.module.chat.ChatConfig;
import com.ftxeven.aircore.service.PlayerService;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.MiniText;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MentionResolver {

    private final Supplier<ChatConfig> config;
    private final PlayerService players;

    public MentionResolver(Supplier<ChatConfig> config, PlayerService players) {
        this.config = config;
        this.players = players;
    }

    public List<MentionMatch> resolve(Player sender, String body) {
        ChatConfig.Mentions mentions = config.get().mentions();
        if (!mentions.enabled() || mentions.symbol().isEmpty() || !body.contains(mentions.symbol())) {
            return List.of();
        }

        Matcher matcher = tokenPattern(mentions).matcher(body);
        List<MentionMatch> matches = new ArrayList<>();
        while (matcher.find()) {
            MentionMatch match = resolveToken(sender, mentions, matcher.group(), matcher.group(1));
            if (match != null) {
                matches.add(match);
            }
        }
        return matches;
    }

    static Pattern tokenPattern(ChatConfig.Mentions mentions) {
        return Pattern.compile(Pattern.quote(mentions.symbol()) + "([A-Za-z0-9_]{1,16})");
    }

    private MentionMatch resolveToken(Player sender, ChatConfig.Mentions mentions, String rawText, String name) {
        ChatConfig.Everyone everyone = mentions.everyone();
        if (everyone.enabled() && name.equalsIgnoreCase(everyone.keyword())) {
            return sender.hasPermission(Permissions.Access.MENTION_ALL) ? MentionMatch.everyone(rawText) : null;
        }

        ChatConfig.Here here = mentions.here();
        if (here.enabled() && name.equalsIgnoreCase(here.keyword())) {
            return sender.hasPermission(Permissions.Access.MENTION_HERE) ? MentionMatch.here(rawText) : null;
        }

        if (!sender.hasPermission(Permissions.Access.MENTION)) {
            return null;
        }

        Player target = findTarget(name, mentions);
        if (target == null) {
            return null;
        }
        if (target.getUniqueId().equals(sender.getUniqueId()) && !mentions.allowSelfMention()) {
            return null;
        }

        return MentionMatch.player(rawText, target);
    }

    private Player findTarget(String name, ChatConfig.Mentions mentions) {
        Player byUsername = findOnlinePlayer(name, mentions.caseSensitive());
        if (byUsername != null) {
            return byUsername;
        }
        return mentions.matchNicknames() ? findByNickname(name, mentions.caseSensitive()) : null;
    }

    private Player findOnlinePlayer(String name, boolean caseSensitive) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            boolean matches = caseSensitive ? online.getName().equals(name) : online.getName().equalsIgnoreCase(name);
            if (matches) {
                return online;
            }
        }
        return null;
    }

    private Player findByNickname(String name, boolean caseSensitive) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            String nickname = players.peek(online.getUniqueId()).map(PlayerProfile::nickname).orElse(null);
            if (nickname == null || nickname.isEmpty()) {
                continue;
            }
            String plain;
            try {
                plain = MiniText.plain(nickname);
            } catch (Exception e) {
                plain = nickname;
            }
            boolean matches = caseSensitive ? plain.equals(name) : plain.equalsIgnoreCase(name);
            if (matches) {
                return online;
            }
        }
        return null;
    }
}