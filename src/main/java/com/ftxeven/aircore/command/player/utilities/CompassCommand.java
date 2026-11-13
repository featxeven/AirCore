package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CompassCommand extends BaseCommand {

    private static final String KEY = "compass";
    private static final String VALUES = "utilities.compass.values.";

    private static final String[] HEADINGS = {
            "north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"
    };

    public CompassCommand(Context ctx) {
        super(ctx, KEY);
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public boolean playerOnly() { return true; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Player player = (Player) sender;
        double bearing = bearing(player.getLocation().getYaw());
        String heading = HEADINGS[(int) Math.round(bearing / 45) % HEADINGS.length];

        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("direction", configs().lang().get(VALUES + heading).getFirst());
        placeholders.put("degrees", String.valueOf(Math.round(bearing) % 360));

        completeCooldown(sender, args);
        messenger().send(sender, configs().lang().get("utilities.compass.facing"), placeholders);
    }

    private static double bearing(float yaw) {
        return ((yaw + 180.0) % 360.0 + 360.0) % 360.0;
    }
}