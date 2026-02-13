package com.ftxeven.aircore.core.gui.invsee.inventory;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.gui.GuiDefinition;
import com.ftxeven.aircore.core.gui.GuiManager;
import com.ftxeven.aircore.core.gui.ItemAction;
import com.ftxeven.aircore.database.player.PlayerInventories;
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

public final class InventoryManager implements GuiManager.CustomGuiManager {

    private static final Set<String> DYNAMIC_GROUPS = Set.of("player-hotbar", "player-inventory", "player-armor", "player-offhand");

    private final AirCore plugin;
    private final ItemAction itemAction;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Set<UUID> pendingUpdates = new HashSet<>();
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
        Map<String, GuiDefinition.GuiItem> items = new HashMap<>();

        for (String key : DYNAMIC_GROUPS) {
            items.put(key, new GuiDefinition.GuiItem(key, GuiDefinition.parseSlots(cfg.getStringList(key)),
                    Material.AIR, null, Collections.emptyList(), false, null, Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                    null, null, null, Collections.emptyMap(), Collections.emptyList(), null, null, null));
        }

        ConfigurationSection itemsSec = cfg.getConfigurationSection("items");
        if (itemsSec != null) {
            for (String key : itemsSec.getKeys(false)) {
                ConfigurationSection sec = itemsSec.getConfigurationSection(key);
                if (sec != null) items.put(key, GuiDefinition.GuiItem.fromSection(key, sec, mm));
            }
        }

