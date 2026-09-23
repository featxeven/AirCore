package com.ftxeven.aircore.command.admin;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.command.CommandDispatcher;
import com.ftxeven.aircore.command.CommandHandler;
import com.ftxeven.aircore.command.Feedback;
import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.core.condition.ExprEvaluator;
import com.ftxeven.aircore.module.variables.VariablesConfig.Constraints;
import com.ftxeven.aircore.module.variables.VariablesConfig.VariableDefinition;
import com.ftxeven.aircore.module.variables.VariablesConfig.VariableScope;
import com.ftxeven.aircore.module.variables.VariablesConfig.VariableType;
import com.ftxeven.aircore.module.variables.VariablesModule;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

public final class SubVariable implements CommandHandler {

    private static final List<String> ACTIONS = List.of("get", "set", "add", "reset", "toggle", "list");

    private final AirCore plugin;
    private final Messenger messenger;
    private final ConfigManager configs;
    private final Feedback feedback;

    public SubVariable(AirCore plugin) {
        this.plugin = plugin;
        this.messenger = plugin.messenger();
        this.configs = plugin.configs();
        this.feedback = new Feedback(configs, messenger, plugin.services());
    }

    @Override
    public String name() { return "variable"; }

    @Override
    public String permission() { return Permissions.ADMIN; }

    @Override
    public int minArgs() { return 1; }

    @Override
    public int maxArgs() { return -1; }

