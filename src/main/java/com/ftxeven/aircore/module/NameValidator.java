package com.ftxeven.aircore.module;

import org.bukkit.permissions.Permissible;

import java.util.List;
import java.util.regex.Pattern;

public final class NameValidator {

    public enum Reason { TOO_LONG, INVALID_FORMAT, BLACKLISTED, PROFANITY }

    public sealed interface Verdict {
        record Allow(String name) implements Verdict {}
        record Reject(Reason reason) implements Verdict {}
    }

    private NameValidator() {
    }

    public static Verdict validate(Permissible sender, String blacklistBypass, String requested,
                                   int maxLength, Pattern validationRegex, List<Pattern> blacklist) {
        String trimmed = requested.strip();

        if (maxLength >= 0 && trimmed.length() > maxLength) {
            return new Verdict.Reject(Reason.TOO_LONG);
        }
        if (!validationRegex.matcher(trimmed).matches()) {
            return new Verdict.Reject(Reason.INVALID_FORMAT);
        }
        if (isBlacklisted(sender, blacklistBypass, blacklist, trimmed)) {
            return new Verdict.Reject(Reason.BLACKLISTED);
        }
        return new Verdict.Allow(trimmed);
    }

    public static boolean isBlacklisted(Permissible sender, String blacklistBypass, List<Pattern> blacklist, String text) {
        return !sender.hasPermission(blacklistBypass) && matchesAny(blacklist, text);
    }

    public static boolean matchesAny(List<Pattern> patterns, String text) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }
}