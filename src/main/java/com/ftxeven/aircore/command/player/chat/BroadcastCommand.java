package com.ftxeven.aircore.command.player.chat;

import com.ftxeven.aircore.command.AbstractCommand;
import com.ftxeven.aircore.config.MainConfig;
import com.ftxeven.aircore.module.chat.PlayerTextRenderer;
import com.ftxeven.aircore.permission.Permissions;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.util.Map;

public final class BroadcastCommand extends AbstractCommand {

    private static final String KEY = "broadcast";
    private static final String MESSAGE_TAG = "message";

    public BroadcastCommand(Context ctx) {
        super(ctx, KEY);
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public int minArgs() { return 1; }

    @Override
    public int maxArgs() { return -1; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        String rawMessage = String.join(" ", args);
        MainConfig.MessageFormat mode = configs().main().formatting().messageFormat();
        Component message = PlayerTextRenderer.render(rawMessage, mode, sender);

        var template = configs().lang().get("chat.broadcast").stream()
                .map(line -> line.replace("%message%", "<" + MESSAGE_TAG + ">"))
                .toList();

        messenger().broadcastWithInsertion(template, Map.of(), MESSAGE_TAG, message);
        completeCooldown(sender, args);
    }
}