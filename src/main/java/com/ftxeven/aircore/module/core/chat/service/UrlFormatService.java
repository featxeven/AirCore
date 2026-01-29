package com.ftxeven.aircore.module.core.chat.service;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UrlFormatService {

    private final boolean enabled;
    private final String template;
    private static final Pattern URL_PATTERN = Pattern.compile(
            "\\bhttps?://[\\w.-]+(?:\\.[\\w.-]+)+(?:/[\\w\\-._~:/?#@!$&'()*+,;=%]*)?",
            Pattern.CASE_INSENSITIVE
    );

    public UrlFormatService(AirCore plugin) {
        this.enabled = plugin.config().urlFormattingEnabled();
        this.template = plugin.config().urlFormatTemplate();
    }

    public String apply(Player player, String message) {
        if (!enabled) return message;
        if (!player.hasPermission("aircore.chat.link")) return message;
        if (template == null || template.isBlank()) return message;

        Matcher matcher = URL_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String url = matcher.group();
            String replacement = template.replace("%link%", url);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(sb);
        return sb.toString();
    }

    public Component apply(Player player, Component input) {
        if (!enabled) return input;
        if (!player.hasPermission("aircore.chat.link")) return input;
        if (template == null || template.isBlank()) return input;

        return input.replaceText(config -> config
                .match(URL_PATTERN)
                .replacement((match, builder) -> {
                    String url = match.group();
                    String formatted = apply(player, url);
                    Component comp = MessageUtil.miniForChat(formatted, null);
                    return comp != null ? comp : Component.text(url);
                })
        );
    }
}
