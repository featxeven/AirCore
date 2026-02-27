package com.ftxeven.aircore.core.modules.chat.service;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.PlaceholderUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DisplayTagsService {
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<String, TagDefinition> tags = new HashMap<>();

    public DisplayTagsService(AirCore plugin) {
        ConfigurationSection section = plugin.config().displayTagsSection();
        if (section == null) return;

        for (String tagId : section.getKeys(false)) {
            if (!plugin.config().displayTagEnabled(tagId)) continue;

            ConfigurationSection tagSec = section.getConfigurationSection(tagId);
            if (tagSec == null) continue;

            tags.put(tagId, new TagDefinition(
                    tagId,
                    tagSec.getStringList("keys"),
                    tagSec.getString("format", ""),
                    "aircore.chat.display." + tagId.toLowerCase()
            ));
        }
    }

    public Component apply(Player player, Component input) {
        Component result = input;
        boolean hasWildcard = player.hasPermission("aircore.chat.display.*");

        for (TagDefinition def : tags.values()) {
            if (def.keys().isEmpty()) continue;
            if (!hasWildcard && !player.hasPermission(def.permission())) continue;

            final Component replacement = def.id().equalsIgnoreCase("show-item")
                    ? buildItemComponent(player, def.format())
                    : buildGenericComponent(player, def.format());

            if (replacement == null) continue;

            for (String key : def.keys()) {
                result = result.replaceText(builder -> builder.matchLiteral(key).replacement(replacement));
            }
        }
        return result;
    }

    private Component buildItemComponent(Player player, String format) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) return null;

        ItemMeta meta = item.getItemMeta();

        final Component finalItemName;
        if (meta != null && meta.hasDisplayName()) {
            Component name = meta.displayName();
            finalItemName = (name != null) ? name.decoration(TextDecoration.ITALIC, true) : Component.empty();
        } else {
            finalItemName = Component.translatable(item.getType().translationKey());
        }

        String preParsed = format.replace("%amount%", String.valueOf(item.getAmount()));

        return mm.deserialize(preParsed)
                .replaceText(builder -> builder.matchLiteral("%item%").replacement(finalItemName))
                .hoverEvent(item.asHoverEvent());
    }

    private Component buildGenericComponent(Player player, String format) {
        String parsed = format.replace("%player%", player.getName());
        return mm.deserialize(PlaceholderUtil.apply(player, parsed));
    }

    private record TagDefinition(String id, List<String> keys, String format, String permission) {}
}