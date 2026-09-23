package com.ftxeven.aircore.gui.impl;

import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.core.gui.render.GuiRenderer;
import com.ftxeven.aircore.gui.LayoutGuiRegistry;
import com.ftxeven.aircore.gui.render.ContainerRenderer;
import com.ftxeven.aircore.model.Kit;
import com.ftxeven.aircore.module.kits.KitClaimMessages;
import com.ftxeven.aircore.module.kits.KitsModule;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class KitPreviewGui implements GuiRenderer.DynamicRenderer {

    public static final String ROLE = "kits-preview";

    private final LayoutGuiRegistry layouts;
    private final Supplier<KitsModule> kitsModule;
    private final Logger logger;

    public KitPreviewGui(LayoutGuiRegistry layouts, Supplier<KitsModule> kitsModule, Logger logger) {
        this.layouts = layouts;
        this.kitsModule = kitsModule;
        this.logger = logger;
    }

    @Override
    public void prepare(Player viewer, GuiSession session) {
        resolvedKit(session).ifPresent(kit -> session.placeholders().putAll(KitClaimMessages.claimedPlaceholders(kit)));
    }

    @Override
    public void render(Player viewer, GuiSession session) {
        Optional<Kit> kit = resolvedKit(session);
        if (kit.isEmpty()) {
            return;
        }
        Set<Integer> slots = layouts.layout(session.definition()).previewSlots();
        if (slots.isEmpty()) {
            return;
        }
        ContainerRenderer.draw(session, kit.get().nonEmptyItems(), slots);
    }

    private Optional<Kit> resolvedKit(GuiSession session) {
        String kitId = session.attribute("id", String.class);
        if (kitId == null) {
            logger.warning("GUI '" + session.definition().id() + "' was opened without a 'kit' context value - nothing will be previewed");
            return Optional.empty();
        }
        Optional<Kit> kit = kitsModule.get().find(kitId);
        if (kit.isEmpty()) {
            logger.warning("GUI '" + session.definition().id() + "' was opened with kit context '" + kitId
                    + "', which isn't a registered kit - nothing will be previewed");
        }
        return kit;
    }
}