package com.ftxeven.aircore.command.player.economy;

import com.ftxeven.aircore.command.AbstractCommand;
import com.ftxeven.aircore.database.repository.PlayerRepository;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.gui.action.GuiActions;
import com.ftxeven.aircore.module.economy.EconomyConfig;
import com.ftxeven.aircore.module.economy.EconomyModule;
import com.ftxeven.aircore.module.economy.format.AmountFormatter;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class BaltopCommand extends AbstractCommand {

    private static final String KEY = "baltop";

    private final Supplier<EconomyModule> economyModule;
    private final PluginGuiManager guis;

    public BaltopCommand(Context ctx, Supplier<EconomyModule> economyModule, PluginGuiManager guis) {
        super(ctx, KEY);
        this.economyModule = economyModule;
        this.guis = guis;
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public boolean playerOnly() { return true; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        completeCooldown(sender, args);

        Optional<String> guiId = resolveGui("menu", args);
        if (guiId.isPresent()) {
            guis.guis().open((Player) sender, guiId.get(), new HashMap<>());
            return;
        }

        listChat(sender);
    }

    private Optional<String> resolveGui(String key, String[] args) {
        return config().gui(key, args).filter(guiId -> GuiActions.guiEnabled(guis.guis(), guiId));
    }

    // Chat fallback

    private void listChat(CommandSender sender) {
        EconomyConfig.Baltop settings = configs().economy().baltop();
        List<PlayerRepository.BalanceEntry> top = services().players().topBalances(true, settings.maxEntries(), settings.minBalance());

        if (top.isEmpty()) {
            messenger().send(sender, configs().lang().get("economy.baltop.list.empty"));
            return;
        }

        String entryTemplate = configs().lang().get("economy.baltop.list.entry").getFirst();
        String separator = configs().lang().get("economy.baltop.list.separator").getFirst();
        AmountFormatter formatter = economyModule.get().formatter();

        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < top.size(); i++) {
            if (i > 0) {
                joined.append(separator);
            }
            PlayerRepository.BalanceEntry entry = top.get(i);

            Map<String, String> name = new LinkedHashMap<>();
            services().players().formatDisplayName(name, "player", entry.holder());

            joined.append(entryTemplate
                    .replace("%rank%", String.valueOf(i + 1))
                    .replace("%player%", name.get("player"))
                    .replace("%balance%", formatter.format(entry.balance())));
        }

        messenger().send(sender, configs().lang().get("economy.baltop.list.header"), Map.of(
                "count", String.valueOf(top.size()),
                "entries", joined.toString()
        ));
    }
}