package com.ftxeven.aircore.core;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.api.AirCoreExpansion;
import com.ftxeven.aircore.listener.*;
import com.ftxeven.aircore.core.chat.command.*;
import com.ftxeven.aircore.core.economy.command.*;
import com.ftxeven.aircore.core.economy.service.EconomyProvider;
import com.ftxeven.aircore.core.home.command.*;
import com.ftxeven.aircore.core.kit.command.*;
import com.ftxeven.aircore.core.teleport.command.*;
import com.ftxeven.aircore.core.utility.command.*;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.*;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class CoreInitializer {

    private final AirCore plugin;

    public CoreInitializer(AirCore plugin) {
        this.plugin = plugin;
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
            } catch (Throwable t) {
                plugin.getLogger().warning("Vault detected but failed to register economy: " + t.getMessage());
            }
        } else {
            plugin.getLogger().warning("Vault not found, economy provider registration skipped");
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
        reg("craftingtable", new CraftingTableCommand(plugin));
        reg("anvil", new AnvilCommand(plugin));
        reg("cartography", new CartographyCommand(plugin));
        reg("loom", new LoomCommand(plugin));
        reg("smithingtable", new SmithingTableCommand(plugin));
        reg("stonecutter", new StonecutterCommand(plugin));
        reg("grindstone", new GrindstoneCommand(plugin));
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

        reg("aircore", new CoreCommand(plugin));

        unregisterDisabledCommands();
    }

    public void setupIntegrations() {
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new AirCoreExpansion(plugin).register();
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
                PluginCommand command = plugin.getCommand(cmdName.toLowerCase());

                if (command != null) {
                    knownCommands.remove(cmdName.toLowerCase());
                    knownCommands.remove(plugin.getName().toLowerCase() + ":" + cmdName.toLowerCase());
                    for (String alias : command.getAliases()) {
                        knownCommands.remove(alias.toLowerCase());
                    }
                    command.unregister((CommandMap) getCommandMapField().get(plugin.getServer()));
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to unregister: " + cmdName, e);
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