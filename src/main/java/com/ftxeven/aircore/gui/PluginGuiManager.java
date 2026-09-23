package com.ftxeven.aircore.gui;

import com.ftxeven.aircore.command.player.PlayerTargetResolver;
import com.ftxeven.aircore.command.player.Selectors;
import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.core.animation.AnimationManager;
import com.ftxeven.aircore.core.gui.GuiManager;
import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.core.gui.OpenOptions;
import com.ftxeven.aircore.core.gui.input.InputRegistry;
import com.ftxeven.aircore.core.gui.render.GuiRenderer;
import com.ftxeven.aircore.gui.action.*;
import com.ftxeven.aircore.gui.config.LayoutConfig;
import com.ftxeven.aircore.gui.impl.*;
import com.ftxeven.aircore.gui.render.RemoteContainerRenderer;
import com.ftxeven.aircore.gui.render.PlayerHeadResolver;
import com.ftxeven.aircore.model.PlayerProfile;
import com.ftxeven.aircore.module.ModuleManager;
import com.ftxeven.aircore.service.ServiceManager;
import com.ftxeven.aircore.service.inventory.InventoryHandle;
import com.ftxeven.aircore.service.inventory.InventoryKind;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.logging.Logger;

public final class PluginGuiManager {

    private static final Set<String> RESERVED_PATHS = Set.of(InputRegistry.INPUT_FOLDER);

    private final Map<String, GuiRenderer.DynamicRenderer> builtinRoles = new HashMap<>();
    private final Set<String> roleBoundIds = new HashSet<>();
    private final Map<String, List<String>> idsByRole = new HashMap<>();

    private final GuiManager guis;
    private final LayoutGuiRegistry layouts;
    private final ServiceManager services;
    private final Logger logger;

    private PluginGuiManager(GuiManager guis, LayoutGuiRegistry layouts, ServiceManager services, Logger logger) {
        this.guis = guis;
        this.layouts = layouts;
        this.services = services;
        this.logger = logger;
    }

    public static @Nullable PluginGuiManager create(JavaPlugin plugin, Messenger messenger, ConfigManager configs,
                                                    ServiceManager services, ModuleManager modules, AnimationManager animations) {
        GuiManager guis = GuiManager.builder(plugin, messenger, animations)
                .heads(new PlayerHeadResolver(services.players()))
                .players(name -> services.players().findByRealName(name).map(PlayerProfile::uuid).orElse(null))
                .reservedPaths(RESERVED_PATHS)
                .layoutReplaceKeys(LayoutConfig.TEMPLATE_KEYS)
                .build();
        if (guis == null) {
            return null;
        }

        guis.onStaleClick((viewer, session) ->
                messenger.send(viewer, configs.lang().get("errors.general.stale-entry"), session.placeholders()));

        LayoutGuiRegistry layouts = new LayoutGuiRegistry(plugin, guis);
        layouts.load();

        PluginGuiManager manager = new PluginGuiManager(guis, layouts, services, plugin.getLogger());
        manager.registerActions(configs, services, modules);
        manager.registerGuis(services, configs, modules);
        return manager;
    }

    public GuiManager guis() {
        return guis;
    }

    public LayoutGuiRegistry layouts() {
        return layouts;
    }

    private void registerActions(ConfigManager configs, ServiceManager services, ModuleManager modules) {
        PlayerTargetResolver resolver = new PlayerTargetResolver(services.players(), configs::main);
        Selectors selectors = new Selectors(() -> configs.commands().selectors());

        ClaimAction claim = new ClaimAction(configs, modules::kits, logger);
        TeleportAction teleport = new TeleportAction(configs, modules::teleport, modules::homes, services, logger);
        RequestAction request = new RequestAction(configs, modules::teleport, modules::economy, resolver, selectors, services);
        PreviewAction preview = new PreviewAction(guis, logger);
        CustomizeAction customize = new CustomizeAction(configs, modules::homes, guis, logger);
        FavoriteAction favorite = new FavoriteAction(modules::homes, logger);
        DeleteAction delete = new DeleteAction(configs, modules::homes, services, logger);
        FilterAction filter = new FilterAction(configs, this);
        SortAction sort = new SortAction(configs, this);
        SearchAction search = new SearchAction(this);
        ItemAction item = new ItemAction(configs, services, modules::economy);

        guis.actions().register("claim", claim.dispatcher(configs));
        guis.actions().register("teleport", teleport.dispatcher(configs));
        guis.actions().register("request", request.dispatcher(configs));
        guis.actions().register("preview", preview);
        guis.actions().register("customize", customize);
        guis.actions().register("favorite", favorite);
        guis.actions().register("delete", delete.dispatcher(configs));
        guis.actions().register("filter", filter);
        guis.actions().register("sort", sort);
        guis.actions().register("search", search);
        guis.actions().register("item", item.dispatcher(configs));
    }

