package com.ftxeven.aircore;

import com.ftxeven.aircore.api.AirCoreExpansion;
import com.ftxeven.aircore.module.core.economy.command.*;
import com.ftxeven.aircore.module.gui.GuiManager;
import com.ftxeven.aircore.module.core.utility.command.EnderchestCommand;
import com.ftxeven.aircore.module.core.utility.command.InvseeCommand;
import com.ftxeven.aircore.listener.*;
import com.ftxeven.aircore.module.core.CoreCommand;
import com.ftxeven.aircore.module.core.CoreManager;
import com.ftxeven.aircore.module.core.chat.command.*;
import com.ftxeven.aircore.module.core.home.HomeManager;
import com.ftxeven.aircore.module.core.home.command.DelHomeCommand;
import com.ftxeven.aircore.module.core.home.command.HomeCommand;
import com.ftxeven.aircore.module.core.home.command.SetHomeCommand;
import com.ftxeven.aircore.module.core.kit.KitManager;
import com.ftxeven.aircore.module.core.kit.command.CreateKitCommand;
import com.ftxeven.aircore.module.core.kit.command.DeleteKitCommand;
import com.ftxeven.aircore.module.core.kit.command.EditKitCommand;
import com.ftxeven.aircore.module.core.kit.command.KitCommand;
import com.ftxeven.aircore.module.core.teleport.TeleportManager;
import com.ftxeven.aircore.module.core.teleport.command.*;
import com.ftxeven.aircore.module.core.utility.UtilityManager;
import com.ftxeven.aircore.module.core.utility.command.*;
import com.ftxeven.aircore.module.core.chat.ChatManager;
import com.ftxeven.aircore.module.core.economy.EconomyManager;
import com.ftxeven.aircore.module.core.economy.service.EconomyProvider;
import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.config.LangManager;
import com.ftxeven.aircore.util.*;
import com.ftxeven.aircore.database.DatabaseManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

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
    private SchedulerUtil schedulerUtil;
    private final Map<String, UUID> nameCache = new ConcurrentHashMap<>();

    @Override
    public void onLoad() {
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        // Config
        this.configManager = new ConfigManager(this);
        this.langManager = new LangManager(this);
        this.schedulerUtil = new SchedulerUtil(this);

        // Log server type
        if (schedulerUtil.isFoliaServer()) {
            getLogger().info("Server is running Folia. All scheduling is region-based");
        } else {
            getLogger().info("Server is running Paper/Spigot. Using standard synchronous scheduler");
        }

        // Database
        this.databaseManager = new DatabaseManager(this);
        try {
            databaseManager.init();
        } catch (Exception e) {
            getLogger().severe("Failed to initialize database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Core managers
        this.coreManager = new CoreManager(this, scheduler());
        this.chatManager = new ChatManager(this);
        this.economyManager = new EconomyManager(this);
        this.teleportManager = new TeleportManager(this);
        this.homeManager = new HomeManager(this);
        this.kitManager = new KitManager(this);
        this.utilityManager = new UtilityManager(this);
        this.guiManager = new GuiManager(this);

        // Register economy provider
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            try {
                getServer().getServicesManager().register(
                        Economy.class,
                        new EconomyProvider(this, economyManager),
                        this,
                        ServicePriority.Highest
                );
            } catch (Throwable t) {
                getLogger().warning("Vault detected but could not register economy: " + t.getMessage());
            }
        } else {
            getLogger().warning("Vault not found, economy provider registration skipped");
        }

        // Unregister disabled commands
        unregisterDisabledCommands();

        // Register listeners
        List<Listener> listeners = new ArrayList<>();

        listeners.add(new ChatControlListener(this));
        listeners.add(new DeathMessageListener(this));
        listeners.add(new PlayerActivityListener(this, scheduler()));
        listeners.add(new PlayerLifecycleListener(this));
        listeners.add(new GuiListener(guiManager));

        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            listeners.add(new WorldGuardListener(this));
        }

        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }

        // Register commands
        registerCommand("msg", new MsgCommand(this));
        registerCommand("reply", new ReplyCommand(this));
        registerCommand("msgtoggle", new MsgToggleCommand(this));
        registerCommand("mentiontoggle", new MentionToggleCommand(this));
        registerCommand("chattoggle", new ChatToggleCommand(this));
        registerCommand("socialspy", new SocialSpyCommand(this));

        registerCommand("eco", new EcoCommand(this, economyManager));
        registerCommand("balance", new BalanceCommand(this, economyManager));
        registerCommand("pay", new PayCommand(this, economyManager));
        registerCommand("paytoggle", new PayToggleCommand(this));

        registerCommand("tpa", new TpaCommand(this, teleportManager));
        registerCommand("tpahere", new TpaHereCommand(this, teleportManager));
        registerCommand("tpaccept", new TpAcceptCommand(this, teleportManager));
        registerCommand("tpdeny", new TpDenyCommand(teleportManager));
        registerCommand("tptoggle", new TpToggleCommand(this));
        registerCommand("tp", new TpCommand(this));
        registerCommand("tphere", new TpHereCommand(this));
        registerCommand("tppos", new TpPosCommand(this));
        registerCommand("tpoffline", new TpOfflineCommand(this));

        registerCommand("sethome", new SetHomeCommand(this, homeManager));
        registerCommand("delhome", new DelHomeCommand(this, homeManager));
        registerCommand("home", new HomeCommand(this, homeManager));

        registerCommand("createkit", new CreateKitCommand(this, kitManager));
        registerCommand("delkit", new DeleteKitCommand(this, kitManager));
        registerCommand("editkit", new EditKitCommand(this, kitManager));
        registerCommand("kit", new KitCommand(this, kitManager));

        registerCommand("block", new BlockCommand(this));
        registerCommand("unblock", new UnblockCommand(this));
        registerCommand("spawn", new SpawnCommand(this, utilityManager));
        registerCommand("setspawn", new SetSpawnCommand(utilityManager));
        registerCommand("warp", new WarpCommand(this, utilityManager));
        registerCommand("setwarp", new SetWarpCommand(this, utilityManager));
        registerCommand("delwarp", new DelWarpCommand(this, utilityManager));
        registerCommand("clearinventory", new ClearInventoryCommand(this));
        registerCommand("feed", new FeedCommand(this));
        registerCommand("heal", new HealCommand(this));
        registerCommand("time", new TimeCommand(this));
        registerCommand("weather", new WeatherCommand(this));
        registerCommand("craftingtable", new CraftingTableCommand(this));
        registerCommand("anvil", new AnvilCommand(this));
        registerCommand("cartography", new CartographyCommand());
        registerCommand("loom", new LoomCommand(this));
        registerCommand("smithingtable", new SmithingTableCommand(this));
        registerCommand("stonecutter", new StonecutterCommand(this));
        registerCommand("grindstone", new GrindstoneCommand(this));
        registerCommand("gamemode", new GamemodeCommand(this));
        registerCommand("kill", new KillCommand(this));
        registerCommand("afk", new AfkCommand(this, utilityManager));
        registerCommand("god", new GodCommand(this));
        registerCommand("speed", new SpeedCommand(this));
        registerCommand("fly", new FlyCommand(this));
        registerCommand("repair", new RepairCommand(this));
        registerCommand("enderchest", new EnderchestCommand(this, guiManager));
        registerCommand("invsee", new InvseeCommand(this, guiManager));
        registerCommand("give", new GiveCommand(this));
        registerCommand("back", new BackCommand(this));
        registerCommand("sell", new SellCommand(this, guiManager));

        registerCommand("aircore", new CoreCommand(this));

        // Register PlaceholderAPI
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new AirCoreExpansion(this).register();
        } else {
            getLogger().warning("PlaceholderAPI not found, skipping placeholder integration");
        }

        // Initialize MessageUtil
        schedulerUtil.runTask(() -> MessageUtil.init(this));

        // Initialize utility helpers
        TitleUtil.init(schedulerUtil);
        SoundUtil.init(schedulerUtil);
        ActionbarUtil.init(schedulerUtil);
        BossbarUtil.init(schedulerUtil);

        getLogger().info("Plugin enabled @ featxeven");
    }

    @Override
    public void onDisable() {
        var invDB = database().inventories();
        invDB.saveAllSync(Bukkit.getOnlinePlayers());

        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("Plugin disabled @ featxeven");
    }

    private void unregisterDisabledCommands() {
        List<String> disabledCommands = configManager.disabledCommands();
        for (String commandName : disabledCommands) {
            unregisterCommand(commandName.toLowerCase());
        }
    }

    private void registerCommand(String name, CommandExecutor executor) {
        String lower = name.toLowerCase();
        if (configManager.disabledCommands().contains(lower)) {
            return;
        }

        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            return;
        }

        cmd.setExecutor(executor);

        var section = getConfig().getConfigurationSection("command-usages." + lower);
        if (section == null) {
            return;
        }

        String usage = section.getString("usage");
        if (usage != null) {
            cmd.setUsage(usage.replace("%label%", name));
        }

        List<String> aliases = section.getStringList("aliases");
        if (aliases.isEmpty()) {
            return;
        }

        aliases.removeIf(a -> a == null || a.isBlank());
        cmd.setAliases(aliases);

        try {
            Map<String, Command> knownCommands = getStringCommandMap();

            String pluginPrefix = getName().toLowerCase() + ":";

            for (String alias : aliases) {
                String key = alias.toLowerCase();
                knownCommands.put(key, cmd);
                knownCommands.put(pluginPrefix + key, cmd);
            }
        } catch (Exception ignored) {
        }
    }

    private Map<String, Command> getStringCommandMap() throws NoSuchFieldException, IllegalAccessException {
        Field commandMapField = getServer().getClass().getDeclaredField("commandMap");
        commandMapField.setAccessible(true);
        SimpleCommandMap commandMap = (SimpleCommandMap) commandMapField.get(getServer());

        Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
        knownCommandsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Command> knownCommands =
                (Map<String, Command>) knownCommandsField.get(commandMap);
        return knownCommands;
    }

    private void unregisterCommand(String commandName) {
        try {
            Field commandMapField = getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            SimpleCommandMap commandMap = (SimpleCommandMap) commandMapField.get(getServer());

            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

            PluginCommand command = getCommand(commandName);
            if (command != null) {
                knownCommands.remove(commandName);
                knownCommands.remove(getName().toLowerCase() + ":" + commandName);
                for (String alias : command.getAliases()) {
                    knownCommands.remove(alias.toLowerCase());
                    knownCommands.remove(getName().toLowerCase() + ":" + alias.toLowerCase());
                }
                command.unregister(commandMap);
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING,
                    "Failed to unregister command '" + commandName + "'", e);
        }
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
    public SchedulerUtil scheduler() { return schedulerUtil; }

    public Map<String, UUID> getNameCache() { return nameCache; }
}