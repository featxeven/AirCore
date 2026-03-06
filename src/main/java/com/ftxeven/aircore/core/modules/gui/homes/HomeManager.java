package com.ftxeven.aircore.core.modules.gui.homes;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.modules.gui.GuiDefinition;
import com.ftxeven.aircore.core.modules.gui.GuiDefinition.GuiItem;
import com.ftxeven.aircore.core.modules.gui.GuiManager;
import com.ftxeven.aircore.core.modules.gui.ItemAction;
import com.ftxeven.aircore.util.PlaceholderUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public final class HomeManager implements GuiManager.CustomGuiManager {
    private final AirCore plugin;
    private final ItemAction itemAction;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private GuiDefinition definition;
    private int[] homeSlots;
    private boolean enabled;
    private final ConfirmManager confirmManager;

    private static final String DEFAULT_SORT = "latest";
    private static final String DEFAULT_FILTER = "all";

    public static final ThreadLocal<HomeHolder> CONSTRUCTION_CONTEXT = new ThreadLocal<>();

    public HomeManager(AirCore plugin, ItemAction itemAction) {
        this.plugin = plugin;
        this.itemAction = itemAction;
        this.confirmManager = new ConfirmManager(plugin, this);
        loadDefinition();
    }

    public boolean isEnabled() { return enabled; }

    public void loadDefinition() {
        File file = new File(plugin.getDataFolder(), "guis/homes/homes.yml");
        if (!file.exists()) plugin.saveResource("guis/homes/homes.yml", false);

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        this.enabled = cfg.getBoolean("enabled", true);

        this.homeSlots = GuiDefinition.parseSlots(cfg.getStringList("home-slots"))
                .stream().mapToInt(Integer::intValue).toArray();

        Map<String, GuiItem> items = new LinkedHashMap<>();
        loadSection(cfg.getConfigurationSection("items"), items);
        loadSection(cfg.getConfigurationSection("buttons"), items);

        this.definition = new GuiDefinition(cfg.getString("title", "Homes"), cfg.getInt("rows", 5), items, cfg);
    }

    private void loadSection(ConfigurationSection sec, Map<String, GuiItem> items) {
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection itemSec = sec.getConfigurationSection(key);
            if (itemSec != null) {
                items.put(key, GuiItem.fromSection(key, itemSec));
            }
        }
    }

    @Override
    public Inventory build(Player viewer, Map<String, String> placeholders) {
        int page = Integer.parseInt(placeholders.getOrDefault("page", "1"));

        InventoryHolder topHolder = viewer.getOpenInventory().getTopInventory().getHolder();

        String currentSort = placeholders.getOrDefault("sort",
                (topHolder instanceof HomeHolder hh) ? hh.getCurrentSort() : getDefaultSort());

        String currentFilter = placeholders.getOrDefault("filter",
                (topHolder instanceof HomeHolder hh) ? hh.getCurrentFilter() : getDefaultFilter());

        List<Map.Entry<String, Location>> homesList = new ArrayList<>(plugin.home().homes().getHomes(viewer.getUniqueId()).entrySet());

        applyFilter(currentFilter, homesList);
        sortHomes(currentSort, homesList);

        int pageSize = homeSlots.length;
        int maxPages = Math.max(1, (int) Math.ceil((double) homesList.size() / pageSize));
        if (page > maxPages) page = maxPages;

        Map<String, Long> timestamps = plugin.database().homes().loadTimestamps(viewer.getUniqueId());
        HomeHolder holder = new HomeHolder(page, maxPages, homesList, currentSort, currentFilter, timestamps);

        CONSTRUCTION_CONTEXT.set(holder);
        try {
            String title = PlaceholderUtil.apply(viewer, definition.title()
                    .replace("%page%", String.valueOf(page))
                    .replace("%pages%", String.valueOf(maxPages)));

            Inventory inv = Bukkit.createInventory(holder, definition.rows() * 9, MM.deserialize("<!italic>" + title));
            holder.setInventory(inv);

            int limit = plugin.home().homes().getLimit(viewer.getUniqueId());
            Map<String, String> ph = new HashMap<>(placeholders);
            ph.put("page", String.valueOf(page));
            ph.put("pages", String.valueOf(maxPages));
            ph.put("sort", buildSortList(currentSort));
            ph.put("filter", buildFilterList(currentFilter));
            ph.put("limit", String.valueOf(limit));

            HomeSlotMapper.fillHomeInventory(inv, definition, viewer, page, maxPages, homesList, homeSlots, ph, limit, true);
            return inv;
        } finally {
            CONSTRUCTION_CONTEXT.remove();
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player viewer) {
        event.setCancelled(true);
        if (!(event.getInventory().getHolder() instanceof HomeHolder holder)) return;

        int slot = event.getSlot();
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) return;

        for (int i = 0; i < homeSlots.length; i++) {
            if (homeSlots[i] == slot) {
                int actualIndex = ((holder.page() - 1) * homeSlots.length) + i;
                if (actualIndex < holder.homes().size()) {
                    handleHomeClick(holder.homes().get(actualIndex).getKey(), viewer, event.getClick(), holder);
                    return;
                }
            }
        }

        GuiItem buttonItem = findItemBySection(slot, current, "buttons");
        if (buttonItem != null) {
            switch (buttonItem.key()) {
                case "next-page" -> { handleNavigation(holder, viewer, 1); return; }
                case "previous-page" -> { handleNavigation(holder, viewer, -1); return; }
                case "sort-by" -> { handleSort(event, holder, viewer); return; }
                case "filter-by" -> { handleFilter(event, holder, viewer); return; }
            }
        }

        GuiItem genericItem = findItemBySection(slot, current, "items");
        if (genericItem != null) {
            handleGenericItem(genericItem, viewer, event.getClick());
        }
    }

    private GuiItem findItemBySection(int slot, ItemStack stack, String section) {
        ConfigurationSection sec = definition.config().getConfigurationSection(section);
        if (sec == null) return null;

        for (String key : sec.getKeys(false)) {
            GuiItem item = definition.items().get(key);
            if (item != null && item.slots().contains(slot)) {
                if (item.material() != stack.getType()) continue;

                if (item.rawName() == null) return item;

                if (stack.hasItemMeta()) {
                    net.kyori.adventure.text.Component clickedName = stack.getItemMeta().displayName();
                    if (clickedName == null) continue;

                    String clickedStr = MM.serialize(clickedName);
                    String expectedStr = MM.serialize(MM.deserialize("<!italic>" + item.rawName()));

                    if (clickedStr.equals(expectedStr) || clickedStr.contains(key)) {
                        return item;
                    }
                }
            }
        }
        return null;
    }

    private void handleSort(InventoryClickEvent event, HomeHolder holder, Player viewer) {
        GuiItem sortItem = definition.items().get("sort-by");
        if (sortItem == null || isOnCooldown(viewer, sortItem)) return;

        ConfigurationSection typesSec = definition.config().getConfigurationSection("buttons.sort-by.types");
        if (typesSec != null) {
            List<String> keys = new ArrayList<>(typesSec.getKeys(false));
            int currentIdx = keys.indexOf(holder.getCurrentSort());
            int nextIdx = event.getClick().isRightClick() ? (currentIdx - 1 + keys.size()) % keys.size() : (currentIdx + 1) % keys.size();
            holder.setCurrentSort(keys.get(nextIdx));

            handleAction(sortItem, viewer, event.getClick(), Collections.emptyMap());
            refresh(event.getInventory(), viewer, Collections.emptyMap());
        }
    }

    private void handleFilter(InventoryClickEvent event, HomeHolder holder, Player viewer) {
        GuiItem filterItem = definition.items().get("filter-by");
        if (filterItem == null || isOnCooldown(viewer, filterItem)) return;

        ConfigurationSection typesSec = definition.config().getConfigurationSection("buttons.filter-by.types");
        if (typesSec == null) return;

        List<String> keys = new ArrayList<>(typesSec.getKeys(false));
        int currentIdx = keys.indexOf(holder.getCurrentFilter());
        int nextIdx = event.getClick().isRightClick() ? (currentIdx - 1 + keys.size()) % keys.size() : (currentIdx + 1) % keys.size();
        String newFilter = keys.get(nextIdx);

        holder.setCurrentFilter(newFilter);

        List<Map.Entry<String, Location>> filteredHomes = new ArrayList<>(plugin.home().homes().getHomes(viewer.getUniqueId()).entrySet());
        applyFilter(newFilter, filteredHomes);

        int newMax = Math.max(1, (int) Math.ceil((double) filteredHomes.size() / homeSlots.length));
        int newPage = Math.min(holder.page(), newMax);

        handleAction(filterItem, viewer, event.getClick(), Collections.emptyMap());

        if (newMax != holder.maxPages()) {
            plugin.gui().openGui("homes", viewer, Map.of("page", String.valueOf(newPage), "filter", newFilter));
        } else {
            holder.updatePagination(newPage, newMax);
            refresh(event.getInventory(), viewer, Collections.emptyMap());
        }
    }

    private void handleNavigation(HomeHolder holder, Player viewer, int direction) {
        String key = direction > 0 ? "next-page" : "previous-page";
        GuiItem navItem = definition.items().get(key);
        if (navItem == null || isOnCooldown(viewer, navItem)) return;

        int targetPage = holder.page() + direction;

        handleAction(navItem, viewer, ClickType.LEFT, Collections.emptyMap());

        if (targetPage >= 1 && targetPage <= holder.maxPages()) {
            plugin.gui().openGui("homes", viewer, Map.of("page", String.valueOf(targetPage)));
        }
    }

    private void handleHomeClick(String name, Player viewer, ClickType click, HomeHolder holder) {
        ConfigurationSection homeSec = definition.config().getConfigurationSection("home-item");
        if (homeSec == null) return;

        List<String> rawActions = click.isShiftClick() ?
                homeSec.getStringList("shift-actions") : homeSec.getStringList("actions");

        String confirmType = null;
        if (rawActions.stream().anyMatch(a -> a.equalsIgnoreCase("[teleport]"))) confirmType = "teleport";
        if (rawActions.stream().anyMatch(a -> a.equalsIgnoreCase("[delete]"))) confirmType = "delete";

        boolean shouldConfirm = confirmType != null &&
                definition.config().getBoolean("home-item.apply-confirm." + confirmType + ".enabled", false);

        if (shouldConfirm) {
            if (confirmType.equals("delete") && !viewer.hasPermission("aircore.command.delhome")) return;

            Map<String, String> ph = Map.of("name", name, "player", viewer.getName());
            for (String action : rawActions) {
                if (!action.equalsIgnoreCase("[teleport]") && !action.equalsIgnoreCase("[delete]")) {
                    itemAction.execute(action, viewer, ph);
                }
            }

            confirmManager.open(viewer, name, confirmType, String.valueOf(holder.page()), holder.getCurrentSort(), holder.getCurrentFilter());
        } else {
            executeHomeActions(rawActions, name, viewer);
        }
    }

    private void executeHomeActions(List<String> actions, String homeName, Player viewer) {
        Map<String, String> ph = Map.of("name", homeName, "player", viewer.getName());
        for (String action : actions) {
            if (action.equalsIgnoreCase("[teleport]")) {
                viewer.performCommand("home " + homeName);
            } else if (action.equalsIgnoreCase("[delete]")) {
                if (viewer.hasPermission("aircore.command.delhome")) {
                    viewer.performCommand("delhome " + homeName);
                    Bukkit.getScheduler().runTaskLater(plugin, () ->
                            plugin.gui().openGui("homes", viewer, Collections.emptyMap()), 1L);
                }
            } else {
                itemAction.execute(action, viewer, ph);
            }
        }
    }

    private boolean isOnCooldown(Player viewer, GuiItem item) {
        if (plugin.gui().cooldowns().isOnCooldown(viewer, item)) {
            plugin.gui().cooldowns().sendCooldownMessage(viewer, item);
            return true;
        }
        plugin.gui().cooldowns().applyCooldown(viewer, item);
        return false;
    }

    public void handleAction(GuiItem item, Player viewer, ClickType click, Map<String, String> extraPh) {
        List<String> actions = item.getActionsForClick(click);
        if (actions == null || actions.isEmpty()) return;

        Map<String, String> ph = new HashMap<>(extraPh);
        ph.put("player", viewer.getName());
        itemAction.executeAll(actions, viewer, ph);
    }

    private void handleGenericItem(GuiItem item, Player viewer, ClickType click) {
        if (isOnCooldown(viewer, item)) return;
        handleAction(item, viewer, click, Collections.emptyMap());
    }

    private String getDefaultSort() {
        ConfigurationSection types = definition.config().getConfigurationSection("buttons.sort-by.types");
        return (types != null) ? types.getKeys(false).stream().findFirst().orElse(DEFAULT_SORT) : DEFAULT_SORT;
    }

    private String getDefaultFilter() {
        ConfigurationSection types = definition.config().getConfigurationSection("buttons.filter-by.types");
        return (types != null) ? types.getKeys(false).stream().findFirst().orElse(DEFAULT_FILTER) : DEFAULT_FILTER;
    }
    private void applyFilter(String type, List<Map.Entry<String, Location>> list) {
        if (type.equalsIgnoreCase("all")) return;
        list.removeIf(entry -> {
            Location loc = entry.getValue();
            return loc.getWorld() == null || !loc.getWorld().getName().equalsIgnoreCase(type);
        });
    }

    private void sortHomes(String type, List<Map.Entry<String, Location>> list) {
        switch (type.toLowerCase()) {
            case "alphabetical" -> list.sort((e1, e2) -> e1.getKey().compareToIgnoreCase(e2.getKey()));
            case "latest" -> Collections.reverse(list);
            case "oldest" -> { /* already handled by db */ }
        }
    }

    private String buildSortList(String current) {
        return buildSelectionList("buttons.sort-by", current);
    }

    private String buildFilterList(String current) {
        return buildSelectionList("buttons.filter-by", current);
    }

    private String buildSelectionList(String path, String current) {
        ConfigurationSection sec = definition.config().getConfigurationSection(path);
        if (sec == null) return "";
        ConfigurationSection types = sec.getConfigurationSection("types");
        if (types == null) return "";

        String selectedFmt = sec.getString("format.selected", "⏵ %name%");
        String unselectedFmt = sec.getString("format.unselected", " %name%");

        if (!sec.getBoolean("show-list", true)) {
            return selectedFmt.replace("%name%", types.getString(current, current));
        }

        StringJoiner joiner = new StringJoiner("\n");
        for (String key : types.getKeys(false)) {
            String fmt = key.equals(current) ? selectedFmt : unselectedFmt;
            joiner.add(fmt.replace("%name%", types.getString(key, key)));
        }
        return joiner.toString();
    }

    @Override
    public void refresh(Inventory inv, Player viewer, Map<String, String> placeholders) {
        if (!(inv.getHolder() instanceof HomeHolder holder)) return;

        List<Map.Entry<String, Location>> homesList = new ArrayList<>(plugin.home().homes().getHomes(viewer.getUniqueId()).entrySet());
        applyFilter(holder.getCurrentFilter(), homesList);
        sortHomes(holder.getCurrentSort(), homesList);

        int maxPages = Math.max(1, (int) Math.ceil((double) homesList.size() / homeSlots.length));
        int page = Math.min(holder.page(), maxPages);

        holder.updatePagination(page, maxPages);
        holder.homes().clear();
        holder.homes().addAll(homesList);

        for (int slot : homeSlots) {
            inv.setItem(slot, null);
        }

        CONSTRUCTION_CONTEXT.set(holder);
        try {
            Map<String, String> ph = new HashMap<>(placeholders);
            ph.put("page", String.valueOf(page));
            ph.put("pages", String.valueOf(maxPages));
            ph.put("sort", buildSortList(holder.getCurrentSort()));
            ph.put("filter", buildFilterList(holder.getCurrentFilter()));
            ph.put("limit", String.valueOf(plugin.home().homes().getLimit(viewer.getUniqueId())));

            HomeSlotMapper.fillHomeInventory(inv, definition, viewer, page, maxPages, homesList, homeSlots, ph, Integer.parseInt(ph.get("limit")), false);
        } finally {
            CONSTRUCTION_CONTEXT.remove();
        }
    }

    @Override public void cleanup() { confirmManager.cleanup(); }
    @Override public boolean owns(Inventory inv) { return inv.getHolder() instanceof HomeHolder; }
    public GuiDefinition definition() { return this.definition; }

    public static class HomeHolder implements InventoryHolder {
        private int page, maxPages;
        private final List<Map.Entry<String, Location>> homes;
        private final Map<String, Long> timestamps;
        private String currentSort, currentFilter;
        private Inventory inventory;

        public HomeHolder(int p, int m, List<Map.Entry<String, Location>> h, String s, String f, Map<String, Long> ts) {
            this.page = p; this.maxPages = m; this.homes = new ArrayList<>(h);
            this.currentSort = s; this.currentFilter = f; this.timestamps = ts;
        }

        public void updatePagination(int p, int m) { this.page = p; this.maxPages = m; }
        @Override public @NotNull Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inv) { this.inventory = inv; }
        public int page() { return page; }
        public int maxPages() { return maxPages; }
        public List<Map.Entry<String, Location>> homes() { return homes; }
        public String getCurrentSort() { return currentSort; }
        public void setCurrentSort(String s) { this.currentSort = s; }
        public String getCurrentFilter() { return currentFilter; }
        public void setCurrentFilter(String f) { this.currentFilter = f; }
        public Map<String, Long> getTimestamps() { return timestamps; }
    }
}