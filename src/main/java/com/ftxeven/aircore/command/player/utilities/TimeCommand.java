package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.core.command.DurationUnits;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.TimeFormatter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

public final class TimeCommand extends BaseCommand {

    private static final String KEY = "time";
    private static final String ADD = "add";

    private final DurationUnits durationUnits;

    public TimeCommand(Context ctx, DurationUnits durationUnits) {
        super(ctx, KEY);
        this.durationUnits = durationUnits;
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public int minArgs(CommandSender sender) {
        return sender instanceof Player ? 2 : 3;
    }

    @Override
    public int maxArgs(CommandSender sender) { return 3; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Optional<String> action = resolveAction(args[0]);
        if (action.isEmpty()) {
            sendUsageError(sender, label, subLabel);
            return;
        }
        boolean isAdd = action.get().equals(ADD);

        OptionalLong parsed = TimeValueParser.resolve(args[1], isAdd, durationUnits, timeFilter());
        if (parsed.isEmpty()) {
            messenger().send(sender, configs().lang().get("errors.general.invalid-time"));
            return;
        }
        long ticks = parsed.getAsLong();

        String worldArg = args.length > 2 ? args[2] : null;
        World world;
        if (worldArg != null) {
            world = Bukkit.getWorld(worldArg);
            if (world == null) {
                messenger().send(sender, configs().lang().get("errors.general.world-not-found"), Map.of("world", worldArg));
                return;
            }
        } else {
            world = ((Player) sender).getWorld();
        }

        try {
            if (isAdd) {
                world.setFullTime(world.getFullTime() + ticks);
            } else {
                world.setTime(ticks);
            }
        } catch (IllegalArgumentException ex) {
            messenger().send(sender, configs().lang().get("errors.general.world-no-clock"), Map.of("world", world.getName()));
            return;
        }

        completeCooldown(sender, args);
        sendResult(sender, isAdd, worldArg != null, ticks, world);
    }

    private String timeFilter() {
        return DurationUnits.filterFor(config().tabComplete(), 2);
    }

    private void sendResult(CommandSender sender, boolean isAdd, boolean explicitWorld, long ticks, World world) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        String time = isAdd
                ? TimeFormatter.ticks(ticks, configs().main().formatting(), configs().lang())
                : TimeFormatter.clock(ticks, configs().main().formatting());
        placeholders.put("time", time);
        placeholders.put("ticks", TimeFormatter.tickCount(ticks, configs().lang()));

        if (explicitWorld) {
            placeholders.put("world", world.getName());
            messenger().send(sender, configs().lang().get(isAdd ? "utilities.time.world.add-in" : "utilities.time.world.set-in"), placeholders);
        } else {
            messenger().send(sender, configs().lang().get(isAdd ? "utilities.time.world.add" : "utilities.time.world.set"), placeholders);
        }
    }
}