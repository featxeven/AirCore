package com.ftxeven.aircore.command.player.teleport;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.database.repository.LocationRepository;
import com.ftxeven.aircore.model.NamedLocation;
import com.ftxeven.aircore.module.teleport.SpawnParams;
import com.ftxeven.aircore.module.teleport.TeleportModule;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class DeletespawnCommand extends BaseCommand {

    private static final String KEY = "deletespawn";

    private final Supplier<TeleportModule> teleport;

    public DeletespawnCommand(Context ctx, Supplier<TeleportModule> teleport) {
        super(ctx, KEY);
        this.teleport = teleport;
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

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
        List<String> knownGroups = teleport.get().configuredSpawnGroups();
        return SpawnParams.suggestTokens(Arrays.copyOfRange(args, 0, args.length - 1), args[args.length - 1], knownGroups);
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        switch (SpawnParams.parse(args)) {
            case SpawnParams.Result.Success(SpawnParams params) -> deleteSpawn(sender, args, params);
            case SpawnParams.Result.UnknownParam(String token) -> messenger().send(sender,
                    configs().lang().get("errors.access.invalid-param"), Map.of("param", token));
            case SpawnParams.Result.InvalidValue(String param, String value) -> messenger().send(sender,
                    configs().lang().get("errors.access.invalid-param-value"), Map.of("param", param, "value", value));
            case SpawnParams.Result.ConflictingParams() -> messenger().send(sender,
                    configs().lang().get("errors.access.conflicting-params"));
        }
    }

    private void deleteSpawn(CommandSender sender, String[] args, SpawnParams params) {
        Optional<NamedLocation> stored = teleport.get().findSpawn(params.resolveKey());
        if (stored.isEmpty() || !teleport.get().deleteSpawn(stored.get().key())) {
            messenger().send(sender, configs().lang().get("teleport.spawn.errors.not-set"));
            return;
        }

        completeCooldown(sender, args);
        sendDeleteMessage(sender, params, stored.get());
    }

    private void sendDeleteMessage(CommandSender sender, SpawnParams params, NamedLocation deleted) {
        if (params.firstJoin()) {
            messenger().send(sender, configs().lang().get("teleport.spawn.deleted-first-join"));
        } else if (params.group() != null) {
            String group = LocationRepository.groupNameFromSpawnKey(deleted.key()).orElse(params.group());
            messenger().send(sender, configs().lang().get("teleport.spawn.deleted-group"), Map.of("group", escapeUserInput(group)));
        } else {
            messenger().send(sender, configs().lang().get("teleport.spawn.deleted"));
        }
    }
}