package com.ftxeven.aircore.command.player.kits;

import com.ftxeven.aircore.command.AbstractCommand;
import com.ftxeven.aircore.model.Kit;
import com.ftxeven.aircore.module.kits.KitsModule;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class DeletekitCommand extends AbstractCommand {

    private static final String KEY = "deletekit";

    private final Supplier<KitsModule> kitsModule;

    public DeletekitCommand(Context ctx, Supplier<KitsModule> kitsModule) {
        super(ctx, KEY);
        this.kitsModule = kitsModule;
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public int minArgs() { return 1; }

    @Override
    public int maxArgs() { return 1; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Optional<Kit> kit = kitsModule.get().find(args[0]);
        if (kit.isEmpty() || !kitsModule.get().delete(kit.get().name())) {
            messenger().send(sender, configs().lang().get("kits.errors.not-found"), Map.of("name", escapeUserInput(args[0])));
            return;
        }

        completeCooldown(sender, args);
        messenger().send(sender, configs().lang().get("kits.deleted"), Map.of("name", escapeUserInput(kit.get().name())));
    }
}