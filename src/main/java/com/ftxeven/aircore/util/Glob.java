package com.ftxeven.aircore.util;

import java.util.Locale;

public final class Glob {

    private final String[] parts; // literal text between the wildcards, lowercased

    private Glob(String[] parts) {
        this.parts = parts;
    }

    public static Glob compile(String pattern) {
        return new Glob(pattern.trim().toLowerCase(Locale.ROOT).split("\\*", -1));
    }

    public boolean matches(String text) {
        String subject = text.toLowerCase(Locale.ROOT);
        if (parts.length == 1) {
            return subject.equals(parts[0]); // no wildcard at all
        }

        String prefix = parts[0];
        String suffix = parts[parts.length - 1];
        if (subject.length() < prefix.length() + suffix.length()
                || !subject.startsWith(prefix)
                || !subject.endsWith(suffix)) {
            return false;
        }

        int from = prefix.length();
        int end = subject.length() - suffix.length();
        for (int i = 1; i < parts.length - 1; i++) {
            int found = subject.indexOf(parts[i], from);
            if (found < 0 || found + parts[i].length() > end) {
                return false;
            }
            from = found + parts[i].length();
        }
        return true;
    }
}