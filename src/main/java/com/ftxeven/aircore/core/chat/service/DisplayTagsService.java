package com.ftxeven.aircore.core.chat.service;

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
import java.util.Objects;

public final class DisplayTagsService {
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<String, TagDefinition> tags = new HashMap<>();

    public DisplayTagsService(AirCore plugin) {
        ConfigurationSection section = plugin.config().displayTagsSection();
        if (section != null) {
            for (String tagId : section.getKeys(false)) {
                ConfigurationSection tagSec = section.getConfigurationSection(tagId);
                if (tagSec == null) continue;

                if (!plugin.config().displayTagEnabled(tagId)) continue;

                List<String> keys = tagSec.getStringList("keys");
                String format = tagSec.getString("format", "");

                tags.put(tagId, new TagDefinition(tagId, keys, format));
            }
        }
    }

    public Component apply(Player player, Component input) {
        Component result = input;

        for (TagDefinition def : tags.values()) {
            if (def.keys.isEmpty()) continue;

            String perm = "aircore.chat.display." + def.id.toLowerCase();
            if (!(player.hasPermission(perm) || player.hasPermission("aircore.chat.display.*"))) {
                continue;
            }

            final Component replacement =
                    def.id.equalsIgnoreCase("show-item")
                            ? buildItemComponent(player, def.format)
                            : buildGenericComponent(player, def.format);

            if (replacement != null) {
                for (String key : def.keys) {
                    result = result.replaceText(b -> b.matchLiteral(key).replacement(replacement));
                }
            }
        }
        return result;
    }

    private Component buildItemComponent(Player player, String format) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        Component itemName = (meta != null && meta.hasDisplayName() && meta.displayName() != null)
                ? Objects.requireNonNull(meta.displayName()).decoration(TextDecoration.ITALIC, true)
                : Component.translatable(item.getType().translationKey());

        return mm.deserialize(format)
                .replaceText(b -> b.matchLiteral("%amount%").replacement(Component.text(item.getAmount())))
                .replaceText(b -> b.matchLiteral("%item%").replacement(itemName))
                .hoverEvent(item.asHoverEvent());
    }

    private Component buildGenericComponent(Player player, String format) {
        String parsed = format.replace("%player%", player.getName());
        parsed = PlaceholderUtil.apply(player, parsed);
        return mm.deserialize(parsed);
    }

    private record TagDefinition(String id, List<String> keys, String format) {
    }
}