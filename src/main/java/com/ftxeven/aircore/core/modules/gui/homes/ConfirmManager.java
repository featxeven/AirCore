package com.ftxeven.aircore.core.modules.gui.homes;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.modules.gui.GuiDefinition;
import com.ftxeven.aircore.util.PlaceholderUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
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
    private int[] homeSlots;

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

        File mainFile = new File(plugin.getDataFolder(), "guis/homes/homes.yml");
        YamlConfiguration mainCfg = YamlConfiguration.loadConfiguration(mainFile);
        List<Integer> parsed = GuiDefinition.parseSlots(mainCfg.getStringList("home-slots"));
        this.homeSlots = parsed.stream().mapToInt(Integer::intValue).toArray();
    }

    private GuiDefinition loadSubSection(ConfigurationSection sec, YamlConfiguration root) {
        if (sec == null) return null;
        Map<String, GuiDefinition.GuiItem> items = new HashMap<>();
        loadItems(sec.getConfigurationSection("buttons"), items);
        loadItems(sec.getConfigurationSection("items"), items);
        return new GuiDefinition(sec.getString("title", "Confirm"), sec.getInt("rows", 3), items, root);
    }

    private void loadItems(ConfigurationSection sec, Map<String, GuiDefinition.GuiItem> items) {
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection itemSec = sec.getConfigurationSection(key);
            if (itemSec != null) items.put(key, GuiDefinition.GuiItem.fromSection(key, itemSec));
        }
    }

    public void open(Player player, String homeName, String actionType, String page, String sortType, String filterType) {
        GuiDefinition def = actionType.equalsIgnoreCase("delete") ? deleteDef : teleportDef;
        if (def == null) return;

        Location loc = plugin.home().homes().getHomes(player.getUniqueId()).get(homeName);
        Map<String, Long> times = plugin.database().homes().loadTimestamps(player.getUniqueId());
        long timestamp = times.getOrDefault(homeName, 0L);

        int totalHomes = plugin.database().homes().getHomeAmount(player.getUniqueId());
        int pageSize = (homeSlots != null && homeSlots.length > 0) ? homeSlots.length : 28;
        int maxPages = Math.max(1, (int) Math.ceil((double) totalHomes / pageSize));

        Map<String, String> ph = new HashMap<>();
        ph.put("name", homeName);
        ph.put("page", page);
        ph.put("pages", String.valueOf(maxPages));

        if (loc != null) {
            ph.put("world", loc.getWorld().getName());
            ph.put("x", String.valueOf(loc.getBlockX()));
            ph.put("y", String.valueOf(loc.getBlockY()));
            ph.put("z", String.valueOf(loc.getBlockZ()));

            LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
            ph.put("time", dt.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            ph.put("date", dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        }

        HomeConfirmHolder holder = new HomeConfirmHolder(def, page, sortType, filterType, ph);

        HomeManager.CONSTRUCTION_CONTEXT.set(new HomeManager.HomeHolder(
                Integer.parseInt(page), 1, new ArrayList<>(), sortType, filterType, new HashMap<>()
        ));

        try {
            String title = PlaceholderUtil.apply(player, def.title(), ph);
            Inventory inv = Bukkit.createInventory(holder, def.rows() * 9, mm.deserialize("<!italic>" + title));
            holder.setInventory(inv);

            def.items().values().stream()
                    .filter(item -> !item.key().equals("confirm") && !item.key().equals("cancel"))
                    .forEach(item -> {
                        ItemStack stack = item.buildStack(player, ph);
                        item.slots().forEach(slot -> inv.setItem(slot, stack));
                    });

            renderButton(inv, def, "confirm", player, ph);
            renderButton(inv, def, "cancel", player, ph);
            player.openInventory(inv);
        } finally {
            HomeManager.CONSTRUCTION_CONTEXT.remove();
        }
    }

    private void renderButton(Inventory inv, GuiDefinition def, String key, Player p, Map<String, String> ph) {
        GuiDefinition.GuiItem item = def.items().get(key);
        if (item != null) {
            ItemStack stack = item.buildStack(p, ph);
            item.slots().forEach(slot -> inv.setItem(slot, stack));
        }
    }

    @EventHandler
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof HomeConfirmHolder holder)) return;
        event.setCancelled(true);

        int slot = event.getSlot();
        Player player = (Player) event.getWhoClicked();
        Map<String, String> ph = holder.getPlaceholders();

        GuiDefinition.GuiItem clickedItem = null;
        for (GuiDefinition.GuiItem item : holder.getDef().items().values()) {
            if (item.slots().contains(slot)) {
                clickedItem = item;
                break;
            }
        }

        if (clickedItem == null) return;

        if (plugin.gui().cooldowns().isOnCooldown(player, clickedItem)) {
            plugin.gui().cooldowns().sendCooldownMessage(player, clickedItem);
            return;
        }
        plugin.gui().cooldowns().applyCooldown(player, clickedItem);

        String key = clickedItem.key();
        if (key.equals("confirm") || key.equals("cancel")) {
            handleConfirmOrCancel(player, clickedItem, holder, event);
        } else {
            homeManager.handleAction(clickedItem, player, event.getClick(), ph);
        }
    }

    private void handleConfirmOrCancel(Player player, GuiDefinition.GuiItem item, HomeConfirmHolder holder, InventoryClickEvent event) {
        homeManager.handleAction(item, player, event.getClick(), holder.getPlaceholders());

        List<String> actions = item.getActionsForClick(event.getClick());
        boolean hasClose = actions != null && actions.stream()
                .anyMatch(a -> a.toLowerCase().contains("[close]"));

        if (!hasClose) {
            plugin.scheduler().runEntityTaskDelayed(player, () -> {
                if (player.isOnline()) {
                    plugin.gui().openGui("homes", player, Map.of(
                            "page", holder.getPrevPage(),
                            "sort", holder.getSortType(),
                            "filter", holder.getFilterType()
                    ));
                }
            }, 2L);
        }
    }

    public void cleanup() { HandlerList.unregisterAll(this); }

    public static final class HomeConfirmHolder implements InventoryHolder {
        private final GuiDefinition def;
        private final String prevPage;
        private final String sortType;
        private final String filterType;
        private final Map<String, String> placeholders;
        private Inventory inventory;

        public HomeConfirmHolder(GuiDefinition def, String prevPage, String sortType, String filterType, Map<String, String> placeholders) {
            this.def = def;
            this.prevPage = prevPage;
            this.sortType = sortType;
            this.filterType = filterType;
            this.placeholders = placeholders;
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