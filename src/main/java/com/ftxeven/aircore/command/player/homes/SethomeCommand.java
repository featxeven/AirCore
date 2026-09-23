package com.ftxeven.aircore.command.player.homes;

import com.ftxeven.aircore.command.AbstractCommand;
import com.ftxeven.aircore.module.NameValidator;
import com.ftxeven.aircore.module.homes.HomesModule;
import com.ftxeven.aircore.permission.PermissionTiers;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.function.Supplier;

public final class SethomeCommand extends AbstractCommand {

    private static final String KEY = "sethome";

    private final Supplier<HomesModule> homes;

    public SethomeCommand(Context ctx, Supplier<HomesModule> homes) {
        super(ctx, KEY);
        this.homes = homes;
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public boolean playerOnly() { return true; }

    @Override
    public int minArgs(CommandSender sender) {
        return namingEnabled() ? 1 : 0;
    }

    @Override
    public int maxArgs(CommandSender sender) {
        return namingEnabled() ? 1 : 0;
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Player player = (Player) sender;
        String requestedName = namingEnabled() ? args[0] : null;

        switch (homes.get().set(player, requestedName)) {
            case HomesModule.SetVerdict.Created(String name) -> {
                completeCooldown(sender, args);
                messenger().send(sender, configs().lang().get("homes.created"), setPlaceholders(player, escapeUserInput(name)));
            }
            case HomesModule.SetVerdict.Overwritten(String name) -> {
                completeCooldown(sender, args);
                messenger().send(sender, configs().lang().get("homes.overwritten"), setPlaceholders(player, escapeUserInput(name)));
            }
            case HomesModule.SetVerdict.AlreadyExists(String name) -> messenger().send(sender,
                    configs().lang().get("homes.errors.already-exists"), Map.of("name", escapeUserInput(name)));
            case HomesModule.SetVerdict.LimitReached(int count, int limit) -> messenger().send(sender,
                    configs().lang().get("homes.errors.limit-reached"), Map.of(
                            "count", String.valueOf(count), "limit", String.valueOf(limit)));
            case HomesModule.SetVerdict.BlockedWorld() -> messenger().send(sender,
                    configs().lang().get("homes.errors.blocked-world"), Map.of("world", player.getWorld().getName()));
            case HomesModule.SetVerdict.InvalidName(NameValidator.Reason reason) -> sendInvalidName(sender, reason, requestedName);
        }
    }

    private boolean namingEnabled() {
        return configs().homes().naming().enabled();
    }

    private Map<String, String> setPlaceholders(Player player, String name) {
        return Map.of(
                "name", name,
                "count", String.valueOf(homes.get().count(player.getUniqueId())),
                "limit", PermissionTiers.display(homes.get().limitFor(player), configs().lang())
        );
    }

    private void sendInvalidName(CommandSender sender, NameValidator.Reason reason, String requestedName) {
        String langKey = homes.get().naming().langKeyFor(reason);
        Map<String, String> placeholders = switch (reason) {
            case TOO_LONG -> Map.of(
                    "length", String.valueOf(requestedName == null ? 0 : requestedName.length()),
                    "max", String.valueOf(configs().homes().naming().maxLength())
            );
            case INVALID_FORMAT, BLACKLISTED -> Map.of(
                    "name", requestedName == null ? "" : escapeUserInput(requestedName)
            );
            case PROFANITY -> Map.of();
        };
        messenger().send(sender, configs().lang().get(langKey), placeholders);
    }
}