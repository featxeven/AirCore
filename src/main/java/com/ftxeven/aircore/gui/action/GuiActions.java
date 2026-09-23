package com.ftxeven.aircore.gui.action;

import com.ftxeven.aircore.command.player.PlayerTargetResolver;
import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.core.gui.GuiManager;
import com.ftxeven.aircore.core.gui.action.ActionContext;
import com.ftxeven.aircore.core.gui.action.ActionTokens;
import com.ftxeven.aircore.util.Placeholders;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

public final class GuiActions {

    private GuiActions() {
    }

    public static boolean guiEnabled(GuiManager guis, String guiId) {
        return guis.definition(guiId).map(gui -> gui.settings().enabled()).orElse(false);
    }

    public static void refreshLater(ActionContext context) {
        Player viewer = context.viewer();
        GuiManager manager = context.manager();
        Scheduler.runEntityLater(viewer, () -> manager.refresh(viewer), 1L);
    }

    public static @Nullable Player resolveOnlineTarget(ActionContext context, ConfigManager configs,
                                                       PlayerTargetResolver resolver, @Nullable String token) {
        if (token == null || token.isBlank()) {
            context.logger().warning("Action on item '" + context.itemKey() + "' in GUI '" + context.guiId()
                    + "' is missing a required 'target:' argument - it will not do anything until this is fixed");
            return null;
        }
        Player online = resolver.onlinePlayer(token).orElse(null);
        if (online == null) {
            context.messenger().send(context.viewer(), configs.lang().get("errors.access.player-not-found"), Map.of("player", token));
        }
        return online;
    }

    public static @Nullable String resolveArgOrAttribute(ActionContext context, String rawArgs, String key) {
        String explicit = ActionTokens.parse(rawArgs).get(key);
        if (explicit != null && !explicit.isBlank()) {
            return Placeholders.apply(context.viewer(), explicit, context.placeholders());
        }
        return context.session().attribute(key, String.class);
    }

    // 'type:' dispatch

    public static @Nullable String requireType(ActionContext context, String verb, String rawArgs) {
        return validateType(context, verb, ActionTokens.parse(rawArgs).get("type"));
    }

    public static @Nullable String requireType(ActionContext context, String verb, Map<String, String> args) {
        return validateType(context, verb, args.get("type"));
    }

    private static @Nullable String validateType(ActionContext context, String verb, @Nullable String type) {
        if (type == null || type.isBlank()) {
            context.logger().warning("[" + verb + "] action on item '" + context.itemKey() + "' in GUI '"
                    + context.guiId() + "' requires a 'type:' argument - it will not do anything until this is fixed");
            return null;
        }
        return type.toLowerCase(Locale.ROOT);
    }

    public static void warnUnknownType(ActionContext context, String verb, String type) {
        context.logger().warning("[" + verb + "] action on item '" + context.itemKey() + "' in GUI '"
                + context.guiId() + "' has unknown type 'type:" + type + "' - it will not do anything until this is fixed");
    }
}