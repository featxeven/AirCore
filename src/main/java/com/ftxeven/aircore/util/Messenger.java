package com.ftxeven.aircore.util;

import com.ftxeven.aircore.core.animation.AnimationManager;
import com.ftxeven.aircore.core.message.BossbarStyles;
import com.ftxeven.aircore.core.message.BossbarTracker;
import com.ftxeven.aircore.core.message.MessageTag;
import com.ftxeven.aircore.core.message.MessageTagParser;
import com.ftxeven.aircore.core.message.MessageTagRenderer;
import com.ftxeven.aircore.config.MessageComponents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;

public final class Messenger {

    private static final String MESSAGE_BOSSBAR_KEY = "message";

    private final AnimationManager animations;
    private final MessageTagParser tagParser;
    private final MessageTagRenderer tagRenderer;
    private final BossbarTracker bossBars;
    private final Logger logger;

    public Messenger(Logger logger, AnimationManager animations) {
        this.animations = animations;
        this.logger = logger;
        this.tagParser = new MessageTagParser(logger);
        this.bossBars = new BossbarTracker();
        this.tagRenderer = new MessageTagRenderer(animations, logger, bossBars);
    }

    // Sending

    public void send(CommandSender target, List<String> lines, Map<String, String> placeholders) {
        if (target instanceof Player player) {
            Scheduler.runEntity(player, () -> sendNow(player, lines, placeholders));
            return;
        }
        sendNow(target, lines, placeholders);
    }

    public void send(CommandSender target, List<String> lines) {
        send(target, lines, Map.of());
    }

    private void sendNow(CommandSender target, List<String> lines, Map<String, String> placeholders) {
        MessageTagRenderer.TitleBuffer titleBuffer = new MessageTagRenderer.TitleBuffer();
        UnaryOperator<String> resolve = text -> Placeholders.apply(target, text, placeholders);
        for (String rawLine : lines) {
            String expanded = Placeholders.expandReferences(rawLine);
            String leftover = tagParser.scan(expanded, MESSAGE_BOSSBAR_KEY, resolve, tag -> tagRenderer.render(target, tag, titleBuffer));
            sendChatLine(target, resolve.apply(leftover));
        }
        tagRenderer.flushTitle(target, titleBuffer);
    }

    public Component resolveWithInsertion(CommandSender target, List<String> lines, Map<String, String> placeholders,
                                          String insertionTag, Component insertion) {
        MessageTagRenderer.TitleBuffer titleBuffer = new MessageTagRenderer.TitleBuffer();
        UnaryOperator<String> resolve = text -> Placeholders.apply(target, text, placeholders);
        List<Component> renderedLines = new ArrayList<>(lines.size());
        for (String rawLine : lines) {
            String expanded = Placeholders.expandReferences(rawLine);
            String leftover = tagParser.scan(expanded, MESSAGE_BOSSBAR_KEY, resolve, tag -> tagRenderer.render(target, tag, titleBuffer));
            String resolvedLeftover = resolve.apply(leftover);
            if (!resolvedLeftover.isEmpty()) {
                renderedLines.add(parseWithInsertion(resolvedLeftover, insertionTag, insertion));
            }
        }
        tagRenderer.flushTitle(target, titleBuffer);

        if (renderedLines.isEmpty()) {
            return Component.empty();
        }
        Component result = renderedLines.getFirst();
        for (int i = 1; i < renderedLines.size(); i++) {
            result = result.append(Component.newline()).append(renderedLines.get(i));
        }
        return result;
    }

    // Structured components - announcements, and anything else built from separate
    // chat/title/subtitle/actionbar/bossbar/sound fields instead of inline <tag:'...'> syntax
    public void send(CommandSender target, MessageComponents.Bundle bundle, Map<String, String> placeholders, String bossbarKey) {
        if (bundle == null) {
            return;
        }
        if (target instanceof Player player) {
            Scheduler.runEntity(player, () -> sendBundleNow(player, bundle, placeholders, bossbarKey));
            return;
        }
        sendBundleNow(target, bundle, placeholders, bossbarKey);
    }

    private void sendBundleNow(CommandSender target, MessageComponents.Bundle bundle, Map<String, String> placeholders, String bossbarKey) {
        if (!bundle.chat().isEmpty()) {
            sendNow(target, bundle.chat(), placeholders);
        }

        MessageTagRenderer.TitleBuffer titleBuffer = new MessageTagRenderer.TitleBuffer();

        if (bundle.actionbar() != null) {
            String text = Placeholders.apply(target, bundle.actionbar(), placeholders);
            tagRenderer.render(target, new MessageTag.ActionBar(text), titleBuffer);
        }
        if (bundle.title() != null) {
            MessageComponents.Timed title = bundle.title();
            String text = Placeholders.apply(target, title.text(), placeholders);
            tagRenderer.render(target, new MessageTag.Title(text, title.fadeIn(), title.stay(), title.fadeOut()), titleBuffer);
        }
        if (bundle.subtitle() != null) {
            String text = Placeholders.apply(target, bundle.subtitle(), placeholders);
            tagRenderer.render(target, new MessageTag.Subtitle(text), titleBuffer);
        }
        tagRenderer.flushTitle(target, titleBuffer);

        if (bundle.bossbar() != null) {
            switch (bundle.bossbar()) {
                case MessageComponents.BossbarAction.Clear ignored -> {
                    if (target instanceof Player player) {
                        bossBars.hide(player.getUniqueId(), bossbarKey);
                    }
                }
                case MessageComponents.BossbarAction.Show show -> {
                    String text = Placeholders.apply(target, show.text(), placeholders);
                    tagRenderer.render(target, new MessageTag.BossBar(
                            bossbarKey,
                            text,
                            bossbarDurationTicks(show.duration()),
                            BossbarStyles.color(show.color(), logger),
                            BossbarStyles.overlay(show.overlay(), logger),
                            (float) show.progress(),
                            show.countdown()
                    ), titleBuffer);
                }
            }
        }

        if (bundle.sound() != null) {
            MessageComponents.Sound sound = bundle.sound();
            tagRenderer.render(target, new MessageTag.Sound(sound.key(), (float) sound.volume(), (float) sound.pitch()), titleBuffer);
        }
    }

