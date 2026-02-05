package com.ftxeven.aircore.module.gui.invsee.enderchest;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.module.gui.GuiDefinition;
import com.ftxeven.aircore.module.gui.GuiManager;
import com.ftxeven.aircore.module.gui.ItemAction;
import com.ftxeven.aircore.database.player.PlayerInventories;
import com.ftxeven.aircore.util.PlaceholderUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public final class EnderchestManager implements GuiManager.CustomGuiManager {

    private final AirCore plugin;
    private final ItemAction itemAction;
    private GuiDefinition definition;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private final Set<String> dynamicGroups = Set.of("player-enderchest");

    private final TargetListener targetListener;
    private final ViewerListener viewerListener;

    public EnderchestManager(AirCore plugin, ItemAction itemAction) {
        this.plugin = plugin;
        this.itemAction = itemAction;

        this.targetListener = new TargetListener(plugin, this);
        Bukkit.getPluginManager().registerEvents(targetListener, plugin);

        this.viewerListener = new ViewerListener(plugin, this);
        Bukkit.getPluginManager().registerEvents(viewerListener, plugin);

        loadDefinition();
    }

    public void unregisterListeners() {
        HandlerList.unregisterAll(targetListener);
        HandlerList.unregisterAll(viewerListener);
    }

    private void loadDefinition() {
        File file = new File(plugin.getDataFolder(), "guis/invsee/enderchest.yml");
        if (!file.exists()) plugin.saveResource("guis/invsee/enderchest.yml", false);

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        String title = cfg.getString("title", "Enderchest");
        int rows = cfg.getInt("rows", 3);

        Map<String, GuiDefinition.GuiItem> items = new HashMap<>();

        for (String key : dynamicGroups) {
            items.put(key, new GuiDefinition.GuiItem(
                    key,
                    GuiDefinition.parseSlots(cfg.getStringList(key)),
                    Material.AIR,
                    null,
                    Collections.emptyList(),
                    false,
                    null,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    null,
                    null,
                    null,
                    Collections.emptyMap(),
                    Collections.emptyList(),
                    null,
                    null,
                    null
            ));
        }

        // Static/custom items
        ConfigurationSection itemsSec = cfg.getConfigurationSection("items");
        if (itemsSec != null) {
            for (String key : itemsSec.getKeys(false)) {
                ConfigurationSection sec = itemsSec.getConfigurationSection(key);
                if (sec == null) continue;

                GuiDefinition.GuiItem guiItem = GuiDefinition.GuiItem.fromSection(key, sec, mm);
                items.put(key, guiItem);
            }
        }

        this.definition = new GuiDefinition(title, rows, items, cfg);
    }

    @Override
    public Inventory build(Player viewer, Map<String, String> placeholders) {
        String targetName = placeholders.get("target");
        Player target = Bukkit.getPlayerExact(targetName);
        UUID targetUUID = target != null
                ? target.getUniqueId()
                : plugin.database().records().uuidFromName(targetName);

        ItemStack[] enderchestContents;
        if (target != null) {
            enderchestContents = target.getEnderChest().getContents();
        } else {
            PlayerInventories.InventoryBundle bundle = plugin.database().inventories().loadAllInventory(targetUUID);
            enderchestContents = bundle != null ? bundle.enderChest() : new ItemStack[PlayerInventories.ENDERCHEST_SIZE];
        }

        String rawTitle = PlaceholderUtil.apply(
                viewer,
                definition.title()
                        .replace("%player%", viewer.getName())
                        .replace("%target%", targetName)
        );

        Inventory inv = Bukkit.createInventory(
                new EnderchestHolder(targetUUID, targetName),
                definition.rows() * 9,
                mm.deserialize(rawTitle)
        );

        EnderchestSlotMapper.fill(inv, definition, enderchestContents);
        EnderchestSlotMapper.fillCustom(inv, definition, viewer, placeholders, this);

        targetListener.registerViewer(targetUUID, viewer);
        return inv;
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player viewer) {
        Inventory top = event.getView().getTopInventory();
        Inventory bottom = event.getView().getBottomInventory();
        Inventory clicked = event.getClickedInventory();

        int slot = event.getSlot();
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        boolean hasModifyPermission = viewer.hasPermission("aircore.command.enderchest.others.modify");

        // Block shift-click from bottom to top if no permission
        if (event.isShiftClick() && clicked == bottom) {
            if (!hasModifyPermission) {
                event.setCancelled(true);
                return;
            }

            if (current == null || current.getType().isAir()) {
                event.setCancelled(true);
                return;
            }

            event.setCancelled(true);
            distributeToSlots(definition, top, current, clicked, event.getSlot());
            syncAndRefresh(top, viewer);
            return;
        }

        if (clicked != top) return;

        boolean dynamic = EnderchestSlotMapper.isDynamicSlot(definition, slot);
        boolean isFillerHere = EnderchestSlotMapper.isCustomFillerAt(definition, slot, current);
        boolean registered = EnderchestSlotMapper.findItem(definition, slot) != null;

        if (!registered) {
            event.setCancelled(true);
            return;
        }

        // If player doesn't have modify permission, only allow actions on fillers/items
        if (!hasModifyPermission) {
            if (dynamic) {
                if (isFillerHere && cursor.getType().isAir()) {
                    event.setCancelled(true);

                    InventoryHolder holder = top.getHolder();
                    if (holder instanceof EnderchestHolder eh) {
                        GuiDefinition.GuiItem custom = EnderchestSlotMapper.findCustomItemAt(definition, slot);
                        if (custom != null) {
                            List<String> actionsToExecute = custom.getActionsForClick(event.getClick());
                            if (actionsToExecute != null && !actionsToExecute.isEmpty()) {
                                itemAction.executeAll(
                                        actionsToExecute,
                                        viewer,
                                        Map.of(
                                                "player", viewer.getName(),
                                                "target", eh.targetName()
                                        )
                                );
                            }
                        }
                    }
                    return;
                }
            } else {
                event.setCancelled(true);

                InventoryHolder holder = top.getHolder();
                if (!(holder instanceof EnderchestHolder eh)) return;

                GuiDefinition.GuiItem item = EnderchestSlotMapper.findItem(definition, slot);
                if (item == null) return;

                List<String> actionsToExecute = item.getActionsForClick(event.getClick());
                if (actionsToExecute != null && !actionsToExecute.isEmpty()) {
                    itemAction.executeAll(
                            actionsToExecute,
                            viewer,
                            Map.of(
                                    "player", viewer.getName(),
                                    "target", eh.targetName()
                            )
                    );
                }
                return;
            }

            event.setCancelled(true);
            return;
        }

        // Player has modify permission

        // Dynamic slot behavior
        if (dynamic) {
            if (isFillerHere && cursor.getType().isAir()) {
                event.setCancelled(true);

                InventoryHolder holder = top.getHolder();
                if (holder instanceof EnderchestHolder eh) {
                    GuiDefinition.GuiItem custom = EnderchestSlotMapper.findCustomItemAt(definition, slot);
                    if (custom != null) {
                        List<String> actionsToExecute = custom.getActionsForClick(event.getClick());
                        if (actionsToExecute != null && !actionsToExecute.isEmpty()) {
                            itemAction.executeAll(
                                    actionsToExecute,
                                    viewer,
                                    Map.of(
                                            "player", viewer.getName(),
                                            "target", eh.targetName()
                                    )
                            );
                        }
                    }
                }
                return;
            }

            if (isFillerHere && !cursor.getType().isAir()) {
                event.setCancelled(true);
                top.setItem(slot, cursor.clone());
                viewer.setItemOnCursor(null);
                syncAndRefresh(top, viewer);
                return;
            }

            event.setCancelled(false);
            plugin.scheduler().runEntityTask(viewer, () -> {
                refreshFillers(top, viewer);
                syncAndRefresh(top, viewer);
            });
            return;
        }

        // Static/custom item slots
        event.setCancelled(true);

        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof EnderchestHolder eh)) return;

        GuiDefinition.GuiItem item = EnderchestSlotMapper.findItem(definition, slot);
        if (item == null) return;

        List<String> actionsToExecute = item.getActionsForClick(event.getClick());
        if (actionsToExecute != null && !actionsToExecute.isEmpty()) {
            itemAction.executeAll(
                    actionsToExecute,
                    viewer,
                    Map.of(
                            "player", viewer.getName(),
                            "target", eh.targetName()
                    )
            );
        }
    }

    void applyEnderchestToTarget(UUID targetUUID, ItemStack[] contents) {
        Player target = Bukkit.getPlayer(targetUUID);

        if (target == null || !target.isOnline()) {
            plugin.database().inventories().saveEnderchest(targetUUID, contents);
            return;
        }

        var ec = target.getEnderChest();
        boolean changed = false;

        ItemStack[] currentContents = ec.getContents();
        for (int i = 0; i < contents.length && i < currentContents.length; i++) {
            ItemStack current = currentContents[i];
            ItemStack next = contents[i];

            if (!itemsEqual(current, next)) {
                ec.setItem(i, next);
                changed = true;
            }
        }

        if (changed) {
            target.updateInventory();
        }
    }

    private static boolean itemsEqual(ItemStack a, ItemStack b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.isSimilar(b) && a.getAmount() == b.getAmount();
    }

    private void syncAndRefresh(Inventory top, Player viewer) {
        plugin.scheduler().runEntityTask(viewer, () -> {
            InventoryHolder holder = top.getHolder();
            if (!(holder instanceof EnderchestHolder eh)) return;

            refreshFillers(top, viewer);

            ItemStack[] contents = EnderchestSlotMapper.extractContents(top, definition);

            applyEnderchestToTarget(eh.targetUUID(), contents);

            targetListener.refreshViewers(eh.targetUUID(), contents);
        });
    }

    private void distributeToSlots(GuiDefinition def, Inventory top, ItemStack moving,
                                   Inventory sourceInv, int sourceSlot) {
        if (moving == null || moving.getType().isAir()) return;

        List<Integer> slots = def.items().get("player-enderchest").slots();

        // Merge into partial stacks first
        for (int dest : slots) {
            ItemStack destItem = top.getItem(dest);
            if (destItem != null && destItem.isSimilar(moving) && destItem.getAmount() < destItem.getMaxStackSize()) {
                int transfer = Math.min(moving.getAmount(), destItem.getMaxStackSize() - destItem.getAmount());
                destItem.setAmount(destItem.getAmount() + transfer);
                moving.setAmount(moving.getAmount() - transfer);
                if (moving.getAmount() <= 0) {
                    sourceInv.setItem(sourceSlot, null);
                    return;
                }
            }
        }

        // Then fill empty slots
        for (int dest : slots) {
            ItemStack destItem = top.getItem(dest);
            if (destItem == null || destItem.getType().isAir()) {
                top.setItem(dest, moving.clone());
                sourceInv.setItem(sourceSlot, null);
                return;
            }
        }
    }

    public void refreshFillers(Inventory top, Player viewer) {
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof EnderchestHolder eh)) {
            return;
        }

        EnderchestSlotMapper.fillCustom(
                top,
                definition,
                viewer,
                Map.of(
                        "player", viewer.getName(),
                        "target", eh.targetName()
                ),
                this
        );
    }

    @Override
    public boolean owns(Inventory inv) {
        return inv.getHolder() instanceof EnderchestHolder;
    }

    public boolean isDynamicGroup(String key) {
        return dynamicGroups.contains(key);
    }

    public GuiDefinition definition() {
        return definition;
    }

    public record EnderchestHolder(UUID targetUUID, String targetName) implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            throw new UnsupportedOperationException();
        }
    }
}