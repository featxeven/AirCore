package com.ftxeven.aircore.module.chat;

import com.ftxeven.aircore.config.MainConfig.MessageFormat;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.MiniText;
import org.bukkit.permissions.Permissible;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlayerInputSanitizer {

    private static final Pattern LEGACY_HEX = Pattern.compile("[&§]#([0-9a-fA-F]{6})");
    private static final Pattern LEGACY_CODE = Pattern.compile("[&§]([0-9a-fk-orA-FK-OR])");
    private static final Pattern MINI_TAG_NAME = Pattern.compile("</?(#[0-9a-fA-F]{6}|[a-zA-Z_]+)");

    private PlayerInputSanitizer() {
    }

    public static String sanitize(String raw, MessageFormat mode, Permissible sender) {
        boolean legacyAllowed = mode != MessageFormat.MINI;
        boolean miniAllowed = mode != MessageFormat.LEGACY;

        Matcher legacyHex = LEGACY_HEX.matcher(raw);
        Matcher legacyCode = LEGACY_CODE.matcher(raw);
        Matcher miniName = MINI_TAG_NAME.matcher(raw);

        StringBuilder out = new StringBuilder(raw.length());
        StringBuilder literal = new StringBuilder();
        Deque<OpenTag> open = new ArrayDeque<>();

        int i = 0;
        int length = raw.length();
        while (i < length) {
            char c = raw.charAt(i);

            if (legacyAllowed && (c == '&' || c == '§')) {
                int end = tryLegacy(raw, i, legacyHex, legacyCode, out, literal, sender);
                if (end > i) {
                    i = end;
                    continue;
                }
            }

            if (miniAllowed && c == '<') {
                int end = tryMini(raw, i, miniName, out, literal, sender, open);
                if (end > i) {
                    i = end;
                    continue;
                }
            }

            literal.append(c);
            i++;
        }
        flushLiteral(out, literal);
        return out.toString();
    }

    private static int tryLegacy(String raw, int pos, Matcher hex, Matcher code, StringBuilder out, StringBuilder literal, Permissible sender) {
        if (hex.find(pos) && hex.start() == pos) {
            flushLiteral(out, literal);
            boolean allowed = sender.hasPermission(Permissions.Access.color("hex"));
            emit(out, raw.substring(pos, hex.end()), "<#" + hex.group(1) + ">", allowed);
            return hex.end();
        }
        if (code.find(pos) && code.start() == pos) {
            flushLiteral(out, literal);
            char letter = Character.toLowerCase(code.group(1).charAt(0));
            String tagName = LegacyCodeTranslator.tagFor(letter);
            boolean allowed = checkTagPermission(tagName, "", sender);
            emit(out, raw.substring(pos, code.end()), "<" + tagName + ">", allowed);
            return code.end();
        }
        return pos;
    }

    private static int tryMini(String raw, int pos, Matcher name, StringBuilder out, StringBuilder literal, Permissible sender, Deque<OpenTag> open) {
        if (!(name.find(pos) && name.start() == pos)) {
            return pos;
        }

        boolean closing = raw.charAt(pos + 1) == '/';
        String tagName = name.group(1).toLowerCase(Locale.ROOT);
        int close = findTagEnd(raw, name.end());
        if (close < 0) {
            return pos;
        }

        String args = closing ? "" : raw.substring(name.end(), close);
        flushLiteral(out, literal);

        boolean allowed = closing ? resolveClose(tagName, sender, open) : resolveOpen(tagName, args, sender, open);
        String rawTag = raw.substring(pos, close + 1);
        emit(out, rawTag, rawTag, allowed);
        return close + 1;
    }

    private static boolean resolveOpen(String name, String args, Permissible sender, Deque<OpenTag> open) {
        boolean allowed = checkTagPermission(name, args, sender);
        open.push(new OpenTag(name, allowed));
        return allowed;
    }

    private static boolean resolveClose(String name, Permissible sender, Deque<OpenTag> open) {
        for (Iterator<OpenTag> it = open.iterator(); it.hasNext(); ) {
            OpenTag tag = it.next();
            if (tag.name().equals(name)) {
                it.remove();
                return tag.allowed();
            }
        }
        return checkTagPermission(name, "", sender);
    }

    private static boolean checkTagPermission(String name, String args, Permissible sender) {
        if (name.startsWith("#")) {
            return sender.hasPermission(Permissions.Access.color("hex"));
        }
        return switch (name) {
            case "reset" -> sender.hasPermission(Permissions.Access.format("reset"));
            case "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
                 "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white" ->
                    sender.hasPermission(Permissions.Access.color(name));
            case "color", "colour", "c" -> colorArgPermission(args, sender);
            case "bold", "b" -> sender.hasPermission(Permissions.Access.decoration("bold"));
            case "italic", "i", "em" -> sender.hasPermission(Permissions.Access.decoration("italic"));
            case "underlined", "u" -> sender.hasPermission(Permissions.Access.decoration("underlined"));
            case "strikethrough", "st" -> sender.hasPermission(Permissions.Access.decoration("strikethrough"));
            case "obfuscated", "obf" -> sender.hasPermission(Permissions.Access.decoration("obfuscated"));
            case "shadow" -> sender.hasPermission(Permissions.Access.decoration("shadow"));
            case "rainbow" -> sender.hasPermission(Permissions.Access.format("rainbow"));
            case "gradient" -> sender.hasPermission(Permissions.Access.format("gradient"));
            case "transition" -> sender.hasPermission(Permissions.Access.format("transition"));
            case "pride" -> sender.hasPermission(Permissions.Access.format("pride"));
            case "font" -> sender.hasPermission(Permissions.Access.format("font"));
            case "insert" -> sender.hasPermission(Permissions.Access.format("insertion"));
            case "hover" -> sender.hasPermission(Permissions.Access.format("hover"));
            case "click" -> clickPermission(args, sender);
            case "key" -> sender.hasPermission(Permissions.Access.format("keybind"));
            case "lang", "tr", "translate", "lang_or", "tr_or", "translate_or" ->
                    sender.hasPermission(Permissions.Access.format("translatable"));
            case "newline", "br", "nl" -> sender.hasPermission(Permissions.Access.format("newline"));
            case "selector", "sel" -> sender.hasPermission(Permissions.Access.format("selector"));
            case "score" -> sender.hasPermission(Permissions.Access.format("score"));
            case "nbt", "data" -> sender.hasPermission(Permissions.Access.format("nbt"));
            case "sprite" -> sender.hasPermission(Permissions.Access.format("sprite"));
            case "head" -> sender.hasPermission(Permissions.Access.format("head"));
            default -> false;
        };
    }

    private static boolean colorArgPermission(String args, Permissible sender) {
        String value = firstArg(args);
        return value.startsWith("#")
                ? sender.hasPermission(Permissions.Access.color("hex"))
                : sender.hasPermission(Permissions.Access.color(value));
    }

    private static boolean clickPermission(String args, Permissible sender) {
        String action = firstArg(args);
        if (action.equals("open_url") || action.equals("copy_to_clipboard")) {
            return sender.hasPermission(Permissions.Access.click(true));
        }
        if (action.equals("run_command") || action.equals("suggest_command")) {
            return sender.hasPermission(Permissions.Access.click(false));
        }
        return false;
    }

    private static String firstArg(String args) {
        if (!args.startsWith(":")) {
            return "";
        }
        String body = args.substring(1);
        int colon = body.indexOf(':');
        return (colon < 0 ? body : body.substring(0, colon)).toLowerCase(Locale.ROOT);
    }

    private static void emit(StringBuilder out, String rawMatch, String functional, boolean allowed) {
        out.append(allowed ? functional : MiniText.mini().escapeTags(rawMatch));
    }

    private static void flushLiteral(StringBuilder out, StringBuilder literal) {
        if (!literal.isEmpty()) {
            out.append(MiniText.mini().escapeTags(literal.toString()));
            literal.setLength(0);
        }
    }

    private static int findTagEnd(String input, int from) {
        boolean inQuotes = false;
        for (int i = from; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\'') {
                inQuotes = !inQuotes;
            } else if (c == '>' && !inQuotes) {
                return i;
            }
        }
        return -1;
    }

    private record OpenTag(String name, boolean allowed) {}
}