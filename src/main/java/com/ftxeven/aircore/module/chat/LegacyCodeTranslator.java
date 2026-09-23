package com.ftxeven.aircore.module.chat;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LegacyCodeTranslator {

    private static final Pattern LEGACY_HEX = Pattern.compile("[&§]#([0-9a-fA-F]{6})");
    private static final Pattern LEGACY_CODE = Pattern.compile("[&§]([0-9a-fk-orA-FK-OR])");

    private static final Map<Character, String> TAGS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"), Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"), Map.entry('8', "dark_gray"),
            Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"),
            Map.entry('f', "white"), Map.entry('k', "obfuscated"), Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"), Map.entry('n', "underlined"), Map.entry('o', "italic"),
            Map.entry('r', "reset")
    );

    private LegacyCodeTranslator() {
    }

    public static String toMiniMessage(String input) {
        if (input.indexOf('&') < 0 && input.indexOf('§') < 0) {
            return input;
        }

        String withHex = LEGACY_HEX.matcher(input).replaceAll(match -> "<#" + match.group(1) + ">");

        Matcher matcher = LEGACY_CODE.matcher(withHex);
        StringBuilder result = new StringBuilder(withHex.length());
        while (matcher.find()) {
            matcher.appendReplacement(result, "<" + tagFor(matcher.group(1).charAt(0)) + ">");
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static String tagFor(char code) {
        return TAGS.get(Character.toLowerCase(code));
    }
}