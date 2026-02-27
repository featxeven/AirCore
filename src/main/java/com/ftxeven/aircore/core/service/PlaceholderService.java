package com.ftxeven.aircore.core.service;

import com.ftxeven.aircore.config.PlaceholderManager;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;
import java.util.Map;

public final class PlaceholderService {

    public String resolve(OfflinePlayer player, PlaceholderManager.PlaceholderEntry entry, Map<String, String> context, String originalKey) {
        for (PlaceholderManager.PlaceholderTier tier : entry.tiers()) {
            boolean allMet = true;

            for (String condition : tier.conditions()) {
                String internal = applyInternal(player, condition, context);
                String parsed = PlaceholderAPI.setPlaceholders(player, internal);

                if (parsed.contains("%")) {
                    allMet = false;
                    break;
                }

                if (!evaluateRaw(parsed)) {
                    allMet = false;
                    break;
                }
            }

            if (allMet) {
                return apply(player, tier.output(), context);
            }

            if (tier.fallback() != null) {
                return apply(player, tier.fallback(), context);
            }

        }

        return "%" + originalKey + "%";
    }

    private String apply(OfflinePlayer player, String text, Map<String, String> context) {
        if (text == null) return "";
        String internal = applyInternal(player, text, context);
        return PlaceholderAPI.setPlaceholders(player, internal);
    }

    private boolean evaluateRaw(String parsed) {
        String[] operators = {"==", "!=", "=~", "!~", ">=", "<=", ">", "<"};
        String foundOp = null;
        int opIndex = -1;

        for (String op : operators) {
            int index = parsed.indexOf(" " + op + " ");
            if (index != -1) {
                foundOp = op;
                opIndex = index;
                break;
            }
        }

        if (foundOp == null) return Boolean.parseBoolean(parsed.trim());

        String left = parsed.substring(0, opIndex).trim();
        String right = parsed.substring(opIndex + foundOp.length() + 1).trim();

        if (right.startsWith("'") && right.endsWith("'")) {
            right = right.substring(1, right.length() - 1);
        }

        return switch (foundOp) {
            case "==" -> left.equals(right);
            case "!=" -> !left.equals(right);
            case "=~" -> left.equalsIgnoreCase(right);
            case "!~" -> !left.equalsIgnoreCase(right);
            case ">"  -> compare(left, right) > 0;
            case "<"  -> compare(left, right) < 0;
            case ">=" -> compare(left, right) >= 0;
            case "<=" -> compare(left, right) <= 0;
            default -> false;
        };
    }

    private String applyInternal(OfflinePlayer player, String text, Map<String, String> context) {
        if (text == null) return "";
        String result = text;

        if (player != null && player.getName() != null) {
            result = result.replace("%player%", player.getName());
        }

        if (context != null && !context.isEmpty()) {
            for (Map.Entry<String, String> entry : context.entrySet()) {
                result = result.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }
        return result;
    }

    private double compare(String left, String right) {
        try {
            return Double.parseDouble(left) - Double.parseDouble(right);
        } catch (NumberFormatException e) {
            return left.compareTo(right);
        }
    }
}