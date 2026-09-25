package com.ftxeven.aircore.command.player.chat;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.module.chat.ChatModule;
import com.ftxeven.aircore.module.chat.pm.PrivateMessageHandler;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class MsgCommand extends BaseCommand {

    private static final String KEY = "msg";

    private final Supplier<ChatModule> chatModule;
    private final String everyonePermission = Permissions.Command.all(KEY);

    public MsgCommand(Context ctx, Supplier<ChatModule> chatModule) {
        super(ctx, KEY);
        this.chatModule = chatModule;
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
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        if (selectors().isAll(targetToken)) {
            if (!checkPermission(sender, everyonePermission)) return;

            UUID excluded = sender instanceof Player player ? player.getUniqueId() : null;
            if (requireOtherPlayersOnline(sender, excluded).isEmpty()) return;

            if (chatModule.get().privateMessages().sendToEveryone(sender, message) >= 0) {
                onSent(sender, args);
            }
            return;
        }

        Optional<Player> target = requireFound(resolver().onlinePlayer(targetToken), sender, targetToken);
        if (target.isEmpty()) return;

        PrivateMessageHandler.SendResult result = chatModule.get().privateMessages().sendToPlayer(sender, target.get(), message);
        if (result == PrivateMessageHandler.SendResult.SENT) {
            onSent(sender, args);
        }
    }

    private void onSent(CommandSender sender, String[] args) {
        completeCooldown(sender, args);
    }
}