package com.ftxeven.aircore.core.module.chat.service;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatFormatService {

    private final AirCore plugin;
    private static final Pattern TAG_PATTERN = Pattern.compile("<([^>]+)>");
    private static final Pattern HEX_PATTERN = Pattern.compile("^/?#[a-f0-9]{6}([a-f0-9]{2})?$");
    private static final Set<String> MINI_COLORS = Set.of(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold",
            "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"
    );
    private static final Set<String> MINI_FORMATS = Set.of(
            "bold", "italic", "underlined", "strikethrough", "obfuscated", "rainbow"
    );
    private static final Pattern LEGACY_CODE_PATTERN = Pattern.compile("&([0-9a-fk-orA-FK-OR])");
    private static final Pattern LEGACY_HEX_PATTERN  = Pattern.compile("&#([a-fA-F0-9]{6})");
    private static final Set<Character> LEGACY_COLOR_CODES = Set.of(
            '0','1','2','3','4','5','6','7','8','9',
            'a','b','c','d','e','f','A','B','C','D','E','F'
    );
    private static final Set<Character> LEGACY_FORMAT_CODES = Set.of(
            'k','l','m','n','o','r','K','L','M','N','O','R'
    );

    public ChatFormatService(AirCore plugin) {
        this.plugin = plugin;
    }

    public Component applyFeatures(Player player, Component base) {
        Component result = plugin.chat().urls().apply(player, base);
        result = plugin.chat().mentions().processMentions(player, result);
        result = plugin.chat().displayTags().apply(player, result);
        return result;
    }

    public Component formatFull(Player player, String rawMessage) {
        String sanitized = sanitizeForChat(player, rawMessage);
        if (isEffectivelyEmpty(sanitized)) return Component.empty();

        String formatString = resolveGroupFormat(player);
        if (formatString == null) return null;

        Component messageComponent = MessageUtil.mini(player, sanitized, Map.of(), false);
        Component base = MessageUtil.mini(player, formatString, Map.of("player", player.getName()), true);
        if (base == null) return Component.empty();

        return applyFeatures(player, base.replaceText(b -> b.matchLiteral("%message%").replacement(messageComponent)));
    }

    public Component processMessageOnly(Player player, String rawMessage) {
        return applyFeatures(player, MessageUtil.mini(player, sanitizeForChat(player, rawMessage), Map.of(), false));
    }

    public String sanitizeForChat(Player player, String raw) {
        if (raw == null || raw.isBlank()) return "";
        return switch (MessageUtil.getFormatMode()) {
            case MINI   -> sanitizeMini(player, raw);
            case LEGACY -> sanitizeLegacy(player, raw);
            case SMART  -> sanitizeMini(player, sanitizeLegacy(player, raw));
        };
    }

    private String resolveGroupFormat(Player player) {
        String group = null;
        if (plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                var user = LuckPermsProvider.get().getUserManager().getUser(player.getUniqueId());
                if (user != null) group = user.getPrimaryGroup();
            } catch (Exception ignored) {}
        }
        return plugin.config().getGroupFormat(group);
    }

    private static boolean isEffectivelyEmpty(String s) {
        return s.replaceAll("<[^>]+>", "")
                .replaceAll("&[0-9a-fk-orA-FK-OR]", "")
                .trim()
                .isEmpty();
    }

    private String sanitizeMini(Player player, String raw) {
        boolean allColors  = player.hasPermission("aircore.chat.color")  || player.hasPermission("aircore.chat.color.*");
        boolean allFormats = player.hasPermission("aircore.chat.format") || player.hasPermission("aircore.chat.format.*");

        Matcher m = TAG_PATTERN.matcher(raw);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            sb.append(raw, last, m.start());
            String content      = m.group(1).trim();
            String lower        = content.toLowerCase();
            String effectiveTag = lower.startsWith("/") ? lower.substring(1) : lower;

            boolean allowed = HEX_PATTERN.matcher(lower).matches()
                    ? allColors
                    : MINI_COLORS.contains(effectiveTag)
                    ? (allColors || player.hasPermission("aircore.chat.color." + effectiveTag))
                    : MINI_FORMATS.contains(effectiveTag)
                    && (allFormats || player.hasPermission("aircore.chat.format." + effectiveTag));

            sb.append(allowed ? '<' + content + '>' : '\\' + ("<" + content + ">"));
            last = m.end();
        }
        return sb.append(raw.substring(last)).toString();
    }

    private String sanitizeLegacy(Player player, String raw) {
        boolean allColors  = player.hasPermission("aircore.chat.color")  || player.hasPermission("aircore.chat.color.*");
        boolean allFormats = player.hasPermission("aircore.chat.format") || player.hasPermission("aircore.chat.format.*");

        Matcher hexM = LEGACY_HEX_PATTERN.matcher(raw);
        StringBuilder hexSb = new StringBuilder();
        int hexLast = 0;
        while (hexM.find()) {
            hexSb.append(raw, hexLast, hexM.start());
            if (allColors) hexSb.append(hexM.group());
            hexLast = hexM.end();
        }
        raw = hexSb.append(raw.substring(hexLast)).toString();

        Matcher m = LEGACY_CODE_PATTERN.matcher(raw);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            sb.append(raw, last, m.start());
            char code = m.group(1).charAt(0);
            boolean allowed = LEGACY_COLOR_CODES.contains(code)
                    ? (allColors  || player.hasPermission("aircore.chat.color."  + Character.toLowerCase(code)))
                    : LEGACY_FORMAT_CODES.contains(code)
                    && (allFormats || player.hasPermission("aircore.chat.format." + Character.toLowerCase(code)));

            if (allowed) sb.append(m.group());
            last = m.end();
        }
        return sb.append(raw.substring(last)).toString();
    }
}