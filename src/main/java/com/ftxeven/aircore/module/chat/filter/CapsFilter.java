package com.ftxeven.aircore.module.chat.filter;

import com.ftxeven.aircore.module.chat.ChatConfig;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class CapsFilter {

    private static final String LANG_KEY = "chat.errors.filters.caps";

    public FilterVerdict apply(ChatConfig.CapsFilter config, Player sender, String body) {
        if (!config.enabled() || bypassed(sender)) {
            return FilterVerdict.allow(body);
        }

        String trimmed = body.strip();
        if (trimmed.length() < config.minLength()) {
            return FilterVerdict.allow(body);
        }

        int letters = 0;
        int upper = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) {
                    upper++;
                }
            }
        }
        if (letters == 0 || (upper * 100 / letters) < config.threshold()) {
            return FilterVerdict.allow(body);
        }

        return config.action() == ChatConfig.FilterAction.BLOCK
                ? FilterVerdict.block(LANG_KEY)
                : FilterVerdict.allow(body.toLowerCase(Locale.ROOT));
    }

    private boolean bypassed(Player sender) {
        return sender.hasPermission(Permissions.Bypass.filter("caps"));
    }
}