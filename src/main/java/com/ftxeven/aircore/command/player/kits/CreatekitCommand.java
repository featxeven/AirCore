package com.ftxeven.aircore.command.player.kits;

import com.ftxeven.aircore.command.AbstractCommand;
import com.ftxeven.aircore.model.Kit;
import com.ftxeven.aircore.module.kits.KitParams;
import com.ftxeven.aircore.module.kits.KitsModule;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.TimeFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class CreatekitCommand extends AbstractCommand {

    private static final String KEY = "createkit";

    private final Supplier<KitsModule> kitsModule;

    public CreatekitCommand(Context ctx, Supplier<KitsModule> kitsModule) {
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
        if (existing.isPresent()) {
            messenger().send(sender, configs().lang().get("kits.errors.already-exists"),
                    Map.of("name", escapeUserInput(existing.get().name())));
            return;
        }

        switch (KitParams.parse(Arrays.copyOfRange(args, 1, args.length))) {
            case KitParams.Result.UnknownParam(String token) -> messenger().send(sender,
                    configs().lang().get("errors.access.invalid-param"), Map.of("param", token));
            case KitParams.Result.InvalidValue(String param, String value) -> messenger().send(sender,
                    configs().lang().get("errors.access.invalid-param-value"), Map.of("param", param, "value", value));
            case KitParams.Result.Success(KitParams params) -> {
                ItemStack[] items = KitsModule.captureItems(player);
                Kit kit = kitsModule.get().build(name, items, params, player.getUniqueId());
                kitsModule.get().save(kit);

                completeCooldown(sender, args);
                messenger().send(sender, configs().lang().get("kits.created"), Map.of(
                        "name", escapeUserInput(kit.name()),
                        "cooldown", TimeFormatter.duration(
                                Duration.ofSeconds(kitsModule.get().cooldownSeconds(kit)),
                                configs().main().formatting(), configs().lang())
                ));
            }
        }
    }
}