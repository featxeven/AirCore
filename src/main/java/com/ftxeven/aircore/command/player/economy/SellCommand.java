package com.ftxeven.aircore.command.player.economy;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.core.gui.OpenOptions;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.gui.action.GuiActions;
import com.ftxeven.aircore.gui.render.GuiFlags;
import com.ftxeven.aircore.module.economy.EconomyModule;
import com.ftxeven.aircore.module.economy.sell.SellHandler;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class SellCommand extends BaseCommand {

    private static final String KEY = "sell";

    private final Supplier<EconomyModule> economyModule;
    private final PluginGuiManager guis;

    public SellCommand(Context ctx, Supplier<EconomyModule> economyModule, PluginGuiManager guis) {
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
        Player player = (Player) sender;

        Optional<String> menuGui = resolveGui("menu", args);
        if (menuGui.isPresent()) {
            guis.guis().open(player, menuGui.get(), new LinkedHashMap<>(),
                    new OpenOptions(GuiFlags.builder(player).build(), Map.of(), OpenOptions.Kind.ENTRY, List.of()));
            return;
        }

        applySale(player, args);
    }

    private Optional<String> resolveGui(String key, String[] args) {
        return config().gui(key, args).filter(guiId -> GuiActions.guiEnabled(guis.guis(), guiId));
    }

    // Applying

    private void applySale(Player player, String[] args) {
        respond(player, economyModule.get().sell().sellHand(player), args);
    }

    private void respond(Player player, SellHandler.Verdict verdict, String[] args) {
        switch (verdict) {
            case SellHandler.Verdict.Sold sold -> {
                completeCooldown(player, args);
                Map<String, String> placeholders = new LinkedHashMap<>();
                economyModule.get().formatter().formatInto(placeholders, "amount", sold.amount());
                economyModule.get().formatter().formatOptionalInto(placeholders, "tax", sold.tax(), "placeholders.empty.tax");
                messenger().send(player, configs().lang().get("economy.sell.success"), placeholders);
            }
            case SellHandler.Verdict.Nothing ignored ->
                    messenger().send(player, configs().lang().get("economy.sell.errors.nothing"));
            case SellHandler.Verdict.Invalid ignored ->
                    messenger().send(player, configs().lang().get("economy.sell.errors.invalid"));
            case SellHandler.Verdict.ExceedsMax exceedsMax -> {
                Map<String, String> placeholders = new LinkedHashMap<>();
                economyModule.get().formatter().formatInto(placeholders, "limit", exceedsMax.limit());
                messenger().send(player, configs().lang().get("economy.sell.errors.exceed-max"), placeholders);
            }
        }
    }
}