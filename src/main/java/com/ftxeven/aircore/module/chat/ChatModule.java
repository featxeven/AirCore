package com.ftxeven.aircore.module.chat;

import com.ftxeven.aircore.core.gui.input.InputRegistry; // FIX
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.module.chat.channel.ChannelMembership;
import com.ftxeven.aircore.module.chat.channel.ChannelResolver;
import com.ftxeven.aircore.module.chat.displaytag.DisplayTagClickHandler;
import com.ftxeven.aircore.module.chat.displaytag.DisplayTagClickRegistry;
import com.ftxeven.aircore.module.chat.displaytag.DisplayTagHandler;
import com.ftxeven.aircore.module.chat.displaytag.DisplayTagRenderer;
import com.ftxeven.aircore.module.chat.displaytag.handler.EnderchestDisplayTagHandler;
import com.ftxeven.aircore.module.chat.displaytag.handler.InventoryDisplayTagHandler;
import com.ftxeven.aircore.module.chat.displaytag.handler.ItemDisplayTagHandler;
import com.ftxeven.aircore.module.chat.displaytag.handler.ShulkerDisplayTagHandler;
import com.ftxeven.aircore.module.chat.filter.ChatFilterPipeline;
import com.ftxeven.aircore.module.chat.filter.WarningLadder;
import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.module.GroupResolver;
import com.ftxeven.aircore.module.chat.format.ChatLineTemplateResolver;
import com.ftxeven.aircore.module.chat.format.GroupFormatResolver;
import com.ftxeven.aircore.module.chat.input.PlayerInputFormatter;
import com.ftxeven.aircore.module.chat.mention.MentionApplier;
import com.ftxeven.aircore.module.chat.mention.MentionResolver;
import com.ftxeven.aircore.module.chat.pm.PrivateMessageHandler;
import com.ftxeven.aircore.module.chat.pm.ReplyTracker;
import com.ftxeven.aircore.module.chat.url.UrlFormatter;
import com.ftxeven.aircore.module.extras.ExtrasModule;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.Cooldowns;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.TimeFormatter;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.*;

