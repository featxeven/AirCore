package com.ftxeven.aircore.module.kits;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.model.Kit;
import com.ftxeven.aircore.service.PlayerService;
import com.ftxeven.aircore.service.Eligibility;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.TimeFormatter;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class KitClaimMessages {

    private KitClaimMessages() {
    }

    public static Eligibility.Denied noPermission(Kit kit) {
        return new Eligibility.Denied("kits.errors.no-permission", Map.of("name", kit.name()));
    }

    public static Eligibility self(Player player, Kit kit, KitClaimHandler.Verdict verdict, ConfigManager configs) {
        return switch (verdict) {
            case KitClaimHandler.Verdict.Claimed ignored -> Eligibility.eligible();
            case KitClaimHandler.Verdict.Dropped ignored -> Eligibility.eligible();
            case KitClaimHandler.Verdict.OnCooldown(Instant expiresAt) -> Eligibility.denied(
                    "kits.errors.on-cooldown", Map.of(
                            "name", kit.name(),
                            "cooldown", TimeFormatter.duration(expiresAt, configs.main().formatting(), configs.lang())));
            case KitClaimHandler.Verdict.AlreadyClaimed ignored -> Eligibility.denied(
                    "kits.errors.one-time", Map.of("name", kit.name()));
            case KitClaimHandler.Verdict.InventoryFull(int requiredSlots) -> Eligibility.denied(
                    "kits.errors.inventory-full", Map.of("name", kit.name(), "required", String.valueOf(requiredSlots)));
            case KitClaimHandler.Verdict.BlockedWorld ignored -> Eligibility.denied(
                    "kits.errors.blocked-world", Map.of("world", player.getWorld().getName()));
        };
    }

    public static Eligibility target(Player recipient, Kit kit, KitClaimHandler.Verdict verdict, PlayerService players) {
        return switch (verdict) {
            case KitClaimHandler.Verdict.Claimed ignored -> Eligibility.eligible();
            case KitClaimHandler.Verdict.Dropped ignored -> Eligibility.eligible();
            case KitClaimHandler.Verdict.OnCooldown ignored -> Eligibility.eligible();
            case KitClaimHandler.Verdict.AlreadyClaimed ignored -> Eligibility.eligible();
            case KitClaimHandler.Verdict.InventoryFull(int requiredSlots) -> Eligibility.denied(
                    "kits.errors.inventory-full-for",
                    targetPlaceholders(recipient.getUniqueId(), kit, players, Map.of("required", String.valueOf(requiredSlots))));
            case KitClaimHandler.Verdict.BlockedWorld ignored -> Eligibility.denied(
                    "kits.errors.blocked-world-for",
                    targetPlaceholders(recipient.getUniqueId(), kit, players, Map.of("world", recipient.getWorld().getName())));
        };
    }

    public static Map<String, String> claimedPlaceholders(Kit kit) {
        return Map.of("name", kit.name(), "items", String.valueOf(kit.itemCount()));
    }

    public static boolean applySelfVerdict(Player player, Kit kit, KitClaimHandler.Verdict verdict, ConfigManager configs, Messenger messenger) {
        if (self(player, kit, verdict, configs) instanceof Eligibility.Denied denied) {
            denied.send(player, configs, messenger);
            return false;
        }

        messenger.send(player, configs.lang().get("kits.claim.self"), claimedPlaceholders(kit));
        if (verdict instanceof KitClaimHandler.Verdict.Dropped) {
            messenger.send(player, configs.lang().get("kits.dropped"));
        }
        return true;
    }

    private static Map<String, String> targetPlaceholders(UUID targetUuid, Kit kit, PlayerService players, Map<String, String> extra) {
        Map<String, String> placeholders = new HashMap<>(extra);
        players.formatDisplayName(placeholders, "target", targetUuid);
        placeholders.put("name", kit.name());
        return placeholders;
    }
}