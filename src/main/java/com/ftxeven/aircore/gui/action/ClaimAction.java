package com.ftxeven.aircore.gui.action;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.core.gui.action.ActionContext;
import com.ftxeven.aircore.gui.render.GuiFlags;
import com.ftxeven.aircore.model.Kit;
import com.ftxeven.aircore.module.kits.KitClaimHandler;
import com.ftxeven.aircore.module.kits.KitClaimMessages;
import com.ftxeven.aircore.module.kits.KitsModule;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.service.Eligibility;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class ClaimAction implements ConfirmableAction {

    private static final String VERB = "claim";

    private final ConfigManager configs;
    private final Supplier<KitsModule> kits;
    private final Logger logger;

    public ClaimAction(ConfigManager configs, Supplier<KitsModule> kits, Logger logger) {
        this.configs = configs;
        this.kits = kits;
        this.logger = logger;
    }

    @Override
    public void execute(ActionContext context, String args) {
        String type = GuiActions.requireType(context, VERB, args);
        if (type == null) {
            return;
        }
        switch (type) {
            case "kit" -> claimKit(context, args);
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
            case "kit" -> decideKit(context, args);
            default -> {
                GuiActions.warnUnknownType(context, VERB, type);
                yield Decision.immediate();
            }
        };
    }

    // kit

    private void claimKit(ActionContext context, String args) {
        String kitName = GuiActions.resolveArgOrAttribute(context, args, "id");
        if (kitName == null) {
            logger.warning("Action '[claim] type:kit' has no 'id:' argument and this screen has no kit context "
                    + "to fall back on - it will not do anything until this is fixed");
            return;
        }

        Optional<Kit> resolved = kits.get().find(kitName);
        if (resolved.isEmpty()) {
            logger.warning("Action '[claim] type:kit' points at kit '" + kitName + "', which isn't a registered "
                    + "kit - it will not do anything until this is fixed");
            return;
        }

        Player player = context.viewer();
        Kit kit = resolved.get();

        if (!Permissions.Access.hasKit(player, kit.name())) {
            KitClaimMessages.noPermission(kit).send(player, configs, context.messenger());
            return;
        }

        KitClaimHandler.Verdict verdict = kits.get().claim(player, kit);
        KitClaimMessages.applySelfVerdict(player, kit, verdict, configs, context.messenger());
    }

    private Decision decideKit(ActionContext context, Map<String, String> args) {
        String kitName = args.get("id");
        if (kitName == null || kitName.isBlank()) {
            return Decision.immediate();
        }

        Optional<Kit> resolved = kits.get().find(kitName);
        if (resolved.isEmpty()) {
            return Decision.immediate();
        }

        Kit kit = resolved.get();
        Player player = context.viewer();

        if (!Permissions.Access.hasKit(player, kit.name())) {
            return Decision.denied(KitClaimMessages.noPermission(kit));
        }

        Optional<KitClaimHandler.Verdict> blocked = kits.get().preview(player, kit);
        if (blocked.isPresent()) {
            return Decision.denied((Eligibility.Denied) KitClaimMessages.self(player, kit, blocked.get(), configs));
        }

        return Decision.needsConfirmation(
                KitClaimMessages.claimedPlaceholders(kit),
                GuiFlags.forKit(player, kits.get(), kit));
    }
}