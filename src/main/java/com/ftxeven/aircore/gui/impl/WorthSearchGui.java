package com.ftxeven.aircore.gui.impl;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.config.LangConfig;
import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.gui.BaseWorthGui;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.module.economy.EconomyModule;
import com.ftxeven.aircore.module.economy.worth.WorthCalculator;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.util.MiniText;
import org.bukkit.entity.Player;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class WorthSearchGui extends BaseWorthGui {

    public static final String ROLE = "worth-search";

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    public WorthSearchGui(ConfigManager configs, ServiceManager services, Supplier<EconomyModule> economy, PluginGuiManager guis) {
        super(configs, services, economy, guis);
    }

    @Override
    public void prepare(Player viewer, GuiSession session) {
        session.placeholders().put("query", query(session));
        super.prepare(viewer, session);
    }

    @Override
    protected List<WorthCalculator.Entry> visibleEntries(GuiSession session, List<WorthCalculator.Entry> catalog) {
        String query = query(session);
        if (query.isBlank()) {
            return catalog;
        }
        String needle = fold(query);
        LangConfig lang = configs.lang();
        return catalog.stream()
                .filter(entry -> fold(MiniText.plain(lang.item(entry.key()))).contains(needle))
                .toList();
    }

    private static String query(GuiSession session) {
        String query = session.attribute(ATTR_SEARCH_QUERY, String.class);
        return query != null ? query : "";
    }

    private static String fold(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return DIACRITICS.matcher(decomposed).replaceAll("").replace('_', ' ').trim().toLowerCase(Locale.ROOT);
    }
}