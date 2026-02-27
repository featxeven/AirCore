package com.ftxeven.aircore.core.modules.gui.homes;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.modules.gui.GuiDefinition;
import com.ftxeven.aircore.core.modules.gui.GuiDefinition.GuiItem;
import com.ftxeven.aircore.core.modules.gui.ItemAction;
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
    private int[] homeSlots;

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

        File mainFile = new File(plugin.getDataFolder(), "guis/homes/homes-target.yml");
        YamlConfiguration mainCfg = YamlConfiguration.loadConfiguration(mainFile);
        this.homeSlots = GuiDefinition.parseSlots(mainCfg.getStringList("home-slots"))
                .stream().mapToInt(Integer::intValue).toArray();
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

        Location loc = plugin.home().homes().getHomes(targetUUID).get(homeName);
        Map<String, Long> times = plugin.database().homes().loadTimestamps(targetUUID);
        long timestamp = times.getOrDefault(homeName, 0L);

        int totalHomes = plugin.database().homes().getHomeAmount(targetUUID);
        int maxPages = Math.max(1, (int) Math.ceil((double) totalHomes / homeSlots.length));

        Map<String, String> ph = new HashMap<>();
        ph.put("target", targetName);
        ph.put("name", homeName);
        ph.put("page", page);
        ph.put("pages", String.valueOf(maxPages));

        if (loc != null) {
            ph.put("world", loc.getWorld() != null ? loc.getWorld().getName() : "-");
            ph.put("x", String.valueOf(loc.getBlockX()));
            ph.put("y", String.valueOf(loc.getBlockY()));
            ph.put("z", String.valueOf(loc.getBlockZ()));

            LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());
            ph.put("time", dt.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            ph.put("date", dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        }

        HomeConfirmTargetHolder holder = new HomeConfirmTargetHolder(targetName, def, page, sortType, filterType, ph);

        HomeTargetManager.CONSTRUCTION_CONTEXT.set(new HomeTargetManager.HomeTargetHolder(
                targetName, Integer.parseInt(page), maxPages, new ArrayList<>(), sortType, filterType, times
        ));

        try {
            Inventory inv = Bukkit.createInventory(holder, def.rows() * 9,
                    MM.deserialize("<!italic>" + PlaceholderUtil.apply(viewer, def.title(), ph)));
            holder.setInventory(inv);

            def.items().values().stream()
                    .filter(item -> !item.key().equals("confirm") && !item.key().equals("cancel"))
                    .forEach(item -> {
                        ItemStack stack = item.buildStack(viewer, ph);
                        item.slots().forEach(slot -> {
                            if (slot < inv.getSize()) inv.setItem(slot, stack);
                        });
                    });

            renderButton(inv, def, "confirm", viewer, ph);
            renderButton(inv, def, "cancel", viewer, ph);

            viewer.openInventory(inv);
        } finally {
            HomeTargetManager.CONSTRUCTION_CONTEXT.remove();
        }
    }

    private void renderButton(Inventory inv, GuiDefinition def, String key, Player p, Map<String, String> ph) {
        GuiItem item = def.items().get(key);
        if (item != null) {
            ItemStack stack = item.buildStack(p, ph);
            item.slots().forEach(slot -> {
                if (slot < inv.getSize()) inv.setItem(slot, stack);
            });
        }
    }

    @EventHandler
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof HomeConfirmTargetHolder holder)) return;
        event.setCancelled(true);

        int slot = event.getSlot();
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) return;

        Player viewer = (Player) event.getWhoClicked();

        GuiItem clickedItem = null;
        for (GuiItem item : holder.getDef().items().values()) {
            if (item.slots().contains(slot)) {
                clickedItem = item;
                if (item.key().equals("confirm") || item.key().equals("cancel")) break;
            }
        }

        if (clickedItem == null) return;

        if (plugin.gui().cooldowns().isOnCooldown(viewer, clickedItem)) {
            plugin.gui().cooldowns().sendCooldownMessage(viewer, clickedItem);
            return;
        }
        plugin.gui().cooldowns().applyCooldown(viewer, clickedItem);

        String key = clickedItem.key();
        if (key.equals("confirm") || key.equals("cancel")) {
            handleConfirmOrCancel(viewer, clickedItem, holder, event.getClick());
        } else {
            executeAction(clickedItem, viewer, event.getClick(), holder.getPlaceholders());
        }
    }

    private void handleConfirmOrCancel(Player viewer, GuiItem item, HomeConfirmTargetHolder holder, ClickType click) {
        executeAction(item, viewer, click, holder.getPlaceholders());

        List<String> actions = item.getActionsForClick(click);
        boolean hasClose = actions != null && actions.stream().anyMatch(a -> a.toLowerCase().contains("[close]"));

        if (!hasClose) {
            plugin.scheduler().runEntityTaskDelayed(viewer, () -> {
                if (viewer.isOnline()) {
                    plugin.gui().openGui("homes-target", viewer, Map.of(
                            "player", holder.getTargetName(),
                            "page", holder.getPrevPage(),
                            "sort", holder.getSortType(),
                            "filter", holder.getFilterType()
                    ));
                }
            }, 2L);
        }
    }

    private void executeAction(GuiItem item, Player viewer, ClickType click, Map<String, String> ph) {
        List<String> actions = item.getActionsForClick(click);
        if (actions != null && !actions.isEmpty()) {
            Map<String, String> fullPh = new HashMap<>(ph);
            fullPh.put("player", viewer.getName());
            itemAction.executeAll(actions, viewer, fullPh);
        }
    }

    public void cleanup() { HandlerList.unregisterAll(this); }

    public static final class HomeConfirmTargetHolder implements InventoryHolder {
        private final String targetName;
        private final GuiDefinition def;
        private final String prevPage;
        private final String sortType;
        private final String filterType;
        private final Map<String, String> placeholders;
        private Inventory inventory;

        public HomeConfirmTargetHolder(String targetName, GuiDefinition def, String prevPage, String sortType, String filterType, Map<String, String> placeholders) {
            this.targetName = targetName;
            this.def = def;
            this.prevPage = prevPage;
            this.sortType = sortType;
            this.filterType = filterType;
            this.placeholders = placeholders;
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