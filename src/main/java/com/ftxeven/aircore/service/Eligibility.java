package com.ftxeven.aircore.service;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.util.Messenger;
import org.bukkit.command.CommandSender;

import java.util.Map;

public sealed interface Eligibility {

    record Eligible() implements Eligibility {}

    record Denied(String langKey, Map<String, String> placeholders) implements Eligibility {
        public Denied(String langKey) {
            this(langKey, Map.of());
        }

        public void send(CommandSender target, ConfigManager configs, Messenger messenger) {
            messenger.send(target, configs.lang().get(langKey), placeholders);
        }
    }

    static Eligibility eligible() {
        return new Eligible();
    }

    static Eligibility denied(String langKey) {
        return new Denied(langKey);
    }

    static Eligibility denied(String langKey, Map<String, String> placeholders) {
        return new Denied(langKey, placeholders);
    }

    default boolean ok() {
        return this instanceof Eligible;
    }
}