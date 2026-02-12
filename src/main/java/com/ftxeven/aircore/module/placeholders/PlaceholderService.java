package com.ftxeven.aircore.module.placeholders;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;

public final class PlaceholderService {

    public String resolve(OfflinePlayer player, PlaceholderLoader.PlaceholderEntry entry) {
        for (PlaceholderLoader.PlaceholderTier tier : entry.tiers()) {
            boolean allMet = true;
            for (String condition : tier.conditions()) {
                if (!evaluate(player, condition)) {
                    allMet = false;
                    break;
                }
            }

            if (allMet) {
                return apply(player, tier.output());
            } else if (tier.fallback() != null) {
                return apply(player, tier.fallback());
            }
        }
        return null;
    }

    private String apply(OfflinePlayer player, String text) {
        if (text == null) return "";
        String processed = text;
        if (player != null && player.getName() != null) {
            processed = processed.replace("%player%", player.getName());
        }
        return PlaceholderAPI.setPlaceholders(player, processed);
    }

    private boolean evaluate(OfflinePlayer player, String expression) {
        String parsed = PlaceholderAPI.setPlaceholders(player, expression);
        String[] operators = {"==", "!=", "=~", "!~", "|-", "-|", "<>", "><", ">=", "<=", ">", "<"};

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

        String l = left.toLowerCase();
        String r = right.toLowerCase();

        return switch (foundOp) {
            case "==" -> left.equals(right);
            case "!=" -> !left.equals(right);
            case "=~" -> left.equalsIgnoreCase(right);
            case "!~" -> !left.equalsIgnoreCase(right);
            case "|-" -> l.startsWith(r);
            case "-|" -> l.endsWith(r);
            case "<>" -> l.contains(r);
            case "><" -> !l.contains(r);
            case ">"  -> compare(left, right) > 0;
            case "<"  -> compare(left, right) < 0;
            case ">=" -> compare(left, right) >= 0;
            case "<=" -> compare(left, right) <= 0;
            default -> false;
        };
    }

    private double compare(String left, String right) {
        try { return Double.parseDouble(left) - Double.parseDouble(right); }
        catch (NumberFormatException e) { return 0; }
    }
}