package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.command.AbstractCommand;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class WeatherCommand extends AbstractCommand {

    private static final String KEY = "weather";
    private static final String THUNDER = "thunder";

    public WeatherCommand(Context ctx) {
        super(ctx, KEY);
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public int minArgs(CommandSender sender) {
        return sender instanceof Player ? 1 : 2;
    }

    @Override
    public int maxArgs(CommandSender sender) { return 2; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Optional<String> action = resolveAction(args[0]);
        if (action.isEmpty()) {
            sendUsageError(sender, label, subLabel);
            return;
        }

        String worldArg = args.length > 1 ? args[1] : null;
        boolean explicitWorld = worldArg != null;
        World world;
        if (explicitWorld) {
            world = Bukkit.getWorld(worldArg);
            if (world == null) {
                messenger().send(sender, configs().lang().get("errors.general.world-not-found"), Map.of("world", worldArg));
                return;
            }
        } else {
            world = ((Player) sender).getWorld();
        }

        applyWeather(world, action.get());
        completeCooldown(sender, args);
        sendResult(sender, action.get(), explicitWorld, world);
    }

    private void applyWeather(World world, String action) {
        boolean storm = !action.equals("clear");
        boolean thundering = action.equals(THUNDER);

        world.setStorm(storm);
        world.setThundering(thundering);
    }

    private void sendResult(CommandSender sender, String action, boolean explicitWorld, World world) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("weather", weatherLabel(action));

        if (explicitWorld) {
            placeholders.put("world", world.getName());
            messenger().send(sender, configs().lang().get("utilities.weather.world.set-in"), placeholders);
        } else {
            messenger().send(sender, configs().lang().get("utilities.weather.world.set"), placeholders);
        }
    }

    private String weatherLabel(String action) {
        return configs().lang().get("utilities.weather.values." + action).getFirst();
    }
}