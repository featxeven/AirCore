package com.ftxeven.aircore.gui.action;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.config.FilterConfig;
import com.ftxeven.aircore.core.gui.GuiManager;
import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.core.gui.action.ActionContext;
import com.ftxeven.aircore.core.gui.action.ActionRegistry;
import com.ftxeven.aircore.core.gui.action.ActionTokens;
import com.ftxeven.aircore.core.gui.action.ForwardNavigation;
import com.ftxeven.aircore.core.gui.nav.ScreenState;
import com.ftxeven.aircore.model.Home;
import com.ftxeven.aircore.gui.render.GuiFlags;
import com.ftxeven.aircore.gui.render.GuiPlaceholders;
import com.ftxeven.aircore.module.homes.HomesModule;
import com.ftxeven.aircore.util.Placeholders;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class CustomizeAction implements ActionRegistry.Handler {

    private static final String VERB = "customize";

    private final ConfigManager configs;
    private final Supplier<HomesModule> homes;
    private final GuiManager guis;
    private final Logger logger;

    public CustomizeAction(ConfigManager configs, Supplier<HomesModule> homes, GuiManager guis, Logger logger) {
        this.configs = configs;
        this.homes = homes;
        this.guis = guis;
        this.logger = logger;
    }

    @Override
    public void execute(ActionContext context, String args) {
        String type = GuiActions.requireType(context, VERB, args);
        if (type == null) {
            return;
        }
        switch (type) {
            case "home" -> customizeHome(context, args);
            default -> GuiActions.warnUnknownType(context, VERB, type);
        }
    }

    // home

    private void customizeHome(ActionContext context, String args) {
        String guiId = ActionTokens.parse(args).get("gui");
        if (guiId != null && !guiId.isBlank()) {
            openCustomizeGui(context, args, guiId);
            return;
        }
        applyIcon(context, args);
    }

    // '[customize] type:home gui:<gui> id:<home> restore:<...>'
    private void openCustomizeGui(ActionContext context, String args, String guiId) {
        if (!GuiActions.guiEnabled(guis, guiId)) {
            logger.warning("Action '[customize] type:home gui:" + guiId + "' points at gui '" + guiId
                    + "', which isn't a registered/enabled GUI - it will not do anything until this is fixed");
            return;
        }
        String homeName = GuiActions.resolveArgOrAttribute(context, args, "id");
        if (homeName == null) {
            logger.warning("Action '[customize] type:home gui:" + guiId + "' is missing a required 'id:' argument "
                    + "and this screen has no home context to fall back on - it will not do anything until this is fixed");
            return;
        }

        Player player = context.viewer();
        UUID owner = GuiPlaceholders.resolveOwner(context.session().target(), player);
        if (!owner.equals(player.getUniqueId())) {
            return; // someone else's home
        }
        Home home = homes.get().find(owner, homeName).orElse(null);
        if (home == null) {
            return; // stale click
        }

        GuiSession current = context.session();
        ScreenState currentLive = ScreenState.liveStateOf(current);
        ForwardNavigation.applyRestore(context, current, currentLive, ForwardNavigation.parse(args, context, "id"));

        ScreenOpener.open(context, guiId, Map.of("id", homeName), Map.of("home", homeName),
                GuiFlags.forHome(player, configs.filter(), owner, home, homes.get().homeResolver(owner)));
    }

    // '[customize] type:home icon:<icon|all>'
    private void applyIcon(ActionContext context, String args) {
        String homeName = GuiActions.resolveArgOrAttribute(context, args, "id");
        if (homeName == null) {
            logger.warning("Action '[customize] type:home' has no 'id:' argument and this screen has no home "
                    + "context to fall back on - it will not do anything until this is fixed");
            return;
        }
        String rawIcon = ActionTokens.parse(args).get("icon");
        if (rawIcon == null || rawIcon.isBlank()) {
            logger.warning("Action '[customize] type:home' on a home context is missing a required 'icon:' "
                    + "argument - it will not do anything until this is fixed");
            return;
        }
        String iconToken = Placeholders.apply(context.viewer(), rawIcon, context.placeholders());

        Player player = context.viewer();
        UUID owner = GuiPlaceholders.resolveOwner(context.session().target(), player);

        if (!owner.equals(player.getUniqueId())) {
            return;
        }
        if (homes.get().find(owner, homeName).isEmpty()) {
            return; // deleted out from under this screen
        }
        if (!canAssign(player, iconToken)) {
            return; // locked icon
        }

        String icon = iconToken.equalsIgnoreCase(FilterConfig.ALL_ID) ? null : iconToken;
        homes.get().setIcon(owner, homeName, icon);
        GuiActions.refreshLater(context);
    }

    private boolean canAssign(Player player, String iconToken) {
        if (iconToken.equalsIgnoreCase(FilterConfig.ALL_ID)) {
            return true; // resetting to the default icon never needs a permission
        }
        return configs.filter().homeIcon(iconToken)
                .map(icon -> icon.isAccessibleTo(player))
                .orElse(false);
    }
}