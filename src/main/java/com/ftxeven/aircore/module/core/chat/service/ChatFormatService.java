package com.ftxeven.aircore.module.core.chat.service;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import com.ftxeven.aircore.util.PlaceholderUtil;
import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatFormatService {

    private final AirCore plugin;
    private final boolean groupFormatEnabled;
    private static final Pattern TAG_PATTERN = Pattern.compile("<([^>]+)>");
    private static final Set<String> COLOR_NAMES = new HashSet<>(Set.of(
            "black","dark_blue","dark_green","dark_aqua","dark_red","dark_purple","gold",
            "gray","dark_gray","blue","green","aqua","red","light_purple","yellow","white"
    ));
    private static final Set<String> DECORATION_NAMES = new HashSet<>(Set.of(
            "bold","italic","underlined","strikethrough","obfuscated", "rainbow"
    ));

    public ChatFormatService(AirCore plugin) {
        this.plugin = plugin;
        this.groupFormatEnabled = plugin.config().groupFormatEnabled();
    }

    public Component format(Player player, String message) {
        String sanitized = sanitizeForChat(player, message);

        String stripped = sanitized.replaceAll("<[^>]+>", "");
        if (stripped.trim().isEmpty()) {
            return null;
        }

        if (!groupFormatEnabled) {
            return null;
        }

        String fmt = resolveGroupFormat(player);
        fmt = PlaceholderUtil.apply(player, fmt);
        fmt = fmt.replace("%player%", player.getName())
                .replace("%message%", sanitized);

        Component base = MessageUtil.miniRaw(fmt);
        if (base == null) return null;

        Component withUrls = plugin.chat().urls().apply(player, base);
        Component withMentions = plugin.chat().mentions().processMentions(player, withUrls);

        return plugin.chat().displayTags().apply(player, withMentions);
    }



    private String resolveGroupFormat(Player player) {
        if (!groupFormatEnabled) {
            return plugin.config().getGroupFormat(null);
        }

        String group = null;
        String fmt;

        if (plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                var api = LuckPermsProvider.get();
                var user = api.getUserManager().getUser(player.getUniqueId());
                if (user != null) {
                    group = user.getPrimaryGroup();
                }
            } catch (Exception ignored) {}
        }

        fmt = plugin.config().getGroupFormat(group);

        return fmt;
    }

    public String sanitizeForChat(Player player, String raw) {
        if (raw == null || raw.isBlank()) return "";

        boolean allowColors = player.hasPermission("aircore.chat.color")
                || player.hasPermission("aircore.chat.color.*");
        boolean allowFormats = player.hasPermission("aircore.chat.format")
                || player.hasPermission("aircore.chat.format.*");

        Matcher m = TAG_PATTERN.matcher(raw);
        StringBuilder sb = new StringBuilder();
        int last = 0;

        while (m.find()) {
            sb.append(raw, last, m.start());

            String tagContent = m.group(1).trim();
            String lower = tagContent.toLowerCase();
            boolean allowed = allowColors && lower.matches("#[a-f0-9]{6}([a-f0-9]{2})?");

            if (!allowed && COLOR_NAMES.contains(lower)) {
                if (allowColors || player.hasPermission("aircore.chat.color." + lower)) {
                    allowed = true;
                }
            }

            if (!allowed && DECORATION_NAMES.contains(lower)) {
                if (allowFormats || player.hasPermission("aircore.chat.format." + lower)) {
                    allowed = true;
                }
            }

            // Closing tags
            if (!allowed && lower.startsWith("/")) {
                String inner = lower.substring(1);
                if (allowColors && inner.matches("#[a-f0-9]{6}([a-f0-9]{2})?")) {
                    allowed = true;
                } else if (COLOR_NAMES.contains(inner)) {
                    if (allowColors || player.hasPermission("aircore.chat.color." + inner)) {
                        allowed = true;
                    }
                } else if (DECORATION_NAMES.contains(inner)) {
                    if (allowFormats || player.hasPermission("aircore.chat.format." + inner)) {
                        allowed = true;
                    }
                }
            }

            if (allowed) {
                sb.append('<').append(tagContent).append('>');
            } else {
                sb.append('\\').append('<').append(tagContent).append('>');
            }

            last = m.end();
        }

        sb.append(raw.substring(last));
        return sb.toString();
    }
}