    @Override
    public String usage() { return "/aircore variable <get|set|add|reset|toggle|list> [key] [value] [player]"; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "get" -> executeGet(sender, args);
            case "set" -> executeSet(sender, args);
            case "add" -> executeAdd(sender, args);
            case "reset" -> executeReset(sender, args);
            case "toggle" -> executeToggle(sender, args);
            case "list" -> executeList(sender, args);
            default -> messenger.send(sender, configs.lang().get("errors.access.incorrect-usage"), Map.of("usage", usage()));
        }
    }

    // Actions

    private void executeGet(CommandSender sender, String[] args) {
        if (!requireArgCount(sender, args, 2, "key")) return;
        String key = args[1];

        Optional<VariableDefinition> maybeVariable = requireDefinition(sender, key);
        if (maybeVariable.isEmpty()) return;
        VariableDefinition variable = maybeVariable.get();

        Optional<Target> maybeTarget = resolveTarget(sender, key, variable, args, 2);
        if (maybeTarget.isEmpty()) return;
        Target target = maybeTarget.get();

        VariablesModule module = plugin.modules().variables();
        String value = (target.owner() == null ? module.get(key) : module.get(target.owner(), key)).orElse(variable.defaultValue());

        if (target.owner() == null) {
            messenger.send(sender, configs.lang().get("general.commands.variables.get.global"), Map.of("variable", key, "value", value));
        } else if (target.isSelf()) {
            messenger.send(sender, configs.lang().get("general.commands.variables.get.self"), Map.of("variable", key, "value", value));
        } else {
            messenger.send(sender, configs.lang().get("general.commands.variables.get.other"),
                    Map.of("variable", key, "value", value, "target", target.label()));
        }
    }

    private void executeSet(CommandSender sender, String[] args) {
        if (!requireArgCount(sender, args, 2, "key")) return;
        String key = args[1];
        if (!requireArgCount(sender, args, 3, "value")) return;
        String rawValue = args[2];

        Optional<VariableDefinition> maybeVariable = requireDefinition(sender, key);
        if (maybeVariable.isEmpty()) return;
        VariableDefinition variable = maybeVariable.get();

        if (variable.type() == VariableType.BOOLEAN
                && !rawValue.equalsIgnoreCase("true") && !rawValue.equalsIgnoreCase("false")) {
            error(sender, "general.commands.variables.errors.data.invalid-boolean", Map.of());
            return;
        }
        if ((variable.type() == VariableType.INTEGER || variable.type() == VariableType.DOUBLE)
                && Double.isNaN(ExprEvaluator.parseNumber(rawValue))) {
            error(sender, "general.commands.variables.errors.data.invalid-number", Map.of("value", rawValue));
            return;
        }
        if (variable.type() == VariableType.STRING) {
            Constraints constraints = variable.constraints();
            if (!constraints.allowedValues().isEmpty() && !constraints.allowedValues().contains(rawValue)) {
                error(sender, "general.commands.variables.errors.data.not-allowed-value",
                        Map.of("value", rawValue, "allowed", String.join(", ", constraints.allowedValues())));
                return;
            }
        }

        Optional<Target> maybeTarget = resolveTarget(sender, key, variable, args, 3);
        if (maybeTarget.isEmpty()) return;
        Target target = maybeTarget.get();

        VariablesModule module = plugin.modules().variables();
        boolean applied = target.owner() == null ? module.set(key, rawValue) : module.set(target.owner(), key, rawValue);
        if (!applied) {
            error(sender, "general.commands.variables.errors.argument.invalid-argument", Map.of("value", rawValue, "argument", key));
            return;
        }

        String newValue = (target.owner() == null ? module.get(key) : module.get(target.owner(), key)).orElse(variable.defaultValue());
        notifyWrite(sender, target, "set", Map.of("variable", key, "value", newValue));
    }

    private void executeAdd(CommandSender sender, String[] args) {
        if (!requireArgCount(sender, args, 2, "key")) return;
        String key = args[1];
        if (!requireArgCount(sender, args, 3, "amount")) return;
        String rawAmount = args[2];

        Optional<VariableDefinition> maybeVariable = requireDefinition(sender, key);
        if (maybeVariable.isEmpty()) return;
        VariableDefinition variable = maybeVariable.get();

        if (variable.type() != VariableType.INTEGER && variable.type() != VariableType.DOUBLE) {
            error(sender, "general.commands.variables.errors.data.wrong-type",
                    Map.of("variable", key, "type", variable.type().name().toLowerCase(Locale.ROOT), "expected", "integer or double"));
            return;
        }

        double amount = ExprEvaluator.parseNumber(rawAmount);
        if (Double.isNaN(amount)) {
            error(sender, "general.commands.variables.errors.data.invalid-number", Map.of("value", rawAmount));
            return;
        }

        Optional<Target> maybeTarget = resolveTarget(sender, key, variable, args, 3);
        if (maybeTarget.isEmpty()) return;
        Target target = maybeTarget.get();

        VariablesModule module = plugin.modules().variables();
        boolean applied = target.owner() == null ? module.add(key, amount) : module.add(target.owner(), key, amount);
        if (!applied) {
            error(sender, "general.commands.variables.errors.argument.invalid-argument", Map.of("value", rawAmount, "argument", key));
            return;
        }

        String newValue = (target.owner() == null ? module.get(key) : module.get(target.owner(), key)).orElse(variable.defaultValue());
        notifyWrite(sender, target, "add", Map.of("variable", key, "value", newValue, "amount", rawAmount));
    }

    private void executeReset(CommandSender sender, String[] args) {
        if (!requireArgCount(sender, args, 2, "key")) return;
        String key = args[1];

        Optional<VariableDefinition> maybeVariable = requireDefinition(sender, key);
        if (maybeVariable.isEmpty()) return;
        VariableDefinition variable = maybeVariable.get();

        Optional<Target> maybeTarget = resolveTarget(sender, key, variable, args, 2);
        if (maybeTarget.isEmpty()) return;
        Target target = maybeTarget.get();

        VariablesModule module = plugin.modules().variables();
        boolean applied = target.owner() == null ? module.reset(key) : module.reset(target.owner(), key);
        if (!applied) {
            error(sender, "general.commands.variables.errors.argument.invalid-argument", Map.of("value", variable.defaultValue(), "argument", key));
            return;
        }

        notifyWrite(sender, target, "reset", Map.of("variable", key, "value", variable.defaultValue()));
    }

    private void executeToggle(CommandSender sender, String[] args) {
        if (!requireArgCount(sender, args, 2, "key")) return;
        String key = args[1];

        Optional<VariableDefinition> maybeVariable = requireDefinition(sender, key);
        if (maybeVariable.isEmpty()) return;
        VariableDefinition variable = maybeVariable.get();

        if (variable.type() != VariableType.BOOLEAN) {
            error(sender, "general.commands.variables.errors.data.wrong-type",
                    Map.of("variable", key, "type", variable.type().name().toLowerCase(Locale.ROOT), "expected", "boolean"));
            return;
        }

        Optional<Target> maybeTarget = resolveTarget(sender, key, variable, args, 2);
        if (maybeTarget.isEmpty()) return;
        Target target = maybeTarget.get();

        VariablesModule module = plugin.modules().variables();
        boolean applied = target.owner() == null ? module.toggle(key) : module.toggle(target.owner(), key);
        if (!applied) {
            error(sender, "general.commands.variables.errors.argument.invalid-argument", Map.of("value", key, "argument", key));
            return;
        }

        boolean newValue = Boolean.parseBoolean(
                (target.owner() == null ? module.get(key) : module.get(target.owner(), key)).orElse(variable.defaultValue()));
        notifyToggle(sender, target, key, newValue);
    }

    private void executeList(CommandSender sender, String[] args) {
        VariablesModule module = plugin.modules().variables();
        boolean playerGiven = args.length > 1;

        UUID owner = null;
        List<String> keys;

        if (playerGiven) {
            String typedName = args[1];
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(typedName);
            if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
                messenger.send(sender, configs.lang().get("errors.access.player-never-joined"), Map.of("player", typedName));
                return;
            }
            owner = targetPlayer.getUniqueId();
            keys = new TreeSet<>(module.keys()).stream()
                    .filter(key -> module.definition(key).map(d -> d.scope() == VariableScope.PLAYER).orElse(false))
                    .toList();
        } else {
            keys = List.copyOf(new TreeSet<>(module.keys()));
        }

        if (keys.isEmpty()) {
            messenger.send(sender, configs.lang().get("general.commands.variables.list.empty"), Map.of());
            return;
        }

        String entryTemplate = configs.lang().get("general.commands.variables.list.entry").getFirst();
        String separator = configs.lang().get("general.commands.variables.list.separator").getFirst();
        UUID finalOwner = owner;

        String rendered = keys.stream()
                .map(key -> renderListEntry(sender, module, key, finalOwner, entryTemplate))
                .collect(Collectors.joining(separator));

        messenger.send(sender, configs.lang().get("general.commands.variables.list.header"),
                Map.of("count", String.valueOf(keys.size()), "variables", rendered));
    }

    private String renderListEntry(CommandSender sender, VariablesModule module, String key, @Nullable UUID owner, String template) {
        VariableDefinition variable = module.definition(key).orElseThrow();
        String value = (owner == null
                ? (variable.scope() == VariableScope.GLOBAL ? module.get(key) : Optional.<String>empty())
                : module.get(owner, key)).orElse(variable.defaultValue());

        Map<String, String> placeholders = Map.of(
                "variable", key,
                "type", variable.type().name().toLowerCase(Locale.ROOT),
                "scope", variable.scope().name().toLowerCase(Locale.ROOT),
                "value", value
        );
        return Placeholders.apply(sender, template, placeholders);
    }

    // Shared helpers

    private boolean requireArgCount(CommandSender sender, String[] args, int required, String argumentName) {
        if (args.length >= required) {
            return true;
        }
        error(sender, "general.commands.variables.errors.argument.missing-argument", Map.of("argument", argumentName));
        return false;
    }

    private Optional<VariableDefinition> requireDefinition(CommandSender sender, String key) {
        Optional<VariableDefinition> definition = plugin.modules().variables().definition(key);
        if (definition.isEmpty()) {
            error(sender, "general.commands.variables.errors.argument.unknown-variable", Map.of("variable", key));
        }
        return definition;
    }

    private Optional<Target> resolveTarget(CommandSender sender, String key, VariableDefinition variable, String[] args, int playerIndex) {
        boolean playerGiven = args.length > playerIndex;

        if (variable.scope() == VariableScope.GLOBAL) {
            if (playerGiven) {
                error(sender, "general.commands.variables.errors.data.not-player-scoped", Map.of("variable", key));
                return Optional.empty();
            }
            return Optional.of(Target.global());
        }

        if (playerGiven) {
            String typedName = args[playerIndex];
            OfflinePlayer target = Bukkit.getOfflinePlayer(typedName);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                messenger.send(sender, configs.lang().get("errors.access.player-never-joined"), Map.of("player", typedName));
                return Optional.empty();
            }
            if (sender instanceof Player self && self.getUniqueId().equals(target.getUniqueId())) {
                return Optional.of(Target.self(self));
            }
            return Optional.of(Target.other(target, typedName));
        }

        if (sender instanceof Player self) {
            return Optional.of(Target.self(self));
        }

        error(sender, "general.commands.variables.errors.data.not-global-scoped", Map.of("variable", key));
        return Optional.empty();
    }

    private void notifyWrite(CommandSender sender, Target target, String action, Map<String, String> basePlaceholders) {
        String base = "general.commands.variables." + action + ".";

        if (target.owner() == null) {
            messenger.send(sender, configs.lang().get(base + "global"), basePlaceholders);
            return;
        }
        if (target.isSelf()) {
            messenger.send(sender, configs.lang().get(base + "self"), basePlaceholders);
            return;
        }

        Map<String, String> withTarget = new LinkedHashMap<>(basePlaceholders);
        withTarget.put("target", target.label());
        messenger.send(sender, configs.lang().get(base + "other"), withTarget);

        feedback.notifyTarget(sender, target.owner(), configs.lang().get(base + "by"), basePlaceholders);
    }

    private void notifyToggle(CommandSender sender, Target target, String key, boolean newValue) {
        String suffix = newValue ? "enabled" : "disabled";
        String base = "general.commands.variables.toggle.";
        Map<String, String> placeholders = Map.of("variable", key);

        if (target.owner() == null) {
            messenger.send(sender, configs.lang().get(base + suffix + "-global"), placeholders);
            return;
        }
        if (target.isSelf()) {
            messenger.send(sender, configs.lang().get(base + suffix), placeholders);
            return;
        }

        Map<String, String> withTarget = new LinkedHashMap<>(placeholders);
        withTarget.put("target", target.label());
        messenger.send(sender, configs.lang().get(base + suffix + "-for"), withTarget);

        feedback.notifyTarget(sender, target.owner(), configs.lang().get(base + suffix + "-by"), placeholders);
    }

    private void error(CommandSender sender, String path, Map<String, String> placeholders) {
        messenger.send(sender, configs.lang().get(path), placeholders);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandDispatcher.filterPrefix(ACTIONS, args[0]);
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) {
            return List.of();
        }

        if (args.length == 2 && !action.equals("list")) {
            return CommandDispatcher.filterPrefix(List.copyOf(plugin.modules().variables().keys()), args[1]);
        }

        int playerIndex = switch (action) {
            case "list" -> 1;
            case "set", "add" -> 3;
            default -> 2; // get, reset, toggle
        };

        if (args.length - 1 == playerIndex) {
            List<String> onlineNames = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            return CommandDispatcher.filterPrefix(onlineNames, args[playerIndex]);
        }

        return List.of();
    }

    private record Target(@Nullable UUID owner, boolean isSelf, String label) {
        static Target global() {
            return new Target(null, false, null);
        }

        static Target self(Player player) {
            return new Target(player.getUniqueId(), true, player.getName());
        }

        static Target other(OfflinePlayer player, String typedName) {
            String name = player.getName() != null ? player.getName() : typedName;
            return new Target(player.getUniqueId(), false, name);
        }
    }
}