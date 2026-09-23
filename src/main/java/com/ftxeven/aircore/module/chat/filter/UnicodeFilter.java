package com.ftxeven.aircore.module.chat.filter;

import com.ftxeven.aircore.module.chat.ChatConfig;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.entity.Player;

public final class UnicodeFilter {

    private static final String LANG_KEY = "chat.errors.filters.unicode";

    public FilterVerdict apply(ChatConfig.UnicodeFilter config, Player sender, String body) {
        if (!config.enabled() || bypassed(sender)) {
            return FilterVerdict.allow(body);
        }

        StringBuilder cleaned = new StringBuilder(body.length());
        boolean flagged = false;
        for (int i = 0; i < body.length(); ) {
            int codepoint = body.codePointAt(i);
            int charCount = Character.charCount(codepoint);
            if (isOffending(codepoint, config.allowLatinExtended())) {
                flagged = true;
            } else {
                cleaned.appendCodePoint(codepoint);
            }
            i += charCount;
        }

        if (!flagged) {
            return FilterVerdict.allow(body);
        }
        if (config.action() == ChatConfig.FilterAction.BLOCK || cleaned.toString().isBlank()) {
            return FilterVerdict.block(LANG_KEY);
        }
        return FilterVerdict.allow(cleaned.toString());
    }

    private boolean isOffending(int codepoint, boolean allowLatinExtended) {
        if (codepoint < 0x80) {
            return false; // plain ASCII is always fine
        }
        int type = Character.getType(codepoint);
        if (type == Character.FORMAT || type == Character.NON_SPACING_MARK
                || type == Character.ENCLOSING_MARK || type == Character.COMBINING_SPACING_MARK) {
            return true; // zero-width/invisible chars and stacked combining marks (zalgo)
        }
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codepoint);
        if (block == Character.UnicodeBlock.LATIN_1_SUPPLEMENT || block == Character.UnicodeBlock.GENERAL_PUNCTUATION) {
            return false;
        }
        if (allowLatinExtended && (block == Character.UnicodeBlock.LATIN_EXTENDED_A
                || block == Character.UnicodeBlock.LATIN_EXTENDED_B
                || block == Character.UnicodeBlock.LATIN_EXTENDED_ADDITIONAL)) {
            return false;
        }
        return true;
    }

    private boolean bypassed(Player sender) {
        return sender.hasPermission(Permissions.Bypass.filter("unicode"));
    }
}