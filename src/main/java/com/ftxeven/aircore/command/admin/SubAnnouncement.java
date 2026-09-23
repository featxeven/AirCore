package com.ftxeven.aircore.command.admin;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.command.CommandDispatcher;
import com.ftxeven.aircore.command.CommandHandler;
import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.module.announcements.AnnouncementsModule;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.Placeholders;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class SubAnnouncement implements CommandHandler {

    private final AirCore plugin;
    private final Messenger messenger;
    private final ConfigManager configs;

    public SubAnnouncement(AirCore plugin) {
        this.plugin = plugin;
        this.messenger = plugin.messenger();
        this.configs = plugin.configs();
    }

    @Override
    public String name() { return "announcement"; }

    @Override
    public String permission() { return Permissions.ADMIN; }

    @Override
    public int minArgs() { return 1; }

    @Override
    public int maxArgs() { return -1; }

    @Override
    public String usage() { return "/aircore announcement <list|trigger> [key] [args]"; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "trigger" -> executeTrigger(sender, args);
            case "list" -> executeList(sender);
            default -> messenger.send(sender, configs.lang().get("errors.access.incorrect-usage"), Map.of("usage", usage()));
        }
    }

    private void executeTrigger(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messenger.send(sender, configs.lang().get("errors.access.incorrect-usage"), Map.of("usage", usage()));
            return;
        }

        String key = args[1];
        List<String> triggerArgs = args.length > 2 ? parseQuotedArgs(args, 2) : List.of();

        AnnouncementsModule module = plugin.modules().announcements();
        if (!module.keys().contains(key)) {
            messenger.send(sender, configs.lang().get("general.commands.announcements.errors.not-found"), Map.of("announcement", key));
            return;
        }

        if (!module.trigger(key, triggerArgs)) {
            messenger.send(sender, configs.lang().get("general.commands.announcements.errors.disabled"), Map.of("announcement", key));
            return;
        }

        messenger.send(sender, configs.lang().get("general.commands.announcements.triggered"), Map.of("announcement", key));
    }

    private static List<String> parseQuotedArgs(String[] args, int from) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = null;

        for (int i = from; i < args.length; i++) {
            String part = args[i];

            if (current != null) {
                current.append(' ').append(part);
                if (part.endsWith("\"")) {
                    tokens.add(current.substring(1, current.length() - 1)); // strip wrapping quotes
                    current = null;
                }
                continue;
            }

            if (part.length() >= 2 && part.startsWith("\"") && part.endsWith("\"")) {
                tokens.add(part.substring(1, part.length() - 1)); // single quoted word
            } else if (part.startsWith("\"")) {
                current = new StringBuilder(part); // opening quote, keep accumulating
            } else {
                tokens.add(part);
            }
        }

        if (current != null) {
            tokens.add(current.substring(1)); // unterminated quote
        }

        return tokens;
    }

    private void executeList(CommandSender sender) {
        AnnouncementsModule module = plugin.modules().announcements();
        Set<String> keys = new TreeSet<>(module.keys());

        if (keys.isEmpty()) {
            messenger.send(sender, configs.lang().get("general.commands.announcements.list.empty"));
            return;
        }

        String enabledTemplate = configs.lang().get("general.commands.announcements.list.entry-enabled").getFirst();
        String disabledTemplate = configs.lang().get("general.commands.announcements.list.entry-disabled").getFirst();
        String fileDisabledTemplate = configs.lang().get("general.commands.announcements.list.entry-file-disabled").getFirst();
        String separator = configs.lang().get("general.commands.announcements.list.separator").getFirst();

        String rendered = keys.stream()
                .map(key -> Placeholders.apply(sender, entryTemplate(module, key, enabledTemplate, disabledTemplate, fileDisabledTemplate), Map.of("announcement", key)))
                .collect(Collectors.joining(separator));

        messenger.send(sender, configs.lang().get("general.commands.announcements.list.header"),
                Map.of("count", String.valueOf(keys.size()), "announcements", rendered));
    }

    private String entryTemplate(AnnouncementsModule module, String key, String enabled, String disabled, String fileDisabled) {
        if (module.isDisabledByFile(key)) {
            return fileDisabled;
        }
        return module.isEnabled(key) ? enabled : disabled;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandDispatcher.filterPrefix(List.of("trigger", "list"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("trigger")) {
            return CommandDispatcher.filterPrefix(List.copyOf(plugin.modules().announcements().keys()), args[1]);
        }
        return List.of();
    }
}