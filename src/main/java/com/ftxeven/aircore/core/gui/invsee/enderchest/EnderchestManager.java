package com.ftxeven.aircore.core.gui.invsee.enderchest;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.gui.GuiDefinition;
import com.ftxeven.aircore.core.gui.GuiManager;
import com.ftxeven.aircore.core.gui.ItemAction;
import com.ftxeven.aircore.database.dao.PlayerInventories;
import com.ftxeven.aircore.util.PlaceholderUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
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

public final class EnderchestManager implements GuiManager.CustomGuiManager {

    private final AirCore plugin;
    private final ItemAction itemAction;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Set<UUID> pendingUpdates = ConcurrentHashMap.newKeySet();
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

        Map<String, GuiDefinition.GuiItem> items = new LinkedHashMap<>();
        items.put("enderchest-slots", createEmptyGroup(cfg.getStringList("enderchest-slots")));

        ConfigurationSection itemsSec = cfg.getConfigurationSection("items");
        if (itemsSec != null) {
            for (String key : itemsSec.getKeys(false)) {
                ConfigurationSection sec = itemsSec.getConfigurationSection(key);
                if (sec != null) items.put(key, GuiDefinition.GuiItem.fromSection(key, sec));
            }
        }
        this.definition = new GuiDefinition(cfg.getString("title", "Enderchest"), cfg.getInt("rows", 3), items, cfg);
    }

    @Override
    public Inventory build(Player viewer, Map<String, String> placeholders) {
        String targetName = placeholders.get("target");
        Player target = Bukkit.getPlayerExact(targetName);
        UUID targetUUID = target != null ? target.getUniqueId() : plugin.database().records().uuidFromName(targetName);

        ItemStack[] contents = (target != null) ? target.getEnderChest().getContents() :
                Optional.ofNullable(plugin.database().inventories().loadAllInventory(targetUUID))
                        .map(PlayerInventories.InventoryBundle::enderChest).orElse(new ItemStack[27]);

        Map<String, String> context = new HashMap<>(placeholders);
        context.put("target", targetName);
        context.put("player", viewer.getName());

        String title = PlaceholderUtil.apply(viewer, definition.title().replace("%target%", targetName), context);
        Inventory inv = Bukkit.createInventory(new EnderchestHolder(targetUUID, targetName, false), definition.rows() * 9, mm.deserialize(title));

        EnderchestSlotMapper.fill(inv, definition, contents);
        EnderchestSlotMapper.fillCustom(inv, definition, viewer, context, this, plugin);

        targetListener.registerViewer(targetUUID, viewer);
        return inv;
    }

    public Inventory buildOwn(Player viewer) {
        ItemStack[] contents = viewer.getEnderChest().getContents();
        String title = PlaceholderUtil.apply(viewer, titleOwn);
        Inventory inv = Bukkit.createInventory(new EnderchestHolder(viewer.getUniqueId(), viewer.getName(), true), 27, mm.deserialize(title));
        inv.setContents(contents);
        targetListener.registerViewer(viewer.getUniqueId(), viewer);
        return inv;
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player viewer) {
        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !(top.getHolder() instanceof EnderchestHolder holder)) return;

        if (holder.isOwn()) {
            if (clicked.equals(top)) { syncAndRefresh(top, viewer); }
            return;
        }

        boolean canModify = viewer.hasPermission("aircore.command.enderchest.others.modify") || holder.targetUUID().equals(viewer.getUniqueId());
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

        GuiDefinition.GuiItem item = null;
        for (GuiDefinition.GuiItem guiItem : definition.items().values()) {
            if (guiItem.slots().contains(slot)) {
                item = guiItem;
                break;
            }
        }

        if (item == null) return;

        boolean dynamic = "enderchest-slots".equals(item.key());
        boolean filler = EnderchestSlotMapper.isCustomFillerAt(definition, slot, current);

        if (canModify && dynamic) {
            if (filler && !cursor.getType().isAir()) {
                top.setItem(slot, cursor.clone());
                viewer.setItemOnCursor(null);
                syncAndRefresh(top, viewer);
            }
            else if (!filler) {
                event.setCancelled(false);
                syncAndRefresh(top, viewer);
            }
        } else {
            handleAction(viewer, holder, item, event.getClick());
        }
    }

    private void handleAction(Player viewer, EnderchestHolder ih, GuiDefinition.GuiItem item, ClickType click) {
        if (item == null || plugin.gui().cooldowns().isOnCooldown(viewer, item)) {
            if (item != null) plugin.gui().cooldowns().sendCooldownMessage(viewer, item);
            return;
        }

        Map<String, String> context = new HashMap<>();
        context.put("player", viewer.getName());
        context.put("target", ih.targetName());

        List<String> actions = item.getActionsForClick(viewer, context, click);

        plugin.gui().cooldowns().applyCooldown(viewer, item);
        if (actions != null && !actions.isEmpty()) {
            itemAction.executeAll(actions, viewer, context);
        }
    }

    public void syncAndRefresh(Inventory top, Player viewer) {
        if (!(top.getHolder() instanceof EnderchestHolder eh) || !pendingUpdates.add(viewer.getUniqueId())) return;

        plugin.scheduler().runEntityTaskDelayed(viewer, () -> {
            try {
                ItemStack[] contents = eh.isOwn()
                        ? top.getContents()
                        : EnderchestSlotMapper.extractContents(top, definition);

                applyEnderchestToTarget(eh.targetUUID(), contents);
                targetListener.refreshViewers(eh.targetUUID(), contents);
            } finally {
                pendingUpdates.remove(viewer.getUniqueId());
            }
        }, 1L);
    }

    void applyEnderchestToTarget(UUID targetUUID, ItemStack[] contents) {
        Player target = Bukkit.getPlayer(targetUUID);
        if (target == null || !target.isOnline()) {
            plugin.scheduler().runAsync(() -> plugin.database().inventories().saveEnderchest(targetUUID, contents));
            return;
        }

        plugin.scheduler().runEntityTask(target, () -> {
            if (!Arrays.equals(target.getEnderChest().getContents(), contents)) {
                target.getEnderChest().setContents(contents);
            }
        });
    }

    private void distributeToSlots(GuiDefinition def, Inventory top, ItemStack moving, Inventory sourceInv, int sourceSlot) {
        List<Integer> slots = def.items().get("enderchest-slots").slots();
        for (int dest : slots) {
            ItemStack target = top.getItem(dest);
            if (target != null && target.isSimilar(moving)) {
                int transfer = Math.min(moving.getAmount(), target.getMaxStackSize() - target.getAmount());
                target.setAmount(target.getAmount() + transfer);
                moving.setAmount(moving.getAmount() - transfer);
            }
            if (moving.getAmount() <= 0) break;
        }
        if (moving.getAmount() > 0) {
            for (int dest : slots) {
                ItemStack target = top.getItem(dest);
                if (target == null || target.getType().isAir() || EnderchestSlotMapper.isCustomFillerAt(def, dest, target)) {
                    top.setItem(dest, moving.clone());
                    moving.setAmount(0);
                    break;
                }
            }
        }
        if (moving.getAmount() <= 0) sourceInv.setItem(sourceSlot, null);
    }

    @Override
    public void refresh(Inventory inv, Player viewer, Map<String, String> placeholders) {
        if (!(inv.getHolder() instanceof EnderchestHolder holder)) return;
        Player target = Bukkit.getPlayer(holder.targetUUID());
        ItemStack[] contents = (target != null) ? target.getEnderChest().getContents() :
                Optional.ofNullable(plugin.database().inventories().loadAllInventory(holder.targetUUID()))
                        .map(PlayerInventories.InventoryBundle::enderChest).orElse(new ItemStack[27]);

        EnderchestSlotMapper.fill(inv, definition, contents);

        if (inv.getSize() != 27) {
            Map<String, String> context = new HashMap<>(placeholders);
            context.put("target", holder.targetName());
            context.put("player", viewer.getName());

            EnderchestSlotMapper.fillCustom(inv, definition, viewer, context, this, plugin);
        }
    }

    private GuiDefinition.GuiItem createEmptyGroup(List<String> slots) {
        return new GuiDefinition.GuiItem(
                "enderchest-slots",
                GuiDefinition.parseSlots(slots),
                "AIR",
                null,
                List.of(),
                false,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                Map.of(),
                List.of(),
                null,
                null,
                null,
                0.0,
                null,
                new TreeMap<>()
        );
    }

    @Override public void cleanup() { HandlerList.unregisterAll(targetListener); HandlerList.unregisterAll(viewerListener); }
    @Override public boolean owns(Inventory inv) { return inv.getHolder() instanceof EnderchestHolder; }
    public boolean isDynamicGroup(String key) { return "enderchest-slots".equals(key); }
    public GuiDefinition definition() { return definition; }
    public TargetListener getTargetListener() { return targetListener; }

    public record EnderchestHolder(UUID targetUUID, String targetName, boolean isOwn) implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { throw new UnsupportedOperationException(); }
    }
}