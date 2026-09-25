package com.ftxeven.aircore.command.player.teleport;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.database.repository.LocationRepository;
import com.ftxeven.aircore.module.Positions;
import com.ftxeven.aircore.module.teleport.SpawnParams;
import com.ftxeven.aircore.module.teleport.TeleportModule;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class SetspawnCommand extends BaseCommand {

    private static final String KEY = "setspawn";

    private final Supplier<TeleportModule> teleport;

    public SetspawnCommand(Context ctx, Supplier<TeleportModule> teleport) {
        super(ctx, KEY);
        this.teleport = teleport;
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public boolean playerOnly() { return true; }

    @Override
    public int minArgs(CommandSender sender) { return 0; }

    @Override
    public int maxArgs(CommandSender sender) { return Integer.MAX_VALUE; }

    @Override
    public String usage(CommandSender sender) {
        return CommandDispatch.usage(config(), false);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return super.tabComplete(sender, args);
        }
        return SpawnParams.suggestTokens(Arrays.copyOfRange(args, 0, args.length - 1), args[args.length - 1], List.of());
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Player player = (Player) sender;

        switch (SpawnParams.parse(args)) {
            case SpawnParams.Result.Success(SpawnParams params) -> {
                teleport.get().setSpawn(params.resolveKey(), Positions.of(player.getLocation()), player.getUniqueId());
                sendSetMessage(sender, params);
            }
            case SpawnParams.Result.UnknownParam(String token) -> messenger().send(sender,
                    configs().lang().get("errors.access.invalid-param"), Map.of("param", token));
            case SpawnParams.Result.InvalidValue(String param, String value) -> messenger().send(sender,
                    configs().lang().get("errors.access.invalid-param-value"), Map.of("param", param, "value", value));
            case SpawnParams.Result.ConflictingParams() -> messenger().send(sender,
                    configs().lang().get("errors.access.conflicting-params"));
        }
    }

    private void sendSetMessage(CommandSender sender, SpawnParams params) {
        if (params.firstJoin()) {
            messenger().send(sender, configs().lang().get("teleport.spawn.set-first-join"));
        } else if (params.group() != null) {
            messenger().send(sender, configs().lang().get("teleport.spawn.set-group"),
                    Map.of("group", escapeUserInput(storedGroup(params))));
        } else {
            messenger().send(sender, configs().lang().get("teleport.spawn.set"));
        }
    }

    private String storedGroup(SpawnParams params) {
        return teleport.get().findSpawn(params.resolveKey())
                .flatMap(location -> LocationRepository.groupNameFromSpawnKey(location.key()))
                .orElse(params.group());
    }
}