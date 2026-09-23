package com.ftxeven.aircore.module;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.module.announcements.AnnouncementsModule;
import com.ftxeven.aircore.module.chat.ChatModule;
import com.ftxeven.aircore.module.economy.EconomyModule;
import com.ftxeven.aircore.module.extras.ExtrasModule;
import com.ftxeven.aircore.module.GroupResolver.VaultGroups;
import com.ftxeven.aircore.module.homes.HomesModule;
import com.ftxeven.aircore.module.kits.KitsModule;
import com.ftxeven.aircore.module.placeholders.PlaceholdersModule;
import com.ftxeven.aircore.module.teleport.TeleportModule;
import com.ftxeven.aircore.module.variables.VariablesModule;
import com.ftxeven.aircore.util.Scheduler;

import java.util.logging.Level;

public final class ModuleManager {

    private final AirCore plugin;

    private GroupResolver groups;
    private PlaceholdersModule placeholders;
    private ChatModule chat;
    private AnnouncementsModule announcements;
    private VariablesModule variables;
    private ExtrasModule extras;
    private KitsModule kits;
    private HomesModule homes;
    private EconomyModule economy;
    private TeleportModule teleport;

    public ModuleManager(AirCore plugin) {
        this.plugin = plugin;
    }

    public boolean load() {
        groups = GroupResolver.create();
        if (groups instanceof VaultGroups vault) {
            Scheduler.runGlobalLater(() -> {
                if (!vault.isHooked()) {
                    plugin.getLogger().warning("Vault is present but no permission plugin ever registered with it, "
                            + "group-based features will use _DEFAULT_ for everyone");
                }
            }, 100L);
        }

        return loadModule("placeholders", () -> placeholders = new PlaceholdersModule(plugin, plugin.configs().placeholders()))
                && loadModule("extras", () -> extras = new ExtrasModule(plugin, plugin.configs(), plugin.messenger(), groups, plugin.cache(), plugin.services()))
                && loadModule("kits", () -> kits = new KitsModule(plugin, plugin.configs(), plugin.cache(), plugin.services()))
                && loadModule("chat", () -> chat = new ChatModule(plugin, plugin.configs(), plugin.messenger(), groups, plugin.services(), extras, plugin.guis()))
                && loadModule("homes", () -> homes = new HomesModule(plugin.configs(), plugin.cache().homes(), () -> chat))
                && loadModule("economy", () -> economy = new EconomyModule(plugin, plugin.configs(), plugin.messenger(), plugin.services(), extras, plugin.hooks()))
                && loadModule("teleport", () -> teleport = new TeleportModule(plugin.configs(), plugin.cache().locations(), plugin.services(), extras, groups, plugin.messenger()))
                && loadModule("announcements", () -> {
            announcements = new AnnouncementsModule(plugin, plugin.configs(), plugin.messenger(), plugin.database().persistentBossbar(), plugin.services());
            announcements.start();
        })
                && loadModule("variables", () -> {
            variables = new VariablesModule(plugin, plugin.configs(), plugin.messenger(), plugin.cache().variables(), announcements);
            variables.start();
        });
    }

    @FunctionalInterface
    private interface ModuleLoader {
        void load() throws Exception;
    }

    private boolean loadModule(String moduleName, ModuleLoader loader) {
        try {
            loader.load();
            return true;
        } catch (Exception | LinkageError e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load the " + moduleName + " module, aborting startup", e);
            return false;
        }
    }

    public GroupResolver groups() { return groups; }
    public PlaceholdersModule placeholders() { return placeholders; }
    public ChatModule chat() { return chat; }
    public ExtrasModule extras() { return extras; }
    public AnnouncementsModule announcements() { return announcements; }
    public VariablesModule variables() { return variables; }
    public KitsModule kits() { return kits; }
    public HomesModule homes() { return homes; }
    public EconomyModule economy() { return economy; }
    public TeleportModule teleport() { return teleport; }

    public void reload() {
        if (placeholders != null) {
            placeholders.invalidateCache();
        }
        if (chat != null) {
            chat.reload();
        }
        if (announcements != null) {
            announcements.reload();
        }
        if (variables != null) {
            variables.reload();
        }
        if (economy != null) {
            economy.reload();
        }
    }

    public void stop() {
        if (announcements != null) {
            announcements.stop();
        }
        if (variables != null) {
            variables.stop();
        }
        if (economy != null) {
            economy.stop();
        }
    }
}