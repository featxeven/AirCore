package com.ftxeven.aircore.module.chat.displaytag;

import com.ftxeven.aircore.core.gui.GuiManager;
import com.ftxeven.aircore.gui.action.GuiActions;
import com.ftxeven.aircore.module.chat.ChatConfig;
import com.ftxeven.aircore.module.chat.LegacyCodeTranslator;
import com.ftxeven.aircore.service.PlayerService;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.MiniText;
import com.ftxeven.aircore.util.Placeholders;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class DisplayTagRenderer {

    private record Match(String rawText, String tagKey, ChatConfig.DisplayTag config) {}

    private final Supplier<ChatConfig> config;
    private final Map<String, DisplayTagHandler> handlers;
    private final DisplayTagClickRegistry clicks;
    private final PlayerService players;
    private final GuiManager guis;
    private final Logger logger;

    public DisplayTagRenderer(Supplier<ChatConfig> config, Map<String, DisplayTagHandler> handlers,
                              DisplayTagClickRegistry clicks, PlayerService players, GuiManager guis, Logger logger) {
        this.config = config;
        this.handlers = handlers;
        this.clicks = clicks;
        this.players = players;
        this.guis = guis;
        this.logger = logger;
    }

    public Component apply(Component playerMessage, String body, Player sender) {
        List<Match> matches = resolve(sender, body);
        if (matches.isEmpty()) {
            return playerMessage;
        }

        Component result = applyPass(playerMessage, matches, sender, true);
        result = applyPass(result, matches, sender, false);
        return result;
    }

    private List<Match> resolve(Player sender, String body) {
        Map<String, ChatConfig.DisplayTag> tags = config.get().displayTags();
        if (tags.isEmpty() || body.isEmpty()) {
            return List.of();
        }
        String lowerBody = body.toLowerCase(Locale.ROOT);

        List<Match> matches = new ArrayList<>();
        for (Map.Entry<String, ChatConfig.DisplayTag> entry : tags.entrySet()) {
            String key = entry.getKey();
            ChatConfig.DisplayTag tag = entry.getValue();
            if (!tag.enabled() || tag.triggers().isEmpty()) {
                continue;
            }
            if (!Permissions.Access.hasDisplayTag(sender, key)) {
                continue;
            }

            DisplayTagHandler handler = handlers.get(key);
            if (handler != null && !handler.available(sender)) {
                continue; // an "item" tag with an empty hand
            }

            for (String trigger : tag.triggers()) {
                if (trigger.isEmpty()) {
                    continue;
                }
                boolean present = tag.caseSensitive()
                        ? body.contains(trigger)
                        : lowerBody.contains(trigger.toLowerCase(Locale.ROOT));
                if (present) {
                    matches.add(new Match(trigger, key, tag));
                }
            }
        }
        return matches;
    }

    private Component applyPass(Component playerMessage, List<Match> matches, Player sender, boolean caseSensitivePass) {
        Map<String, Match> byRawText = new LinkedHashMap<>();
        for (Match match : matches) {
            if (match.config().caseSensitive() != caseSensitivePass) {
                continue;
            }
            String key = caseSensitivePass ? match.rawText() : match.rawText().toLowerCase(Locale.ROOT);
            byRawText.putIfAbsent(key, match);
        }
        if (byRawText.isEmpty()) {
            return playerMessage;
        }

        List<String> triggers = new ArrayList<>(byRawText.keySet());
        triggers.sort(Comparator.comparingInt(String::length).reversed());
        String joined = triggers.stream().map(Pattern::quote).collect(Collectors.joining("|"));
        Pattern pattern = Pattern.compile(joined, caseSensitivePass ? 0 : Pattern.CASE_INSENSITIVE);

        return playerMessage.replaceText(TextReplacementConfig.builder()
                .match(pattern)
                .replacement((matchResult, builder) -> {
                    String matchedKey = caseSensitivePass ? matchResult.group() : matchResult.group().toLowerCase(Locale.ROOT);
                    Match match = byRawText.get(matchedKey);
                    return match != null ? render(match, sender) : null;
                })
                .build());
    }

    private Component render(Match match, Player sender) {
        DisplayTagHandler handler = handlers.get(match.tagKey());

        Map<String, String> placeholders = new HashMap<>();
        players.formatDisplayName(placeholders, "player", sender.getUniqueId());
        if (handler != null) {
            handler.contribute(sender, placeholders);
        }

        String resolved = LegacyCodeTranslator.toMiniMessage(Placeholders.apply(sender, match.config().format(), placeholders));
        Component rendered = MiniText.parseDynamic(resolved);

        if (handler == null) {
            return rendered;
        }

        ChatConfig.DisplayTag tag = match.config();
        if (!tag.hasGui()) {
            if (handler.guiRequired()) {
                logger.warning("Display tag '" + match.tagKey() + "' requires a 'gui:' in modules/chat.yml but "
                        + "none is configured - clicking it will not do anything until this is fixed");
            }
            return rendered;
        }
        if (!GuiActions.guiEnabled(guis, tag.gui())) {
            logger.warning("Display tag '" + match.tagKey() + "' points at gui '" + tag.gui()
                    + "', which isn't a registered/enabled GUI - clicking it will not do anything until this is fixed");
            return rendered;
        }

        Optional<DisplayTagClickContext> context = handler.click(sender);
        if (context.isEmpty()) {
            return rendered;
        }

        long ttlMillis = tag.expiresAfter() * 1000L;
        String token = clicks.mint(match.tagKey(), sender.getUniqueId(), context.get(), ttlMillis);
        Key id = Key.key("aircore", "display-tag/" + token);
        return rendered.clickEvent(ClickEvent.custom(id, BinaryTagHolder.binaryTagHolder("{}")));
    }
}