package com.ftxeven.aircore.core.gui.homes;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.gui.GuiDefinition;
import com.ftxeven.aircore.core.gui.GuiDefinition.GuiItem;
import com.ftxeven.aircore.core.gui.GuiManager;
import com.ftxeven.aircore.core.gui.ItemAction;
import com.ftxeven.aircore.util.MessageUtil;
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
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public final class HomeTargetManager implements GuiManager.CustomGuiManager {
    private final AirCore plugin;
    private final ItemAction itemAction;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private GuiDefinition definition;
    private int[] homeSlots;
    private final ConfirmTargetManager confirmManager;
    private final Set<String> buttonKeys = new HashSet<>();

    private static final String DEFAULT_SORT = "latest";
    private static final String DEFAULT_FILTER = "all";

    public static final ThreadLocal<HomeTargetHolder> CONSTRUCTION_CONTEXT = new ThreadLocal<>();

    public HomeTargetManager(AirCore plugin, ItemAction itemAction) {
        this.plugin = plugin;
        this.itemAction = itemAction;
        this.confirmManager = new ConfirmTargetManager(plugin, itemAction);
        loadDefinition();
    }

    public void loadDefinition() {
        File file = new File(plugin.getDataFolder(), "guis/homes/homes-target.yml");
        if (!file.exists()) plugin.saveResource("guis/homes/homes-target.yml", false);

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        this.homeSlots = GuiDefinition.parseSlots(cfg.getStringList("home-slots"))
                .stream().mapToInt(Integer::intValue).toArray();

        this.buttonKeys.clear();
        ConfigurationSection btnSec = cfg.getConfigurationSection("buttons");
        if (btnSec != null) {
            this.buttonKeys.addAll(btnSec.getKeys(false));
        }

        Map<String, GuiItem> items = new LinkedHashMap<>();
        loadSection(cfg.getConfigurationSection("items"), items);
        loadSection(btnSec, items);

        this.definition = new GuiDefinition(cfg.getString("title", "Target's Homes"), cfg.getInt("rows", 5), items, cfg);
    }

    private void loadSection(ConfigurationSection sec, Map<String, GuiItem> items) {
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection itemSec = sec.getConfigurationSection(key);
            if (itemSec != null) items.put(key, GuiItem.fromSection(key, itemSec));
        }
    }

    @Override
    public Inventory build(Player viewer, Map<String, String> placeholders) {
        String targetName = placeholders.get("player");
        int page = Integer.parseInt(placeholders.getOrDefault("page", "1"));

        UUID targetUUID = plugin.database().records().uuidFromName(targetName);
        if (targetUUID == null) return null;

        InventoryHolder topHolder = viewer.getOpenInventory().getTopInventory().getHolder();

        String currentSort = placeholders.getOrDefault("sort",
                (topHolder instanceof HomeTargetHolder hh) ? hh.getCurrentSort() : getDefaultSort());

        String currentFilter = placeholders.getOrDefault("filter",
                (topHolder instanceof HomeTargetHolder hh) ? hh.getCurrentFilter() : getDefaultFilter());

        List<Map.Entry<String, Location>> homesList = new ArrayList<>(plugin.database().homes().load(targetUUID).entrySet());

        applyFilter(currentFilter, homesList);
        sortHomes(currentSort, homesList);

        int pageSize = homeSlots.length;
        int maxPages = Math.max(1, (int) Math.ceil((double) homesList.size() / pageSize));
        if (page > maxPages) page = maxPages;

        Map<String, Long> timestamps = plugin.database().homes().loadTimestamps(targetUUID);
        HomeTargetHolder holder = new HomeTargetHolder(targetName, page, maxPages, homesList, currentSort, currentFilter, timestamps);

        CONSTRUCTION_CONTEXT.set(holder);
        try {
            String title = PlaceholderUtil.apply(viewer, definition.title()
                    .replace("%page%", String.valueOf(page))
                    .replace("%pages%", String.valueOf(maxPages))
                    .replace("%target%", targetName));

            Inventory inv = Bukkit.createInventory(holder, definition.rows() * 9, MM.deserialize("<!italic>" + title));
            holder.setInventory(inv);

            int targetLimit = plugin.home().homes().getLimit(targetUUID);
            Map<String, String> ph = new HashMap<>(placeholders);
            ph.put("page", String.valueOf(page));
            ph.put("pages", String.valueOf(maxPages));
            ph.put("target", targetName);
            ph.put("sort", buildSortList(currentSort));
            ph.put("filter", buildFilterList(currentFilter));
            ph.put("limit", String.valueOf(targetLimit));

            HomeSlotMapper.fillHomeInventory(plugin, inv, definition, viewer, page, maxPages, homesList, homeSlots, ph, targetLimit, true);
            return inv;
        } finally {
            CONSTRUCTION_CONTEXT.remove();
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player viewer) {
        event.setCancelled(true);
        if (!(event.getInventory().getHolder() instanceof HomeTargetHolder holder)) return;

        int slot = event.getSlot();

        for (int i = 0; i < homeSlots.length; i++) {
            if (homeSlots[i] == slot) {
                int actualIndex = ((holder.page() - 1) * homeSlots.length) + i;

                if (actualIndex < holder.homes().size()) {
                    handleHomeClick(holder.homes().get(actualIndex).getKey(), viewer, event.getClick(), holder);
                    return;
                }

                UUID targetUUID = plugin.database().records().uuidFromName(holder.targetName());
                int targetLimit = targetUUID != null ? plugin.home().homes().getLimit(targetUUID) : 0;
                if (actualIndex < targetLimit) {
                    return;
                }
                break;
            }
        }

        GuiItem clickedButton = null;
        for (String key : buttonKeys) {
            GuiItem item = definition.items().get(key);
            if (item != null && item.slots().contains(slot)) {
                if (isButtonActive(key, holder)) {
                    clickedButton = item;
                    break;
                }
            }
        }

        if (clickedButton != null) {
            switch (clickedButton.key()) {
                case "next-page" -> handleNavigation(holder, viewer, 1);
                case "previous-page" -> handleNavigation(holder, viewer, -1);
                case "sort-by" -> handleSort(event, holder, viewer);
                case "filter-by" -> handleFilter(event, holder, viewer);
                default -> handleGenericItem(clickedButton, viewer, event.getClick(), holder.targetName());
            }
            return;
        }

        GuiItem clickedCustomItem = null;
        for (GuiItem item : definition.items().values()) {
            if (!buttonKeys.contains(item.key()) && item.slots().contains(slot)) {
                clickedCustomItem = item;
                break;
            }
        }

        if (clickedCustomItem != null) {
            handleGenericItem(clickedCustomItem, viewer, event.getClick(), holder.targetName());
        }
    }

    private boolean isButtonActive(String key, HomeTargetHolder holder) {
        if (definition.config().getBoolean("always-show-buttons", false)) return true;

        return switch (key) {
            case "next-page" -> holder.page() < holder.maxPages();
            case "previous-page" -> holder.page() > 1;
            default -> true;
        };
    }

    private void handleSort(InventoryClickEvent event, HomeTargetHolder holder, Player viewer) {
        GuiItem sortItem = definition.items().get("sort-by");
        if (sortItem == null || isOnCooldown(viewer, sortItem)) return;

        ConfigurationSection typesSec = definition.config().getConfigurationSection("buttons.sort-by.types");
        if (typesSec != null) {
            List<String> keys = new ArrayList<>(typesSec.getKeys(false));
            int currentIdx = keys.indexOf(holder.getCurrentSort());
            int nextIdx = event.getClick().isRightClick() ? (currentIdx - 1 + keys.size()) % keys.size() : (currentIdx + 1) % keys.size();
            holder.setCurrentSort(keys.get(nextIdx));

            handleAction(sortItem, viewer, event.getClick(), Map.of("target", holder.targetName()));
            refresh(event.getInventory(), viewer, Collections.emptyMap());
        }
    }

    private void handleFilter(InventoryClickEvent event, HomeTargetHolder holder, Player viewer) {
        GuiItem filterItem = definition.items().get("filter-by");
        if (filterItem == null || isOnCooldown(viewer, filterItem)) return;

        ConfigurationSection typesSec = definition.config().getConfigurationSection("buttons.filter-by.types");
        if (typesSec == null) return;

        List<String> keys = new ArrayList<>(typesSec.getKeys(false));
        int currentIdx = keys.indexOf(holder.getCurrentFilter());
        int nextIdx = event.getClick().isRightClick() ? (currentIdx - 1 + keys.size()) % keys.size() : (currentIdx + 1) % keys.size();
        String newFilter = keys.get(nextIdx);

        holder.setCurrentFilter(newFilter);

        UUID targetUUID = plugin.database().records().uuidFromName(holder.targetName());
        if (targetUUID == null) return;

        List<Map.Entry<String, Location>> filteredHomes = new ArrayList<>(plugin.database().homes().load(targetUUID).entrySet());
        applyFilter(newFilter, filteredHomes);
        sortHomes(holder.getCurrentSort(), filteredHomes);

        int newMax = Math.max(1, (int) Math.ceil((double) filteredHomes.size() / homeSlots.length));
        int newPage = Math.min(holder.page(), newMax);

        handleAction(filterItem, viewer, event.getClick(), Map.of("target", holder.targetName()));

        if (newMax != holder.maxPages()) {
            plugin.gui().openGui("homes-target", viewer, Map.of(
                    "page", String.valueOf(newPage),
                    "filter", newFilter,
                    "sort", holder.getCurrentSort(),
                    "player", holder.targetName()
            ));
        } else {
            holder.updatePagination(newPage, newMax);
            refresh(event.getInventory(), viewer, Collections.emptyMap());
        }
    }

    private void handleNavigation(HomeTargetHolder holder, Player viewer, int direction) {
        String key = direction > 0 ? "next-page" : "previous-page";
        GuiItem navItem = definition.items().get(key);
        if (navItem == null || isOnCooldown(viewer, navItem)) return;

        handleAction(navItem, viewer, ClickType.LEFT, Map.of("target", holder.targetName()));

        int targetPage = holder.page() + direction;
        if (targetPage >= 1 && targetPage <= holder.maxPages()) {
            plugin.gui().openGui("homes-target", viewer, Map.of(
                    "page", String.valueOf(targetPage),
                    "player", holder.targetName()
            ));
        }
    }

    private void handleHomeClick(String name, Player viewer, ClickType click, HomeTargetHolder holder) {
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
            if (confirmType.equals("delete") && !viewer.hasPermission("aircore.command.delhome.others")) return;

            Map<String, String> ph = Map.of("name", name, "target", holder.targetName(), "player", viewer.getName());

            for (String action : rawActions) {
                if (!action.equalsIgnoreCase("[teleport]") && !action.equalsIgnoreCase("[delete]")) {
                    itemAction.execute(action, viewer, ph);
                }
            }

            confirmManager.open(viewer, holder.targetName(), name, confirmType,
                    String.valueOf(holder.page()), holder.getCurrentSort(), holder.getCurrentFilter());
        } else {
            executeTargetHomeActions(rawActions, name, holder.targetName(), viewer);
        }
    }

    private void executeTargetHomeActions(List<String> actions, String homeName, String targetName, Player viewer) {
        Map<String, String> ph = Map.of("name", homeName, "target", targetName, "player", viewer.getName());
        UUID targetUUID = plugin.database().records().uuidFromName(targetName);
        if (targetUUID == null) return;

        for (String action : actions) {
            if (action.equalsIgnoreCase("[teleport]")) {
                Map<String, Location> targetHomes = plugin.home().homes().getHomes(targetUUID);
                if (targetHomes.isEmpty()) targetHomes = plugin.database().homes().load(targetUUID);

                Location loc = targetHomes.get(homeName.toLowerCase());
                if (loc == null) {
                    MessageUtil.send(viewer, "homes.errors.not-found-for", Map.of("player", targetName, "name", homeName));
                    continue;
                }

                plugin.core().teleports().startCountdown(viewer, viewer, () -> {
                    plugin.core().teleports().teleport(viewer, loc);
                    MessageUtil.send(viewer, "homes.teleport.success-other", Map.of("player", targetName, "name", homeName));
                }, reason -> MessageUtil.send(viewer, "homes.teleport.cancelled-other", Map.of("player", targetName, "name", homeName)));

            } else if (action.equalsIgnoreCase("[delete]")) {
                if (!viewer.hasPermission("aircore.command.delhome.others")) continue;

                plugin.home().homes().deleteHome(targetUUID, homeName.toLowerCase());
                plugin.database().homes().delete(targetUUID, homeName.toLowerCase());

                MessageUtil.send(viewer, "homes.management.deleted-for", Map.of("player", targetName, "name", homeName));

                plugin.scheduler().runEntityTask(viewer, () -> {
                    if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof HomeTargetHolder) {
                        refresh(viewer.getOpenInventory().getTopInventory(), viewer, Collections.emptyMap());
                    }
                });
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
        Map<String, String> ph = new HashMap<>(extraPh);
        ph.put("player", viewer.getName());
        List<String> actions = item.getActionsForClick(viewer, ph, click);
        if (actions != null) itemAction.executeAll(actions, viewer, ph);
    }

    private void handleGenericItem(GuiItem item, Player viewer, ClickType click, String target) {
        if (isOnCooldown(viewer, item)) return;
        handleAction(item, viewer, click, Map.of("target", target));
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
            case "oldest" -> { /* handled */ }
        }
    }

    private String buildSortList(String current) { return buildSelectionList("buttons.sort-by", current); }
    private String buildFilterList(String current) { return buildSelectionList("buttons.filter-by", current); }

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
            joiner.add((key.equals(current) ? selectedFmt : unselectedFmt).replace("%name%", types.getString(key, key)));
        }
        return joiner.toString();
    }

    @Override
    public void refresh(Inventory inv, Player viewer, Map<String, String> placeholders) {
        if (!(inv.getHolder() instanceof HomeTargetHolder holder)) return;

        UUID targetUUID = plugin.database().records().uuidFromName(holder.targetName());
        if (targetUUID == null) return;

        List<Map.Entry<String, Location>> homesList = new ArrayList<>(plugin.database().homes().load(targetUUID).entrySet());
        applyFilter(holder.getCurrentFilter(), homesList);
        sortHomes(holder.getCurrentSort(), homesList);

        int maxPages = Math.max(1, (int) Math.ceil((double) homesList.size() / homeSlots.length));
        int page = Math.min(holder.page(), maxPages);

        holder.updatePagination(page, maxPages);
        holder.homes().clear();
        holder.homes().addAll(homesList);

        for (int slot : homeSlots) inv.setItem(slot, null);

        CONSTRUCTION_CONTEXT.set(holder);
        try {
            int targetLimit = plugin.home().homes().getLimit(targetUUID);
            Map<String, String> ph = new HashMap<>(placeholders);
            ph.put("page", String.valueOf(page));
            ph.put("pages", String.valueOf(maxPages));
            ph.put("target", holder.targetName());
            ph.put("sort", buildSortList(holder.getCurrentSort()));
            ph.put("filter", buildFilterList(holder.getCurrentFilter()));
            ph.put("limit", String.valueOf(targetLimit));

            HomeSlotMapper.fillHomeInventory(plugin, inv, definition, viewer, page, maxPages, homesList, homeSlots, ph, targetLimit, false);
        } finally {
            CONSTRUCTION_CONTEXT.remove();
        }
    }

    @Override public void cleanup() { confirmManager.cleanup(); }
    @Override public boolean owns(Inventory inv) { return inv.getHolder() instanceof HomeTargetHolder; }
    public GuiDefinition definition() { return this.definition; }

    public static class HomeTargetHolder implements InventoryHolder {
        private final String targetName;
        private int page, maxPages;
        private final List<Map.Entry<String, Location>> homes;
        private final Map<String, Long> timestamps;
        private String currentSort, currentFilter;
        private Inventory inventory;

        public HomeTargetHolder(String target, int p, int m, List<Map.Entry<String, Location>> h, String s, String f, Map<String, Long> ts) {
            this.targetName = target; this.page = p; this.maxPages = m; this.homes = new ArrayList<>(h);
            this.currentSort = s; this.currentFilter = f; this.timestamps = ts;
        }

        public void updatePagination(int p, int m) { this.page = p; this.maxPages = m; }
        @Override public @NotNull Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inv) { this.inventory = inv; }
        public String targetName() { return targetName; }
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