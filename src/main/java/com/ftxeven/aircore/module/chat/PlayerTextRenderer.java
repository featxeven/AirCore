package com.ftxeven.aircore.module.chat;

import com.ftxeven.aircore.config.MainConfig;
import com.ftxeven.aircore.util.MiniText;
import net.kyori.adventure.text.Component;
import org.bukkit.permissions.Permissible;

public final class PlayerTextRenderer {

    private PlayerTextRenderer() {
    }

    public static Component render(String rawInput, MainConfig.MessageFormat mode, Permissible sender) {
        String safe = PlayerInputSanitizer.sanitize(rawInput, mode, sender);
        try {
            return MiniText.mini().deserialize(safe);
        } catch (Exception e) {
            return Component.text(MiniText.mini().stripTags(safe));
        }
    }
}