package com.ftxeven.aircore.core.module.utility.service;

import com.ftxeven.aircore.AirCore;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NickService {

    private final AirCore plugin;
    private final Map<UUID, String> nicks = new ConcurrentHashMap<>();

    public NickService(@NotNull AirCore plugin) {
        this.plugin = plugin;
    }

    public void load(@NotNull UUID uuid, @Nullable String rawNick) {
        if (rawNick != null && !rawNick.isBlank()) nicks.put(uuid, rawNick);
        else nicks.remove(uuid);
    }

    public void set(@NotNull UUID uuid, @Nullable String rawNick) {
        if (rawNick == null || rawNick.isBlank()) {
            nicks.remove(uuid);
            plugin.database().records().setNick(uuid, null);
        } else {
            nicks.put(uuid, rawNick);
            plugin.database().records().setNick(uuid, rawNick);
        }

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            String nameString = (rawNick == null || rawNick.isBlank())
                    ? player.getName()
                    : getDisplayName(uuid, player.getName());
            player.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(nameString));
        }
    }

    public void clear(@NotNull UUID uuid) {
        this.set(uuid, null);
    }

    public boolean hasNick(@NotNull UUID uuid) {
        if (nicks.containsKey(uuid)) return true;

        String dbNick = plugin.database().records().getNick(uuid);
        if (dbNick != null) {
            nicks.put(uuid, dbNick);
            return true;
        }
        return false;
    }

    @NotNull
    public String getDisplayName(@NotNull UUID uuid, @NotNull String realName) {
        String nick = nicks.get(uuid);

        if (nick == null) {
            nick = plugin.database().records().getNick(uuid);
            if (nick != null) {
                nicks.put(uuid, nick);
            }
        }

        if (nick == null || nick.isBlank()) return realName;

        String prefix = plugin.config().nicknamePrefix();
        return prefix.isEmpty() ? nick : prefix + nick;
    }

    public void unload(@NotNull UUID uuid) { nicks.remove(uuid); }
}