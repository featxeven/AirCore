package com.ftxeven.aircore.gui.action;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.core.gui.action.ActionContext;
import com.ftxeven.aircore.model.Home;
import com.ftxeven.aircore.gui.render.GuiFlags;
import com.ftxeven.aircore.gui.render.GuiPlaceholders;
import com.ftxeven.aircore.module.homes.HomesModule;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.service.ServiceManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class DeleteAction implements ConfirmableAction {

    private static final String VERB = "delete";
    private static final String TARGET_PERMISSION = Permissions.Command.of("playerhome");

    private final ConfigManager configs;
    private final Supplier<HomesModule> homes;
    private final ServiceManager services;
    private final Logger logger;

    public DeleteAction(ConfigManager configs, Supplier<HomesModule> homes, ServiceManager services, Logger logger) {
        this.configs = configs;
        this.homes = homes;
        this.services = services;
        this.logger = logger;
    }

    @Override
    public void execute(ActionContext context, String args) {
        String type = GuiActions.requireType(context, VERB, args);
        if (type == null) {
            return;
        }
        switch (type) {
            case "home" -> deleteHome(context, args);
            default -> GuiActions.warnUnknownType(context, VERB, type);
        }
    }

    @Override
    public Decision decide(ActionContext context, Map<String, String> args) {
        String type = GuiActions.requireType(context, VERB, args);
        if (type == null) {
            return Decision.immediate();
        }
        return switch (type) {
            case "home" -> decideHome(context, args);
            default -> {
                GuiActions.warnUnknownType(context, VERB, type);
                yield Decision.immediate();
            }
        };
    }

    // home

    private void deleteHome(ActionContext context, String args) {
        String homeName = GuiActions.resolveArgOrAttribute(context, args, "id");
        if (homeName == null) {
            logger.warning("Action '[delete] type:home' has no 'id:' argument and this screen has no home "
                    + "context to fall back on - it will not do anything until this is fixed");
            return;
        }

        Player player = context.viewer();
        UUID owner = GuiPlaceholders.resolveOwner(context.session().target(), player);
        boolean self = owner.equals(player.getUniqueId());

        if (!self && !player.hasPermission(TARGET_PERMISSION)) {
            notifyStale(context, homeName); // stale click on a screen the player no longer has access to
            return;
        }
        if (!homes.get().delete(owner, homeName)) {
            notifyStale(context, homeName); // already gone
            return;
        }

        if (self) {
            context.messenger().send(player, configs.lang().get("homes.deleted.self"), Map.of("name", homeName));
            return;
        }

        Map<String, String> forAdmin = targetPlaceholders(owner);
        forAdmin.put("name", homeName);
        context.messenger().send(player, configs.lang().get("homes.deleted.other"), forAdmin);

        Player target = Bukkit.getPlayer(owner);
        if (target != null) {
            Map<String, String> forTarget = new LinkedHashMap<>();
            services.players().formatDisplayName(forTarget, "player", player.getUniqueId());
            forTarget.put("name", homeName);
            context.messenger().send(target, configs.lang().get("homes.deleted.by"), forTarget);
        }
    }

    private void notifyStale(ActionContext context, String homeName) {
        context.messenger().send(context.viewer(), configs.lang().get("errors.general.stale-entry"), Map.of("name", homeName));
    }

    private Decision decideHome(ActionContext context, Map<String, String> args) {
        String homeName = args.get("id");
        if (homeName == null || homeName.isBlank()) {
            return Decision.immediate();
        }

        Player player = context.viewer();
        UUID owner = GuiPlaceholders.resolveOwner(context.session().target(), player);
        boolean self = owner.equals(player.getUniqueId());

        if (!self && !player.hasPermission(TARGET_PERMISSION)) {
            return Decision.immediate();
        }
        Home home = homes.get().find(owner, homeName).orElse(null);
        if (home == null) {
            return Decision.immediate();
        }

        Map<String, String> placeholders = self ? new LinkedHashMap<>() : targetPlaceholders(owner);
        placeholders.putAll(GuiPlaceholders.home(home, configs.main().formatting()));
        return Decision.needsConfirmation(placeholders, GuiFlags.forHome(player, configs.filter(), owner, home, homes.get().homeResolver(owner)));
    }

    private Map<String, String> targetPlaceholders(UUID targetUuid) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        services.players().formatDisplayName(placeholders, "target", targetUuid);
        return placeholders;
    }
}