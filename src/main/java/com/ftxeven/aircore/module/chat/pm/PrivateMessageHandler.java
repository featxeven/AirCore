package com.ftxeven.aircore.module.chat.pm;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.config.MainConfig;
import com.ftxeven.aircore.module.chat.ChatConfig;
import com.ftxeven.aircore.module.chat.PlayerTextRenderer;
import com.ftxeven.aircore.module.chat.displaytag.DisplayTagRenderer;
import com.ftxeven.aircore.module.chat.filter.ChatFilterPipeline;
import com.ftxeven.aircore.module.chat.filter.FilterVerdict;
import com.ftxeven.aircore.module.chat.filter.WarningLadder;
import com.ftxeven.aircore.module.chat.url.UrlFormatter;
import com.ftxeven.aircore.module.extras.ExtrasConfig;
import com.ftxeven.aircore.module.extras.block.BlockHandler;
import com.ftxeven.aircore.module.extras.afk.AfkHandler;
import com.ftxeven.aircore.service.PlayerService;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.Messenger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class PrivateMessageHandler {

    private static final String MESSAGE_TAG = "message";

    public enum SendResult { SENT, SELF, TARGET_DISABLED, BLOCKED, FILTERED, ON_COOLDOWN }

    private final ConfigManager configs;
    private final Messenger messenger;
    private final PlayerService players;
    private final BlockHandler blocks;
    private final AfkHandler afk;
    private final ChatFilterPipeline filters;
    private final WarningLadder infractions;
    private final Predicate<Player> cooldownGate;
    private final Consumer<Player> cooldownRecorder;
    private final ReplyTracker replies;
    private final DisplayTagRenderer displayTags;
    private final UrlFormatter urlFormatter;

    public PrivateMessageHandler(ConfigManager configs, Messenger messenger, PlayerService players, BlockHandler blocks,
                                 AfkHandler afk, ChatFilterPipeline filters, WarningLadder infractions,
                                 Predicate<Player> cooldownGate, Consumer<Player> cooldownRecorder, ReplyTracker replies,
                                 DisplayTagRenderer displayTags, UrlFormatter urlFormatter) {
        this.configs = configs;
        this.messenger = messenger;
        this.players = players;
        this.blocks = blocks;
        this.afk = afk;
        this.filters = filters;
        this.infractions = infractions;
        this.cooldownGate = cooldownGate;
        this.cooldownRecorder = cooldownRecorder;
        this.replies = replies;
        this.displayTags = displayTags;
        this.urlFormatter = urlFormatter;
    }

    private ChatConfig.PrivateMessages settings() {
        return configs.chat().privateMessages();
    }

    // Direct messaging

    public SendResult sendToPlayer(CommandSender sender, Player target, String rawMessage) {
        ChatConfig.PrivateMessages settings = settings();
        Player playerSender = sender instanceof Player player ? player : null;

        if (playerSender != null) {
            if (settings.applyCooldown() && !cooldownGate.test(playerSender)) {
                return SendResult.ON_COOLDOWN; // cooldownGate already messaged the sender
            }
            if (playerSender.getUniqueId().equals(target.getUniqueId()) && !settings.allowSelfMessage()) {
                messenger.send(sender, configs.lang().get("chat.msg.errors.self"));
                return SendResult.SELF;
            }
            if (blocks.blocksInteraction(ExtrasConfig.BlockAction.PRIVATE_MESSAGES, target.getUniqueId(), playerSender)) {
                Map<String, String> placeholders = new LinkedHashMap<>();
                players.formatDisplayName(placeholders, "player", target.getUniqueId());
                messenger.send(sender, configs.lang().get("extras.block.errors.blocked-by"), placeholders);
                return SendResult.BLOCKED;
            }
        }
        if (!sender.hasPermission(Permissions.Bypass.CHAT_TOGGLE) && !targetAllowsMessages(target.getUniqueId())) {
            Map<String, String> placeholders = new LinkedHashMap<>();
            players.formatDisplayName(placeholders, "player", target.getUniqueId());
            messenger.send(sender, configs.lang().get("chat.msg.errors.disabled"), placeholders);
            return SendResult.TARGET_DISABLED;
        }

        Optional<String> filtered = playerSender != null ? applyFilters(playerSender, rawMessage) : Optional.of(rawMessage);
        if (filtered.isEmpty()) {
            return SendResult.FILTERED; // sender already notified by applyFilters
        }

        deliver(sender, target, filtered.get());
        afk.notifyIfAfk(sender, target.getUniqueId(), ExtrasConfig.AfkNotifyAction.PRIVATE_MESSAGES);
        if (playerSender != null && settings.applyCooldown()) {
            cooldownRecorder.accept(playerSender);
        }
        return SendResult.SENT;
    }

    public boolean reply(Player sender, String rawMessage) {
        Optional<UUID> targetId = replies.get(sender.getUniqueId(), settings().replyExpiresAfter());
        Player target = targetId.map(Bukkit::getPlayer).orElse(null);
        if (target == null) {
            messenger.send(sender, configs.lang().get("chat.msg.errors.no-reply"));
            return false;
        }
        return sendToPlayer(sender, target, rawMessage) == SendResult.SENT;
    }

    // "Everyone" variant
    public int sendToEveryone(CommandSender sender, String rawMessage) {
        ChatConfig.PrivateMessages settings = settings();
        Player playerSender = sender instanceof Player player ? player : null;

        if (playerSender != null && settings.applyCooldown() && !cooldownGate.test(playerSender)) {
            return -1;
        }

        Optional<String> filtered = playerSender != null ? applyFilters(playerSender, rawMessage) : Optional.of(rawMessage);
        if (filtered.isEmpty()) {
            return -1; // sender already notified by applyFilters
        }
        String body = filtered.get();

        Component message = buildMessage(sender, body, settings);
        Map<String, String> placeholders = new HashMap<>();
        players.formatSender(placeholders, "player", sender);
        Component resolved = resolve(sender, "chat.msg.broadcast", placeholders, message);

        List<Player> recipients = broadcastRecipients(playerSender);
        for (Player recipient : recipients) {
            messenger.sendComponent(recipient, resolved);
        }
        messenger.sendComponent(Bukkit.getConsoleSender(), resolved);

        Set<UUID> notified = recipients.stream().map(Player::getUniqueId).collect(Collectors.toSet());
        notifySpies(sender, notified, placeholders, message, "chat.spy.broadcast");

        if (playerSender != null && settings.applyCooldown()) {
            cooldownRecorder.accept(playerSender);
        }
        return recipients.size();
    }

    public void handleQuit(UUID uuid) {
        replies.clear(uuid);
    }

    // Internal

    private Optional<String> applyFilters(Player sender, String rawMessage) {
        if (!settings().applyFilters()) {
            return Optional.of(rawMessage);
        }

        ChatFilterPipeline.FilterOutcome outcome = filters.apply(sender, rawMessage);
        if (outcome.verdict() instanceof FilterVerdict.Block(String langKey)) {
            messenger.send(sender, configs.lang().get(langKey));
            for (String filter : outcome.triggeredFilters()) {
                infractions.recordViolation(sender, filter, true);
            }
            return Optional.empty();
        }

        for (String filter : outcome.triggeredFilters()) {
            infractions.recordViolation(sender, filter, false);
        }
        return Optional.of(((FilterVerdict.Allow) outcome.verdict()).body());
    }

    private List<Player> broadcastRecipients(@Nullable Player sender) {
        List<Player> recipients = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (sender != null && online.getUniqueId().equals(sender.getUniqueId())) {
                recipients.add(online);
                continue;
            }
            if (sender == null || allowsBroadcastFrom(online, sender)) {
                recipients.add(online);
            }
        }
        return recipients;
    }

    private boolean allowsBroadcastFrom(Player recipient, Player sender) {
        if (recipient.hasPermission(Permissions.Bypass.CHAT_TOGGLE)) {
            return true;
        }
        if (blocks.blocksInteraction(ExtrasConfig.BlockAction.PRIVATE_MESSAGES, recipient.getUniqueId(), sender)) {
            return false;
        }
        return targetAllowsMessages(recipient.getUniqueId());
    }

    private void deliver(CommandSender sender, Player target, String rawMessage) {
        ChatConfig.PrivateMessages settings = settings();
        Component message = buildMessage(sender, rawMessage, settings);

        Map<String, String> placeholders = new HashMap<>();
        players.formatSender(placeholders, "player", sender);
        players.formatDisplayName(placeholders, "target", target.getUniqueId());

        messenger.sendComponent(sender, resolve(sender, "chat.msg.sent", placeholders, message));
        messenger.sendComponent(target, resolve(target, "chat.msg.received", placeholders, message));

        if (sender instanceof Player playerSender) {
            replies.remember(playerSender.getUniqueId(), target.getUniqueId());
        }
        notifySpies(sender, Set.of(target.getUniqueId()), placeholders, message, "chat.spy.msg");
    }

    private Component buildMessage(CommandSender sender, String rawMessage, ChatConfig.PrivateMessages settings) {
        MainConfig.MessageFormat mode = configs.main().formatting().messageFormat();
        Component message = PlayerTextRenderer.render(rawMessage, mode, sender);
        if (settings.applyUrlFormatting()) {
            message = urlFormatter.format(rawMessage, message, sender);
        }
        if (settings.applyDisplayTags() && sender instanceof Player playerSender) {
            message = displayTags.apply(message, rawMessage, playerSender);
        }
        return message;
    }

    private Component resolve(CommandSender context, String langKey, Map<String, String> placeholders, Component message) {
        List<String> template = configs.lang().get(langKey).stream()
                .map(line -> line.replace("%message%", "<" + MESSAGE_TAG + ">"))
                .toList();
        return messenger.resolveWithInsertion(context, template, placeholders, MESSAGE_TAG, message);
    }

    private void notifySpies(CommandSender sender, Set<UUID> excluded, Map<String, String> placeholders, Component message, String langKey) {
        Component resolved = resolve(sender, langKey, placeholders, message);
        UUID senderUuid = sender instanceof Player playerSender ? playerSender.getUniqueId() : null;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (senderUuid != null && online.getUniqueId().equals(senderUuid)) continue;
            if (excluded.contains(online.getUniqueId())) continue;
            boolean spying = players.peek(online.getUniqueId())
                    .map(profile -> profile.toggles().socialSpy())
                    .orElse(false);
            if (spying) {
                messenger.sendComponent(online, resolved);
            }
        }
    }

    private boolean targetAllowsMessages(UUID target) {
        return players.peek(target).map(profile -> profile.toggles().msg()).orElse(true);
    }
}