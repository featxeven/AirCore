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
import org.bukkit.event.inventory.ClickType;
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
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Set<UUID> pendingUpdates = new HashSet<>();
    private final TargetListener targetListener;
    private final ViewerListener viewerListener;
    private GuiDefinition definition;
    private String titleOwn;

    public EnderchestManager(AirCore plugin, ItemAction itemAction) {
        this.plugin = plugin;
        this.itemAction = itemAction;
        this.targetListener = new TargetListener(plugin, this);
        this.viewerListener = new ViewerListener(this);

        Bukkit.getPluginManager().registerEvents(targetListener, plugin);
        Bukkit.getPluginManager().registerEvents(viewerListener, plugin);
        loadDefinition();
    }

    private void loadDefinition() {
        File file = new File(plugin.getDataFolder(), "guis/invsee/enderchest.yml");
        if (!file.exists()) plugin.saveResource("guis/invsee/enderchest.yml", false);

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        this.titleOwn = cfg.getString("title-own", "Ender Chest");

        Map<String, GuiDefinition.GuiItem> items = new HashMap<>();
        Set<String> dynamicGroups = Set.of("player-enderchest");

        for (String key : dynamicGroups) {
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
        this.definition = new GuiDefinition(cfg.getString("title", "Enderchest"), cfg.getInt("rows", 3), items, cfg);
    }

    @Override
    public Inventory build(Player viewer, Map<String, String> placeholders) {
        String targetName = placeholders.get("target");
        Player target = Bukkit.getPlayerExact(targetName);
        UUID targetUUID = target != null ? target.getUniqueId() : plugin.database().records().uuidFromName(targetName);

        ItemStack[] contents;
        if (target != null) {
            contents = target.getEnderChest().getContents();
        } else {
            PlayerInventories.InventoryBundle bundle = plugin.database().inventories().loadAllInventory(targetUUID);
            contents = bundle != null ? bundle.enderChest() : new ItemStack[27];
        }

        String title = PlaceholderUtil.apply(viewer, definition.title().replace("%player%", viewer.getName()).replace("%target%", targetName));
        Inventory inv = Bukkit.createInventory(new EnderchestHolder(targetUUID, targetName), definition.rows() * 9, mm.deserialize(title));

        EnderchestSlotMapper.fill(inv, definition, contents);
        EnderchestSlotMapper.fillCustom(inv, definition, viewer, placeholders, this);
        targetListener.registerViewer(targetUUID, viewer);
        return inv;
    }

    public Inventory buildOwn(Player viewer) {
        ItemStack[] contents = viewer.getEnderChest().getContents();
        String title = PlaceholderUtil.apply(viewer, titleOwn);
        Inventory inv = Bukkit.createInventory(new EnderchestHolder(viewer.getUniqueId(), viewer.getName()), 27, mm.deserialize(title));
        inv.setContents(contents);
        targetListener.registerViewer(viewer.getUniqueId(), viewer);
        return inv;
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player viewer) {
        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !(top.getHolder() instanceof EnderchestHolder holder)) return;

        int slot = event.getSlot();
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean hasModifyPerm = viewer.hasPermission("aircore.command.enderchest.others.modify")
                || holder.targetUUID().equals(viewer.getUniqueId());

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

        boolean dynamic = EnderchestSlotMapper.isDynamicSlot(definition, slot);
        boolean isFiller = EnderchestSlotMapper.isCustomFillerAt(definition, slot, current);
        GuiDefinition.GuiItem item = EnderchestSlotMapper.findItem(definition, slot);

        if (item == null) {
            event.setCancelled(true);
            return;
        }

        if (!hasModifyPerm || (dynamic && isFiller && cursor.getType().isAir())) {
            event.setCancelled(true);
            handleAction(viewer, holder, isFiller ? EnderchestSlotMapper.findCustomItemAt(definition, slot) : item, event.getClick());
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

    private void handleAction(Player viewer, EnderchestHolder ih, GuiDefinition.GuiItem item, ClickType click) {
        if (item == null) return;
        List<String> actions = item.getActionsForClick(click);
        if (actions != null && !actions.isEmpty()) {
            itemAction.executeAll(actions, viewer, Map.of("player", viewer.getName(), "target", ih.targetName()));
        }
    }

    public void syncAndRefresh(Inventory top, Player viewer) {
        if (!(top.getHolder() instanceof EnderchestHolder eh)) return;
        if (!pendingUpdates.add(viewer.getUniqueId())) return;

        plugin.scheduler().runEntityTaskDelayed(viewer, () -> {
            try {
                refreshFillers(top, viewer);
                ItemStack[] contents = EnderchestSlotMapper.extractContents(top, definition);
                applyEnderchestToTarget(eh.targetUUID(), contents);
                targetListener.refreshViewers(eh.targetUUID(), contents);
            } finally {
                pendingUpdates.remove(viewer.getUniqueId());
            }
        }, 1L);
    }

    public void refreshFillers(Inventory top, Player viewer) {
        if (!(top.getHolder() instanceof EnderchestHolder eh) || top.getSize() == 27) return;
        EnderchestSlotMapper.fillCustom(top, definition, viewer, Map.of("player", viewer.getName(), "target", eh.targetName()), this);
    }

    void applyEnderchestToTarget(UUID targetUUID, ItemStack[] contents) {
        Player target = Bukkit.getPlayer(targetUUID);
        if (target == null || !target.isOnline()) {
            plugin.database().inventories().saveEnderchest(targetUUID, contents);
            return;
        }
        var ec = target.getEnderChest();
        if (!Arrays.equals(ec.getContents(), contents)) {
            ec.setContents(contents);
            target.updateInventory();
        }
    }

    private void distributeToSlots(GuiDefinition def, Inventory top, ItemStack moving, Inventory sourceInv, int sourceSlot) {
        List<Integer> slots = def.items().get("player-enderchest").slots();
        for (int dest : slots) {
            ItemStack target = top.getItem(dest);
            if (target != null && target.isSimilar(moving)) {
                int transfer = Math.min(moving.getAmount(), target.getMaxStackSize() - target.getAmount());
                if (transfer > 0) { target.setAmount(target.getAmount() + transfer); moving.setAmount(moving.getAmount() - transfer); }
            }
            if (moving.getAmount() <= 0) { sourceInv.setItem(sourceSlot, null); return; }
        }
        for (int dest : slots) {
            ItemStack target = top.getItem(dest);
            if (target == null || target.getType().isAir() || EnderchestSlotMapper.isCustomFillerAt(def, dest, target)) {
                top.setItem(dest, moving.clone());
                sourceInv.setItem(sourceSlot, null);
                return;
            }
        }
    }

    public void unregisterListeners() { HandlerList.unregisterAll(targetListener); HandlerList.unregisterAll(viewerListener); }
    @Override public boolean owns(Inventory inv) { return inv.getHolder() instanceof EnderchestHolder; }
    public boolean isDynamicGroup(String key) { return "player-enderchest".equals(key); }
    public GuiDefinition definition() { return definition; }

    public record EnderchestHolder(UUID targetUUID, String targetName) implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { throw new UnsupportedOperationException(); }
    }
}