package com.ftxeven.aircore.module.chat.displaytag;

import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.gui.action.GuiActions;
import com.ftxeven.aircore.gui.impl.PreviewEnderchestGui;
import com.ftxeven.aircore.gui.impl.PreviewInventoryGui;
import com.ftxeven.aircore.gui.impl.PreviewItemGui;
import com.ftxeven.aircore.gui.impl.PreviewShulkerGui;
import com.ftxeven.aircore.module.chat.ChatConfig;
import com.ftxeven.aircore.util.Messenger;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class DisplayTagClickHandler {

    private static final String NAMESPACE = "aircore";
    private static final String KEY_PREFIX = "display-tag/";

    private final DisplayTagClickRegistry clicks;
    private final Supplier<ChatConfig> config;
    private final Messenger messenger;
    private final PluginGuiManager guiManager;

    public DisplayTagClickHandler(DisplayTagClickRegistry clicks, Supplier<ChatConfig> config,
                                  Messenger messenger, PluginGuiManager guiManager) {
        this.clicks = clicks;
        this.config = config;
        this.messenger = messenger;
        this.guiManager = guiManager;
    }

    public void handle(PlayerCustomClickEvent event) {
        Key id = event.getIdentifier();
        if (!id.namespace().equals(NAMESPACE) || !id.value().startsWith(KEY_PREFIX)) {
            return;
        }
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)) {
            return;
        }
        Player viewer = connection.getPlayer();
        String token = id.value().substring(KEY_PREFIX.length());

        switch (clicks.resolve(token)) {
            case DisplayTagClickRegistry.ClickResolution.NotFound ignored -> {
                // unknown/spoofed token
            }
            case DisplayTagClickRegistry.ClickResolution.Expired(String tagKey) ->
                    sendExpiredMessage(viewer, tagKey);
            case DisplayTagClickRegistry.ClickResolution.Valid(String tagKey, UUID ownerUuid, DisplayTagClickContext context) ->
                    openPreview(viewer, tagKey, ownerUuid, context);
        }
    }

    private void openPreview(Player viewer, String tagKey, UUID ownerUuid, DisplayTagClickContext context) {
        ChatConfig.DisplayTag tag = config.get().displayTags().get(tagKey);
        if (tag == null || !tag.hasGui() || !GuiActions.guiEnabled(guiManager.guis(), tag.gui())) {
            return;
        }
        String guiId = tag.gui();
        String commandName = "display-tag/" + tagKey;

        switch (context) {
            case DisplayTagClickContext.ItemPreview preview ->
                    guiManager.openForTarget(viewer, commandName, guiId, ownerUuid,
                            Map.of(PreviewItemGui.ATTR_ITEM, preview.item()));

            case DisplayTagClickContext.InventoryPreview preview ->
                    guiManager.openForTarget(viewer, commandName, guiId, ownerUuid, inventoryAttributes(preview));

            case DisplayTagClickContext.EnderChestPreview preview ->
                    guiManager.openForTarget(viewer, commandName, guiId, ownerUuid,
                            Map.of(PreviewEnderchestGui.ATTR_CONTENTS, preview.contents()));

            case DisplayTagClickContext.ShulkerPreview preview ->
                    guiManager.openForTarget(viewer, commandName, guiId, ownerUuid, Map.<String, Object>of(
                            PreviewShulkerGui.ATTR_SHULKER_ITEM, preview.shulkerItem(),
                            PreviewShulkerGui.ATTR_CONTENTS, preview.contents()));
        }
    }

    private Map<String, Object> inventoryAttributes(DisplayTagClickContext.InventoryPreview preview) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(PreviewInventoryGui.ATTR_CONTENTS, preview.contents());
        attributes.put(PreviewInventoryGui.ATTR_ARMOR, preview.armor());
        if (preview.offhand() != null) {
            attributes.put(PreviewInventoryGui.ATTR_OFFHAND, preview.offhand());
        }
        return attributes;
    }

    private void sendExpiredMessage(Player viewer, String tagKey) {
        ChatConfig.DisplayTag tag = config.get().displayTags().get(tagKey);
        List<String> message = tag != null ? tag.expiredMessage() : List.of();
        messenger.send(viewer, message);
    }
}