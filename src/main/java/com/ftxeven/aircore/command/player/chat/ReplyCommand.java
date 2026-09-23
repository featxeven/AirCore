package com.ftxeven.aircore.command.player.chat;

import com.ftxeven.aircore.command.AbstractCommand;
import com.ftxeven.aircore.module.chat.ChatModule;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

public final class ReplyCommand extends AbstractCommand {

    private static final String KEY = "reply";

    private final Supplier<ChatModule> chatModule;

    public ReplyCommand(Context ctx, Supplier<ChatModule> chatModule) {
        super(ctx, KEY);
        this.chatModule = chatModule;
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
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Player player = (Player) sender;
        String message = String.join(" ", args);
        if (chatModule.get().privateMessages().reply(player, message)) {
            completeCooldown(sender, args);
        }
    }
}