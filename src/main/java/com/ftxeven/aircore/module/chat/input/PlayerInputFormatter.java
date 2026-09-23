package com.ftxeven.aircore.module.chat.input;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.module.chat.ChatConfig;
import com.ftxeven.aircore.module.chat.PlayerTextRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.permissions.Permissible;

import java.util.List;

public final class PlayerInputFormatter {

    private final ConfigManager configs;

    public PlayerInputFormatter(ConfigManager configs) {
        this.configs = configs;
    }

    public void handleSignChange(SignChangeEvent event) {
        if (!enabled(input().signs())) {
            return;
        }
        Player player = event.getPlayer();
        int lines = event.lines().size();
        for (int i = 0; i < lines; i++) {
            String raw = PlainTextComponentSerializer.plainText().serialize(event.line(i));
            if (!raw.isEmpty()) {
                event.line(i, render(raw, player));
            }
        }
    }

    public void handleAnvilRename(PrepareAnvilEvent event) {
        if (!enabled(input().anvil())) {
            return;
        }
        AnvilView view = event.getView();
        String raw = view.getRenameText();
        if (raw == null || raw.isEmpty()) {
            return;
        }
        if (!(view.getPlayer() instanceof Player player)) {
            return;
        }

        ItemStack result = event.getResult();
        if (result == null) {
            return;
        }
        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.displayName(render(raw, player));
        result.setItemMeta(meta);
        event.setResult(result);
    }

    public void handleBookEdit(PlayerEditBookEvent event) {
        if (!event.isSigning() || !enabled(input().books())) {
            return;
        }
        Player player = event.getPlayer();
        BookMeta meta = event.getNewBookMeta();
        List<String> pages = meta.getPages();
        for (int i = 0; i < pages.size(); i++) {
            meta.page(i + 1, render(pages.get(i), player));
        }
        event.setNewBookMeta(meta);
    }

    private boolean enabled(ChatConfig.InputToggle toggle) {
        return configs.chat().enabled() && toggle.enabled();
    }

    private ChatConfig.PlayerInput input() {
        return configs.chat().playerInput();
    }

    private Component render(String raw, Permissible sender) {
        return PlayerTextRenderer.render(raw, configs.main().formatting().messageFormat(), sender);
    }
}