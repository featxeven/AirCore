package com.ftxeven.aircore.gui.action;

import com.ftxeven.aircore.core.gui.action.ActionContext;
import com.ftxeven.aircore.core.gui.action.ActionRegistry;
import com.ftxeven.aircore.core.gui.action.ActionTokens;
import com.ftxeven.aircore.gui.render.GuiPlaceholders;
import com.ftxeven.aircore.module.homes.HomesModule;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class FavoriteAction implements ActionRegistry.Handler {

    private static final String VERB = "favorite";

    private final Supplier<HomesModule> homes;
    private final Logger logger;

    public FavoriteAction(Supplier<HomesModule> homes, Logger logger) {
        this.homes = homes;
        this.logger = logger;
    }

    @Override
    public void execute(ActionContext context, String args) {
        String type = GuiActions.requireType(context, VERB, args);
        if (type == null) {
            return;
        }
        switch (type) {
            case "home" -> favoriteHome(context, args);
            default -> GuiActions.warnUnknownType(context, VERB, type);
        }
    }

    // home

    private void favoriteHome(ActionContext context, String args) {
        String homeName = GuiActions.resolveArgOrAttribute(context, args, "id");
        if (homeName == null) {
            logger.warning("Action '[favorite] type:home' has no 'id:' argument and this screen has no home "
                    + "context to fall back on - it will not do anything until this is fixed");
            return;
        }

        String scope = ActionTokens.parse(args).get("scope");
        Boolean favorite = parseScope(scope);
        if (favorite == null) {
            logger.warning("Action '[favorite] type:home' requires 'scope:add' or 'scope:remove' (got '"
                    + scope + "') - it will not do anything until this is fixed");
            return;
        }

        Player player = context.viewer();
        UUID owner = GuiPlaceholders.resolveOwner(context.session().target(), player);

        if (!owner.equals(player.getUniqueId())) {
            return;
        }
        if (!homes.get().setFavorite(owner, homeName, favorite)) {
            return; // home is gone
        }
        GuiActions.refreshLater(context);
    }

    private @Nullable Boolean parseScope(@Nullable String scope) {
        if (scope == null) {
            return null;
        }
        return switch (scope.toLowerCase(Locale.ROOT)) {
            case "add" -> Boolean.TRUE;
            case "remove" -> Boolean.FALSE;
            default -> null;
        };
    }
}