    private void registerGuis(ServiceManager services, ConfigManager configs, ModuleManager modules) {
        builtinRoles.put(PreviewItemGui.ROLE, new PreviewItemGui(services.players(), layouts, configs::lang));
        builtinRoles.put(PreviewInventoryGui.ROLE, new PreviewInventoryGui(services.players(), layouts));
        builtinRoles.put(PreviewEnderchestGui.ROLE, new PreviewEnderchestGui(services.players(), layouts));
        builtinRoles.put(PreviewShulkerGui.ROLE, new PreviewShulkerGui(services.players(), layouts, configs::lang));

        builtinRoles.put(LiveInvseeGui.ROLE, new LiveInvseeGui(services.players(), layouts, logger));
        builtinRoles.put(LiveEnderseeGui.ROLE, new LiveEnderseeGui(services.players(), layouts, logger));
        builtinRoles.put(LiveEnderchestGui.ROLE, new LiveEnderchestGui(services.players(), layouts, logger));

        builtinRoles.put(KitPreviewGui.ROLE, new KitPreviewGui(layouts, modules::kits, logger));

        HomeBrowserGui homesBrowser = new HomeBrowserGui(configs, services, modules::homes, this);
        builtinRoles.put(HomeBrowserGui.ROLE, homesBrowser);
        builtinRoles.put(HomeSearchGui.ROLE, new HomeSearchGui(configs, services, modules::homes, this));
        builtinRoles.put(HomeCustomizeGui.ROLE, new HomeCustomizeGui(configs, services, modules::homes, this));
        builtinRoles.put(HomeConfirmGui.ROLE, new HomeConfirmGui(configs, services, modules::homes, this));

        builtinRoles.put(LeaderboardBrowserGui.ROLE, new LeaderboardBrowserGui(configs, services, modules::economy, this, logger));
        builtinRoles.put(LeaderboardSearchGui.ROLE, new LeaderboardSearchGui(configs, services, modules::economy, this, logger));

        DisposalGui disposal = new DisposalGui(services, this);
        builtinRoles.put(DisposalGui.ROLE, disposal);
        builtinRoles.put(DisposalGui.CONFIRM_ROLE, disposal);

        SellGui sell = new SellGui(configs, services, modules::economy, this);
        builtinRoles.put(SellGui.ROLE, sell);
        builtinRoles.put(SellGui.CONFIRM_ROLE, sell);

        bindRoles();
    }

    // (re)attaches each built-in renderer to whichever currently-loaded GUI id(s) declare its role,
    // and detaches any id that declared a role on a previous load but no longer does
    private void bindRoles() {
        Set<String> stillBound = new HashSet<>();
        Map<String, List<String>> boundIdsByRole = new HashMap<>();

        for (String id : guis.ids()) {
            guis.definition(id).ifPresent(config -> {
                String role = config.role();
                if (role == null) {
                    return;
                }
                GuiRenderer.DynamicRenderer renderer = builtinRoles.get(role);
                if (renderer == null) {
                    logger.warning("GUI '" + id + "' declares role '" + role + "', which isn't a recognized "
                            + "role - it will render with only its own static items");
                    return;
                }
                guis.dynamicRenderer(id, renderer);
                stillBound.add(id);
                boundIdsByRole.computeIfAbsent(role, key -> new ArrayList<>()).add(id);
            });
        }

        for (String previouslyBound : roleBoundIds) {
            if (!stillBound.contains(previouslyBound)) {
                guis.renderer().removeDynamicRenderer(previouslyBound);
            }
        }
        roleBoundIds.clear();
        roleBoundIds.addAll(stillBound);

        idsByRole.clear();
        idsByRole.putAll(boundIdsByRole);
    }

    public RoleResolution resolveRole(String role) {
        List<String> ids = idsByRole.getOrDefault(role, List.of());
        return switch (ids.size()) {
            case 0 -> new RoleResolution.NotFound();
            case 1 -> new RoleResolution.Found(ids.getFirst());
            default -> new RoleResolution.Ambiguous(List.copyOf(ids));
        };
    }

    // whether a loaded GUI declares the given role. Several GUIs may share one role, an explicit
    // 'gui:' on an action is checked against this
    public boolean declaresRole(String guiId, String role) {
        return idsByRole.getOrDefault(role, List.of()).contains(guiId);
    }