    // narrow rendering hook for PersistentBossbarCoordinator
    public void renderBossBar(Player player, MessageTag.BossBar tag) {
        tagRenderer.render(player, tag, new MessageTagRenderer.TitleBuffer());
    }

    // Chat composition

    public Component parseWithInsertion(String resolvedTemplate, String tag, Component insertion) {
        TagResolver resolver = Placeholder.component(tag, insertion);
        try {
            return MiniText.parse(resolvedTemplate, resolver);
        } catch (Exception e) {
            logger.warning("Could not parse composed line '" + resolvedTemplate + "': " + e.getMessage());
            return deserialize(resolvedTemplate).append(insertion);
        }
    }

    public void sendComponent(Player target, Component component) {
        Scheduler.runEntity(target, () -> target.sendMessage(component));
    }

    // same, for non-Player senders like console
    public void sendComponent(CommandSender target, Component component) {
        if (target instanceof Player player) {
            sendComponent(player, component);
            return;
        }
        target.sendMessage(component);
    }

    // Broadcasting

    public void broadcast(List<String> lines, Map<String, String> placeholders) {
        broadcast(lines, placeholders, null);
    }

    public void broadcast(List<String> lines, Map<String, String> placeholders, UUID excluded) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (excluded == null || !player.getUniqueId().equals(excluded)) {
                send(player, lines, placeholders);
            }
        }
        send(Bukkit.getConsoleSender(), lines, placeholders);
    }

    public void broadcastWithInsertion(List<String> lines, Map<String, String> placeholders, String insertionTag, Component insertion) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Component resolved = resolveWithInsertion(player, lines, placeholders, insertionTag, insertion);
            if (resolved != Component.empty()) {
                sendComponent(player, resolved);
            }
        }
        Component consoleResolved = resolveWithInsertion(Bukkit.getConsoleSender(), lines, placeholders, insertionTag, insertion);
        if (consoleResolved != Component.empty()) {
            sendComponent(Bukkit.getConsoleSender(), consoleResolved);
        }
    }

    // GUI rendering (item display names, lore, gui titles)

    public Component renderLine(CommandSender viewer, String line, Map<String, String> placeholders, long startTick) {
        String substituted = Placeholders.apply(viewer, line, placeholders);
        Component rendered = deserialize(animations.resolve(substituted, startTick));
        return rendered.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public Component renderLine(CommandSender viewer, String line, Map<String, String> placeholders) {
        return renderLine(viewer, line, placeholders, animations.currentTick());
    }

    public List<Component> renderLines(CommandSender viewer, List<String> lines, Map<String, String> placeholders, long startTick) {
        List<Component> rendered = new ArrayList<>(lines.size());
        for (String line : lines) {
            rendered.add(renderLine(viewer, line, placeholders, startTick));
        }
        return rendered;
    }

    // Plain text

    public String plain(String line, Map<String, String> placeholders) {
        String expanded = Placeholders.expandReferences(line);
        String leftover = tagParser.strip(expanded);
        String substituted = Placeholders.apply(null, leftover, placeholders);
        String resolved = animations.resolve(substituted);
        return MiniText.plain(deserialize(resolved));
    }

    // Cleanup

    public void clearBossBars(UUID uuid) {
        bossBars.hideAll(uuid);
    }

    // Chat lines

    private void sendChatLine(CommandSender target, String rawText) {
        String text = animations.ensureLoopCount(rawText, "a chat message");
        long refStart = animations.currentTick();
        String[] lastSent = {animations.resolve(text, refStart)};
        deliverChatFrame(target, lastSent[0]);

        if (!animations.isAnimated(text)) {
            return;
        }

        long duration = animations.duration(text);
        animations.scheduleRedrawLoop(target, refStart, duration, elapsed -> {
            String frame = animations.resolve(text, refStart);
            if (!frame.equals(lastSent[0])) {
                deliverChatFrame(target, frame);
                lastSent[0] = frame;
            }
        }, () -> { });
    }

    private void deliverChatFrame(CommandSender target, String text) {
        if (!text.isEmpty()) {
            target.sendMessage(deserialize(text));
        }
    }

    private Component deserialize(String text) {
        try {
            return MiniText.parseDynamic(text);
        } catch (Exception e) {
            logger.warning("Could not parse MiniMessage text '" + text + "': " + e.getMessage());
            return Component.text(text);
        }
    }

    // Bossbar helpers

    private long bossbarDurationTicks(int durationSeconds) {
        return durationSeconds * 20L;
    }
}