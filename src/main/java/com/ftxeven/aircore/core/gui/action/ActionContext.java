package com.ftxeven.aircore.core.gui.action;

import com.ftxeven.aircore.core.gui.GuiManager;
import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.util.Messenger;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

public record ActionContext(
        Player viewer,
        GuiSession session,
        GuiManager manager,
        Messenger messenger,
        Map<String, String> placeholders,
        Function<String, String> placeholderResolver,
        Function<String, String> flagResolver,
        String itemKey,
        Logger logger
) {
    public String guiId() {
        return session.definition().id();
    }
}