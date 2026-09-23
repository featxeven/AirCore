package com.ftxeven.aircore.api.papi;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.model.PlayerProfile;
import com.ftxeven.aircore.module.chat.channel.ChannelMembership;
import com.ftxeven.aircore.module.extras.block.BlockHandler;
import com.ftxeven.aircore.service.PlayerService;
import com.ftxeven.aircore.util.TimeFormatter;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

final class PlayerPlaceholders {

    private static final String ONLINE = "online";

    private final PlayerService players;
    private final ConfigManager configs;
    private final ChannelMembership channelMembership;
    private final BlockHandler blocks;

    PlayerPlaceholders(PlayerService players, ConfigManager configs, ChannelMembership channelMembership, BlockHandler blocks) {
        this.players = players;
        this.configs = configs;
        this.channelMembership = channelMembership;
        this.blocks = blocks;
    }

    @Nullable String resolve(@Nullable OfflinePlayer viewer, String key) {
        if (viewer == null) {
            return null;
        }
        String lower = key.toLowerCase(Locale.ROOT);

        if (lower.startsWith("block_")) {
            return resolveOwnBlockStat(viewer, lower.substring("block_".length()));
        }
        if (lower.startsWith("blocked_by_")) {
            return blockedBy(viewer, key.substring("blocked_by_".length()));
        }
        if (lower.startsWith("has_blocked_")) {
            return hasBlocked(viewer, key.substring("has_blocked_".length()));
        }

        return players.find(viewer.getUniqueId())
                .map(profile -> resolve(viewer, profile, lower))
                .orElse(null);
    }

    private @Nullable String resolve(OfflinePlayer viewer, PlayerProfile profile, String key) {
        if (key.startsWith("toggle_")) {
            return toggle(profile, key.substring("toggle_".length()));
        }
        return switch (key) {
            case "displayname" -> players.displayName(profile);
            case "nickname" -> profile.nickname() != null ? profile.nickname() : "";
            case "realname" -> profile.name();
            case "has_nickname" -> String.valueOf(profile.nickname() != null && !profile.nickname().isEmpty());
            case "first_join" -> String.valueOf(profile.firstJoinAt().equals(profile.lastSeenAt()));
            case "first_join_date" -> TimeFormatter.date(profile.firstJoinAt(), configs.main().formatting());
            case "first_join_time" -> TimeFormatter.time(profile.firstJoinAt(), configs.main().formatting());
            case "last_seen" -> viewer.isOnline()
                    ? ONLINE
                    : TimeFormatter.duration(Duration.between(profile.lastSeenAt(), Instant.now()), configs.main().formatting(), configs.lang());
            case "last_seen_date" -> viewer.isOnline()
                    ? ONLINE
                    : TimeFormatter.date(profile.lastSeenAt(), configs.main().formatting());
            case "last_seen_time" -> viewer.isOnline()
                    ? ONLINE
                    : TimeFormatter.time(profile.lastSeenAt(), configs.main().formatting());
            case "last_seen_raw" -> viewer.isOnline()
                    ? ONLINE
                    : String.valueOf(Math.max(0, Duration.between(profile.lastSeenAt(), Instant.now()).getSeconds()));
            case "join_number" -> String.valueOf(profile.joinNumber());
            case "chat_channel" -> viewer.isOnline()
                    ? channelMembership.current(viewer.getPlayer())
                    : (profile.chatChannel() != null ? profile.chatChannel() : "");
            case "god_mode" -> String.valueOf(profile.godMode());
            case "walk_speed" -> String.valueOf(profile.walkSpeed());
            case "fly_speed" -> String.valueOf(profile.flySpeed());
            default -> null;
        };
    }

    private @Nullable String toggle(PlayerProfile profile, String name) {
        PlayerProfile.Toggles toggles = profile.toggles();
        return switch (name) {
            case "msg" -> String.valueOf(toggles.msg());
            case "socialspy" -> String.valueOf(toggles.socialSpy());
            case "chat" -> String.valueOf(toggles.chat());
            case "mention" -> String.valueOf(toggles.mention());
            case "announce" -> String.valueOf(toggles.announce());
            case "pay" -> String.valueOf(toggles.pay());
            case "payconfirm" -> String.valueOf(toggles.payConfirm());
            case "tp" -> String.valueOf(toggles.tp());
            case "tpautoaccept" -> String.valueOf(toggles.tpAutoAccept());
            case "tpconfirm" -> String.valueOf(toggles.tpConfirm());
            default -> null;
        };
    }

    // Block list - own count/limit/available, and this pair's relationship with a named player

    private @Nullable String resolveOwnBlockStat(OfflinePlayer viewer, String stat) {
        return switch (stat) {
            case "count" -> String.valueOf(blocks.countBlocked(viewer.getUniqueId()));
            case "limit" -> String.valueOf(blockLimit(viewer));
            case "available" -> blockAvailable(viewer);
            default -> null;
        };
    }

    private @Nullable String blockedBy(OfflinePlayer viewer, String targetName) {
        if (targetName.isEmpty()) {
            return null;
        }
        return players.findByRealName(targetName)
                .map(target -> String.valueOf(blocks.isBlocked(target.uuid(), viewer.getUniqueId())))
                .orElse(null);
    }

    private @Nullable String hasBlocked(OfflinePlayer viewer, String targetName) {
        if (targetName.isEmpty()) {
            return null;
        }
        return players.findByRealName(targetName)
                .map(target -> String.valueOf(blocks.isBlocked(viewer.getUniqueId(), target.uuid())))
                .orElse(null);
    }

    private int blockLimit(OfflinePlayer viewer) {
        return viewer.isOnline() ? blocks.maxBlocked(viewer.getPlayer()) : blocks.configuredMaxBlocked();
    }

    private String blockAvailable(OfflinePlayer viewer) {
        int limit = blockLimit(viewer);
        if (limit < 0) {
            return "-1";
        }
        return String.valueOf(Math.max(0, limit - blocks.countBlocked(viewer.getUniqueId())));
    }
}