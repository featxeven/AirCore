package com.ftxeven.aircore.module.chat.filter;

import com.ftxeven.aircore.module.NameValidator;
import com.ftxeven.aircore.module.chat.ChatConfig;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.entity.Player;

import java.util.regex.Pattern;

public final class AdvertisingFilter {

    private static final String LANG_KEY = "chat.errors.filters.advertising";

    private static final Pattern IPV4 = Pattern.compile(
            "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b");

    private static final Pattern IPV6 = Pattern.compile(
            "\\[?(?:(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}"
                    + "|(?:[0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{1,4})*)?::(?:[0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{1,4})*)?)]?");

    private static final Pattern DOMAIN = Pattern.compile(
            "(?i)\\b[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.(?:com|net|org|gg|io|co|me|xyz|dev|fun|shop|store|pl|de|ru|uk)\\b");

    public FilterVerdict apply(ChatConfig.AdvertisingFilter config, Player sender, String body) {
        if (!config.enabled() || bypassed(sender)) {
            return FilterVerdict.allow(body);
        }
        if (NameValidator.matchesAny(config.whitelist(), body)) {
            return FilterVerdict.allow(body);
        }

        if (config.blockIps() && (IPV4.matcher(body).find() || IPV6.matcher(body).find())) {
            return FilterVerdict.block(LANG_KEY);
        }
        if (config.blockDomains() && DOMAIN.matcher(body).find()) {
            return FilterVerdict.block(LANG_KEY);
        }
        if (NameValidator.matchesAny(config.patterns(), body)) {
            return FilterVerdict.block(LANG_KEY);
        }
        return FilterVerdict.allow(body);
    }

    private boolean bypassed(Player sender) {
        return sender.hasPermission(Permissions.Bypass.filter("advertising"));
    }
}