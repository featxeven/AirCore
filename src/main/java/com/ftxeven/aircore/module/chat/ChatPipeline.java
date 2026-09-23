package com.ftxeven.aircore.module.chat;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.model.Position;
import com.ftxeven.aircore.module.Positions;
import com.ftxeven.aircore.module.chat.channel.ChannelMembership;
import com.ftxeven.aircore.module.chat.channel.ChannelResolver;
import com.ftxeven.aircore.module.chat.displaytag.DisplayTagRenderer;
import com.ftxeven.aircore.module.chat.filter.ChatFilterPipeline;
import com.ftxeven.aircore.module.chat.filter.ChatFilterPipeline.FilterOutcome;
import com.ftxeven.aircore.module.chat.filter.FilterVerdict;
import com.ftxeven.aircore.module.chat.filter.WarningLadder;
import com.ftxeven.aircore.module.chat.format.ChatLineTemplateResolver;
import com.ftxeven.aircore.module.chat.format.GroupFormatResolver;
import com.ftxeven.aircore.module.chat.mention.MentionApplier;
import com.ftxeven.aircore.module.chat.mention.MentionMatch;
import com.ftxeven.aircore.module.chat.mention.MentionResolver;
import com.ftxeven.aircore.module.chat.url.UrlFormatter;
import com.ftxeven.aircore.module.extras.ExtrasConfig;
import com.ftxeven.aircore.module.extras.block.BlockHandler;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.Placeholders;
import com.ftxeven.aircore.util.Scheduler;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ChatPipeline {

    private static final String MESSAGE_TAG = "message";

    private record Rendered(
            UUID senderUuid,
            Component line,
            Position origin,
            int radius, // -1 = global
            @Nullable String channelKey,
            boolean membersOnly,
            boolean mentionsEveryone,
            boolean senderBypassesBlocks,
            List<MentionMatch> mentions
    ) {}

    private final ConfigManager configs;
    private final Messenger messenger;
    private final ServiceManager services;
    private final BlockHandler blocks;
    private final ChannelMembership membership;
    private final ChannelResolver channels;
    private final ChatFilterPipeline filters;
    private final WarningLadder infractions;
    private final GroupFormatResolver groupFormats;
    private final ChatLineTemplateResolver lineTemplates;
    private final MentionResolver mentionResolver;
    private final MentionApplier mentionApplier;
    private final UrlFormatter urlFormatter;
    private final DisplayTagRenderer displayTags;

    public ChatPipeline(ConfigManager configs, Messenger messenger, ServiceManager services, BlockHandler blocks,
                        ChannelMembership membership, ChannelResolver channels, ChatFilterPipeline filters,
                        WarningLadder infractions, GroupFormatResolver groupFormats,
                        ChatLineTemplateResolver lineTemplates, MentionResolver mentionResolver,
                        MentionApplier mentionApplier, UrlFormatter urlFormatter, DisplayTagRenderer displayTags) {
        this.configs = configs;
        this.messenger = messenger;
        this.services = services;
        this.blocks = blocks;
        this.membership = membership;
        this.channels = channels;
        this.filters = filters;
        this.infractions = infractions;
        this.groupFormats = groupFormats;
        this.lineTemplates = lineTemplates;
        this.mentionResolver = mentionResolver;
        this.mentionApplier = mentionApplier;
        this.urlFormatter = urlFormatter;
        this.displayTags = displayTags;
    }

    // Phase 1 - event thread

    public void accept(Player sender, String rawMessage, Runnable onAccepted) {
        ChannelResolver.Resolution channel = channels.resolve(sender, rawMessage).orElse(null);
        String body = channel != null ? channel.body() : rawMessage;

        FilterOutcome outcome = filters.apply(sender, body);
        if (outcome.verdict() instanceof FilterVerdict.Block(String langKey)) {
            messenger.send(sender, configs.lang().get(langKey));
            recordViolations(sender, outcome, true);
            return;
        }
        String filtered = ((FilterVerdict.Allow) outcome.verdict()).body();

        // everything from here needs the sender's own thread
        Scheduler.runEntity(sender, () -> {
            Rendered rendered = render(sender, filtered, channel);
            if (rendered == null) {
                return;
            }
            onAccepted.run();
            recordViolations(sender, outcome, false);
            deliver(sender, rendered);
        });
    }

    private void recordViolations(Player sender, FilterOutcome outcome, boolean blocked) {
        for (String filter : outcome.triggeredFilters()) {
            infractions.recordViolation(sender, filter, blocked);
        }
    }

    // Phase 2 - sender's region thread

    private @Nullable Rendered render(Player sender, String body, @Nullable ChannelResolver.Resolution channel) {
        if (!sender.isOnline()) {
            return null;
        }

        List<MentionMatch> mentions = mentionResolver.resolve(sender, body);

        GroupFormatResolver.Resolved groupFormat = groupFormats.resolve(sender);
        String template = lineTemplates.resolve(
                channel != null ? channel.channelKey() : null,
                channel != null ? channel.definition().format() : null,
                groupFormat.key(),
                groupFormat.template());

        Map<String, String> placeholders = new HashMap<>();
        services.players().peek(sender.getUniqueId()).ifPresentOrElse(
                profile -> services.players().formatDisplayName(placeholders, profile),
                () -> placeholders.put("player", sender.getName()));

        String resolved = LegacyCodeTranslator.toMiniMessage(Placeholders.apply(sender, template, placeholders));

        Component message = PlayerTextRenderer.render(body, configs.main().formatting().messageFormat(), sender);
        message = mentionApplier.highlight(message, mentions, sender);
        message = urlFormatter.format(body, message, sender);
        message = displayTags.apply(message, body, sender);   // reads the sender's inventory - correct thread

        Component line = messenger.parseWithInsertion(resolved, MESSAGE_TAG, message);

        int radius = channel != null ? channel.definition().radius() : configs.chat().general().radius();

        boolean senderBypassesBlocks = sender.hasPermission(Permissions.Bypass.BLOCK);

        return new Rendered(
                sender.getUniqueId(),
                line,
                Positions.of(sender.getLocation()),
                radius,
                channel != null ? channel.channelKey() : null,
                channel != null && channel.definition().membersOnly(),
                mentions.stream().anyMatch(m -> m.kind() == MentionMatch.Kind.EVERYONE),
                senderBypassesBlocks,
                mentions);
    }

    // Phase 3 - each recipient's region thread

    private void deliver(Player sender, Rendered rendered) {
        messenger.sendComponent(Bukkit.getConsoleSender(), rendered.line());
        messenger.sendComponent(sender, rendered.line());

        List<Player> candidates = candidates(rendered);
        List<Player> reached = java.util.Collections.synchronizedList(new ArrayList<>());

        for (Player candidate : candidates) {
            if (candidate.getUniqueId().equals(rendered.senderUuid())) {
                continue;
            }
            Scheduler.runEntity(candidate, () -> {
                if (!candidate.isOnline() || !inRange(candidate, rendered)) {
                    return;
                }
                reached.add(candidate);
                messenger.sendComponent(candidate, rendered.line());
            });
        }

        Scheduler.runEntityLater(sender, () -> {
            List<Player> recipients = new ArrayList<>(reached);
            recipients.add(sender);
            mentionApplier.notify(rendered.mentions(), sender, recipients);
        }, 2L);
    }

    /** only touches the online list, caches, and config. */
    private List<Player> candidates(Rendered rendered) {
        List<Player> pool = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(rendered.senderUuid())) {
                continue;
            }
            if (blocks.blocksInteraction(ExtrasConfig.BlockAction.CHAT, online.getUniqueId(), rendered.senderUuid(), rendered.senderBypassesBlocks())) {
                continue;
            }
            if (!allowsChat(online.getUniqueId())) {
                continue;
            }
            String theirChannel = membership.current(online.getUniqueId());
            if (rendered.mentionsEveryone()) {
                pool.add(online);
                continue;
            }
            if (rendered.membersOnly()) {
                if (theirChannel.equals(rendered.channelKey())) {
                    pool.add(online);
                }
                continue;
            }
            if (!theirChannel.equals(rendered.channelKey()) && isolates(theirChannel)) {
                continue;
            }
            pool.add(online);
        }
        return pool;
    }

    /** runs on the recipient's own thread, so reading their location is legal */
    private boolean inRange(Player recipient, Rendered rendered) {
        if (rendered.mentionsEveryone() || rendered.membersOnly() || rendered.radius() < 0) {
            return true;
        }
        Position origin = rendered.origin();
        if (!recipient.getWorld().getName().equals(origin.world())) {
            return false;
        }
        double radiusSquared = (double) rendered.radius() * rendered.radius();
        return Positions.distanceSquared(origin, Positions.of(recipient.getLocation())) <= radiusSquared;
    }

    private boolean isolates(String channelKey) {
        ChatConfig.ChannelDefinition definition = configs.chat().channels().definitions().get(channelKey);
        return definition != null && definition.isolatesMembers();
    }

    private boolean allowsChat(UUID target) {
        return services.players().peek(target).map(profile -> profile.toggles().chat()).orElse(true);
    }
}