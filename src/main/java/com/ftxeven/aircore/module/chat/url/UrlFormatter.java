package com.ftxeven.aircore.module.chat.url;

import com.ftxeven.aircore.module.chat.ChatConfig;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.MiniText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.permissions.Permissible;

import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class UrlFormatter {

    private static final Pattern URL = Pattern.compile(
            "(?i)\\b((?:https?://|www\\.)[-a-z0-9+&@#/%?=~_|!:,.;]*[-a-z0-9+&@#/%=~_|])"
    );

    private final Supplier<ChatConfig> config;

    public UrlFormatter(Supplier<ChatConfig> config) {
        this.config = config;
    }

    public Component format(String body, Component playerMessage, Permissible sender) {
        ChatConfig.Urls urls = config.get().urls();
        if (!urls.enabled() || urls.format().isEmpty() || !mightContainUrl(body)
                || !sender.hasPermission(Permissions.Access.URL_FORMATTING)) {
            return playerMessage;
        }

        return playerMessage.replaceText(TextReplacementConfig.builder()
                .match(URL)
                .replacement((matchResult, builder) -> render(matchResult.group(), urls.format()))
                .build());
    }

    private Component render(String rawText, String template) {
        String href = hasScheme(rawText) ? rawText : "https://" + rawText;
        return MiniText.parseDynamic(template.replace("%url%", href));
    }

    private boolean hasScheme(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private boolean mightContainUrl(String body) {
        String lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("http") || lower.contains("www.");
    }
}