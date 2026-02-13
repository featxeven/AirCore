package com.ftxeven.aircore.core.chat.service;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.service.ToggleService;
import com.ftxeven.aircore.util.ActionbarUtil;
import com.ftxeven.aircore.util.MessageUtil;
import com.ftxeven.aircore.util.SoundUtil;
import com.ftxeven.aircore.util.TitleUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.regex.Pattern;

public final class MentionService {

    private final AirCore plugin;

    public MentionService(AirCore plugin) {
        this.plugin = plugin;
    }

    public Component processMentions(Player sender, Component message) {
        if (!plugin.config().mentionsEnabled()) return message;
        if (!sender.hasPermission("aircore.chat.mention")) return message;

        String format = plugin.config().mentionFormat();
        if (format.isBlank()) return message;

        boolean allowSelf = plugin.config().mentionAllowSelf();

        Component result = message;

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(sender) && !allowSelf) continue;

            String token = "@" + target.getName();
            Component mentionComp = buildMentionComponent(target, format);

            Component before = result;

            if (plugin.config().mentionCaseSensitive()) {
                result = result.replaceText(builder ->
                        builder.match(Pattern.quote(token) + "(?![A-Za-z0-9])")
                                .replacement(mentionComp)
                );
            } else {
                result = result.replaceText(builder ->
                        builder.match("(?i)" + Pattern.quote(token) + "(?![A-Za-z0-9])")
                                .replacement(mentionComp)
                );
            }

            if (!result.equals(before)) {
                var toggles = plugin.core().toggles();
                var blocks = plugin.core().blocks();
                java.util.UUID targetId = target.getUniqueId();

                boolean canNotify = toggles.isEnabled(targetId, ToggleService.Toggle.MENTIONS)
                        && toggles.isEnabled(targetId, ToggleService.Toggle.CHAT)
                        && !blocks.isBlocked(targetId, sender.getUniqueId());

                if (canNotify) {
                    notifyMention(sender, target);
                }
            }
        }

        return result;
    }

    private Component buildMentionComponent(Player target, String format) {
        String raw = format.replace("%player%", target.getName());
        return MessageUtil.miniForChat(raw, Map.of());
    }

    private void notifyMention(Player sender, Player target) {
        SoundUtil.play(
                target,
                plugin.config().mentionSoundName(),
                plugin.config().mentionSoundVolume(),
                plugin.config().mentionSoundPitch()
        );

        TitleUtil.send(
                target,
                plugin.config().mentionTitleText(),
                plugin.config().mentionSubtitleText(),
                plugin.config().mentionTitleFadeIn(),
                plugin.config().mentionTitleStay(),
                plugin.config().mentionTitleFadeOut(),
                Map.of("player", sender.getName())
        );

        ActionbarUtil.send(
                plugin,
                target,
                plugin.config().mentionActionbarText(),
                Map.of("player", sender.getName())
        );
    }
}
