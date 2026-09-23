package com.ftxeven.aircore.command.admin;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.command.CommandDispatcher;
import com.ftxeven.aircore.command.CommandHandler;
import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.module.placeholders.PlaceholdersModule;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.Placeholders;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class SubPlaceholder implements CommandHandler {

    private final AirCore plugin;
    private final Messenger messenger;
    private final ConfigManager configs;

    public SubPlaceholder(AirCore plugin) {
        this.plugin = plugin;
        this.messenger = plugin.messenger();
        this.configs = plugin.configs();
    }

    @Override
    public String name() { return "placeholder"; }

    @Override
    public String permission() { return Permissions.ADMIN; }

    @Override
    public int minArgs() { return 1; }

    @Override
    public int maxArgs() { return -1; }

    @Override
    public String usage() { return "/aircore placeholder <list|parse> [args]"; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> executeList(sender);
            case "parse" -> executeParse(sender, args);
            default -> messenger.send(sender, configs.lang().get("errors.access.incorrect-usage"), Map.of("usage", usage()));
        }
    }

    private void executeList(CommandSender sender) {
        Set<String> keys = new TreeSet<>(plugin.modules().placeholders().keys());

        if (keys.isEmpty()) {
            messenger.send(sender, configs.lang().get("general.commands.placeholders.list.empty"));
            return;
        }

        String entryTemplate = configs.lang().get("general.commands.placeholders.list.entry").getFirst();
        String separator = configs.lang().get("general.commands.placeholders.list.separator").getFirst();

        String rendered = keys.stream()
                .map(key -> Placeholders.apply(sender, entryTemplate, Map.of("placeholder", key)))
                .collect(Collectors.joining(separator));

        messenger.send(sender, configs.lang().get("general.commands.placeholders.list.header"),
                Map.of("count", String.valueOf(keys.size()), "placeholders", rendered));
    }

    private void executeParse(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messenger.send(sender, configs.lang().get("errors.access.incorrect-usage"), Map.of("usage", usage()));
            return;
        }

        String key = args[1];
        List<String> placeholderArgs = args.length > 2
                ? Arrays.asList(args).subList(2, args.length)
                : List.of();

        PlaceholdersModule module = plugin.modules().placeholders();
        if (!module.has(key)) {
            messenger.send(sender, configs.lang().get("general.commands.placeholders.errors.not-found"),
                    Map.of("placeholder", key));
            return;
        }

        String value = module.parse(sender, key, placeholderArgs);
        String display = placeholderArgs.isEmpty() ? key : key + "_" + String.join("_", placeholderArgs);

        messenger.send(sender, configs.lang().get("general.commands.placeholders.parsed"),
                Map.of("placeholder", display, "value", value != null ? value : ""));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandDispatcher.filterPrefix(List.of("list", "parse"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("parse")) {
            return CommandDispatcher.filterPrefix(List.copyOf(plugin.modules().placeholders().keys()), args[1]);
        }
        return List.of();
    }
}