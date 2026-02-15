package com.ftxeven.aircore.core.gui.homes;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.gui.GuiDefinition;
import com.ftxeven.aircore.util.PlaceholderUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
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

    public void open(Player player, String homeName, String actionType, String page) {
        GuiDefinition def = actionType.equalsIgnoreCase("delete") ? deleteDef : teleportDef;
        if (def == null) return;

        org.bukkit.Location loc = plugin.home().homes().getHomes(player.getUniqueId()).get(homeName);
        Map<String, Long> times = plugin.database().homes().loadTimestamps(player.getUniqueId());
        long timestamp = times.getOrDefault(homeName, 0L);

        Map<String, String> ph = new HashMap<>();
        ph.put("name", homeName);
        ph.put("page", page);

        if (loc != null) {
            ph.put("world", loc.getWorld().getName());
            ph.put("x", String.valueOf(loc.getBlockX()));
            ph.put("y", String.valueOf(loc.getBlockY()));
            ph.put("z", String.valueOf(loc.getBlockZ()));

            LocalDateTime dt = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
            ph.put("time", dt.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            ph.put("date", dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        }

        String title = PlaceholderUtil.apply(player, def.title(), ph);
        HomeConfirmHolder holder = new HomeConfirmHolder(def, page, ph);

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
    }

    private void renderButton(Inventory inv, GuiDefinition def, String key, Player p, Map<String, String> ph) {
        GuiDefinition.GuiItem item = def.items().get(key);
        if (item != null) {
            ItemStack stack = item.buildStack(p, ph);
            item.slots().forEach(slot -> inv.setItem(slot, stack));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof HomeConfirmHolder holder)) return;
        event.setCancelled(true);

        int slot = event.getSlot();
        Player player = (Player) event.getWhoClicked();
        Map<String, String> ph = holder.getPlaceholders();

        GuiDefinition.GuiItem confirmBtn = holder.getDef().items().get("confirm");
        if (confirmBtn != null && confirmBtn.slots().contains(slot)) {
            homeManager.handleAction(confirmBtn, player, event.getClick(), ph);

            List<String> actions = confirmBtn.getActionsForClick(event.getClick());
            boolean hasClose = actions != null && actions.stream()
                    .anyMatch(a -> a.toLowerCase().contains("[close]"));

            if (!hasClose) {
                plugin.scheduler().runEntityTask(player, () -> plugin.gui().openGui("homes", player, Map.of("page", holder.getPrevPage())));
            }
            return;
        }

        GuiDefinition.GuiItem cancelBtn = holder.getDef().items().get("cancel");
        if (cancelBtn != null && cancelBtn.slots().contains(slot)) {
            homeManager.handleAction(cancelBtn, player, event.getClick(), ph);
            plugin.gui().openGui("homes", player, Map.of("page", holder.getPrevPage()));
            return;
        }

        for (GuiDefinition.GuiItem item : holder.getDef().items().values()) {
            if (item.key().equals("confirm") || item.key().equals("cancel")) continue;
            if (item.slots().contains(slot)) {
                homeManager.handleAction(item, player, event.getClick(), ph);
                return;
            }
        }
    }

    public void cleanup() { HandlerList.unregisterAll(this); }

    public static final class HomeConfirmHolder implements InventoryHolder {
        private final GuiDefinition def;
        private final String prevPage;
        private final Map<String, String> placeholders;
        private Inventory inventory;

        public HomeConfirmHolder(GuiDefinition def, String prevPage, Map<String, String> placeholders) {
            this.def = def;
            this.prevPage = prevPage;
            this.placeholders = placeholders;
        }

        @Override public @NotNull Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }

        public GuiDefinition getDef() { return def; }
        public String getPrevPage() { return prevPage; }
        public Map<String, String> getPlaceholders() { return placeholders; }
    }
}