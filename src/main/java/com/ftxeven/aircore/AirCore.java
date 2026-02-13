package com.ftxeven.aircore;

import com.ftxeven.aircore.config.*;
import com.ftxeven.aircore.database.DatabaseManager;
import com.ftxeven.aircore.core.*;
import com.ftxeven.aircore.core.chat.ChatManager;
import com.ftxeven.aircore.core.economy.EconomyManager;
import com.ftxeven.aircore.core.home.HomeManager;
import com.ftxeven.aircore.core.kit.KitManager;
import com.ftxeven.aircore.core.teleport.TeleportManager;
import com.ftxeven.aircore.core.utility.UtilityManager;
import com.ftxeven.aircore.core.gui.GuiManager;
import com.ftxeven.aircore.util.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AirCore extends JavaPlugin {
    private ConfigManager configManager;
    private LangManager langManager;
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
    private final Map<String, UUID> nameCache = new ConcurrentHashMap<>();
    private String latestVersion = null;

    @Override
    public void onLoad() {
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        this.schedulerUtil = new SchedulerUtil(this);
        this.configManager = new ConfigManager(this);
        this.langManager = new LangManager(this);

        logServerType();

        this.databaseManager = new DatabaseManager(this);
        if (!initDatabase()) return;

        initManagers();

        CoreInitializer registry = new CoreInitializer(this);
        registry.registerEconomy();
        registry.registerListeners();
        registry.registerCommands();
        registry.setupIntegrations();

        setupUtilities();
        checkUpdates();

        getLogger().info("Plugin enabled @ featxeven");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            database().inventories().saveAllSync(Bukkit.getOnlinePlayers());
            databaseManager.close();
        }
        getLogger().info("Plugin disabled @ featxeven");
    }

    private void initManagers() {
        this.placeholderManager = new PlaceholderManager(this);
        this.coreManager = new CoreManager(this, scheduler());
        this.chatManager = new ChatManager(this);
        this.economyManager = new EconomyManager(this);
        this.teleportManager = new TeleportManager(this);
        this.homeManager = new HomeManager(this);
        this.kitManager = new KitManager(this);
        this.utilityManager = new UtilityManager(this);
        this.guiManager = new GuiManager(this);
    }

    private boolean initDatabase() {
        try {
            databaseManager.init();
            return true;
        } catch (Exception e) {
            getLogger().severe("Failed to initialize database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    private void setupUtilities() {
        schedulerUtil.runTask(() -> MessageUtil.init(this));
        TitleUtil.init(schedulerUtil);
        SoundUtil.init(schedulerUtil);
        ActionbarUtil.init(schedulerUtil);
        BossbarUtil.init(schedulerUtil);
    }

    private void logServerType() {
        String type = schedulerUtil.isFoliaServer() ? "Folia (Region-based)" : "Paper/Spigot (Standard)";
        getLogger().info("Server is running " + type);
    }

    private void checkUpdates() {
        scheduler().runAsync(() -> {
            try (InputStream is = URI.create("https://api.spigotmc.org/legacy/update.php?resource=130425").toURL().openStream();
                 Scanner scanner = new Scanner(is)) {

                if (scanner.hasNext()) {
                    String latest = scanner.next();
                    String current = getPluginMeta().getVersion();

                    if (!current.equalsIgnoreCase(latest)) {
                        getLogger().warning("A new update is available!");
                        getLogger().warning("Current: " + current + " | Latest: " + latest);
                        getLogger().warning("Download: https://www.spigotmc.org/resources/130425/");

                        this.latestVersion = latest;
                    }
                }
            } catch (IOException e) {
                getLogger().warning("Could not check for updates: " + e.getMessage());
            }
        });
    }

    public ConfigManager config() { return configManager; }
    public LangManager lang() { return langManager; }
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
    public Map<String, UUID> getNameCache() { return nameCache; }
    public String getLatestVersion() { return latestVersion; }
}