package com.ftxeven.aircore.core.gui.homes;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.gui.GuiDefinition;
import com.ftxeven.aircore.core.gui.GuiManager;
import com.ftxeven.aircore.core.gui.ItemAction;
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
    private final MiniMessage mm = MiniMessage.miniMessage();
    private GuiDefinition definition;
    private int[] homeSlots;
    private boolean enabled;
    private final HomeConfirmManager confirmManager;

    public HomeManager(AirCore plugin, ItemAction itemAction) {
        this.plugin = plugin;
        this.itemAction = itemAction;
        loadDefinition();
        this.confirmManager = new HomeConfirmManager(plugin, this);
    }

    public boolean isEnabled() { return enabled; }

    public void loadDefinition() {
        File file = new File(plugin.getDataFolder(), "guis/homes/homes.yml");
        if (!file.exists()) plugin.saveResource("guis/homes/homes.yml", false);

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        this.enabled = cfg.getBoolean("enabled", true);

        List<Integer> parsed = GuiDefinition.parseSlots(cfg.getStringList("home-slots"));
        this.homeSlots = parsed.stream().mapToInt(Integer::intValue).toArray();

        Map<String, GuiDefinition.GuiItem> items = new LinkedHashMap<>();
        loadSection(cfg.getConfigurationSection("items"), items);
        loadSection(cfg.getConfigurationSection("buttons"), items);

        this.definition = new GuiDefinition(cfg.getString("title", "Homes"), cfg.getInt("rows", 5), items, cfg);
    }

    private void loadSection(ConfigurationSection sec, Map<String, GuiDefinition.GuiItem> items) {
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection itemSec = sec.getConfigurationSection(key);
            if (itemSec != null) {
                items.put(key, GuiDefinition.GuiItem.fromSection(key, itemSec));
            }
        }
    }

    @Override
    public Inventory build(Player viewer, Map<String, String> placeholders) {
        int page = Integer.parseInt(placeholders.getOrDefault("page", "1"));
        Map<String, Location> homeMap = plugin.home().homes().getHomes(viewer.getUniqueId());
        List<Map.Entry<String, Location>> homesList = new ArrayList<>(homeMap.entrySet());

        String defaultSort = getDefaultSort();
        sortHomes(defaultSort, homesList);

        int pageSize = homeSlots.length;
        int maxPages = Math.max(1, (int) Math.ceil((double) homesList.size() / pageSize));
        if (page > maxPages) page = maxPages;

        Map<String, Long> timestamps = plugin.database().homes().loadTimestamps(viewer.getUniqueId());
        HomeHolder holder = new HomeHolder(page, maxPages, homesList, defaultSort, timestamps);
        String title = definition.title()
                .replace("%page%", String.valueOf(page))
                .replace("%maxpages%", String.valueOf(maxPages))
                .replace("%player%", viewer.getName());

        Inventory inv = Bukkit.createInventory(holder, definition.rows() * 9,
                mm.deserialize("<!italic>" + PlaceholderUtil.apply(viewer, title)));

        holder.setInventory(inv);

        int homeLimit = plugin.home().homes().getLimit(viewer.getUniqueId());

        Map<String, String> ph = new HashMap<>(placeholders);
        ph.put("sort", buildSortList(defaultSort));
        ph.put("limit", String.valueOf(homeLimit));

        HomeSlotMapper.fillHomeInventory(inv, definition, viewer, page, maxPages, homesList, homeSlots, ph, homeLimit);

        return inv;
    }

    private String getDefaultSort() {
        ConfigurationSection typesSec = definition.config().getConfigurationSection("buttons.sort-by.types");
        if (typesSec != null) {
            return typesSec.getKeys(false).stream().findFirst().orElse("latest");
        }
        return "latest";
    }

    private void sortHomes(String type, List<Map.Entry<String, Location>> list) {
        switch (type.toLowerCase()) {
            case "alphabetical" -> list.sort((e1, e2) -> e1.getKey().compareToIgnoreCase(e2.getKey()));
            case "latest" -> Collections.reverse(list);
            case "oldest" -> { /* db already provides this order */ }
        }
    }

    private String buildSortList(String current) {
        ConfigurationSection sortSec = definition.config().getConfigurationSection("buttons.sort-by");
        if (sortSec == null) return "";
        ConfigurationSection types = sortSec.getConfigurationSection("types");
        if (types == null) return "";

        boolean showList = sortSec.getBoolean("show-list", true);
        String selectedFmt = sortSec.getString("format.selected", "⏵ %name%");
        String unselectedFmt = sortSec.getString("format.unselected", " %name%");

        if (!showList) {
            String name = types.getString(current);
            return selectedFmt.replace("%name%", name != null ? name : current);
        }

        StringJoiner joiner = new StringJoiner("\n");
        for (String key : types.getKeys(false)) {
            String name = types.getString(key);
            String fmt = key.equals(current) ? selectedFmt : unselectedFmt;
            joiner.add(fmt.replace("%name%", name != null ? name : key));
        }
        return joiner.toString();
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player viewer) {
        event.setCancelled(true);
        if (!(event.getInventory().getHolder() instanceof HomeHolder holder)) return;

        int slot = event.getSlot();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        for (int i = 0; i < homeSlots.length; i++) {
            if (homeSlots[i] == slot) {
                int actualIndex = ((holder.page() - 1) * homeSlots.length) + i;
                if (actualIndex < holder.homes().size()) {
                    handleHomeClick(holder.homes().get(actualIndex).getKey(), viewer, event.getClick(), holder.page());
                    return;
                }
            }
        }

        boolean alwaysShow = definition.config().getBoolean("always-show-buttons", false);

        if (isFunctionalKeyAtSlot(slot, "next-page")) {
            if (holder.page() < holder.maxPages() || alwaysShow) {
                executeKeyActions("next-page", viewer, event.getClick(), Collections.emptyMap());
                if (holder.page() < holder.maxPages()) {
                    plugin.gui().openGui("homes", viewer, Map.of("page", String.valueOf(holder.page() + 1)));
                }
                return;
            }
        }

        if (isFunctionalKeyAtSlot(slot, "previous-page")) {
            if (holder.page() > 1 || alwaysShow) {
                executeKeyActions("previous-page", viewer, event.getClick(), Collections.emptyMap());
                if (holder.page() > 1) {
                    plugin.gui().openGui("homes", viewer, Map.of("page", String.valueOf(holder.page() - 1)));
                }
                return;
            }
        }

        if (isFunctionalKeyAtSlot(slot, "sort-by")) {
            executeKeyActions("sort-by", viewer, event.getClick(), Map.of("sort", buildSortList(holder.getCurrentSort())));
            return;
        }

        handleGenericItem(slot, viewer, event.getClick());
    }

    private boolean isFunctionalKeyAtSlot(int slot, String key) {
        GuiDefinition.GuiItem gi = definition.items().get(key);
        return gi != null && gi.slots().contains(slot);
    }

    private void handleHomeClick(String name, Player viewer, ClickType click, int page) {
        ConfigurationSection homeSec = definition.config().getConfigurationSection("home-item");
        if (homeSec == null) return;

        ConfigurationSection applyConfirm = homeSec.getConfigurationSection("apply-confirm");

        if (applyConfirm != null) {
            List<String> potentialKeys = getPotentialClickKeys(click);

            for (String key : potentialKeys) {
                for (String typeKey : applyConfirm.getKeys(false)) {
                    ConfigurationSection typeSec = applyConfirm.getConfigurationSection(typeKey);
                    if (typeSec == null || !typeSec.getBoolean("enabled", false)) continue;

                    if (typeSec.contains(key)) {
                        confirmManager.open(viewer, name, typeKey, String.valueOf(page));

                        List<String> uiActions = typeSec.getStringList(key);
                        if (!uiActions.isEmpty()) {
                            executeRawActions(uiActions, viewer, Map.of("name", name));
                        }

                        return;
                    }
                }
            }
        }

        GuiDefinition.GuiItem template = GuiDefinition.GuiItem.fromSection("home-item", homeSec);
        handleAction(template, viewer, click, Map.of("name", name));
    }

    public void executeRawActions(List<String> actions, Player player, Map<String, String> ph) {
        if (actions == null || actions.isEmpty()) return;

        Map<String, String> fullPh = new HashMap<>(ph);
        fullPh.put("player", player.getName());

        itemAction.executeAll(actions, player, fullPh);
    }

    private List<String> getPotentialClickKeys(ClickType click) {
        List<String> keys = new ArrayList<>();

        switch (click) {
            case SHIFT_LEFT -> {
                keys.add("shift-left-actions");
                keys.add("shift-actions");
            }
            case SHIFT_RIGHT -> {
                keys.add("shift-right-actions");
                keys.add("shift-actions");
            }
            case LEFT -> keys.add("left-actions");
            case RIGHT -> keys.add("right-actions");
        }

        keys.add("actions");
        return keys;
    }

    private void handleGenericItem(int slot, Player viewer, ClickType click) {
        for (GuiDefinition.GuiItem item : definition.items().values()) {
            if (item.slots().contains(slot)) {
                handleAction(item, viewer, click, Collections.emptyMap());
                return;
            }
        }
    }

    private void executeKeyActions(String key, Player viewer, ClickType click, Map<String, String> ph) {
        GuiDefinition.GuiItem item = definition.items().get(key);
        if (item != null) handleAction(item, viewer, click, ph);
    }

    public void handleAction(GuiDefinition.GuiItem item, Player viewer, ClickType click, Map<String, String> extraPh) {
        if (item == null) return;
        if (plugin.gui().cooldowns().isOnCooldown(viewer, item)) {
            plugin.gui().cooldowns().sendCooldownMessage(viewer, item);
            return;
        }
        plugin.gui().cooldowns().applyCooldown(viewer, item);
        List<String> actions = item.getActionsForClick(click);
        if (actions != null && !actions.isEmpty()) {
            Map<String, String> ph = new HashMap<>(extraPh);
            ph.put("player", viewer.getName());
            itemAction.executeAll(actions, viewer, ph);
        }
    }

    @Override
    public void refresh(Inventory inv, Player viewer, Map<String, String> placeholders) {
        if (!(inv.getHolder() instanceof HomeHolder holder)) return;

        ConfigurationSection typesSec = definition.config().getConfigurationSection("buttons.sort-by.types");
        if (typesSec != null) {
            List<String> keys = new ArrayList<>(typesSec.getKeys(false));
            if (!keys.isEmpty()) {
                String current = holder.getCurrentSort();
                int nextIdx = (keys.indexOf(current) + 1) % keys.size();
                holder.setCurrentSort(keys.get(nextIdx));
            }
        }

        Map<String, Location> homeMap = plugin.home().homes().getHomes(viewer.getUniqueId());
        List<Map.Entry<String, Location>> homesList = new ArrayList<>(homeMap.entrySet());
        sortHomes(holder.getCurrentSort(), homesList);

        holder.homes().clear();
        holder.homes().addAll(homesList);

        int homeLimit = plugin.home().homes().getLimit(viewer.getUniqueId());

        Map<String, String> ph = new HashMap<>(placeholders);
        ph.put("sort", buildSortList(holder.getCurrentSort()));
        ph.put("limit", String.valueOf(homeLimit));

        HomeSlotMapper.fillHomeInventory(inv, definition, viewer, holder.page(), holder.maxPages(), holder.homes(), homeSlots, ph, homeLimit);
    }

    @Override
    public void cleanup() {
        confirmManager.cleanup();
    }

    @Override
    public boolean owns(Inventory inv) { return inv.getHolder() instanceof HomeHolder; }

    public static class HomeHolder implements InventoryHolder {
        private final int page, maxPages;
        private final List<Map.Entry<String, Location>> homes;
        private final Map<String, Long> timestamps;
        private String currentSort;
        private Inventory inventory;

        public HomeHolder(int p, int m, List<Map.Entry<String, Location>> h, String initialSort, Map<String, Long> ts) {
            this.page = p;
            this.maxPages = m;
            this.homes = new ArrayList<>(h);
            this.currentSort = initialSort;
            this.timestamps = ts;
        }

        @Override
        public @NotNull Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public int page() { return page; }
        public int maxPages() { return maxPages; }
        public List<Map.Entry<String, Location>> homes() { return homes; }
        public String getCurrentSort() { return currentSort; }
        public void setCurrentSort(String currentSort) { this.currentSort = currentSort; }
        public Map<String, Long> getTimestamps() { return timestamps; }

    }
}