package com.ftxeven.aircore.command.player;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class Selectors {

    public static final String ALL = "all";
    public static final String SERVER = "server";

    private final Supplier<Map<String, String>> selectors;

    public Selectors(Supplier<Map<String, String>> selectors) {
        this.selectors = selectors;
    }

    public Optional<String> token(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(selectors.get().get(key.toLowerCase(Locale.ROOT)));
    }

    // for built-in keys (ALL, SERVER) that CommandsConfig guarantees are always present
    public String required(String key) {
        return token(key).orElseThrow(() -> new IllegalStateException(
                "Selector '" + key + "' has no token - CommandsConfig should always provide a default for this key"));
    }

    public boolean matches(String key, String typed) {
        return typed != null && token(key).filter(typed::equalsIgnoreCase).isPresent();
    }

    public boolean isAll(String typed) {
        return matches(ALL, typed);
    }

    public boolean isServer(String typed) {
        return matches(SERVER, typed);
    }
}