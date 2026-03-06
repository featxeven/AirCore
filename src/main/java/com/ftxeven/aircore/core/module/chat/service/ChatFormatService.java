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

    private static final Set<String> COLORS = Set.of(
            "black","dark_blue","dark_green","dark_aqua","dark_red","dark_purple","gold",
            "gray","dark_gray","blue","green","aqua","red","light_purple","yellow","white"
    );

    private static final Set<String> FORMATS = Set.of(
            "bold","italic","underlined","strikethrough","obfuscated", "rainbow"
    );

    public ChatFormatService(AirCore plugin) {
        this.plugin = plugin;
    }

    public Component format(Player player, String message) {
        if (!plugin.config().groupFormatEnabled()) {
            return null;
        }

        String fmt = resolveGroupFormat(player);
        if (fmt == null || fmt.isBlank()) {
            return null;
        }

        String sanitized = sanitizeForChat(player, message);

        if (sanitized.replaceAll("<[^>]+>", "").trim().isEmpty()) {
            return Component.empty();
        }

        Component base = MessageUtil.mini(player, fmt, Map.of("player", player.getName()), true);

        if (base == null) return Component.empty();

        Component messageComponent = MessageUtil.mini(player, sanitized, Map.of(), false);

        Component formatted = base.replaceText(builder ->
                builder.matchLiteral("%message%").replacement(messageComponent)
        );

        Component processed = plugin.chat().urls().apply(player, formatted);
        processed = plugin.chat().mentions().processMentions(player, processed);

        return plugin.chat().displayTags().apply(player, processed);
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

    public String sanitizeForChat(Player player, String raw) {
        if (raw == null || raw.isBlank()) return "";

        final boolean allColors = player.hasPermission("aircore.chat.color") || player.hasPermission("aircore.chat.color.*");
        final boolean allFormats = player.hasPermission("aircore.chat.format") || player.hasPermission("aircore.chat.format.*");

        Matcher m = TAG_PATTERN.matcher(raw);
        StringBuilder sb = new StringBuilder();
        int last = 0;

        while (m.find()) {
            sb.append(raw, last, m.start());

            String content = m.group(1).trim();
            String lower = content.toLowerCase();

            String effectiveTag = lower.startsWith("/") ? lower.substring(1) : lower;
            boolean allowed = false;

            if (HEX_PATTERN.matcher(lower).matches()) {
                allowed = allColors;
            }
            else if (COLORS.contains(effectiveTag)) {
                allowed = allColors || player.hasPermission("aircore.chat.color." + effectiveTag);
            }
            else if (FORMATS.contains(effectiveTag)) {
                allowed = allFormats || player.hasPermission("aircore.chat.format." + effectiveTag);
            }

            if (allowed) {
                sb.append('<').append(content).append('>');
            } else {
                sb.append('\\').append('<').append(content).append('>');
            }

            last = m.end();
        }

        return sb.append(raw.substring(last)).toString();
    }
}