    public sealed interface RoleResolution {
        record Found(String guiId) implements RoleResolution {}
        record NotFound() implements RoleResolution {}
        record Ambiguous(List<String> guiIds) implements RoleResolution {}
    }

    public void openForTarget(Player viewer, String commandName, @Nullable String guiId, UUID target, Map<String, ?> attributes) {
        if (!requireGui(commandName, guiId)) {
            return;
        }
        Map<String, Object> merged = new HashMap<>(attributes);
        merged.put(GuiSession.ATTR_TARGET, target);
        guis.open(viewer, guiId, new HashMap<>(), OpenOptions.entry(merged, List.of()));
    }

    public void openOwnEnderChest(Player player, @Nullable String guiId) {
        if (!requireGui("enderchest", guiId)) {
            return;
        }

        UUID uuid = player.getUniqueId();

        services.inventories()
                .open(uuid, uuid, InventoryKind.ENDER, true,
                        change -> Scheduler.runEntity(player, () -> applyRemoteChange(player, change)))
                .thenAccept(opened -> Scheduler.runEntity(player, () -> {
                    Map<String, Object> attributes = new HashMap<>();
                    attributes.put(GuiSession.ATTR_TARGET, uuid);
                    attributes.put(LiveEnderchestGui.ATTR_HANDLE, opened.handle());
                    attributes.put(LiveEnderchestGui.ATTR_INITIAL_CONTENTS, opened.initialContents());
                    guis.open(player, guiId, new HashMap<>(), OpenOptions.entry(attributes, List.of()));
                }))
                .exceptionally(error -> {
                    logger.warning("Could not open " + guiId + " for " + player.getName() + ": " + error.getMessage());
                    return null;
                });
    }

    public void openLiveInventory(Player viewer, UUID target, InventoryKind kind, boolean canModify, @Nullable String guiId) {
        String commandName = kind == InventoryKind.MAIN ? "invsee" : "endersee";
        if (!requireGui(commandName, guiId)) {
            return;
        }

        String handleAttr = kind == InventoryKind.MAIN ? LiveInvseeGui.ATTR_HANDLE : LiveEnderseeGui.ATTR_HANDLE;
        String contentsAttr = kind == InventoryKind.MAIN ? LiveInvseeGui.ATTR_INITIAL_CONTENTS : LiveEnderseeGui.ATTR_INITIAL_CONTENTS;
        String modifyAttr = kind == InventoryKind.MAIN ? LiveInvseeGui.ATTR_CAN_MODIFY : LiveEnderseeGui.ATTR_CAN_MODIFY;

        services.inventories()
                .open(viewer.getUniqueId(), target, kind, canModify,
                        change -> Scheduler.runEntity(viewer, () -> applyRemoteChange(viewer, change)))
                .thenAccept(opened -> Scheduler.runEntity(viewer, () -> {
                    Map<String, Object> attributes = new HashMap<>();
                    attributes.put(GuiSession.ATTR_TARGET, target);
                    attributes.put(handleAttr, opened.handle());
                    attributes.put(contentsAttr, opened.initialContents());
                    attributes.put(modifyAttr, canModify);
                    guis.open(viewer, guiId, new HashMap<>(), OpenOptions.entry(attributes, List.of()));
                }))
                .exceptionally(error -> {
                    logger.warning("Could not open " + guiId + " for " + viewer.getName() + ": " + error.getMessage());
                    return null;
                });
    }

    public void openMenu(Player viewer, String commandName, @Nullable String guiId) {
        if (!requireGui(commandName, guiId)) {
            return;
        }
        guis.open(viewer, guiId, new HashMap<>());
    }

    private boolean requireGui(String commandName, @Nullable String guiId) {
        if (guiId == null || guiId.isBlank()) {
            logger.warning("Command '" + commandName + "' has no 'gui.menu' configured in commands.yml - "
                    + "it will not do anything until this is fixed");
            return false;
        }
        if (!GuiActions.guiEnabled(guis, guiId)) {
            logger.warning("Command '" + commandName + "' points at gui '" + guiId
                    + "', which isn't a registered/enabled GUI - it will not do anything until this is fixed");
            return false;
        }
        return true;
    }

    private void applyRemoteChange(Player viewer, InventoryHandle.SlotChange change) {
        GuiSession session = guis.session(viewer);
        if (session != null && session.liveContainer() instanceof RemoteContainerRenderer container) {
            container.applyRemoteChange(session, change);
        }
    }

    public boolean reload() {
        boolean ok = guis.reload();
        layouts.reload();
        bindRoles();
        return ok;
    }

    public void shutdown() {
        guis.shutdown();
    }
}