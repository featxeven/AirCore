package com.ftxeven.aircore.core.modules.gui.invsee.inventory;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.modules.gui.GuiDefinition;
import com.ftxeven.aircore.core.modules.gui.GuiDefinition.GuiItem;
import com.ftxeven.aircore.core.modules.gui.GuiManager;
import com.ftxeven.aircore.core.modules.gui.ItemAction;
import com.ftxeven.aircore.database.dao.PlayerInventories.InventoryBundle;
import com.ftxeven.aircore.util.PlaceholderUtil;
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
import java.util.concurrent.ConcurrentHashMap;

public final class InventoryManager implements GuiManager.CustomGuiManager {

    private static final Set<String> DYNAMIC_GROUPS = Set.of(
            "hotbar-slots",
            "inventory-slots",
            "armor-slots",
            "offhand-slots"
    );

    private final AirCore plugin;
    private final ItemAction itemAction;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Set<UUID> pendingUpdates = ConcurrentHashMap.newKeySet();
    private final TargetListener targetListener;
    private final ViewerListener viewerListener;
    private GuiDefinition definition;

    public InventoryManager(AirCore plugin, ItemAction itemAction) {
        this.plugin = plugin;
        this.itemAction = itemAction;
        this.targetListener = new TargetListener(plugin, this);
        this.viewerListener = new ViewerListener(this);

        Bukkit.getPluginManager().registerEvents(targetListener, plugin);
        Bukkit.getPluginManager().registerEvents(viewerListener, plugin);
        loadDefinition();
    }

    private void loadDefinition() {
        File file = new File(plugin.getDataFolder(), "guis/invsee/inventory.yml");
        if (!file.exists()) plugin.saveResource("guis/invsee/inventory.yml", false);

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        Map<String, GuiItem> items = new LinkedHashMap<>();

        for (String key : DYNAMIC_GROUPS) {
            items.put(key, createEmptyGroup(key, cfg.getStringList(key)));
        }

        ConfigurationSection itemsSec = cfg.getConfigurationSection("items");
        if (itemsSec != null) {
            for (String key : itemsSec.getKeys(false)) {
                ConfigurationSection sec = itemsSec.getConfigurationSection(key);
                if (sec != null) items.put(key, GuiItem.fromSection(key, sec));
            }
        }

        this.definition = new GuiDefinition(cfg.getString("title", "Invsee"), cfg.getInt("rows", 6), items, cfg);
    }

    @Override
    public Inventory build(Player viewer, Map<String, String> placeholders) {
        String targetName = placeholders.get("target");
        Player target = Bukkit.getPlayerExact(targetName);
        UUID targetUUID = target != null ? target.getUniqueId() : plugin.database().records().uuidFromName(targetName);

        InventoryBundle bundle = target != null
                ? new InventoryBundle(target.getInventory().getContents(), target.getInventory().getArmorContents(), target.getInventory().getItemInOffHand(), target.getEnderChest().getContents())
                : Optional.ofNullable(plugin.database().inventories().loadAllInventory(targetUUID)).orElse(emptyBundle());

        Map<String, String> context = new HashMap<>(placeholders);
        context.put("target", targetName);
        context.put("player", viewer.getName());

        String title = PlaceholderUtil.apply(viewer, definition.title().replace("%target%", targetName), context);
        InvseeHolder holder = new InvseeHolder(targetUUID, targetName);
        Inventory inv = Bukkit.createInventory(holder, definition.rows() * 9, mm.deserialize(title));
        holder.setInventory(inv);

        InventorySlotMapper.fillCustom(inv, definition, viewer, context, this);
        InventorySlotMapper.fill(inv, definition, bundle, viewer, context);

        targetListener.registerViewer(targetUUID, viewer);
        return inv;
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player viewer) {
        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !(top.getHolder() instanceof InvseeHolder holder)) return;

