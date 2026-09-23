package com.ftxeven.aircore.core.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class CommandDispatch {

    private CommandDispatch() {
    }

    public static Optional<Map.Entry<String, DynamicCommand>> match(Map<String, DynamicCommand> commands, String label) {
        for (Map.Entry<String, DynamicCommand> entry : commands.entrySet()) {
            DynamicCommand command = entry.getValue();
            if (command.enabled() && matchesLabel(command, label)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    private static boolean matchesLabel(DynamicCommand command, String label) {
        return command.name().equalsIgnoreCase(label) || containsIgnoreCase(command.aliases(), label);
    }

    private static boolean containsIgnoreCase(List<String> values, String target) {
        for (String value : values) {
            if (value.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    public static String usage(DynamicCommand command, boolean showOthers) {
        String others = command.usageOthers();
        return showOthers && !others.isBlank() ? others : command.usage();
    }

    public static String format(String template, String label, String subLabel) {
        String formatted = template.replace("%label%", label);
        return subLabel != null ? formatted.replace("%sublabel%", subLabel) : formatted.replace(" %sublabel%", "");
    }

    public static int maxArgs(int required, Availability... trailing) {
        int max = required;
        for (Availability availability : trailing) {
            if (availability != Availability.DISABLED) {
                max++;
            }
        }
        return max;
    }

    public static int minArgs(int required, Availability... trailing) {
        int lastRequired = -1;
        for (int i = 0; i < trailing.length; i++) {
            if (trailing[i] == Availability.REQUIRED) {
                lastRequired = i;
            }
        }
        if (lastRequired < 0) {
            return required;
        }

        int min = required;
        for (int i = 0; i <= lastRequired; i++) {
            if (trailing[i] != Availability.DISABLED) {
                min++;
            }
        }
        return min;
    }

    public static boolean targetFeedbackAllowed(CommandSender sender, UUID target, boolean consoleFeedbackEnabled) {
        boolean actingOnSelf = sender instanceof Player player && player.getUniqueId().equals(target);
        return !actingOnSelf && (sender instanceof Player || consoleFeedbackEnabled);
    }

    public enum Availability {
        REQUIRED, OPTIONAL, DISABLED;

        public static Availability ofPermission(CommandSender sender, String permission) {
            return sender.hasPermission(permission) ? OPTIONAL : DISABLED;
        }

        public static Availability ofConfig(boolean enabled) {
            return enabled ? OPTIONAL : DISABLED;
        }
    }
}