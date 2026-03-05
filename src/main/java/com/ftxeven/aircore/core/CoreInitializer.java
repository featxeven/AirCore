package com.ftxeven.aircore.core;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.api.AirCoreAPI;
import com.ftxeven.aircore.core.api.DefaultAirCoreAPI;
import com.ftxeven.aircore.api.AirCorePAPIExpansion;
import com.ftxeven.aircore.api.AirCoreProvider;
import com.ftxeven.aircore.config.AnnouncementManager;
import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.config.LangManager;
import com.ftxeven.aircore.config.PlaceholderManager;
import com.ftxeven.aircore.core.modules.chat.ChatManager;
import com.ftxeven.aircore.core.modules.economy.EconomyManager;
import com.ftxeven.aircore.core.modules.gui.GuiManager;
import com.ftxeven.aircore.core.modules.home.HomeManager;
import com.ftxeven.aircore.core.modules.kit.KitManager;
import com.ftxeven.aircore.core.modules.teleport.TeleportManager;
import com.ftxeven.aircore.core.modules.utility.UtilityManager;
import com.ftxeven.aircore.database.DatabaseManager;
import com.ftxeven.aircore.database.dao.PlayerInventories;
import com.ftxeven.aircore.listener.*;
import com.ftxeven.aircore.core.modules.chat.command.*;
import com.ftxeven.aircore.core.modules.economy.command.*;
import com.ftxeven.aircore.core.modules.economy.service.EconomyProvider;
import com.ftxeven.aircore.core.modules.home.command.*;
import com.ftxeven.aircore.core.modules.kit.command.*;
import com.ftxeven.aircore.core.modules.teleport.command.*;
import com.ftxeven.aircore.core.modules.utility.command.*;
import com.ftxeven.aircore.util.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class CoreInitializer {

    private final AirCore plugin;

    public CoreInitializer(AirCore plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        plugin.setSchedulerUtil(new SchedulerUtil(plugin));
        logServerType();

        plugin.setConfigManager(new ConfigManager(plugin));
        plugin.setLangManager(new LangManager(plugin));

        DatabaseManager db = new DatabaseManager(plugin);
        try {
            db.init();
            plugin.setDatabaseManager(db);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }

        initManagers();

        registerAPI();

        registerEconomy();
        registerListeners();
        registerCommands();
        setupUtilities();
        setupIntegrations();

        checkUpdates();
    }

    private void registerAPI() {
        try {
            AirCoreAPI implementation = new DefaultAirCoreAPI(plugin);
            plugin.setApi(implementation);
            AirCoreProvider.register(implementation);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Failed to register API:", t);
        }
    }

    private void logServerType() {
        String type = plugin.scheduler().isFoliaServer() ? "Folia (Region-based)" : "Paper/Spigot (Standard)";
        plugin.getLogger().info("Server is running " + type);
    }

    public void registerEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") != null) {
            try {
                plugin.getServer().getServicesManager().register(
                        Economy.class,
                        new EconomyProvider(plugin, plugin.economy()),
                        plugin,
                        ServicePriority.Highest
                );
                plugin.getLogger().info("Successfully hooked into Vault");
            } catch (Throwable t) {
                plugin.getLogger().warning("Vault detected but failed to register economy provider: " + t.getMessage());
            }
        } else {
            plugin.getLogger().warning("Vault not found, economy provider registration skipped.");
        }
    }

    private void initManagers() {
        plugin.setPlaceholderManager(new PlaceholderManager(plugin));
        plugin.setCoreManager(new CoreManager(plugin, plugin.scheduler()));
        plugin.setAnnouncementManager(new AnnouncementManager(plugin));
        plugin.setChatManager(new ChatManager(plugin));
        plugin.setEconomyManager(new EconomyManager(plugin));
        plugin.setTeleportManager(new TeleportManager(plugin));
        plugin.setHomeManager(new HomeManager(plugin));
        plugin.setKitManager(new KitManager(plugin));
        plugin.setUtilityManager(new UtilityManager(plugin));
        plugin.setGuiManager(new GuiManager(plugin));
    }

    private void setupUtilities() {
        plugin.scheduler().runTask(() -> MessageUtil.init(plugin));
        TitleUtil.init(plugin.scheduler());
        SoundUtil.init(plugin.scheduler());
        ActionbarUtil.init(plugin.scheduler());
        BossbarUtil.init(plugin.scheduler());
    }

    public void shutdown() {
        AirCoreProvider.unregister();
        
        if (plugin.announcements() != null) plugin.announcements().shutdown();
        BossbarUtil.hideAll();
        if (plugin.scheduler() != null) plugin.scheduler().cancelAll();

        handleDataShutdown();

        if (plugin.database() != null) {
            plugin.database().close();
        }
    }

    private void handleDataShutdown() {
        if (plugin.database() == null) return;
        Map<UUID, PlayerInventories.InventorySnapshot> shutdownSnapshots = new HashMap<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                shutdownSnapshots.put(player.getUniqueId(), plugin.database().inventories().createSnapshot(player));
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to snapshot " + player.getName() + " during shutdown!");
            }
        }

        if (!shutdownSnapshots.isEmpty()) {
            plugin.getLogger().info("Saving " + shutdownSnapshots.size() + " player inventories...");
            plugin.database().inventories().saveAllSync(shutdownSnapshots);
        }
    }

    public void registerListeners() {
        PluginManager pm = plugin.getServer().getPluginManager();

        pm.registerEvents(new ChatControlListener(plugin), plugin);
        pm.registerEvents(new DeathMessageListener(plugin), plugin);
        pm.registerEvents(new PlayerActivityListener(plugin, plugin.scheduler()), plugin);
        pm.registerEvents(new PlayerLifecycleListener(plugin), plugin);
        pm.registerEvents(new GuiListener(plugin.gui()), plugin);

        if (pm.getPlugin("WorldGuard") != null) {
            pm.registerEvents(new WorldGuardListener(plugin), plugin);
        }
    }

    private void checkUpdates() {
        plugin.scheduler().runAsync(() -> {
            try (InputStream is = URI.create("https://api.spiget.org/v2/resources/130425/versions/latest").toURL().openStream();
                 InputStreamReader reader = new InputStreamReader(is)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("name")) {
                    String latest = json.get("name").getAsString();
                    String current = plugin.getPluginMeta().getVersion();
                    if (!current.equalsIgnoreCase(latest)) {
                        plugin.setLatestVersion(latest);
                        plugin.getLogger().warning("A new update is available! Current: " + current + " | Latest: " + latest);
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    public void registerCommands() {
        reg("msg", new MsgCommand(plugin));
        reg("reply", new ReplyCommand(plugin));
        reg("msgtoggle", new MsgToggleCommand(plugin));
        reg("mentiontoggle", new MentionToggleCommand(plugin));
        reg("chattoggle", new ChatToggleCommand(plugin));
        reg("socialspy", new SocialSpyCommand(plugin));

        reg("eco", new EcoCommand(plugin));
        reg("balance", new BalanceCommand(plugin));
        reg("pay", new PayCommand(plugin));
        reg("paytoggle", new PayToggleCommand(plugin));
        reg("sell", new SellCommand(plugin, plugin.gui()));

        reg("tpa", new TpaCommand(plugin));
        reg("tpahere", new TpaHereCommand(plugin));
        reg("tpaccept", new TpAcceptCommand(plugin));
        reg("tpdeny", new TpDenyCommand(plugin));
        reg("tptoggle", new TpToggleCommand(plugin));
        reg("tp", new TpCommand(plugin));
        reg("tphere", new TpHereCommand(plugin));
        reg("tppos", new TpPosCommand(plugin));
        reg("tpoffline", new TpOfflineCommand(plugin));

        reg("sethome", new SetHomeCommand(plugin));
        reg("delhome", new DelHomeCommand(plugin));
        reg("home", new HomeCommand(plugin, plugin.gui()));

        reg("createkit", new CreateKitCommand(plugin));
        reg("delkit", new DeleteKitCommand(plugin));
        reg("editkit", new EditKitCommand(plugin));
        reg("kit", new KitCommand(plugin));

        reg("block", new BlockCommand(plugin));
        reg("unblock", new UnblockCommand(plugin));
        reg("spawn", new SpawnCommand(plugin));
        reg("setspawn", new SetSpawnCommand(plugin));
        reg("warp", new WarpCommand(plugin));
        reg("setwarp", new SetWarpCommand(plugin));
        reg("delwarp", new DelWarpCommand(plugin));
        reg("clearinventory", new ClearInventoryCommand(plugin));
        reg("feed", new FeedCommand(plugin));
        reg("heal", new HealCommand(plugin));
        reg("time", new TimeCommand(plugin));
        reg("weather", new WeatherCommand(plugin));
        reg("craftingtable", new VirtualGuiCommand(plugin, "craftingtable", p -> p.openWorkbench(null, true)));
        reg("anvil", new VirtualGuiCommand(plugin, "anvil", p -> p.openAnvil(null, true)));
        reg("cartography", new VirtualGuiCommand(plugin, "cartography", p -> p.openCartographyTable(null, true)));
        reg("loom", new VirtualGuiCommand(plugin, "loom", p -> p.openLoom(null, true)));
        reg("smithingtable", new VirtualGuiCommand(plugin, "smithingtable", p -> p.openSmithingTable(null, true)));
        reg("stonecutter", new VirtualGuiCommand(plugin, "stonecutter", p -> p.openStonecutter(null, true)));
        reg("grindstone", new VirtualGuiCommand(plugin, "grindstone", p -> p.openGrindstone(null, true)));
        reg("gamemode", new GamemodeCommand(plugin));
        reg("kill", new KillCommand(plugin));
        reg("afk", new AfkCommand(plugin));
        reg("god", new GodCommand(plugin));
        reg("speed", new SpeedCommand(plugin));
        reg("fly", new FlyCommand(plugin));
        reg("repair", new RepairCommand(plugin));
        reg("enderchest", new EnderchestCommand(plugin, plugin.gui()));
        reg("invsee", new InvseeCommand(plugin, plugin.gui()));
        reg("give", new GiveCommand(plugin));
        reg("back", new BackCommand(plugin));
        reg("announcement", new AnnouncementCommand(plugin));
        reg("announcetoggle", new AnnounceToggleCommand(plugin));

        reg("aircore", new CoreCommand(plugin));

        unregisterDisabledCommands();
    }

    public void setupIntegrations() {
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new AirCorePAPIExpansion(plugin).register();
        }
    }

    private void reg(String name, CommandExecutor executor) {
        String lower = name.toLowerCase();
        if (plugin.config().disabledCommands().contains(lower)) return;

        PluginCommand cmd = plugin.getCommand(name);
        if (cmd == null) return;

        cmd.setExecutor(executor);

        var section = plugin.getConfig().getConfigurationSection("command-usages." + lower);
        if (section == null) return;

        String usage = section.getString("usage");
        if (usage != null) {
            cmd.setUsage(usage.replace("%label%", name));
        }

        List<String> aliases = section.getStringList("aliases");
        if (aliases.isEmpty()) return;

        aliases.removeIf(a -> a == null || a.isBlank());
        cmd.setAliases(aliases);

        try {
            Map<String, Command> knownCommands = getKnownCommands();
            String pluginPrefix = plugin.getName().toLowerCase() + ":";

            for (String alias : aliases) {
                String key = alias.toLowerCase();
                knownCommands.put(key, cmd);
                knownCommands.put(pluginPrefix + key, cmd);
            }
        } catch (Exception ignored) {}
    }

    private void unregisterDisabledCommands() {
        List<String> disabled = plugin.config().disabledCommands();
        for (String cmdName : disabled) {
            try {
                Map<String, Command> knownCommands = getKnownCommands();
                String label = cmdName.toLowerCase();

                knownCommands.remove(plugin.getName().toLowerCase() + ":" + label);
                Command vanillaCmd = knownCommands.get("minecraft:" + label);

                if (vanillaCmd != null) {
                    knownCommands.put(label, vanillaCmd);
                } else {
                    knownCommands.remove(label);
                }

                PluginCommand pluginCmd = plugin.getCommand(label);
                if (pluginCmd != null) {
                    for (String alias : pluginCmd.getAliases()) {
                        knownCommands.remove(alias.toLowerCase());
                        knownCommands.remove(plugin.getName().toLowerCase() + ":" + alias.toLowerCase());
                    }
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to restore vanilla command for: " + cmdName, e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Command> getKnownCommands() throws Exception {
        SimpleCommandMap commandMap = (SimpleCommandMap) getCommandMapField().get(plugin.getServer());
        Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
        knownCommandsField.setAccessible(true);
        return (Map<String, Command>) knownCommandsField.get(commandMap);
    }

    private Field getCommandMapField() throws NoSuchFieldException {
        Field field = plugin.getServer().getClass().getDeclaredField("commandMap");
        field.setAccessible(true);
        return field;
    }
}