        boolean canModify = viewer.hasPermission("aircore.command.invsee.modify");
        int slot = event.getSlot();
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (clicked.equals(event.getView().getBottomInventory())) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                if (canModify && current != null && !current.getType().isAir()) {
                    distributeToSlots(definition, top, current, clicked, slot);
                    syncAndRefresh(top, viewer);
                }
            }
            return;
        }

        event.setCancelled(true);
        GuiItem item = InventorySlotMapper.findItem(definition, slot);
        if (item == null) return;

        boolean dynamic = InventorySlotMapper.isDynamicSlot(definition, slot);
        boolean filler = InventorySlotMapper.isCustomFillerAt(definition, slot, current);

        if (isArmorSlot(slot) && !cursor.getType().isAir() && !isValidArmorForSlot(cursor, slot)) return;

        if (event.getClick() == ClickType.NUMBER_KEY && clicked == top) {
            if (!canModify || !dynamic) return;
            ItemStack hotbarItem = viewer.getInventory().getItem(event.getHotbarButton());
            if (isArmorSlot(slot) && hotbarItem != null && !hotbarItem.getType().isAir() && !isValidArmorForSlot(hotbarItem, slot)) return;

            top.setItem(slot, hotbarItem == null ? null : hotbarItem.clone());
            viewer.getInventory().setItem(event.getHotbarButton(), filler ? null : current);
            syncAndRefresh(top, viewer);
            return;
        }

        if (canModify && dynamic) {
            if (filler && !cursor.getType().isAir()) {
                top.setItem(slot, cursor.clone());
                viewer.setItemOnCursor(null);
                syncAndRefresh(top, viewer);
            } else if (!filler) {
                event.setCancelled(false);
                syncAndRefresh(top, viewer);
            }
        } else {
            handleAction(viewer, holder, filler ? InventorySlotMapper.findCustomItemAt(definition, slot) : item, event.getClick());
        }
    }

    private void handleAction(Player viewer, InvseeHolder ih, GuiItem item, ClickType click) {
        if (item == null || plugin.gui().cooldowns().isOnCooldown(viewer, item)) {
            if (item != null) plugin.gui().cooldowns().sendCooldownMessage(viewer, item);
            return;
        }
        plugin.gui().cooldowns().applyCooldown(viewer, item);
        List<String> actions = item.getActionsForClick(click);
        if (actions != null && !actions.isEmpty()) {
            Map<String, String> context = new HashMap<>();
            context.put("player", viewer.getName());
            context.put("target", ih.targetName());

            itemAction.executeAll(actions, viewer, context);
        }
    }

    private void distributeToSlots(GuiDefinition def, Inventory top, ItemStack moving, Inventory sourceInv, int sourceSlot) {
        String[] priority = {"armor-slots", "offhand-slots", "inventory-slots", "hotbar-slots"};
        for (String groupKey : priority) {
            List<Integer> slots = def.items().get(groupKey).slots();

            for (int slot : slots) {
                ItemStack target = top.getItem(slot);

                if (groupKey.equals("armor-slots") && !isValidArmorForSlot(moving, slot)) continue;

                if (target != null && target.isSimilar(moving)) {
                    int transfer = Math.min(moving.getAmount(), target.getMaxStackSize() - target.getAmount());
                    target.setAmount(target.getAmount() + transfer);
                    moving.setAmount(moving.getAmount() - transfer);
                }
                if (moving.getAmount() <= 0) break;
            }

            if (moving.getAmount() > 0) {
                for (int slot : slots) {
                    if (groupKey.equals("armor-slots") && !isValidArmorForSlot(moving, slot)) continue;

                    ItemStack target = top.getItem(slot);
                    if (target == null || target.getType().isAir() || InventorySlotMapper.isCustomFillerAt(def, slot, target)) {
                        top.setItem(slot, moving.clone());
                        moving.setAmount(0);
                        break;
                    }
                }
            }

            if (moving.getAmount() <= 0) {
                sourceInv.setItem(sourceSlot, null);
                return;
            }
        }
    }

    public void syncAndRefresh(Inventory top, Player viewer) {
        if (!(top.getHolder() instanceof InvseeHolder ih) || !pendingUpdates.add(viewer.getUniqueId())) return;

        plugin.scheduler().runEntityTaskDelayed(viewer, () -> {
            try {
                InventoryBundle bundle = InventorySlotMapper.extractBundle(top, definition);
                applyBundleToTarget(ih.targetUUID(), bundle);

                Map<String, String> placeholders = Map.of(
                        "player", viewer.getName(),
                        "target", ih.targetName()
                );

                targetListener.refreshViewers(ih.targetUUID(), bundle, viewer, placeholders);

            } finally {
                pendingUpdates.remove(viewer.getUniqueId());
            }
        }, 1L);
    }

    void applyBundleToTarget(UUID targetUUID, InventoryBundle bundle) {
        Player target = Bukkit.getPlayer(targetUUID);

        plugin.database().inventories().saveInventory(targetUUID, bundle.contents(), bundle.armor(), bundle.offhand());

        if (target != null && target.isOnline()) {
            plugin.scheduler().runEntityTask(target, () -> {
                var inv = target.getInventory();
                inv.setContents(bundle.contents());
                inv.setArmorContents(bundle.armor());
                inv.setItemInOffHand(bundle.offhand());
                target.updateInventory();
            });
        }
    }

    private boolean isArmorSlot(int slot) {
        return definition.items().get("armor-slots").slots().contains(slot);
    }

    public boolean isValidArmorForSlot(ItemStack item, int slot) {
        if (item == null || item.getType().isAir()) return false;
        List<Integer> armor = definition.items().get("armor-slots").slots();
        if (armor.size() != 4) return false;
        String name = item.getType().name();
        if (slot == armor.get(0)) return name.endsWith("_BOOTS");
        if (slot == armor.get(1)) return name.endsWith("_LEGGINGS");
        if (slot == armor.get(2)) return name.endsWith("_CHESTPLATE");
        if (slot == armor.get(3)) return name.endsWith("_HELMET") || item.getType() == Material.CARVED_PUMPKIN || item.getType() == Material.PLAYER_HEAD;
        return false;
    }

    @Override
    public void refresh(Inventory inv, Player viewer, Map<String, String> placeholders) {
        if (!(inv.getHolder() instanceof InvseeHolder holder)) return;

        Player target = Bukkit.getPlayer(holder.targetUUID());
        InventoryBundle bundle = (target != null && target.isOnline())
                ? new InventoryBundle(
                target.getInventory().getContents(),
                target.getInventory().getArmorContents(),
                target.getInventory().getItemInOffHand(),
                target.getEnderChest().getContents()
        )
                : Optional.ofNullable(plugin.database().inventories().loadAllInventory(holder.targetUUID()))
                .orElse(emptyBundle());

        Map<String, String> context = new HashMap<>(placeholders);
        context.put("target", holder.targetName());
        context.put("player", viewer.getName());

        InventorySlotMapper.fillCustom(inv, definition, viewer, context, this);
        InventorySlotMapper.fill(inv, definition, bundle, viewer, context);
    }

    private GuiItem createEmptyGroup(String key, List<String> slots) {
        return new GuiItem(key, GuiDefinition.parseSlots(slots), Material.AIR, null, Collections.emptyList(),
                false, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), null, null, null, Collections.emptyMap(),
                Collections.emptyList(), null, null, null, 0.0, null);
    }

    @Override public void cleanup() { HandlerList.unregisterAll(targetListener); HandlerList.unregisterAll(viewerListener); }
    @Override public boolean owns(Inventory inv) { return inv.getHolder() instanceof InvseeHolder; }
    public boolean isDynamicGroup(String key) { return DYNAMIC_GROUPS.contains(key); }
    public GuiDefinition definition() { return definition; }
    public TargetListener getTargetListener() { return targetListener; }
    private static InventoryBundle emptyBundle() { return new InventoryBundle(new ItemStack[36], new ItemStack[4], null, new ItemStack[27]); }

    public static class InvseeHolder implements InventoryHolder {
        private final UUID targetUUID;
        private final String targetName;
        private Inventory inventory;
        public InvseeHolder(UUID uuid, String name) { this.targetUUID = uuid; this.targetName = name; }
        public UUID targetUUID() { return targetUUID; }
        public String targetName() { return targetName; }
        public void setInventory(Inventory inv) { this.inventory = inv; }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }
}