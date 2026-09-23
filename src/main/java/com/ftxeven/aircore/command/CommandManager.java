package com.ftxeven.aircore.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.command.admin.AdminCommand;
import com.ftxeven.aircore.command.player.PlayerTargetResolver;
import com.ftxeven.aircore.command.player.Selectors;
import com.ftxeven.aircore.command.player.ToggleCommand;
import com.ftxeven.aircore.command.player.ToggleKey;
import com.ftxeven.aircore.command.player.chat.*;
import com.ftxeven.aircore.command.player.economy.*;
import com.ftxeven.aircore.command.player.extras.AfkCommand;
import com.ftxeven.aircore.command.player.extras.BlockCommand;
import com.ftxeven.aircore.command.player.extras.NicknameCommand;
import com.ftxeven.aircore.command.player.extras.UnblockCommand;
import com.ftxeven.aircore.command.player.kits.*;
import com.ftxeven.aircore.command.player.teleport.*;
import com.ftxeven.aircore.command.player.utilities.*;
import com.ftxeven.aircore.command.player.homes.*;
import com.ftxeven.aircore.core.command.AsyncTabCompleteListener;
import com.ftxeven.aircore.core.command.DurationUnits;
import com.ftxeven.aircore.core.command.DynamicCommandRegistry;
import com.ftxeven.aircore.core.command.Shortcuts;
import com.ftxeven.aircore.core.command.Shortcuts.Shortcut;
import com.ftxeven.aircore.core.command.tabcomplete.TabCompleteEngine;
import com.ftxeven.aircore.core.condition.ConditionEvaluator;
import org.bukkit.command.PluginCommand;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class CommandManager {

    private final AirCore plugin;

    private final CommandDispatcher dispatcher;
    private final DynamicCommandRegistry dynamicCommands;
    private final AbstractCommand.Context ctx;
    private final DurationUnits durationUnits;
    private final Map<String, CommandHandler> commandHandlersByName = new LinkedHashMap<>();

    public CommandManager(AirCore plugin) {
        this.plugin = plugin;

        this.dispatcher = new CommandDispatcher(plugin.messenger(), plugin.configs(), plugin.services());
        this.dynamicCommands = new DynamicCommandRegistry(plugin);
        this.durationUnits = new DurationUnits(() -> plugin.configs().commands().durationUnits());

        PlayerTargetResolver resolver = new PlayerTargetResolver(plugin.services().players(), plugin.configs()::main);
        Selectors selectors = new Selectors(() -> plugin.configs().commands().selectors());
        TabCompleteEngine tabCompleteEngine = buildTabCompleteEngine(resolver, selectors);
        Feedback feedback = new Feedback(plugin.configs(), plugin.messenger(), plugin.services());
        Scopes scopes = new Scopes(plugin.configs(), plugin.messenger(), plugin.services(), resolver, selectors, feedback);

        this.ctx = new AbstractCommand.Context(plugin.configs(), plugin.messenger(), plugin.services(),
                plugin.modules(), resolver, tabCompleteEngine, selectors, feedback, scopes);
    }

    public void registerAll() {
        registerAdminCommand();
        registerPlayerCommands();
        registerShortcuts();
        registerAsyncTabComplete();
    }

    private void registerAdminCommand() {
        PluginCommand command = plugin.getCommand("aircore");

        AdminCommand executor = new AdminCommand(plugin);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerPlayerCommands() {
        // Chat commands
        register(new ToggleCommand(ctx, ToggleKey.MSG));
        register(new ToggleCommand(ctx, ToggleKey.SOCIAL_SPY));
        register(new ToggleCommand(ctx, ToggleKey.CHAT));
        register(new ToggleCommand(ctx, ToggleKey.MENTIONS));
        register(new ToggleCommand(ctx, ToggleKey.ANNOUNCEMENTS));
        register(new ChannelCommand(ctx, plugin.modules()::chat));
        register(new MsgCommand(ctx, plugin.modules()::chat));
        register(new ReplyCommand(ctx, plugin.modules()::chat));
        register(new BroadcastCommand(ctx));

        // Economy commands
        register(new BalanceCommand(ctx, plugin.modules()::economy));
        register(new BaltopCommand(ctx, plugin.modules()::economy, plugin.guis()));
        register(new PayCommand(ctx, plugin.modules()::economy, plugin.guis()));
        register(new ToggleCommand(ctx, ToggleKey.PAY));
        register(new ToggleCommand(ctx, ToggleKey.PAY_CONFIRM));
        register(new EconomyCommand(ctx, plugin.modules()::economy));
        register(new SellCommand(ctx, plugin.modules()::economy, plugin.guis()));

        // Teleport commands
        register(new ToggleCommand(ctx, ToggleKey.TP));
        register(new ToggleCommand(ctx, ToggleKey.TP_CONFIRM));
        register(new ToggleCommand(ctx, ToggleKey.TP_AUTO_ACCEPT));
        register(new TpCommand(ctx, plugin.modules()::teleport));
        register(new TpposCommand(ctx, plugin.modules()::teleport));
        register(new TphereCommand(ctx, plugin.modules()::teleport));
        register(new TpofflineCommand(ctx, plugin.modules()::teleport));
        register(new TpaCommand(ctx, plugin.modules()::teleport, plugin.guis()));
        register(new TpahereCommand(ctx, plugin.modules()::teleport, plugin.guis()));
        register(new TpacceptCommand(ctx, plugin.modules()::teleport));
        register(new TpdenyCommand(ctx, plugin.modules()::teleport));
        register(new TpcancelCommand(ctx, plugin.modules()::teleport));
        register(new BackCommand(ctx, plugin.modules()::teleport, plugin.guis()));
        register(new SpawnCommand(ctx, plugin.modules()::teleport));
        register(new SetspawnCommand(ctx, plugin.modules()::teleport));
        register(new DeletespawnCommand(ctx, plugin.modules()::teleport));
        register(new WarpCommand(ctx, plugin.modules()::teleport, plugin.guis()));
        register(new SetwarpCommand(ctx, plugin.modules()::teleport));
        register(new DeletewarpCommand(ctx, plugin.modules()::teleport));

        // Kit commands
        register(new KitCommand(ctx, plugin.modules()::kits, plugin.guis()));
        register(new CreatekitCommand(ctx, plugin.modules()::kits));
        register(new EditkitCommand(ctx, plugin.modules()::kits));
        register(new DeletekitCommand(ctx, plugin.modules()::kits));

        // Home commands
        HomeCommand homeCommand = new HomeCommand(ctx, plugin.modules()::homes, plugin.modules()::teleport, plugin.guis());
        register(homeCommand);
        register(new SethomeCommand(ctx, plugin.modules()::homes));
        register(new DeletehomeCommand(ctx, plugin.modules()::homes, plugin.guis()));
        register(new PlayerhomeCommand(ctx, plugin.modules()::homes, plugin.modules()::teleport, plugin.guis(), homeCommand));

        // Extras commands
        register(new AfkCommand(ctx));
        register(new BlockCommand(ctx));
        register(new UnblockCommand(ctx));
        register(new NicknameCommand(ctx));

        // Utility commands
        register(new HealCommand(ctx));
        register(new FeedCommand(ctx));
        register(new FlyCommand(ctx));
        register(new GodCommand(ctx));
        register(new SpeedCommand(ctx));
        register(new GamemodeCommand(ctx));
        register(new TimeCommand(ctx, durationUnits));
        register(new PlayertimeCommand(ctx, durationUnits));
        register(new WeatherCommand(ctx));
        register(new PlayerweatherCommand(ctx));
        register(new RepairCommand(ctx));
        register(new RepairallCommand(ctx));
        register(new ClearinventoryCommand(ctx));
        register(new SudoCommand(ctx));
        register(new SeenCommand(ctx));
        register(new KillCommand(ctx));
        register(new PingCommand(ctx));
        register(new InvseeCommand(ctx, plugin.guis()));
        register(new EnderseeCommand(ctx, plugin.guis()));
        register(new EnderchestCommand(ctx, plugin.guis()));
        register(new DisposalCommand(ctx, plugin.guis()));
        for (VirtualWorkstation workstation : VirtualWorkstation.values()) {
            register(new VirtualWorkstationCommand(ctx, workstation));
        }
    }

    private void register(CommandHandler command) {
        if (!command.enabled()) {
            return;
        }
        dynamicCommands.register(command.name(), command.aliases(), command.permission(), dispatcher.asExecutor(command));
        commandHandlersByName.put(command.name().toLowerCase(Locale.ROOT), command);
    }

    private void registerShortcuts() {
        for (Map.Entry<String, Shortcut> entry : plugin.configs().commands().shortcuts().entrySet()) {
            String shortcutKey = entry.getKey();
            Shortcuts.Target target = Shortcuts.target(entry.getValue());

            CommandHandler targetCommand = commandHandlersByName.get(target.command().toLowerCase(Locale.ROOT));
            if (targetCommand == null) {
                plugin.getLogger().warning("Shortcut '" + shortcutKey + "' in commands.yml points at '"
                        + target.command() + "', which isn't a registered command - skipping it");
                continue;
            }

            dynamicCommands.register(shortcutKey, entry.getValue().aliases(), targetCommand.permission(),
                    dispatcher.asExecutor(targetCommand), typedArgs -> Shortcuts.merge(target.args(), typedArgs));
        }
    }

    private void registerAsyncTabComplete() {
        new AsyncTabCompleteListener(plugin, dynamicCommands).register();
    }

    private TabCompleteEngine buildTabCompleteEngine(PlayerTargetResolver resolver, Selectors selectors) {
        var sources = TabSources.build(plugin.configs(), durationUnits, selectors, resolver,
                plugin.modules()::extras, plugin.modules()::kits, plugin.modules()::teleport,
                plugin.modules()::homes, plugin.getLogger()::warning);
        var conditions = new ConditionEvaluator(plugin.getLogger()::warning);
        return new TabCompleteEngine(sources, conditions);
    }
}