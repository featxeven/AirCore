package com.ftxeven.aircore.module.gui.invsee.inventory;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.module.gui.GuiDefinition;
import com.ftxeven.aircore.module.gui.GuiManager;
import com.ftxeven.aircore.module.gui.ItemAction;
import com.ftxeven.aircore.database.player.PlayerInventories;
import com.ftxeven.aircore.util.PlaceholderUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public final class InventoryManager implements GuiManager.CustomGuiManager {

    private final AirCore plugin;
    private final ItemAction itemAction;
    private GuiDefinition definition;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private final Set<String> dynamicGroups = Set.of(
            "player-hotbar",
            "player-inventory",
            "player-armor",
            "player-offhand"
    );

    private final TargetListener targetListener;
    private final ViewerListener viewerListener;

    public InventoryManager(AirCore plugin, ItemAction itemAction) {
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
        File file = new File(plugin.getDataFolder(), "guis/invsee/inventory.yml");
        if (!file.exists()) plugin.saveResource("guis/invsee/inventory.yml", false);

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        String title = cfg.getString("title", "Invsee");
        int rows = cfg.getInt("rows", 6);

        Map<String, GuiDefinition.GuiItem> items = new HashMap<>();

        // Dynamic groups
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

        PlayerInventories.InventoryBundle bundle = target != null
                ? new PlayerInventories.InventoryBundle(
                target.getInventory().getContents(),
                target.getInventory().getArmorContents(),
                target.getInventory().getItemInOffHand(),
                target.getEnderChest().getContents()
        )
                : plugin.database().inventories().loadAllInventory(targetUUID);

        if (bundle == null) bundle = emptyBundle();

        String rawTitle = PlaceholderUtil.apply(
                viewer,
                definition.title()
                        .replace("%player%", viewer.getName())
                        .replace("%target%", targetName)
        );

        InvseeHolder holder = new InvseeHolder(targetUUID, targetName);
        Inventory inv = Bukkit.createInventory(holder, definition.rows() * 9, mm.deserialize(rawTitle));
        holder.setInventory(inv);

        InventorySlotMapper.fill(inv, definition, bundle);
        InventorySlotMapper.fillCustom(inv, definition, viewer, placeholders, this);

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

        List<Integer> armorSlots = definition.items().get("player-armor").slots();

        boolean hasModifyPermission = viewer.hasPermission("aircore.command.invsee.modify");

        // SHIFT CLICK
        if (event.isShiftClick() && clicked == bottom) {
            // Block shift-click from bottom to top if no permission
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

        boolean dynamic = InventorySlotMapper.isDynamicSlot(definition, slot);
        boolean isFillerHere = InventorySlotMapper.isCustomFillerAt(definition, slot, current);
        boolean registered = InventorySlotMapper.findItem(definition, slot) != null;

        if (!registered) {
            event.setCancelled(true);
            return;
        }

        if (!hasModifyPermission) {
            if (dynamic) {
                // Only allow if it's a filler
                if (isFillerHere && cursor.getType().isAir()) {
                    event.setCancelled(true);

                    InventoryHolder holder = top.getHolder();
                    if (holder instanceof InvseeHolder ih) {
                        GuiDefinition.GuiItem custom = InventorySlotMapper.findCustomItemAt(definition, slot);
                        if (custom != null) {
                            List<String> actionsToExecute = custom.getActionsForClick(event.getClick());
                            if (actionsToExecute != null && !actionsToExecute.isEmpty()) {
                                itemAction.executeAll(
                                        actionsToExecute,
                                        viewer,
                                        Map.of(
                                                "player", viewer.getName(),
                                                "target", ih.targetName()
                                        )
                                );
                            }
                        }
                    }
                    return;
                }
            } else {
                // Static slot allow action execution
                event.setCancelled(true);

                InventoryHolder holder = top.getHolder();
                if (!(holder instanceof InvseeHolder ih)) return;

                GuiDefinition.GuiItem item = InventorySlotMapper.findItem(definition, slot);
                if (item == null) return;

                List<String> actionsToExecute = item.getActionsForClick(event.getClick());
                if (actionsToExecute != null && !actionsToExecute.isEmpty()) {
                    itemAction.executeAll(
                            actionsToExecute,
                            viewer,
                            Map.of(
                                    "player", viewer.getName(),
                                    "target", ih.targetName()
                            )
                    );
                }
                return;
            }

            // Block any other interaction
            event.setCancelled(true);
            return;
        }

        // Player has modify permission

        // NUMBER KEY
        if (event.getClick() == ClickType.NUMBER_KEY) {
            ItemStack hotbar = viewer.getInventory().getItem(event.getHotbarButton());

            if (armorSlots.contains(slot)
                    && hotbar != null
                    && !hotbar.getType().isAir()
                    && !isValidArmorForSlot(hotbar, slot, definition)) {
                event.setCancelled(true);
                return;
            }

            event.setCancelled(true);
            top.setItem(slot, hotbar == null ? null : hotbar.clone());
            viewer.getInventory().setItem(event.getHotbarButton(), isFillerHere ? null : current);
            syncAndRefresh(top, viewer);
            return;
        }

        // DYNAMIC SLOT
        if (dynamic) {
            // Filler is visible and cursor is empty
            if (isFillerHere && cursor.getType().isAir()) {
                event.setCancelled(true);

                InventoryHolder holder = top.getHolder();
                if (holder instanceof InvseeHolder ih) {
                    GuiDefinition.GuiItem custom = InventorySlotMapper.findCustomItemAt(definition, slot);
                    if (custom != null) {
                        List<String> actionsToExecute = custom.getActionsForClick(event.getClick());
                        if (actionsToExecute != null && !actionsToExecute.isEmpty()) {
                            itemAction.executeAll(
                                    actionsToExecute,
                                    viewer,
                                    Map.of(
                                            "player", viewer.getName(),
                                            "target", ih.targetName()
                                    )
                            );
                        }
                    }
                }
                return;
            }

            // Filler is visible and cursor has item
            if (isFillerHere && !cursor.getType().isAir()) {
                if (armorSlots.contains(slot) && !isValidArmorForSlot(cursor, slot, definition)) {
                    event.setCancelled(true);
                    return;
                }

                event.setCancelled(true);
                top.setItem(slot, cursor.clone());
                viewer.setItemOnCursor(null);
                syncAndRefresh(top, viewer);
                return;
            }

            // Normal dynamic slot behavior
            if (armorSlots.contains(slot) && !cursor.getType().isAir()) {
                if (!isValidArmorForSlot(cursor, slot, definition)) {
                    event.setCancelled(true);
                    return;
                }
            }

            event.setCancelled(false);
            plugin.scheduler().runEntityTask(viewer, () -> {
                refreshFillers(top, viewer);
                syncAndRefresh(top, viewer);
            });
            return;
        }

        // STATIC GUI ITEM
        event.setCancelled(true);

        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof InvseeHolder ih)) return;

        GuiDefinition.GuiItem item = InventorySlotMapper.findItem(definition, slot);
        if (item == null) return;

        if (InventorySlotMapper.isDynamicSlot(definition, slot)) {
            // Dynamic slot
            if (InventorySlotMapper.isCustomFillerAt(definition, slot, current)) {
                List<String> actionsToExecute = item.getActionsForClick(event.getClick());
                if (actionsToExecute != null && !actionsToExecute.isEmpty()) {
                    itemAction.executeAll(
                            actionsToExecute,
                            viewer,
                            Map.of(
                                    "player", viewer.getName(),
                                    "target", ih.targetName()
                            )
                    );
                }
            }
        } else {
            // Non-dynamic
            List<String> actionsToExecute = item.getActionsForClick(event.getClick());
            if (actionsToExecute != null && !actionsToExecute.isEmpty()) {
                itemAction.executeAll(
                        actionsToExecute,
                        viewer,
                        Map.of(
                                "player", viewer.getName(),
                                "target", ih.targetName()
                        )
                );
            }
        }
    }

    private void distributeToSlots(GuiDefinition def,
                                   Inventory top,
                                   ItemStack moving,
                                   Inventory sourceInv,
                                   int sourceSlot) {
        if (moving == null || moving.getType().isAir()) return;

        // Armor slots
        List<Integer> armor = def.items().get("player-armor").slots();
        for (int slot : armor) {
            ItemStack it = top.getItem(slot);
            if (it != null && it.isSimilar(moving) && it.getAmount() < it.getMaxStackSize()
                    && isValidArmorForSlot(moving, slot, def)) {
                int transfer = Math.min(moving.getAmount(), it.getMaxStackSize() - it.getAmount());
                it.setAmount(it.getAmount() + transfer);
                moving.setAmount(moving.getAmount() - transfer);
                if (moving.getAmount() <= 0) {
                    sourceInv.setItem(sourceSlot, null);
                    return;
                }
            }
        }
        for (int slot : armor) {
            ItemStack it = top.getItem(slot);
            if ((it == null || it.getType().isAir()
                    || InventorySlotMapper.isCustomFillerAt(def, slot, it))
                    && isValidArmorForSlot(moving, slot, def)) {
                top.setItem(slot, moving.clone());
                sourceInv.setItem(sourceSlot, null);
                return;
            }
        }

        // Offhand
        List<Integer> offhand = def.items().get("player-offhand").slots();
        for (int slot : offhand) {
            ItemStack it = top.getItem(slot);
            if (it != null && it.isSimilar(moving) && it.getAmount() < it.getMaxStackSize()) {
                int transfer = Math.min(moving.getAmount(), it.getMaxStackSize() - it.getAmount());
                it.setAmount(it.getAmount() + transfer);
                moving.setAmount(moving.getAmount() - transfer);
                if (moving.getAmount() <= 0) {
                    sourceInv.setItem(sourceSlot, null);
                    return;
                }
            }
        }
        for (int slot : offhand) {
            ItemStack it = top.getItem(slot);
            if (it == null || it.getType().isAir()
                    || InventorySlotMapper.isCustomFillerAt(def, slot, it)) {
                top.setItem(slot, moving.clone());
                sourceInv.setItem(sourceSlot, null);
                return;
            }
        }

        // Main inventory
        List<Integer> contents = def.items().get("player-inventory").slots();
        for (int slot : contents) {
            ItemStack it = top.getItem(slot);
            if (it != null && it.isSimilar(moving) && it.getAmount() < it.getMaxStackSize()) {
                int transfer = Math.min(moving.getAmount(), it.getMaxStackSize() - it.getAmount());
                it.setAmount(it.getAmount() + transfer);
                moving.setAmount(moving.getAmount() - transfer);
                if (moving.getAmount() <= 0) {
                    sourceInv.setItem(sourceSlot, null);
                    return;
                }
            }
        }
        for (int slot : contents) {
            ItemStack it = top.getItem(slot);
            if (it == null || it.getType().isAir()
                    || InventorySlotMapper.isCustomFillerAt(def, slot, it)) {
                top.setItem(slot, moving.clone());
                sourceInv.setItem(sourceSlot, null);
                return;
            }
        }

        // Hotbar
        List<Integer> hotbar = def.items().get("player-hotbar").slots();
        for (int slot : hotbar) {
            ItemStack it = top.getItem(slot);
            if (it != null && it.isSimilar(moving) && it.getAmount() < it.getMaxStackSize()) {
                int transfer = Math.min(moving.getAmount(), it.getMaxStackSize() - it.getAmount());
                it.setAmount(it.getAmount() + transfer);
                moving.setAmount(moving.getAmount() - transfer);
                if (moving.getAmount() <= 0) {
                    sourceInv.setItem(sourceSlot, null);
                    return;
                }
            }
        }
        for (int slot : hotbar) {
            ItemStack it = top.getItem(slot);
            if (it == null || it.getType().isAir()
                    || InventorySlotMapper.isCustomFillerAt(def, slot, it)) {
                top.setItem(slot, moving.clone());
                sourceInv.setItem(sourceSlot, null);
                return;
            }
        }
    }

    void applyBundleToTarget(UUID targetUUID, PlayerInventories.InventoryBundle bundle) {
        Player target = Bukkit.getPlayer(targetUUID);

        if (target == null || !target.isOnline()) {
            plugin.database().inventories()
                    .saveInventory(targetUUID, bundle.contents(), bundle.armor(), bundle.offhand());
            return;
        }

        var inv = target.getInventory();
        boolean changed = false;

        // main contents (per slot)
        ItemStack[] incoming = bundle.contents();

        for (int i = 0; i < incoming.length; i++) {
            ItemStack current = inv.getItem(i);
            ItemStack next = incoming[i];

            if (!itemsEqual(current, next)) {
                inv.setItem(i, next);
                changed = true;
            }
        }

        // armor (only if different)
        ItemStack[] currentArmor = inv.getArmorContents();
        ItemStack[] newArmor = bundle.armor();

        if (!armorEquals(currentArmor, newArmor)) {
            inv.setArmorContents(newArmor);
            changed = true;
        }

        // offhand
        ItemStack offhand = inv.getItemInOffHand();
        if (!itemsEqual(offhand, bundle.offhand())) {
            inv.setItemInOffHand(bundle.offhand());
            changed = true;
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

    private static boolean armorEquals(ItemStack[] a, ItemStack[] b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.length != b.length) return false;

        for (int i = 0; i < a.length; i++) {
            ItemStack ia = a[i];
            ItemStack ib = b[i];

            if (ia == null && ib == null) continue;
            if (ia == null || ib == null) return false;
            if (!ia.isSimilar(ib)) return false;
        }
        return true;
    }

    private void syncAndRefresh(Inventory top, Player viewer) {
        plugin.scheduler().runEntityTask(viewer, () -> {
            InventoryHolder holder = top.getHolder();
            if (!(holder instanceof InvseeHolder ih)) return;

            refreshFillers(top, viewer);

            PlayerInventories.InventoryBundle bundle =
                    InventorySlotMapper.extractBundle(top, definition);

            applyBundleToTarget(ih.targetUUID(), bundle);

            targetListener.refreshViewers(ih.targetUUID(), bundle);
        });
    }


    public void refreshFillers(Inventory top, Player viewer) {
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof InvseeHolder ih)) {
            return;
        }

        InventorySlotMapper.fillCustom(
                top,
                definition,
                viewer,
                Map.of(
                        "player", viewer.getName(),
                        "target", ih.targetName()
                ),
                this
        );
    }

    private static PlayerInventories.InventoryBundle emptyBundle() {
        return new PlayerInventories.InventoryBundle(
                new ItemStack[PlayerInventories.CONTENTS_SIZE],
                new ItemStack[PlayerInventories.ARMOR_SIZE],
                null,
                new ItemStack[PlayerInventories.ENDERCHEST_SIZE]
        );
    }

    @Override
    public boolean owns(Inventory inv) {
        return inv.getHolder() instanceof InvseeHolder;
    }

    public boolean isDynamicGroup(String key) {
        return dynamicGroups.contains(key);
    }

    public GuiDefinition definition() {
        return definition;
    }

    public static class InvseeHolder implements InventoryHolder {
        private final UUID targetUUID;
        private final String targetName;
        private Inventory inventory;

        public InvseeHolder(UUID targetUUID, String targetName) {
            this.targetUUID = targetUUID;
            this.targetName = targetName;
        }

        public UUID targetUUID() {
            return targetUUID;
        }

        public String targetName() {
            return targetName;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return Objects.requireNonNullElseGet(inventory, () -> Bukkit.createInventory(this, 9, Component.text("Invalid Inventory")));
        }
    }

    public boolean isValidArmorForSlot(ItemStack item, int slot, GuiDefinition def) {
        if (item == null || item.getType().isAir()) return false;

        List<Integer> armor = def.items().get("player-armor").slots();
        if (armor.size() != 4) return false;

        Material type = item.getType();
        if (slot == armor.get(0)) return type.name().endsWith("_BOOTS");
        if (slot == armor.get(1)) return type.name().endsWith("_LEGGINGS");
        if (slot == armor.get(2)) return type.name().endsWith("_CHESTPLATE");
        if (slot == armor.get(3))
            return type.name().endsWith("_HELMET")
                    || type == Material.CARVED_PUMPKIN
                    || type == Material.PLAYER_HEAD;

        return false;
    }
}