        this.definition = new GuiDefinition(cfg.getString("title", "Invsee"), cfg.getInt("rows", 6), items, cfg);
    }

    @Override
    public Inventory build(Player viewer, Map<String, String> placeholders) {
        String targetName = placeholders.get("target");
        Player target = Bukkit.getPlayerExact(targetName);
        UUID targetUUID = target != null ? target.getUniqueId() : plugin.database().records().uuidFromName(targetName);

        PlayerInventories.InventoryBundle bundle = target != null
                ? new PlayerInventories.InventoryBundle(target.getInventory().getContents(), target.getInventory().getArmorContents(), target.getInventory().getItemInOffHand(), target.getEnderChest().getContents())
                : plugin.database().inventories().loadAllInventory(targetUUID);

        if (bundle == null) bundle = emptyBundle();

        String title = PlaceholderUtil.apply(viewer, definition.title().replace("%player%", viewer.getName()).replace("%target%", targetName));
        InvseeHolder holder = new InvseeHolder(targetUUID, targetName);
        Inventory inv = Bukkit.createInventory(holder, definition.rows() * 9, mm.deserialize(title));
        holder.setInventory(inv);

        InventorySlotMapper.fill(inv, definition, bundle);
        InventorySlotMapper.fillCustom(inv, definition, viewer, placeholders, this);
        targetListener.registerViewer(targetUUID, viewer);

        return inv;
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player viewer) {
        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !(top.getHolder() instanceof InvseeHolder holder)) return;

        int slot = event.getSlot();
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean hasModifyPerm = viewer.hasPermission("aircore.command.invsee.modify");

        if (clicked == event.getView().getBottomInventory()) {
            if (event.isShiftClick()) {
                if (!hasModifyPerm || current == null || current.getType().isAir()) {
                    event.setCancelled(true);
                    return;
                }
                event.setCancelled(true);
                distributeToSlots(definition, top, current, clicked, slot);
                syncAndRefresh(top, viewer);
            }
            return;
        }

        boolean dynamic = InventorySlotMapper.isDynamicSlot(definition, slot);
        boolean isFiller = InventorySlotMapper.isCustomFillerAt(definition, slot, current);
        GuiDefinition.GuiItem item = InventorySlotMapper.findItem(definition, slot);

        if (item == null) {
            event.setCancelled(true);
            return;
        }

        if (!hasModifyPerm || (dynamic && isFiller && cursor.getType().isAir())) {
            event.setCancelled(true);
            handleAction(viewer, holder, isFiller ? InventorySlotMapper.findCustomItemAt(definition, slot) : item, event.getClick());
            return;
        }

        if (event.getClick() == ClickType.NUMBER_KEY) {
            ItemStack hotbar = viewer.getInventory().getItem(event.getHotbarButton());
            if (isArmorSlot(slot) && hotbar != null && !isValidArmorForSlot(hotbar, slot)) {
                event.setCancelled(true);
                return;
            }
            event.setCancelled(true);
            top.setItem(slot, hotbar == null ? null : hotbar.clone());
            viewer.getInventory().setItem(event.getHotbarButton(), isFiller ? null : current);
            syncAndRefresh(top, viewer);
            return;
        }

        if (isArmorSlot(slot) && !cursor.getType().isAir() && !isValidArmorForSlot(cursor, slot)) {
            event.setCancelled(true);
            return;
        }

        if (dynamic) {
            if (isFiller && !cursor.getType().isAir()) {
                event.setCancelled(true);
                top.setItem(slot, cursor.clone());
                viewer.setItemOnCursor(null);
                syncAndRefresh(top, viewer);
            } else {
                event.setCancelled(false);
                syncAndRefresh(top, viewer);
            }
        } else {
            event.setCancelled(true);
            handleAction(viewer, holder, item, event.getClick());
        }
    }

    private void handleAction(Player viewer, InvseeHolder ih, GuiDefinition.GuiItem item, ClickType click) {
        if (item == null) return;
        List<String> actions = item.getActionsForClick(click);
        if (actions != null && !actions.isEmpty()) {
            itemAction.executeAll(actions, viewer, Map.of("player", viewer.getName(), "target", ih.targetName()));
        }
    }

    private void distributeToSlots(GuiDefinition def, Inventory top, ItemStack moving, Inventory sourceInv, int sourceSlot) {
        String[] priority = {"player-armor", "player-offhand", "player-inventory", "player-hotbar"};
        for (String groupKey : priority) {
            List<Integer> slots = def.items().get(groupKey).slots();
            for (int slot : slots) {
                ItemStack target = top.getItem(slot);
                if (target != null && target.isSimilar(moving) && (!groupKey.equals("player-armor") || isValidArmorForSlot(moving, slot))) {
                    int transfer = Math.min(moving.getAmount(), target.getMaxStackSize() - target.getAmount());
                    if (transfer > 0) {
                        target.setAmount(target.getAmount() + transfer);
                        moving.setAmount(moving.getAmount() - transfer);
                    }
                }
                if (moving.getAmount() <= 0) break;
            }
            if (moving.getAmount() > 0) {
                for (int slot : slots) {
                    ItemStack target = top.getItem(slot);
                    if ((target == null || target.getType().isAir() || InventorySlotMapper.isCustomFillerAt(def, slot, target))
                            && (!groupKey.equals("player-armor") || isValidArmorForSlot(moving, slot))) {
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
        if (!(top.getHolder() instanceof InvseeHolder ih)) return;
        if (!pendingUpdates.add(viewer.getUniqueId())) return;

        plugin.scheduler().runEntityTaskDelayed(viewer, () -> {
            try {
                refreshFillers(top, viewer);
                PlayerInventories.InventoryBundle bundle = InventorySlotMapper.extractBundle(top, definition);
                applyBundleToTarget(ih.targetUUID(), bundle);
                targetListener.refreshViewers(ih.targetUUID(), bundle);
            } finally {
                pendingUpdates.remove(viewer.getUniqueId());
            }
        }, 1L);
    }

    public void refreshFillers(Inventory top, Player viewer) {
        if (!(top.getHolder() instanceof InvseeHolder ih)) return;
        InventorySlotMapper.fillCustom(top, definition, viewer, Map.of("player", viewer.getName(), "target", ih.targetName()), this);
    }

    void applyBundleToTarget(UUID targetUUID, PlayerInventories.InventoryBundle bundle) {
        Player target = Bukkit.getPlayer(targetUUID);
        if (target == null || !target.isOnline()) {
            plugin.database().inventories().saveInventory(targetUUID, bundle.contents(), bundle.armor(), bundle.offhand());
            return;
        }
        var inv = target.getInventory();
        boolean changed = false;
        if (!Arrays.equals(inv.getContents(), bundle.contents())) { inv.setContents(bundle.contents()); changed = true; }
        if (!Arrays.equals(inv.getArmorContents(), bundle.armor())) { inv.setArmorContents(bundle.armor()); changed = true; }
        if (!Objects.equals(inv.getItemInOffHand(), bundle.offhand())) { inv.setItemInOffHand(bundle.offhand()); changed = true; }
        if (changed) target.updateInventory();
    }

    private boolean isArmorSlot(int slot) {
        return definition.items().get("player-armor").slots().contains(slot);
    }

    public boolean isValidArmorForSlot(ItemStack item, int slot) {
        if (item == null || item.getType().isAir()) return false;
        List<Integer> armor = definition.items().get("player-armor").slots();
        if (armor.size() != 4) return false;
        String name = item.getType().name();
        if (slot == armor.get(0)) return name.endsWith("_BOOTS");
        if (slot == armor.get(1)) return name.endsWith("_LEGGINGS");
        if (slot == armor.get(2)) return name.endsWith("_CHESTPLATE");
        if (slot == armor.get(3)) return name.endsWith("_HELMET") || item.getType() == Material.CARVED_PUMPKIN || item.getType() == Material.PLAYER_HEAD;
        return false;
    }

    public void unregisterListeners() { HandlerList.unregisterAll(targetListener); HandlerList.unregisterAll(viewerListener); }
    @Override public boolean owns(Inventory inv) { return inv.getHolder() instanceof InvseeHolder; }
    public boolean isDynamicGroup(String key) { return DYNAMIC_GROUPS.contains(key); }
    public GuiDefinition definition() { return definition; }

    private static PlayerInventories.InventoryBundle emptyBundle() {
        return new PlayerInventories.InventoryBundle(new ItemStack[36], new ItemStack[4], null, new ItemStack[27]);
    }

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