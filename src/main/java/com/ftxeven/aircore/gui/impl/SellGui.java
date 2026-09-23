package com.ftxeven.aircore.gui.impl;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.gui.BaseHoldingGui;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.gui.config.LayoutConfig;
import com.ftxeven.aircore.gui.render.GuiFlags;
import com.ftxeven.aircore.gui.render.GuiPlaceholders;
import com.ftxeven.aircore.module.economy.EconomyModule;
import com.ftxeven.aircore.module.economy.sell.SellHandler;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.service.item.HoldingKind;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Supplier;

public final class SellGui extends BaseHoldingGui {

    public static final String ROLE = "sell";
    public static final String CONFIRM_ROLE = "sell-confirm";

    private final ConfigManager configs;
    private final Supplier<EconomyModule> economy;

    public SellGui(ConfigManager configs, ServiceManager services, Supplier<EconomyModule> economy, PluginGuiManager guis) {
        super(services, guis, HoldingKind.SELL);
        this.configs = configs;
        this.economy = economy;
    }

    @Override
    public void prepare(Player viewer, GuiSession session) {
        SellHandler sell = economy.get().sell();
        boolean skipInvalid = configs.economy().gui().sell().skipInvalidItems();

        ItemStack[] selected = held(holding(), viewer, kind());
        ItemStack[] all = combined(selected, ownedStorage(viewer));

        GuiPlaceholders.formatSellAmount(session.placeholders(), economy.get().formatter(), "worth", "tax",
                sell.previewBatch(viewer, selected, skipInvalid));
        GuiPlaceholders.formatSellAmount(session.placeholders(), economy.get().formatter(), "worth_all", "tax_all",
                sell.previewBatch(viewer, all, skipInvalid));

        session.flagResolver(GuiFlags.forSell(viewer, sell, selected, all, skipInvalid));
    }

    @Override
    public void render(Player viewer, GuiSession session) {
        LayoutConfig layout = guis.layouts().layout(session.definition());
        attachSlots(viewer, session, layout.sellSlots());
    }
}