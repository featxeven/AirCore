package com.ftxeven.aircore.command.player.chat;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.core.command.CommandDispatch.Availability;
import com.ftxeven.aircore.module.chat.ChatConfig;
import com.ftxeven.aircore.module.chat.ChatModule;
import com.ftxeven.aircore.module.chat.channel.ChannelMembership;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Supplier;

public final class ChannelCommand extends BaseCommand {

    private static final String KEY = "channel";

    private final Supplier<ChatModule> chatModule;
    private final String othersPermission = Permissions.Command.others(KEY);
    private final String allPermission = Permissions.Command.all(KEY);

    public ChannelCommand(Context ctx, Supplier<ChatModule> chatModule) {
        super(ctx, KEY);
        this.chatModule = chatModule;
    }

    @Override
    public boolean enabled() { return super.enabled() && configs().chat().channels().enabled(); }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public int maxArgs(CommandSender sender) {
        return CommandDispatch.maxArgs(1, Availability.ofConfig(canTargetOthers(sender)));
    }

    @Override
    public String usage(CommandSender sender) {
        return CommandDispatch.usage(config(), canTargetOthers(sender));
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        if (args.length == 0) {
            listChannels(sender);
            return;
        }

        String channelKey = args[0];
        ChatConfig.ChannelDefinition definition = configs().chat().channels().definitions().get(channelKey);
        if (definition == null) {
            messenger().send(sender, configs().lang().get("chat.channel.errors.not-found"), Map.of("channel", channelKey));
            return;
        }

        if (args.length == 1) {
            switchSelf(sender, args, channelKey);
            return;
        }

        String targetToken = args[1];
        if (selectors().isAll(targetToken)) {
            switchAll(sender, args, channelKey);
        } else {
            switchOther(sender, args, channelKey, targetToken);
        }
    }

    // Internal

    private boolean canTargetOthers(CommandSender sender) {
        return sender.hasPermission(othersPermission) || sender.hasPermission(allPermission);
    }

    private List<String> joinableChannels(CommandSender sender) {
        return configs().chat().channels().joinableBy(sender);
    }

    private void listChannels(CommandSender sender) {
        List<String> visible = joinableChannels(sender);
        if (visible.isEmpty()) {
            messenger().send(sender, configs().lang().get("chat.channel.list.empty"));
            return;
        }

        Map<String, ChatConfig.ChannelDefinition> definitions = configs().chat().channels().definitions();
        String entryTemplate = configs().lang().get("chat.channel.list.entry").getFirst();
        String separator = configs().lang().get("chat.channel.list.separator").getFirst();
        String noDescription = configs().lang().get("chat.channel.list.no-description").getFirst();

        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < visible.size(); i++) {
            if (i > 0) {
                joined.append(separator);
            }
            String key = visible.get(i);
            String description = definitions.get(key).description();
            joined.append(entryTemplate
                    .replace("%channel%", key)
                    .replace("%description%", description.isEmpty() ? noDescription : description));
        }

        messenger().send(sender, configs().lang().get("chat.channel.list.header"), Map.of(
                "count", String.valueOf(visible.size()),
                "channels", joined.toString()
        ));
    }

    private void switchSelf(CommandSender sender, String[] args, String channelKey) {
        Optional<Player> resolved = requirePlayer(sender);
        if (resolved.isEmpty()) return;
        Player player = resolved.get();

        if (!Permissions.Access.hasChannel(player, channelKey)) {
            messenger().send(sender, configs().lang().get("chat.channel.errors.no-permission"), Map.of("channel", channelKey));
            return;
        }

        ChannelMembership membership = chatModule.get().channelMembership();
        if (membership.current(player).equals(channelKey)) {
            messenger().send(sender, configs().lang().get("chat.channel.errors.already-in"), Map.of("channel", channelKey));
            return;
        }

        membership.switchTo(player, channelKey);
        completeCooldown(sender, args);
        messenger().send(sender, configs().lang().get("chat.channel.switched"), Map.of("channel", channelKey));
    }

    private void switchOther(CommandSender sender, String[] args, String channelKey, String targetToken) {
        if (!checkPermission(sender, othersPermission)) return;

        Optional<Player> target = requireFound(resolver().onlinePlayer(targetToken), sender, targetToken);
        if (target.isEmpty()) return;
        Player targetPlayer = target.get();

        ChannelMembership membership = chatModule.get().channelMembership();
        if (membership.current(targetPlayer).equals(channelKey)) {
            messenger().send(sender, configs().lang().get("chat.channel.errors.already-in-for"), targetPlaceholders(targetPlayer.getUniqueId(), channelKey));
            return;
        }

        membership.switchTo(targetPlayer, channelKey);
        completeCooldown(sender, args);
        messenger().send(sender, configs().lang().get("chat.channel.switched-for"), targetPlaceholders(targetPlayer.getUniqueId(), channelKey));
        notifyTarget(sender, targetPlayer.getUniqueId(), configs().lang().get("chat.channel.switched-by"), Map.of("channel", channelKey));
    }

    private void switchAll(CommandSender sender, String[] args, String channelKey) {
        if (!checkPermission(sender, allPermission)) return;

        ChannelMembership membership = chatModule.get().channelMembership();
        Map<String, String> byPlaceholders = Map.of("channel", channelKey);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (membership.current(online).equals(channelKey)) {
                continue;
            }
            membership.switchTo(online, channelKey);
            notifyTarget(sender, online.getUniqueId(), configs().lang().get("chat.channel.switched-by"), byPlaceholders);
        }

        completeCooldown(sender, args);
        messenger().send(sender, configs().lang().get("chat.channel.switched-all"), Map.of("channel", channelKey));
    }

    private Map<String, String> targetPlaceholders(UUID targetUuid, String channelKey) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        services().players().formatDisplayName(placeholders, "target", targetUuid);
        placeholders.put("channel", channelKey);
        return placeholders;
    }
}