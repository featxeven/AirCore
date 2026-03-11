package com.ftxeven.aircore;

import com.ftxeven.aircore.api.AirCoreAPI;
import com.ftxeven.aircore.config.*;
import com.ftxeven.aircore.core.hook.HookManager;
import com.ftxeven.aircore.database.DatabaseManager;
import com.ftxeven.aircore.core.*;
import com.ftxeven.aircore.core.module.chat.ChatManager;
import com.ftxeven.aircore.core.module.economy.EconomyManager;
import com.ftxeven.aircore.core.module.home.HomeManager;
import com.ftxeven.aircore.core.module.kit.KitManager;
import com.ftxeven.aircore.core.module.teleport.TeleportManager;
import com.ftxeven.aircore.core.module.utility.UtilityManager;
import com.ftxeven.aircore.core.gui.GuiManager;
import com.ftxeven.aircore.util.*;
import org.bukkit.plugin.java.JavaPlugin;

public final class AirCore extends JavaPlugin {

    private ConfigManager configManager;
    private LangManager langManager;
    private AnnouncementManager announcementManager;
    private ChatManager chatManager;
    private EconomyManager economyManager;
    private TeleportManager teleportManager;
    private DatabaseManager databaseManager;
    private CoreManager coreManager;
    private HomeManager homeManager;
    private KitManager kitManager;
    private UtilityManager utilityManager;
    private GuiManager guiManager;
    private PlaceholderManager placeholderManager;
    private SchedulerUtil schedulerUtil;
    private CoreInitializer coreInitializer;
    private AirCoreAPI api;
    private HookManager hookManager;
    private String latestVersion = null;

    @Override
    public void onLoad() {
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        this.coreInitializer = new CoreInitializer(this);
        this.coreInitializer.initialize();

        getLogger().info("AirCore enabled @ featxeven");
    }

    @Override
    public void onDisable() {
        if (coreInitializer != null) {
            coreInitializer.shutdown();
        }
        getLogger().info("AirCore disabled @ featxeven");
    }

    public void setConfigManager(ConfigManager configManager) { this.configManager = configManager; }
    public void setLangManager(LangManager langManager) { this.langManager = langManager; }
    public void setAnnouncementManager(AnnouncementManager announcementManager) { this.announcementManager = announcementManager; }
    public void setChatManager(ChatManager chatManager) { this.chatManager = chatManager; }
    public void setEconomyManager(EconomyManager economyManager) { this.economyManager = economyManager; }
    public void setTeleportManager(TeleportManager teleportManager) { this.teleportManager = teleportManager; }
    public void setDatabaseManager(DatabaseManager databaseManager) { this.databaseManager = databaseManager; }
    public void setCoreManager(CoreManager coreManager) { this.coreManager = coreManager; }
    public void setHomeManager(HomeManager homeManager) { this.homeManager = homeManager; }
    public void setKitManager(KitManager kitManager) { this.kitManager = kitManager; }
    public void setUtilityManager(UtilityManager utilityManager) { this.utilityManager = utilityManager; }
    public void setGuiManager(GuiManager guiManager) { this.guiManager = guiManager; }
    public void setPlaceholderManager(PlaceholderManager placeholderManager) { this.placeholderManager = placeholderManager; }
    public void setSchedulerUtil(SchedulerUtil schedulerUtil) { this.schedulerUtil = schedulerUtil; }
    public void setApi(AirCoreAPI api) { this.api = api; }
    public void setLatestVersion(String latestVersion) { this.latestVersion = latestVersion; }
    public void setHookManager(HookManager hookManager) { this.hookManager = hookManager; }

    public ConfigManager config() { return configManager; }
    public LangManager lang() { return langManager; }
    public AnnouncementManager announcements() { return announcementManager; }
    public ChatManager chat() { return chatManager; }
    public EconomyManager economy() { return economyManager; }
    public TeleportManager teleport() { return teleportManager; }
    public DatabaseManager database() { return databaseManager; }
    public CoreManager core() { return coreManager; }
    public HomeManager home() { return homeManager; }
    public KitManager kit() { return kitManager; }
    public UtilityManager utility() { return utilityManager; }
    public GuiManager gui() { return guiManager; }
    public PlaceholderManager placeholders() { return placeholderManager; }
    public SchedulerUtil scheduler() { return schedulerUtil; }
    public AirCoreAPI api() { return api; }
    public String getLatestVersion() { return latestVersion; }
    public HookManager hooks() { return hookManager; }
}