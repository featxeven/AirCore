package com.ftxeven.aircore.permission;

import org.bukkit.permissions.Permissible;

public final class Permissions {

    public static final String ADMIN = "aircore.admin";

    private Permissions() {
    }

    private static boolean hasNamedPermission(Permissible sender, String base, String name) {
        return sender.hasPermission(base + "." + name) || sender.hasPermission(base + ".*");
    }

    /**
     * aircore.command.<command>[.others|.all|.server|.modify|.teleport|.delete]
     */
    public static final class Command {
        private static final String BASE = "aircore.command";

        private Command() {
        }

        public static String of(String key) {
            return BASE + "." + key;
        }

        public static String others(String key) {
            return of(key) + ".others";
        }

        public static String all(String key) {
            return of(key) + ".all";
        }

        public static String server(String key) {
            return of(key) + ".server";
        }

        public static String modify(String key) {
            return of(key) + ".modify";
        }

        public static String teleport(String key) {
            return of(key) + ".teleport";
        }

        public static String virtual(String key) {
            return of("virtual." + key);
        }
    }

    /**
     * aircore.bypass.<bypass...>
     */
    public static final class Bypass {
        private static final String BASE = "aircore.bypass";

        // Chat
        public static final String CHAT_COOLDOWN = BASE + ".chat.cooldown";
        public static final String CHAT_TOGGLE = BASE + ".chat.toggle";
        public static final String WARNINGS = BASE + ".chat.warnings";

        // Economy
        public static final String PAY_TOGGLE = BASE + ".economy.pay.toggle";
        public static final String NEGATIVE_BALANCE = BASE + ".economy.balance.negative";
        public static final String MAX_BALANCE = BASE + ".economy.balance.max";
        public static final String TAX = BASE + ".economy.tax";

        // Homes
        public static final String LIMIT = BASE + ".home.limit";
        public static final String HOME_DISABLED_WORLDS = BASE + ".home.disabled-worlds";

        // Kits
        public static final String KIT_DISABLED_WORLDS = BASE + ".kit.disabled-worlds";
        public static final String ONETIME = BASE + ".kit.onetime";

        // Teleport
        public static final String TELEPORT_COOLDOWN = BASE + ".teleport.cooldown";
        public static final String COUNTDOWN = BASE + ".teleport.countdown";
        public static final String TELEPORT_TOGGLE = BASE + ".teleport.toggle";
        public static final String MAX_PENDING = BASE + ".teleport.max-pending";

        // Misc
        public static final String BLOCK = BASE + ".block";
        public static final String BLOCK_LIMIT = BASE + ".block.limit";
        public static final String AFK_IDLE = BASE + ".afk.idle";
        public static final String AFK_ACTIONS = BASE + ".afk.actions";
        public static final String NICKNAME_BLACKLIST = BASE + ".blacklist.nickname";
        public static final String HOME_BLACKLIST = BASE + ".blacklist.home";
        public static final String AFK_BLACKLIST = BASE + ".blacklist.afk";

        private Bypass() {
        }

        public static String filter(String key) {
            return BASE + ".chat.filter." + key;
        }

        public static String kitCooldown(String kitName) {
            return BASE + ".kit.cooldown." + kitName;
        }

        public static String teleportDisabledWorlds(String key) {
            return BASE + ".teleport.disabled-worlds." + key;
        }

        public static String restriction(String key) {
            return BASE + ".restriction." + key;
        }

        /** Bypasses the per-command cooldown for the given command key */
        public static String command(String key) {
            return BASE + ".command." + key;
        }
    }

    /**
     * aircore.access.<access...>
     */
    public static final class Access {
        private static final String BASE = "aircore.access";

        public static final String MENTION = BASE + ".chat.mention";
        public static final String MENTION_ALL = BASE + ".chat.mention.all";
        public static final String MENTION_HERE = BASE + ".chat.mention.here";
        public static final String URL_FORMATTING = BASE + ".chat.url-formatting";
        public static final String NOTIFY_WARNINGS = BASE + ".chat.notify.warnings";

        private Access() {
        }

        public static boolean hasChannel(Permissible sender, String channelKey) {
            return hasNamedPermission(sender, BASE + ".chat.channel", channelKey);
        }

        public static boolean hasDisplayTag(Permissible sender, String tagKey) {
            return hasNamedPermission(sender, BASE + ".chat.display-tag", tagKey);
        }

        public static String color(String key) {
            return BASE + ".chat.color." + key;
        }

        public static String decoration(String key) {
            return BASE + ".chat.decoration." + key;
        }

        public static String format(String key) {
            return BASE + ".chat.format." + key;
        }

        public static String click(boolean safe) {
            return format("click." + (safe ? "safe" : "unsafe"));
        }

        public static boolean hasWarp(Permissible sender, String warpName) {
            return hasNamedPermission(sender, BASE + ".warp", warpName);
        }

        public static boolean hasKit(Permissible sender, String kitName) {
            return hasNamedPermission(sender, BASE + ".kit", kitName);
        }

        public static boolean hasHomeIcon(Permissible sender, String iconId) {
            return hasNamedPermission(sender, BASE + ".home.icon", iconId);
        }

        public static boolean hasHomeBundle(Permissible sender, String bundleId) {
            return hasNamedPermission(sender, BASE + ".home.bundle", bundleId);
        }
    }
}