package com.ftxeven.aircore;

import com.ftxeven.aircore.api.papi.AirCoreExpansion;
import com.ftxeven.aircore.command.CommandManager;
import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.core.animation.AnimationManager;
import com.ftxeven.aircore.core.hook.HookRegistry;
import com.ftxeven.aircore.database.DatabaseManager;
import com.ftxeven.aircore.database.cache.CacheManager;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.listener.GuiListener;
import com.ftxeven.aircore.listener.PlayerListener;
import com.ftxeven.aircore.module.ModuleManager;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.Placeholders;
import com.ftxeven.aircore.util.Scheduler;
import com.ftxeven.aircore.util.Version;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class AirCore extends JavaPlugin {

    private ConfigManager configs;
    private DatabaseManager database;
    private CacheManager cache;
    private HookRegistry hooks;
    private AnimationManager animations;
    private Messenger messenger;
    private ServiceManager services;
    private ModuleManager modules;
    private PluginGuiManager guis;
    private CommandManager commands;
    private AirCoreExpansion placeholders;
    private Metrics metrics;

    @Override
    public void onEnable() {
        getLogger().info("Running on " + Bukkit.getName() + " - " + Bukkit.getVersion());

        configs = new ConfigManager(this);
        if (!configs.load()) {
            getLogger().severe("One or more config files failed to load, disabling plugin");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        database = new DatabaseManager(this, configs);
        if (!database.connect()) {
            getLogger().severe("Failed to connect to the database, disabling plugin");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        cache = new CacheManager(database, getLogger());
        cache.startMaintenance(getLogger());

        hooks = new HookRegistry(this);

        animations = new AnimationManager(this, configs.animations()::animations);
        animations.start();

        messenger = new Messenger(getLogger(), animations);

        services = new ServiceManager(this);

        modules = new ModuleManager(this);

        guis = PluginGuiManager.create(this, messenger, configs, services, modules, animations);
        if (guis == null) {
            getLogger().severe("One or more GUI files failed to load, disabling plugin");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!modules.load()) {
            getLogger().severe("One or more modules failed to load, disabling plugin");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        services.load();

        getServer().getPluginManager().registerEvents(
                new PlayerListener(services, configs, messenger, modules, guis), this);
        getServer().getPluginManager().registerEvents(
                new GuiListener(guis.guis()), this);

        commands = new CommandManager(this);
        commands.registerAll();

        if (Placeholders.papiEnabled()) {
            placeholders = new AirCoreExpansion(this);
            placeholders.register();
        }

        metrics = new Metrics(this, 33556);

        Version.check();
    }

    @Override
    public void onDisable() {
        if (placeholders != null) {
            placeholders.unregister();
        }
        if (guis != null) {
            guis.shutdown();
        }
        if (modules != null) {
            modules.stop();
        }
        if (cache != null) {
            cache.close();
        }

        Scheduler.cancelGlobal();
        Scheduler.cancelAsync();

        if (animations != null) {
            animations.stop();
        }
        if (database != null) {
            database.close();
        }
    }

    public ConfigManager configs() { return configs; }

    public DatabaseManager database() { return database; }

    public CacheManager cache() { return cache; }

    public HookRegistry hooks() {
        return hooks;
    }

    public Messenger messenger() { return messenger; }

    public ServiceManager services() { return services; }

    public PluginGuiManager guis() { return guis; }

    public ModuleManager modules() { return modules; }

    public CommandManager commands() { return commands; }

    public Metrics metrics() { return metrics; }
}