public final class ChatModule implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final Messenger messenger;
    private final InputRegistry guiInput;

    private final GroupFormatResolver groupFormats;
    private final ChannelMembership channelMembership;
    private final ChannelResolver channels;
    private final ChatLineTemplateResolver lineTemplates;
    private final UrlFormatter urlFormatter;
    private final WarningLadder infractions;
    private final ChatFilterPipeline filters;
    private final PrivateMessageHandler privateMessages;
    private final DisplayTagRenderer displayTagRenderer;
    private final DisplayTagClickHandler displayTagClickHandler;
    private final PlayerInputFormatter playerInput;
    private final ChatPipeline pipeline;
    private final Cooldowns<UUID> cooldowns = new Cooldowns<>();

    public ChatModule(JavaPlugin plugin, ConfigManager configs, Messenger messenger, GroupResolver groups,
                      ServiceManager services, ExtrasModule extras, PluginGuiManager guiManager) {
        this.plugin = plugin;
        this.configs = configs;
        this.messenger = messenger;
        this.guiInput = guiManager.guis().input();
        this.groupFormats = new GroupFormatResolver(configs::chat, groups, plugin);
        this.channelMembership = new ChannelMembership(configs, messenger, services.players());
        this.channels = new ChannelResolver(configs::chat, channelMembership);
        this.lineTemplates = new ChatLineTemplateResolver(configs::chat, plugin);
        this.urlFormatter = new UrlFormatter(configs::chat);
        this.infractions = new WarningLadder(configs::chat, messenger, services.players());
        this.filters = new ChatFilterPipeline(configs::chat, services.profanity());
        this.playerInput = new PlayerInputFormatter(configs);

        Map<String, DisplayTagHandler> displayTagHandlers = Map.of(
                "item", new ItemDisplayTagHandler(configs::lang),
                "inventory", new InventoryDisplayTagHandler(),
                "enderchest", new EnderchestDisplayTagHandler(),
                "shulker", new ShulkerDisplayTagHandler(configs::lang)
        );
        DisplayTagClickRegistry displayTagClicks = new DisplayTagClickRegistry();
        this.displayTagRenderer = new DisplayTagRenderer(configs::chat, displayTagHandlers, displayTagClicks, services.players(), guiManager.guis(), plugin.getLogger());
        this.displayTagClickHandler = new DisplayTagClickHandler(displayTagClicks, configs::chat, messenger, guiManager);

        this.privateMessages = new PrivateMessageHandler(
                configs, messenger, services.players(), extras.blocks(), extras.afk(), filters, infractions,
                this::checkCooldown, this::recordCooldown, new ReplyTracker(), displayTagRenderer, urlFormatter);

        MentionResolver mentionResolver = new MentionResolver(configs::chat, services.players());
        MentionApplier mentionApplier = new MentionApplier(configs::chat, messenger, services.players(), extras.blocks(), extras.afk());
        this.pipeline = new ChatPipeline(configs, messenger, services, extras.blocks(), channelMembership, channels,
                filters, infractions, groupFormats, lineTemplates, mentionResolver, mentionApplier, urlFormatter, displayTagRenderer);

        registerChatListener();
    }

    public boolean enabled() {
        return configs.chat().enabled();
    }

    public ChatFilterPipeline filters() {
        return filters;
    }

    public WarningLadder infractions() {
        return infractions;
    }

    public PrivateMessageHandler privateMessages() {
        return privateMessages;
    }

    public ChannelResolver channelResolver() {
        return channels;
    }

    public ChannelMembership channelMembership() {
        return channelMembership;
    }

    public void reload() {
        lineTemplates.invalidate();
        groupFormats.resetWarnings();
        filters.reload();

        unregisterChatListener();
        registerChatListener();
    }

    // Chat listener registration

    private void registerChatListener() {
        EventPriority priority = configs.chat().general().eventPriority();
        Bukkit.getPluginManager().registerEvent(
                AsyncChatEvent.class,
                this,
                priority,
                (listener, event) -> onChatEvent((AsyncChatEvent) event),
                plugin,
                true
        );
    }

    private void unregisterChatListener() {
        HandlerList.unregisterAll(this);
    }

    private void onChatEvent(AsyncChatEvent event) {
        if (!enabled()) {
            return;
        }
        if (guiInput.awaitingChat(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message());
        handleChat(event.getPlayer(), raw);
    }

    public void handleChat(Player player, String rawMessage) {
        if (!checkCooldown(player)) {
            return;
        }
        pipeline.accept(player, rawMessage, () -> recordCooldown(player));
    }

    public void handleCustomClick(PlayerCustomClickEvent event) {
        displayTagClickHandler.handle(event);
    }

    // Player input (signs, anvils, books)

    public void handleSignChange(SignChangeEvent event) {
        playerInput.handleSignChange(event);
    }

    public void handleAnvilRename(PrepareAnvilEvent event) {
        playerInput.handleAnvilRename(event);
    }

    public void handleBookEdit(PlayerEditBookEvent event) {
        playerInput.handleBookEdit(event);
    }

    public void handleQuit(UUID uuid) {
        filters.handleQuit(uuid);
        infractions.handleQuit(uuid);
        privateMessages.handleQuit(uuid);
        channelMembership.handleQuit(uuid);
    }

    public boolean checkCooldown(Player player) {
        if (player.hasPermission(Permissions.Bypass.CHAT_COOLDOWN)) {
            return true;
        }
        double remainingSeconds = cooldownRemaining(player);
        if (remainingSeconds > 0) {
            sendTimingMessage(player, "chat.errors.cooldown", remainingSeconds);
            return false;
        }
        return true;
    }

    public void recordCooldown(Player player) {
        if (player.hasPermission(Permissions.Bypass.CHAT_COOLDOWN)) {
            return;
        }
        cooldowns.hit(player.getUniqueId());
    }

    private double cooldownRemaining(Player player) {
        return cooldowns.remainingSeconds(player.getUniqueId(), configs.chat().general().cooldown());
    }

    private void sendTimingMessage(Player player, String langKey, double remainingSeconds) {
        Duration remaining = Duration.ofNanos(Math.round(remainingSeconds * 1_000_000_000L));
        messenger.send(player, configs.lang().get(langKey), Map.of(
                "timeout", TimeFormatter.duration(remaining, configs.main().formatting(), configs.lang())
        ));
    }
}