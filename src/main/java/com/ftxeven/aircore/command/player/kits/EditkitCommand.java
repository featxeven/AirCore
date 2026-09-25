package com.ftxeven.aircore.command.player.kits;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.model.Kit;
import com.ftxeven.aircore.module.kits.KitParams;
import com.ftxeven.aircore.module.kits.KitsModule;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class EditkitCommand extends BaseCommand {

    private static final String KEY = "editkit";

    private final Supplier<KitsModule> kitsModule;

    public EditkitCommand(Context ctx, Supplier<KitsModule> kitsModule) {
        super(ctx, KEY);
        this.kitsModule = kitsModule;
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public boolean playerOnly() { return true; }

    @Override
    public int minArgs() { return 1; }

    @Override
    public int maxArgs() { return -1; }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            return super.tabComplete(sender, args);
        }
        return KitParams.suggestTokens(Arrays.copyOfRange(args, 1, args.length - 1), args[args.length - 1]);
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Player player = (Player) sender;
        String name = args[0];

        Optional<Kit> existing = kitsModule.get().find(name);
        if (existing.isEmpty()) {
            messenger().send(sender, configs().lang().get("kits.errors.not-found"), Map.of("name", escapeUserInput(name)));
            return;
        }

        switch (KitParams.parse(Arrays.copyOfRange(args, 1, args.length))) {
            case KitParams.Result.UnknownParam(String token) -> messenger().send(sender,
                    configs().lang().get("errors.access.invalid-param"), Map.of("param", token));
            case KitParams.Result.InvalidValue(String param, String value) -> messenger().send(sender,
                    configs().lang().get("errors.access.invalid-param-value"), Map.of("param", param, "value", value));
            case KitParams.Result.Success(KitParams params) -> {
                ItemStack[] items = KitsModule.captureItems(player);
                Kit updated = kitsModule.get().applyEdits(existing.get(), params, items);
                kitsModule.get().save(updated);

                completeCooldown(sender, args);
                messenger().send(sender, configs().lang().get("kits.edited"), Map.of("name", escapeUserInput(updated.name())));
            }
        }
    }
}