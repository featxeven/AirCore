package com.ftxeven.aircore.module.chat.mention;

import com.ftxeven.aircore.config.MessageComponents;
import com.ftxeven.aircore.module.chat.ChatConfig;
import com.ftxeven.aircore.module.extras.ExtrasConfig;
import com.ftxeven.aircore.module.extras.afk.AfkHandler;
import com.ftxeven.aircore.module.extras.block.BlockHandler;
import com.ftxeven.aircore.service.PlayerService;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.MiniText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class MentionApplier {

    private static final String MENTION_BOSSBAR_KEY = "mention";

    private final Supplier<ChatConfig> config;
    private final Messenger messenger;
    private final PlayerService players;
    private final BlockHandler blocks;
    private final AfkHandler afk;

    public MentionApplier(Supplier<ChatConfig> config, Messenger messenger, PlayerService players, BlockHandler blocks, AfkHandler afk) {
        this.config = config;
        this.messenger = messenger;
        this.players = players;
        this.blocks = blocks;
        this.afk = afk;
    }

    // Highlighting

    public Component highlight(Component playerMessage, List<MentionMatch> matches, Player sender) {
        if (matches.isEmpty()) {
            return playerMessage;
        }

        ChatConfig.Mentions mentions = config.get().mentions();
        Map<String, MentionMatch> byRawText = new LinkedHashMap<>();
        for (MentionMatch match : matches) {
            byRawText.putIfAbsent(match.rawText(), match);
        }

        // single pass over the same token grammar used to resolve matches, so a raw
        // text like "@Steve" can never bleed into an unrelated "@Steve2" replacement
        Pattern token = MentionResolver.tokenPattern(mentions);
        return playerMessage.replaceText(TextReplacementConfig.builder()
                .match(token)
                .replacement((matchResult, builder) -> {
                    MentionMatch match = byRawText.get(matchResult.group());
                    return match != null ? renderReplacement(match, mentions, sender) : null;
                })
                .build());
    }

    private Component renderReplacement(MentionMatch match, ChatConfig.Mentions mentions, Player sender) {
        String template = switch (match.kind()) {
            case EVERYONE -> mentions.everyone().format();
            case HERE -> mentions.here().format();
            case PLAYER -> mentions.format();
        };

        Player nameSource = match.kind() == MentionMatch.Kind.PLAYER ? match.target() : sender;
        Map<String, String> placeholders = new LinkedHashMap<>();
        players.peek(nameSource.getUniqueId()).ifPresentOrElse(
                profile -> players.formatDisplayName(placeholders, "player", profile),
                () -> placeholders.put("player", nameSource.getName())
        );

        return MiniText.parseDynamic(substitute(template, placeholders));
    }

    private String substitute(String template, Map<String, String> placeholders) {
        String resolved = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolved = resolved.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return resolved;
    }

    // Notification

    public void notify(List<MentionMatch> matches, Player sender, List<Player> messageRecipients) {
        if (matches.isEmpty()) {
            return;
        }

        Set<UUID> eligible = new HashSet<>();
        for (Player recipient : messageRecipients) {
            eligible.add(recipient.getUniqueId());
        }

        Map<UUID, Player> targets = new LinkedHashMap<>();
        Set<UUID> named = new HashSet<>();
        for (MentionMatch match : matches) {
            switch (match.kind()) {
                case EVERYONE -> addBroadcastTargets(targets, Bukkit.getOnlinePlayers(), sender);
                case HERE -> addBroadcastTargets(targets, messageRecipients, sender);
                case PLAYER -> {
                    Player target = match.target();
                    if (eligible.contains(target.getUniqueId())
                            && !blocks.blocksInteraction(ExtrasConfig.BlockAction.MENTIONS, target.getUniqueId(), sender)
                            && targetAllowsMentions(target.getUniqueId())) {
                        targets.putIfAbsent(target.getUniqueId(), target);
                        named.add(target.getUniqueId());
                    }
                }
            }
        }

        if (targets.isEmpty()) {
            return;
        }

        MessageComponents.Bundle bundle = buildBundle(config.get().mentions().components());
        Map<String, String> placeholders = new LinkedHashMap<>();
        players.peek(sender.getUniqueId()).ifPresentOrElse(
                profile -> players.formatDisplayName(placeholders, "player", profile),
                () -> placeholders.put("player", sender.getName())
        );
        for (Player target : targets.values()) {
            messenger.send(target, bundle, placeholders, MENTION_BOSSBAR_KEY);
        }
        for (UUID uuid : named) {
            afk.notifyIfAfk(sender, uuid, ExtrasConfig.AfkNotifyAction.MENTIONS);
        }
    }

    private void addBroadcastTargets(Map<UUID, Player> targets, Collection<? extends Player> candidates, Player sender) {
        for (Player recipient : candidates) {
            if (recipient.getUniqueId().equals(sender.getUniqueId())) {
                continue;
            }
            if (blocks.blocksInteraction(ExtrasConfig.BlockAction.MENTIONS, recipient.getUniqueId(), sender)) {
                continue;
            }
            if (!targetAllowsMentions(recipient.getUniqueId())) {
                continue;
            }
            targets.putIfAbsent(recipient.getUniqueId(), recipient);
        }
    }

    private boolean targetAllowsMentions(UUID target) {
        return players.peek(target).map(profile -> profile.toggles().mention()).orElse(true);
    }

    private MessageComponents.Bundle buildBundle(ChatConfig.MentionComponents c) {
        return new MessageComponents.Bundle(
                c.chat().enabled() ? List.of(c.chat().text()) : List.of(),
                c.title().enabled()
                        ? new MessageComponents.Timed(c.title().text(), c.title().fadeIn(), c.title().stay(), c.title().fadeOut())
                        : null,
                c.subtitle().enabled() ? c.subtitle().text() : null,
                c.actionbar().enabled() ? c.actionbar().text() : null,
                c.bossbar().enabled()
                        ? new MessageComponents.BossbarAction.Show(
                        c.bossbar().text(), c.bossbar().duration(), c.bossbar().color(), c.bossbar().overlay(),
                        c.bossbar().countdown(), 1.0, false, false)
                        : null,
                c.sound().enabled() ? new MessageComponents.Sound(c.sound().key(), c.sound().volume(), c.sound().pitch()) : null
        );
    }
}