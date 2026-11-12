package com.ftxeven.aircore.gui.action;

import com.ftxeven.aircore.command.Scopes;
import com.ftxeven.aircore.command.Scopes.ScopeAccess;
import com.ftxeven.aircore.command.player.utilities.ClearinventoryCommand;
import com.ftxeven.aircore.command.player.utilities.LiveActionRunner;
import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.core.gui.action.ActionContext;
import com.ftxeven.aircore.core.gui.action.ActionRegistry;
import com.ftxeven.aircore.service.ServiceManager;
import org.bukkit.entity.Player;

public final class ClearAction implements ActionRegistry.Handler {

    private static final String VERB = "clear";
    private static final ScopeAccess ACCESS = ScopeAccess.othersAndAll(ClearinventoryCommand.KEY);

    private final ConfigManager configs;
    private final ServiceManager services;
    private final Scopes scopes;
    private final LiveActionRunner runner;

    public ClearAction(ConfigManager configs, ServiceManager services, Scopes scopes, LiveActionRunner runner) {
        this.configs = configs;
        this.services = services;
        this.scopes = scopes;
        this.runner = runner;
    }

    @Override
    public void execute(ActionContext context, String args) {
        String type = GuiActions.requireType(context, VERB, args);
        if (type == null) {
            return;
        }
        switch (type) {
            case "inventory" -> clearInventory(context, args);
            default -> GuiActions.warnUnknownType(context, VERB, type);
        }
    }

    // inventory

    private void clearInventory(ActionContext context, String args) {
        Player player = context.viewer();
        String target = GuiActions.resolveArgOrAttribute(context, args, "target");
        String commandName = configs.commands().findCommandOrDisabled(ClearinventoryCommand.KEY).name();

        scopes.resolveLive(player, target, ACCESS, scope ->
                runner.run(player, scope, ClearinventoryCommand.KEYS, ClearinventoryCommand::clear,
                        () -> services.commandCooldowns().complete(commandName, player, new String[0])));
    }
}