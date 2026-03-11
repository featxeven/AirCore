package com.ftxeven.aircore.core.gui.invsee.inventory;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.gui.GuiDefinition;
import com.ftxeven.aircore.core.gui.GuiDefinition.GuiItem;
import com.ftxeven.aircore.core.gui.GuiManager;
import com.ftxeven.aircore.core.gui.ItemAction;
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
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class InventoryManager implements GuiManager.CustomGuiManager {

    private static final Set<String> DYNAMIC_GROUPS = Set.of("hotbar-slots", "inventory-slots", "armor-slots", "offhand-slots");
    private static final String[] SHIFT_PRIORITY = {"armor-slots", "offhand-slots", "inventory-slots", "hotbar-slots"};
    private final AirCore plugin;
    private final ItemAction itemAction;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Set<UUID> pendingUpdates = ConcurrentHashMap.newKeySet();
    private final TargetListener targetListener;
    private final ViewerListener viewerListener;
    private GuiDefinition definition;
    private final Map<UUID, Map<Integer, Long>> slotLocks = new ConcurrentHashMap<>();

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
        for (String key : DYNAMIC_GROUPS) items.put(key, createEmptyGroup(key, cfg.getStringList(key)));
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
                : Optional.ofNullable(plugin.database().inventories().loadAllInventory(targetUUID)).orElseGet(InventoryManager::emptyBundle);

        InvseeHolder holder = new InvseeHolder(targetUUID, targetName);
        Inventory inv = Bukkit.createInventory(holder, definition.rows() * 9, mm.deserialize("<!italic>" + PlaceholderUtil.apply(viewer, definition.title().replace("%target%", targetName), placeholders)));
        holder.setInventory(inv);
        InventorySlotMapper.fillCustom(inv, definition, viewer, placeholders, this, plugin);
        InventorySlotMapper.fill(plugin, inv, definition, bundle, viewer, placeholders);
        targetListener.registerViewer(targetUUID, viewer);
        return inv;
    }

    public boolean isSlotLocked(UUID targetUUID, int slot) {
        Map<Integer, Long> targetMap = slotLocks.get(targetUUID);
        if (targetMap == null) return false;
        Long expiry = targetMap.get(slot);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            targetMap.remove(slot);
            return false;
        }
        return true;
    }

    public void lockSlot(UUID targetUUID, int slot) {
        long lockDuration = plugin.scheduler().isFoliaServer() ? 100L : 0L;

        if (lockDuration > 0) {
            slotLocks.computeIfAbsent(targetUUID, k -> new ConcurrentHashMap<>())
                    .put(slot, System.currentTimeMillis() + lockDuration);
        }
    }

    public boolean hasActiveLocks(UUID targetUUID) {
        if (!plugin.scheduler().isFoliaServer()) return false;

        Map<Integer, Long> targetMap = slotLocks.get(targetUUID);
        if (targetMap == null || targetMap.isEmpty()) return false;

        targetMap.entrySet().removeIf(entry -> System.currentTimeMillis() > entry.getValue());
        return !targetMap.isEmpty();
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player viewer) {
        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !(top.getHolder() instanceof InvseeHolder holder)) return;

        UUID targetUUID = holder.targetUUID();
        int slot = event.getSlot();

        if (clicked.equals(top)) {
            if (isSlotLocked(targetUUID, slot)) {
                event.setCancelled(true);
                return;
            }
            lockSlot(targetUUID, slot);
        }

        boolean canModify = viewer.hasPermission("aircore.command.invsee.modify");
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (clicked.equals(event.getView().getBottomInventory())) {
            if (event.isShiftClick() && canModify && current != null && current.getType() != Material.AIR) {
                event.setCancelled(true);
                if (distributeToSlots(top, current)) {
                    clicked.setItem(slot, null);
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

        if (!canModify || !dynamic) {
            handleAction(viewer, holder, item, event.getClick());
            return;
        }

        if (event.getAction() == InventoryAction.HOTBAR_SWAP) {
            ItemStack hotbarItem = viewer.getInventory().getItem(event.getHotbarButton());
            if (isArmorSlot(slot) && hotbarItem != null && !isValidArmorForSlot(hotbarItem, slot)) return;

            top.setItem(slot, (hotbarItem == null || hotbarItem.getType().isAir()) ? null : hotbarItem.clone());
            viewer.getInventory().setItem(event.getHotbarButton(), filler ? null : current);
            syncAndRefresh(top, viewer);
            return;
        }

        if (filler) {
            if (!cursor.getType().isAir()) {
                if (isArmorSlot(slot) && !isValidArmorForSlot(cursor, slot)) return;
                top.setItem(slot, cursor.clone());
                viewer.setItemOnCursor(null);
                syncAndRefresh(top, viewer);
            }
        } else {
            event.setCancelled(false);
            plugin.scheduler().runEntityTaskDelayed(viewer, () -> syncAndRefresh(top, viewer), 1L);
        }
    }

    private boolean distributeToSlots(Inventory top, ItemStack toMove) {
        ItemStack item = toMove.clone();
        for (String groupKey : SHIFT_PRIORITY) {
            GuiItem group = definition.items().get(groupKey);
            if (group == null) continue;
            for (int slot : group.slots()) {
                if (groupKey.equals("armor-slots") && !isValidArmorForSlot(item, slot)) continue;
                ItemStack target = top.getItem(slot);
                if (target != null && target.isSimilar(item) && !InventorySlotMapper.isCustomFillerAt(definition, slot, target)) {
                    int adding = Math.min(item.getAmount(), target.getMaxStackSize() - target.getAmount());
                    if (adding > 0) {
                        target.setAmount(target.getAmount() + adding);
                        item.setAmount(item.getAmount() - adding);
                    }
                }
                if (item.getAmount() <= 0) return true;
            }
        }
        for (String groupKey : SHIFT_PRIORITY) {
            GuiItem group = definition.items().get(groupKey);
            if (group == null) continue;
            for (int slot : group.slots()) {
                if (groupKey.equals("armor-slots") && !isValidArmorForSlot(item, slot)) continue;
                ItemStack target = top.getItem(slot);
                if (target == null || target.getType() == Material.AIR || InventorySlotMapper.isCustomFillerAt(definition, slot, target)) {
                    top.setItem(slot, item.clone());
                    item.setAmount(0);
                    return true;
                }
            }
        }
        toMove.setAmount(item.getAmount());
        return item.getAmount() <= 0;
    }

    public void syncAndRefresh(Inventory top, Player viewer) {
        if (!(top.getHolder() instanceof InvseeHolder ih)) return;

        UUID targetUUID = ih.targetUUID();
        if (!pendingUpdates.add(targetUUID)) return;

        Player target = Bukkit.getPlayer(targetUUID);

        if (target != null && target.isOnline()) {
            plugin.scheduler().runEntityTaskDelayed(target, () -> {
                try {
                    InventoryBundle bundle = InventorySlotMapper.extractBundle(top, definition);
                    applyBundleToTarget(targetUUID, bundle);

                    Map<String, String> ph = Map.of("player", viewer.getName(), "target", ih.targetName());
                    targetListener.refreshViewers(targetUUID, bundle, viewer, ph);

                    viewer.updateInventory();
                } finally {
                    plugin.scheduler().runEntityTaskDelayed(target, () -> pendingUpdates.remove(targetUUID), 2L);
                }
            }, 1L);
        } else {
            plugin.scheduler().runAsync(() -> {
                try {
                    InventoryBundle bundle = InventorySlotMapper.extractBundle(top, definition);
                    plugin.database().inventories().saveInventory(targetUUID, bundle.contents(), bundle.armor(), bundle.offhand());
                } finally {
                    pendingUpdates.remove(targetUUID);
                }
            });
        }
    }

    void applyBundleToTarget(UUID targetUUID, InventoryBundle bundle) {
        plugin.database().inventories().saveInventory(targetUUID, bundle.contents(), bundle.armor(), bundle.offhand());

        Player target = Bukkit.getPlayer(targetUUID);
        if (target != null && target.isOnline()) {
            target.getInventory().setContents(bundle.contents());
            target.getInventory().setArmorContents(bundle.armor());
            target.getInventory().setItemInOffHand(bundle.offhand());
            target.updateInventory();
        }
    }

    private void handleAction(Player viewer, InvseeHolder ih, GuiItem item, ClickType click) {
        if (item == null || plugin.gui().cooldowns().isOnCooldown(viewer, item)) return;
        Map<String, String> ctx = Map.of("player", viewer.getName(), "target", ih.targetName());
        plugin.gui().cooldowns().applyCooldown(viewer, item);
        List<String> actions = item.getActionsForClick(viewer, ctx, click);
        if (actions != null) itemAction.executeAll(actions, viewer, ctx);
    }

    public boolean isValidArmorForSlot(ItemStack item, int slot) {
        if (item == null || item.getType() == Material.AIR) return false;
        GuiItem armorGroup = definition.items().get("armor-slots");
        if (armorGroup == null) return false;
        List<Integer> armor = armorGroup.slots();
        if (armor.size() < 4) return false;
        Material type = item.getType();
        String name = type.name();
        if (slot == armor.get(0)) return name.endsWith("_BOOTS");
        if (slot == armor.get(1)) return name.endsWith("_LEGGINGS");
        if (slot == armor.get(2)) return name.endsWith("_CHESTPLATE");
        if (slot == armor.get(3)) return name.endsWith("_HELMET") || type == Material.CARVED_PUMPKIN || type == Material.PLAYER_HEAD;
        return false;
    }

    private boolean isArmorSlot(int slot) {
        GuiItem armorGroup = definition.items().get("armor-slots");
        return armorGroup != null && armorGroup.slots().contains(slot);
    }

    @Override public void refresh(Inventory inv, Player viewer, Map<String, String> placeholders) {
        if (!(inv.getHolder() instanceof InvseeHolder holder)) return;
        Player target = Bukkit.getPlayer(holder.targetUUID());
        InventoryBundle bundle = (target != null && target.isOnline())
                ? new InventoryBundle(target.getInventory().getContents(), target.getInventory().getArmorContents(), target.getInventory().getItemInOffHand(), target.getEnderChest().getContents())
                : Optional.ofNullable(plugin.database().inventories().loadAllInventory(holder.targetUUID())).orElseGet(InventoryManager::emptyBundle);
        InventorySlotMapper.fillCustom(inv, definition, viewer, placeholders, this, plugin);
        InventorySlotMapper.fill(plugin, inv, definition, bundle, viewer, placeholders);
    }

    private GuiItem createEmptyGroup(String key, List<String> slots) {
        return new GuiItem(key, GuiDefinition.parseSlots(slots), "AIR", null, List.of(), false, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null, Map.of(), List.of(), null, null, null, 0.0, null, new TreeMap<>());
    }

    @Override public void cleanup() { HandlerList.unregisterAll(targetListener); HandlerList.unregisterAll(viewerListener); }
    @Override public boolean owns(Inventory inv) { return inv != null && inv.getHolder() instanceof InvseeHolder; }
    public boolean isDynamicGroup(String key) { return DYNAMIC_GROUPS.contains(key); }
    public GuiDefinition definition() { return definition; }
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

    public boolean isPending(UUID targetUUID) { return pendingUpdates.contains(targetUUID); }
    public TargetListener getTargetListener() { return targetListener; }
}