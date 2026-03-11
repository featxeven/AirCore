package com.ftxeven.aircore.core.gui.homes;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.gui.GuiDefinition;
import com.ftxeven.aircore.core.gui.GuiDefinition.GuiItem;
import com.ftxeven.aircore.core.gui.ItemAction;
import com.ftxeven.aircore.util.MessageUtil;
import com.ftxeven.aircore.util.PlaceholderUtil;
import com.ftxeven.aircore.util.TimeUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class ConfirmTargetManager implements Listener {

    private final AirCore plugin;
    private final ItemAction itemAction;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private GuiDefinition teleportDef;
    private GuiDefinition deleteDef;

    public ConfirmTargetManager(AirCore plugin, ItemAction itemAction) {
        this.plugin = plugin;
        this.itemAction = itemAction;
        loadDefinition();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void loadDefinition() {
        File file = new File(plugin.getDataFolder(), "guis/homes/confirm-target.yml");
        if (!file.exists()) plugin.saveResource("guis/homes/confirm-target.yml", false);

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        this.teleportDef = loadSubSection(cfg.getConfigurationSection("teleport"), cfg);
        this.deleteDef = loadSubSection(cfg.getConfigurationSection("delete"), cfg);
    }

    private GuiDefinition loadSubSection(ConfigurationSection sec, YamlConfiguration root) {
        if (sec == null) return null;
        Map<String, GuiItem> items = new LinkedHashMap<>();
        loadItems(sec.getConfigurationSection("buttons"), items);
        loadItems(sec.getConfigurationSection("items"), items);
        return new GuiDefinition(sec.getString("title", "Confirm"), sec.getInt("rows", 3), items, root);
    }

    private void loadItems(ConfigurationSection sec, Map<String, GuiItem> items) {
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection itemSec = sec.getConfigurationSection(key);
            if (itemSec != null) items.put(key, GuiItem.fromSection(key, itemSec));
        }
    }

    public void open(Player viewer, String targetName, String homeName, String actionType, String page, String sortType, String filterType) {
        GuiDefinition def = actionType.equalsIgnoreCase("delete") ? deleteDef : teleportDef;
        if (def == null) return;

        UUID targetUUID = plugin.database().records().uuidFromName(targetName);
        if (targetUUID == null) return;

        Location loc = plugin.home().homes().getHomes(targetUUID).get(homeName.toLowerCase());
        Map<String, Long> times = plugin.database().homes().loadTimestamps(targetUUID);
        long timestamp = times.getOrDefault(homeName.toLowerCase(), 0L);

        Map<String, String> ph = new HashMap<>();
        ph.put("target", targetName);
        ph.put("name", homeName);
        ph.put("page", page);

        if (loc != null) {
            ph.put("world", loc.getWorld() != null ? loc.getWorld().getName() : "-");
            ph.put("x", String.valueOf(loc.getBlockX()));
            ph.put("y", String.valueOf(loc.getBlockY()));
            ph.put("z", String.valueOf(loc.getBlockZ()));

            LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());
            ph.put("time", dt.format(DateTimeFormatter.ofPattern("HH:mm")));
            ph.put("date", TimeUtil.formatDate(plugin, timestamp * 1000L));
        }

        HomeConfirmTargetHolder holder = new HomeConfirmTargetHolder(targetName, def, page, sortType, filterType, ph);
        Inventory inv = Bukkit.createInventory(holder, def.rows() * 9, MM.deserialize("<!italic>" + PlaceholderUtil.apply(viewer, def.title(), ph)));
        holder.setInventory(inv);

        def.items().values().stream()
                .filter(item -> !item.key().equals("confirm") && !item.key().equals("cancel"))
                .forEach(item -> renderToInventory(inv, item, viewer, ph));

        def.items().values().stream()
                .filter(item -> item.key().equals("confirm") || item.key().equals("cancel"))
                .forEach(item -> renderToInventory(inv, item, viewer, ph));

        viewer.openInventory(inv);
    }

    private void renderToInventory(Inventory inv, GuiItem item, Player viewer, Map<String, String> ph) {
        ItemStack stack = item.buildStack(viewer, ph, plugin);
        for (int slot : item.slots()) {
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, stack);
            }
        }
    }

    @EventHandler
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof HomeConfirmTargetHolder holder)) return;
        event.setCancelled(true);

        int slot = event.getSlot();
        Player viewer = (Player) event.getWhoClicked();

        GuiItem actionItem = null;
        for (String key : List.of("confirm", "cancel")) {
            GuiItem item = holder.getDef().items().get(key);
            if (item != null && item.slots().contains(slot)) {
                actionItem = item;
                break;
            }
        }

        if (actionItem != null) {
            if (isOnCooldown(viewer, actionItem)) return;
            handleConfirmOrCancel(viewer, actionItem, holder, event.getClick());
            return;
        }

        for (GuiItem item : holder.getDef().items().values()) {
            if (item.slots().contains(slot)) {
                if (isOnCooldown(viewer, item)) return;
                executeAction(item, viewer, event.getClick(), holder.getPlaceholders());
                return;
            }
        }
    }

    private void handleConfirmOrCancel(Player viewer, GuiItem item, HomeConfirmTargetHolder holder, ClickType click) {
        List<String> actions = item.getActionsForClick(viewer, holder.getPlaceholders(), click);
        if (actions == null || actions.isEmpty()) return;

        String targetName = holder.getTargetName();
        String homeName = holder.getPlaceholders().get("name");
        UUID targetUUID = plugin.database().records().uuidFromName(targetName);

        if (item.key().equals("confirm") && targetUUID != null) {
            for (String action : actions) {
                String lower = action.toLowerCase();
                if (lower.contains("[teleport]")) {
                    handleTeleport(viewer, targetUUID, targetName, homeName);
                } else if (lower.contains("[delete]")) {
                    handleDelete(viewer, targetUUID, targetName, homeName);
                }
            }
        }

        executeAction(item, viewer, click, holder.getPlaceholders());

        if (actions.stream().anyMatch(a -> a.equalsIgnoreCase("[close]"))) {
            viewer.closeInventory();
        } else {
            plugin.scheduler().runEntityTaskDelayed(viewer, () -> plugin.gui().openGui("homes-target", viewer, Map.of(
                    "player", targetName,
                    "page", holder.getPrevPage(),
                    "sort", holder.getSortType(),
                    "filter", holder.getFilterType()
            )), 1L);
        }
    }

    private void handleTeleport(Player viewer, UUID targetUUID, String targetName, String homeName) {
        Map<String, Location> targetHomes = plugin.home().homes().getHomes(targetUUID);
        if (targetHomes.isEmpty()) targetHomes = plugin.database().homes().load(targetUUID);

        Location loc = targetHomes.get(homeName.toLowerCase());
        if (loc != null) {
            plugin.core().teleports().startCountdown(viewer, viewer, () -> {
                plugin.core().teleports().teleport(viewer, loc);
                MessageUtil.send(viewer, "homes.teleport.success-other", Map.of("player", targetName, "name", homeName));
            }, reason -> MessageUtil.send(viewer, "homes.teleport.cancelled-other", Map.of("player", targetName, "name", homeName)));
        }
    }

    private void handleDelete(Player viewer, UUID targetUUID, String targetName, String homeName) {
        if (!viewer.hasPermission("aircore.command.delhome.others")) return;

        plugin.home().homes().deleteHome(targetUUID, homeName.toLowerCase());
        plugin.database().homes().delete(targetUUID, homeName.toLowerCase());
        MessageUtil.send(viewer, "homes.management.deleted-for", Map.of("player", targetName, "name", homeName));

        Player targetPlayer = Bukkit.getPlayer(targetUUID);
        if (targetPlayer != null && !targetPlayer.equals(viewer)) {
            MessageUtil.send(targetPlayer, "homes.management.deleted-by", Map.of("player", viewer.getName(), "name", homeName));
        }
    }

    private void executeAction(GuiItem item, Player viewer, ClickType click, Map<String, String> ph) {
        List<String> actions = item.getActionsForClick(viewer, ph, click);
        if (actions != null) {
            Map<String, String> fullPh = new HashMap<>(ph);
            fullPh.put("player", viewer.getName());
            itemAction.executeAll(actions, viewer, fullPh);
        }
    }

    private boolean isOnCooldown(Player p, GuiItem item) {
        if (plugin.gui().cooldowns().isOnCooldown(p, item)) {
            plugin.gui().cooldowns().sendCooldownMessage(p, item);
            return true;
        }
        plugin.gui().cooldowns().applyCooldown(p, item);
        return false;
    }

    public void cleanup() { HandlerList.unregisterAll(this); }

    public static final class HomeConfirmTargetHolder implements InventoryHolder {
        private final String targetName, prevPage, sortType, filterType;
        private final GuiDefinition def;
        private final Map<String, String> placeholders;
        private Inventory inventory;

        public HomeConfirmTargetHolder(String targetName, GuiDefinition def, String prevPage, String sortType, String filterType, Map<String, String> placeholders) {
            this.targetName = targetName; this.def = def; this.prevPage = prevPage; this.sortType = sortType; this.filterType = filterType; this.placeholders = placeholders;
        }
        @Override public @NotNull Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public String getTargetName() { return targetName; }
        public GuiDefinition getDef() { return def; }
        public String getPrevPage() { return prevPage; }
        public String getSortType() { return sortType; }
        public String getFilterType() { return filterType; }
        public Map<String, String> getPlaceholders() { return placeholders; }
    }
}