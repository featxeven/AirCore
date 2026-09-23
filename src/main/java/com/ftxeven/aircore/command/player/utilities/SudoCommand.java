package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.command.AbstractCommand;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class SudoCommand extends AbstractCommand {

    private static final String KEY = "sudo";
    private static final String ALL_PERMISSION = Permissions.Command.all(KEY);

    public SudoCommand(Context ctx) {
        super(ctx, KEY);
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public int minArgs() { return 2; }

    @Override
    public int maxArgs() { return -1; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        String targetToken = args[0];
        String command = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        if (selectors().isAll(targetToken)) {
            if (!checkPermission(sender, ALL_PERMISSION)) return;
            for (Player online : Bukkit.getOnlinePlayers()) {
                runAsPlayer(online, command);
            }
            completeCooldown(sender, args);
            messenger().send(sender, configs().lang().get("utilities.sudo.executed-all"), Map.of("command", command));
            return;
        }

        Optional<Player> target = requireFound(resolver().onlinePlayer(targetToken), sender, targetToken);
        if (target.isEmpty()) return;

        runAsPlayer(target.get(), command);
        completeCooldown(sender, args);
        announceExecuted(sender, target.get(), command);
    }

    private void runAsPlayer(Player player, String command) {
        Scheduler.runEntity(player, () -> player.performCommand(command));
    }

    private void announceExecuted(CommandSender sender, Player target, String command) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        services().players().formatDisplayName(placeholders, "target", target.getUniqueId());
        placeholders.put("command", command);
        messenger().send(sender, configs().lang().get("utilities.sudo.executed"), placeholders);
    }
}