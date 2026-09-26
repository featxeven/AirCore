package com.ftxeven.aircore.api.papi;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.module.placeholders.PlaceholdersModule;
import com.ftxeven.aircore.module.variables.VariablesModule;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class AirCoreExpansion extends PlaceholderExpansion {

    @FunctionalInterface
    private interface Section {
        @Nullable String resolve(@Nullable OfflinePlayer viewer, String key);
    }

    private final AirCore plugin;
    private final Map<String, Section> sections;

    public AirCoreExpansion(AirCore plugin) {
        this.plugin = plugin;
        this.sections = buildSections(plugin);
    }

    private static Map<String, Section> buildSections(AirCore plugin) {
        PlaceholdersModule customPlaceholders = plugin.modules().placeholders();
        VariablesModule variables = plugin.modules().variables();

        Map<String, Section> sections = new LinkedHashMap<>();
        sections.put("key_", (viewer, key) ->
                viewer != null && viewer.isOnline() ? customPlaceholders.resolve(viewer.getPlayer(), key) : null);
        sections.put("var_", variables::resolve);
        sections.put("player_", new PlayerPlaceholders(
                plugin.services().players(),
                plugin.configs(),
                plugin.modules().chat().channelMembership(),
                plugin.modules().extras().blocks())::resolve);
        sections.put("economy_", new EconomyPlaceholders(
                plugin.services().players(), plugin.modules().economy())::resolve);
        sections.put("kit_", new KitPlaceholders(
                plugin.modules().kits(), plugin.configs())::resolve);
        sections.put("home_", new HomePlaceholders(
                plugin.modules().homes(), plugin.configs())::resolve);
        sections.put("teleport_", new TeleportPlaceholders(
                plugin.modules().teleport(), plugin.configs())::resolve);
        sections.put("afk_", new AfkPlaceholders(
                plugin.modules().extras().afk(), plugin.configs())::resolve);
        sections.put("leaderboard_", new LeaderboardPlaceholders(
                plugin.services().leaderboards(), plugin.configs(), plugin.modules().economy(), plugin.services().players())::resolve);

        GuiPlaceholders gui = new GuiPlaceholders(plugin.guis(), plugin.configs(), plugin.services().players());
        sections.put("gui_", (viewer, key) ->
                viewer != null && viewer.isOnline() ? gui.resolve(viewer.getPlayer(), key) : null);

        return Map.copyOf(sections);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "aircore";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public @NotNull String getRequiredPlugin() {
        return "AirCore";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String lower = params.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Section> section : sections.entrySet()) {
            String prefix = section.getKey();
            if (lower.startsWith(prefix)) {
                return section.getValue().resolve(player, params.substring(prefix.length()));
            }
        }
        return null;
    }
}