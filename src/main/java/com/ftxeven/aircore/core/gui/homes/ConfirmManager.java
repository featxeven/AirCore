package com.ftxeven.aircore.core.gui.homes;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.gui.GuiDefinition;
import com.ftxeven.aircore.core.gui.GuiDefinition.GuiItem;
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

public final class ConfirmManager implements Listener {

    private final AirCore plugin;
    private final HomeManager homeManager;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private GuiDefinition teleportDef;
    private GuiDefinition deleteDef;

    public ConfirmManager(AirCore plugin, HomeManager homeManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
        loadDefinition();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void loadDefinition() {
        File file = new File(plugin.getDataFolder(), "guis/homes/confirm.yml");
        if (!file.exists()) plugin.saveResource("guis/homes/confirm.yml", false);

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

    public void open(Player player, String homeName, String actionType, String page, String sortType, String filterType) {
        GuiDefinition def = actionType.equalsIgnoreCase("delete") ? deleteDef : teleportDef;
        if (def == null) return;

        Location loc = plugin.home().homes().getHomes(player.getUniqueId()).get(homeName.toLowerCase());
        Map<String, Long> times = plugin.database().homes().loadTimestamps(player.getUniqueId());
        long timestamp = times.getOrDefault(homeName.toLowerCase(), 0L);

        Map<String, String> ph = new HashMap<>();
        ph.put("name", homeName);
        ph.put("page", page);

        if (loc != null) {
            ph.put("world", loc.getWorld().getName());
            ph.put("x", String.valueOf(loc.getBlockX()));
            ph.put("y", String.valueOf(loc.getBlockY()));
            ph.put("z", String.valueOf(loc.getBlockZ()));

            LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());
            ph.put("time", dt.format(DateTimeFormatter.ofPattern("HH:mm")));
            ph.put("date", TimeUtil.formatDate(plugin, timestamp * 1000L));
        }

        HomeConfirmHolder holder = new HomeConfirmHolder(def, page, sortType, filterType, ph);
        Inventory inv = Bukkit.createInventory(holder, def.rows() * 9, mm.deserialize("<!italic>" + PlaceholderUtil.apply(player, def.title(), ph)));
        holder.setInventory(inv);

        def.items().values().stream()
                .filter(item -> !item.key().equals("confirm") && !item.key().equals("cancel"))
                .forEach(item -> renderToInventory(inv, item, player, ph));

        def.items().values().stream()
                .filter(item -> item.key().equals("confirm") || item.key().equals("cancel"))
                .forEach(item -> renderToInventory(inv, item, player, ph));

        player.openInventory(inv);
    }

    private void renderToInventory(Inventory inv, GuiItem item, Player player, Map<String, String> ph) {
        ItemStack stack = item.buildStack(player, ph, plugin);
        for (int slot : item.slots()) {
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, stack);
            }
        }
    }

    @EventHandler
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof HomeConfirmHolder holder)) return;
        event.setCancelled(true);

        int slot = event.getSlot();
        Player player = (Player) event.getWhoClicked();

        GuiItem actionItem = null;
        for (String key : List.of("confirm", "cancel")) {
            GuiItem item = holder.getDef().items().get(key);
            if (item != null && item.slots().contains(slot)) {
                actionItem = item;
                break;
            }
        }

        if (actionItem != null) {
            if (isOnCooldown(player, actionItem)) return;
            handleConfirmOrCancel(player, actionItem, holder, event.getClick());
            return;
        }

        for (GuiItem item : holder.getDef().items().values()) {
            if (item.slots().contains(slot)) {
                if (isOnCooldown(player, item)) return;
                homeManager.handleAction(item, player, event.getClick(), holder.getPlaceholders());
                return;
            }
        }
    }

    private void handleConfirmOrCancel(Player player, GuiItem item, HomeConfirmHolder holder, ClickType click) {
        List<String> actions = item.getActionsForClick(player, holder.getPlaceholders(), click);
        if (actions == null || actions.isEmpty()) return;

        String homeName = holder.getPlaceholders().get("name");
        UUID uuid = player.getUniqueId();
        String nameLower = homeName.toLowerCase();

        if (item.key().equals("confirm")) {
            for (String action : actions) {
                String lower = action.toLowerCase();
                if (lower.contains("[teleport]")) {
                    handleInternalTeleport(player, uuid, homeName, nameLower);
                } else if (lower.contains("[delete]")) {
                    handleInternalDelete(player, uuid, homeName, nameLower);
                }
            }
        }

        homeManager.handleAction(item, player, click, holder.getPlaceholders());

        if (actions.stream().anyMatch(a -> a.toLowerCase().contains("[close]"))) {
            player.closeInventory();
        } else {
            plugin.scheduler().runEntityTaskDelayed(player, () -> plugin.gui().openGui("homes", player, Map.of(
                    "page", holder.getPrevPage(),
                    "sort", holder.getSortType(),
                    "filter", holder.getFilterType()
            )), 1L);
        }
    }

    private void handleInternalTeleport(Player player, UUID uuid, String homeName, String nameLower) {
        Location loc = plugin.home().homes().getHomes(uuid).get(nameLower);
        if (loc != null) {
            plugin.core().teleports().startCountdown(player, player, () -> {
                plugin.core().teleports().teleport(player, loc);
                MessageUtil.send(player, "homes.teleport.success", Map.of("name", homeName));
            }, reason -> MessageUtil.send(player, "homes.teleport.cancelled", Map.of("name", homeName)));
        }
    }

    private void handleInternalDelete(Player player, UUID uuid, String homeName, String nameLower) {
        if (player.hasPermission("aircore.command.delhome")) {
            plugin.home().homes().deleteHome(uuid, nameLower);
            plugin.database().homes().delete(uuid, nameLower);
            MessageUtil.send(player, "homes.management.deleted", Map.of("name", homeName));
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

    public static final class HomeConfirmHolder implements InventoryHolder {
        private final GuiDefinition def;
        private final String prevPage, sortType, filterType;
        private final Map<String, String> placeholders;
        private Inventory inventory;

        public HomeConfirmHolder(GuiDefinition def, String prevPage, String sortType, String filterType, Map<String, String> placeholders) {
            this.def = def; this.prevPage = prevPage; this.sortType = sortType; this.filterType = filterType; this.placeholders = placeholders;
        }
        @Override public @NotNull Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public GuiDefinition getDef() { return def; }
        public String getPrevPage() { return prevPage; }
        public String getSortType() { return sortType; }
        public String getFilterType() { return filterType; }
        public Map<String, String> getPlaceholders() { return placeholders; }
    }
}