package com.ftxeven.aircore.module.chat.mention;

import org.bukkit.entity.Player;

public record MentionMatch(String rawText, Player target, Kind kind) {

    public enum Kind { PLAYER, EVERYONE, HERE }

    public static MentionMatch player(String rawText, Player target) {
        return new MentionMatch(rawText, target, Kind.PLAYER);
    }

    public static MentionMatch everyone(String rawText) {
        return new MentionMatch(rawText, null, Kind.EVERYONE);
    }

    public static MentionMatch here(String rawText) {
        return new MentionMatch(rawText, null, Kind.HERE);
    }
}