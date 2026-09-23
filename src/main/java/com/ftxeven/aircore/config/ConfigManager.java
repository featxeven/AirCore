package com.ftxeven.aircore.config;

import com.ftxeven.aircore.module.announcements.AnnouncementsConfig;
import com.ftxeven.aircore.module.chat.ChatConfig;
import com.ftxeven.aircore.module.economy.EconomyConfig;
import com.ftxeven.aircore.module.economy.worth.WorthItemsConfig;
import com.ftxeven.aircore.module.economy.worth.WorthModifiersConfig;
import com.ftxeven.aircore.module.extras.ExtrasConfig;
import com.ftxeven.aircore.module.homes.HomesConfig;
import com.ftxeven.aircore.module.kits.KitsConfig;
import com.ftxeven.aircore.module.placeholders.PlaceholdersConfig;
import com.ftxeven.aircore.module.teleport.TeleportConfig;
import com.ftxeven.aircore.module.variables.VariablesConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConfigManager {

    private final JavaPlugin plugin;
    private final List<LoadableConfig> configs = new ArrayList<>();

    private final MainConfig main;
    private final StorageConfig storage;
    private final CommandsConfig commands;
    private final AnimationsConfig animations;
    private final LangConfig lang;
    private final FilterConfig filter;
    private final LeaderboardsConfig leaderboards;

    private final ChatConfig chat;
    private final EconomyConfig economy;
    private final WorthItemsConfig worthItems;
    private final WorthModifiersConfig worthModifiers;
    private final TeleportConfig teleport;
    private final HomesConfig homes;
    private final KitsConfig kits;
    private final ExtrasConfig extras;
    private final AnnouncementsConfig announcements;
    private final PlaceholdersConfig placeholders;
    private final VariablesConfig variables;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;

        main = register(new MainConfig(plugin));
        storage = register(new StorageConfig(plugin));
        commands = register(new CommandsConfig(plugin));
        animations = register(new AnimationsConfig(plugin));
        filter = register(new FilterConfig(plugin));
        leaderboards = register(new LeaderboardsConfig(plugin));
        lang = new LangConfig(plugin, animations);

        chat = register(new ChatConfig(plugin));
        economy = register(new EconomyConfig(plugin));
        worthItems = register(new WorthItemsConfig(plugin));
        worthModifiers = register(new WorthModifiersConfig(plugin));
        teleport = register(new TeleportConfig(plugin));
        homes = register(new HomesConfig(plugin));
        kits = register(new KitsConfig(plugin));
        extras = register(new ExtrasConfig(plugin));
        announcements = register(new AnnouncementsConfig(plugin));
        placeholders = register(new PlaceholdersConfig(plugin));
        variables = register(new VariablesConfig(plugin));
    }

    public boolean load() {
        boolean modulesFresh = new BundledDefaults(plugin).isFresh("modules", ".yml", Set.of());
        announcements.useSharedFreshness(modulesFresh);
        placeholders.useSharedFreshness(modulesFresh);
        variables.useSharedFreshness(modulesFresh);

        // Phase 1: parse everything
        Map<LoadableConfig, Runnable> prepared = new LinkedHashMap<>();
        boolean parsed = true;
        for (LoadableConfig config : configs) {
            try {
                prepared.put(config, config.prepare());
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load " + config.fileName() + ": " + e.getMessage());
                parsed = false;
            }
        }
        if (!parsed) {
            plugin.getLogger().severe("Config load aborted, nothing was applied");
            return false;
        }

        // Phase 2: swap in
        boolean ok = true;
        for (Map.Entry<LoadableConfig, Runnable> entry : prepared.entrySet()) {
            try {
                entry.getValue().run();
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to apply " + entry.getKey().fileName() + ": " + e.getMessage());
                ok = false;
            }
        }

        if (main.general() == null) {
            plugin.getLogger().severe("Skipping lang load, " + main.fileName() + " never loaded successfully");
            return false;
        }
        try {
            lang.load(main.general().lang(), main.general().itemsLang());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load lang files: " + e.getMessage());
            ok = false;
        }
        return ok;
    }

    public boolean reload() {
        return load();
    }

    public MainConfig main() { return main; }
    public StorageConfig storage() { return storage; }
    public CommandsConfig commands() { return commands; }
    public AnimationsConfig animations() { return animations; }
    public LangConfig lang() { return lang; }
    public FilterConfig filter() { return filter; }
    public LeaderboardsConfig leaderboards() { return leaderboards; }

    public ChatConfig chat() { return chat; }
    public EconomyConfig economy() { return economy; }
    public WorthItemsConfig worthItems() { return worthItems; }
    public WorthModifiersConfig worthModifiers() { return worthModifiers; }
    public TeleportConfig teleport() { return teleport; }
    public HomesConfig homes() { return homes; }
    public KitsConfig kits() { return kits; }
    public ExtrasConfig extras() { return extras; }
    public AnnouncementsConfig announcements() { return announcements; }
    public PlaceholdersConfig placeholders() { return placeholders; }
    public VariablesConfig variables() { return variables; }

    private <T extends LoadableConfig> T register(T config) {
        configs.add(config);
        return config;
    }
}