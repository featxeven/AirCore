package com.ftxeven.aircore.gui.render;

import com.ftxeven.aircore.config.FilterConfig;
import com.ftxeven.aircore.model.Home;
import com.ftxeven.aircore.model.Kit;
import com.ftxeven.aircore.model.NamedLocation;
import com.ftxeven.aircore.module.economy.EconomyModule;
import com.ftxeven.aircore.module.economy.sell.SellHandler;
import com.ftxeven.aircore.module.kits.KitsModule;
import com.ftxeven.aircore.module.teleport.TeleportModule;
import com.ftxeven.aircore.service.item.HoldingKind;
import com.ftxeven.aircore.service.item.HoldingService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class GuiFlags {

    private GuiFlags() {
    }

    // Entry point

    public static Builder builder(Player viewer) {
        return new Builder(viewer);
    }

    public static Function<String, String> custom(Player viewer, BiFunction<String, String, Boolean> checks) {
        return builder(viewer).can(checks).build();
    }

    public static final class Builder {

        private final Player viewer;
        private final List<Function<String, String>> resolvers = new ArrayList<>();

        private Builder(Player viewer) {
            this.viewer = viewer;
            resolvers.add(namespaced("has", (permission, arg) -> viewer.hasPermission(permission)));
        }

        public Builder is(BiFunction<String, String, Boolean> checks) {
            resolvers.add(namespaced("is", checks));
            return this;
        }

        public Builder can(BiFunction<String, String, Boolean> checks) {
            resolvers.add(namespaced("can", checks));
            return this;
        }

        // is:self - true when 'holder' is the viewer. Generic flag, not tied to a specific gui
        public Builder self(UUID holder) {
            return is((flag, arg) -> flag.equals("self") ? holder.equals(viewer.getUniqueId()) : null);
        }

        // is:online[:<player>] - true when that player (name or uuid) is online
        public Builder online(@Nullable UUID subject) {
            return is((flag, arg) -> flag.equals("online") ? isOnline(subject, arg) : null);
        }

        // same, for a screen with no subject of its own
        public Builder online() {
            return online(null);
        }

        public Function<String, String> build() {
            List<Function<String, String>> snapshot = List.copyOf(resolvers);
            return key -> {
                for (Function<String, String> resolver : snapshot) {
                    String result = resolver.apply(key);
                    if (result != null) {
                        return result;
                    }
                }
                return null;
            };
        }

        private boolean isOnline(@Nullable UUID subject, @Nullable String arg) {
            if (arg != null) {
                return playerOnline(arg);
            }
            if (subject != null) {
                return Bukkit.getPlayer(subject) != null;
            }
            UUID self = viewer.getUniqueId();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.getUniqueId().equals(self)) {
                    return true;
                }
            }
            return false;
        }
    }

    // shared

    private static boolean playerOnline(String token) {
        if (token.isBlank()) {
            return false;
        }
        if (Bukkit.getPlayerExact(token) != null) {
            return true;
        }
        try {
            return Bukkit.getPlayer(UUID.fromString(token)) != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private record Flag(String namespace, String value) {
        static @Nullable Flag parse(String key) {
            int sep = key.indexOf(':');
            return sep < 0 ? null : new Flag(key.substring(0, sep), key.substring(sep + 1));
        }
    }

    private static Function<String, String> namespaced(String namespace, BiFunction<String, String, Boolean> checks) {
        return key -> {
            Flag flag = Flag.parse(key);
            if (flag == null || !flag.namespace().equals(namespace)) {
                return null;
            }
            int sep = flag.value().indexOf(':');
            String name = sep < 0 ? flag.value() : flag.value().substring(0, sep);
            String arg = sep < 0 ? null : flag.value().substring(sep + 1);
            Boolean result = checks.apply(name, arg);
            return result != null ? String.valueOf(result) : null;
        };
    }

    // Economy

    // can:pay - true when 'sender' is able to pay 'targetUuid' 'amount'
    public static Function<String, String> forPay(Player sender, EconomyModule economy, UUID targetUuid, double amount) {
        return builder(sender)
                .can((flag, arg) -> flag.equals("pay") ? economy.pay().preview(sender, targetUuid, amount).isEmpty() : null)
                .build();
    }

    // Homes

    public static Function<String, String> forHome(Player viewer, FilterConfig filter, UUID owner, @Nullable Home home,
                                                   Function<String, Optional<Home>> ownerHomes) {
        return new HomeContext(viewer, filter, owner, home, null, ownerHomes).resolver();
    }

    public static Function<String, String> forIcon(Player viewer, FilterConfig filter, UUID owner, @Nullable Home home,
                                                   FilterConfig.HomeIcon rendered, Function<String, Optional<Home>> ownerHomes) {
        return new HomeContext(viewer, filter, owner, home, rendered, ownerHomes).resolver();
    }

    private static final class HomeContext {

        private static final String VALID_HOME = "valid_home";
        private static final String OWNED_HOME = "owned_home";
        private static final String FAVORITE_HOME = "favorite_home";
        private static final String BUNDLED_ICON = "bundled_icon";
        private static final String SELECTED_ICON = "selected_icon";
        private static final String USE_ICON = "use_icon";

        private final Player viewer;
        private final FilterConfig filter;
        private final UUID owner;
        private final @Nullable Home home;
        private final @Nullable FilterConfig.HomeIcon rendered;
        private final Function<String, Optional<Home>> ownerHomes;

        private HomeContext(Player viewer, FilterConfig filter, UUID owner, @Nullable Home home,
                            @Nullable FilterConfig.HomeIcon rendered, Function<String, Optional<Home>> ownerHomes) {
            this.viewer = viewer;
            this.filter = filter;
            this.owner = owner;
            this.home = home;
            this.rendered = rendered;
            this.ownerHomes = ownerHomes;
        }

        private Function<String, String> resolver() {
            return builder(viewer).is(this::isFlag).can(this::canFlag).build();
        }

        // =is:

        private @Nullable Boolean isFlag(String flag, @Nullable String arg) {
            return switch (flag) {
                case VALID_HOME -> subjectHome(arg).isPresent();
                case OWNED_HOME -> owner.equals(viewer.getUniqueId()) && (arg == null || subjectHome(arg).isPresent());
                case FAVORITE_HOME -> subjectHome(arg).map(Home::favorite).orElse(false);
                case BUNDLED_ICON -> subjectIcon(arg).map(FilterConfig.HomeIcon::hasBundle).orElse(false);
                case SELECTED_ICON -> isSelected(arg);
                default -> null;
            };
        }

        private boolean isSelected(@Nullable String arg) {
            String current = currentIcon();
            if (arg == null) {
                return rendered != null && rendered.id().equals(current);
            }
            return arg.equalsIgnoreCase(FilterConfig.ALL_ID) ? current == null : arg.equals(current);
        }

        // =can:

        private @Nullable Boolean canFlag(String flag, @Nullable String arg) {
            if (!flag.equals(USE_ICON)) {
                return null;
            }
            return canUse(arg);
        }

        private boolean canUse(@Nullable String arg) {
            if (arg != null && arg.equalsIgnoreCase(FilterConfig.ALL_ID)) {
                return true; // resetting to the default icon is never gated
            }
            Optional<FilterConfig.HomeIcon> icon = subjectIcon(arg);
            if (icon.isEmpty()) {
                return arg == null; // nothing on show has nothing to gate
            }
            return icon.get().isAccessibleTo(viewer);
        }

        // Subjects

        private Optional<Home> subjectHome(@Nullable String arg) {
            if (arg == null) {
                return Optional.ofNullable(home);
            }
            return arg.isBlank() ? Optional.empty() : ownerHomes.apply(arg);
        }

        private Optional<FilterConfig.HomeIcon> subjectIcon(@Nullable String arg) {
            if (arg == null) {
                return displayedIcon();
            }
            return arg.isBlank() ? Optional.empty() : filter.homeIcon(arg);
        }

        private Optional<FilterConfig.HomeIcon> displayedIcon() {
            if (rendered != null) {
                return Optional.of(rendered);
            }
            return Optional.ofNullable(home).map(Home::icon).flatMap(filter::homeIcon);
        }

        private @Nullable String currentIcon() {
            String raw = home != null ? home.icon() : null;
            return GuiCyclers.iconResolvable(filter, raw) ? raw : null;
        }
    }

    // Kits

    public static Function<String, String> forKit(Player viewer, KitsModule kits, @Nullable Kit kit) {
        return new KitContext(viewer, kits, kit).resolver();
    }

    private static final class KitContext {

        private static final String CLAIM = "claim_kit";
        private static final String ON_COOLDOWN = "kit_on_cooldown";

        private final Player viewer;
        private final KitsModule kits;
        private final @Nullable Kit kit;

        private KitContext(Player viewer, KitsModule kits, @Nullable Kit kit) {
            this.viewer = viewer;
            this.kits = kits;
            this.kit = kit;
        }

        private Function<String, String> resolver() {
            return builder(viewer).is(this::isFlag).can(this::canFlag).build();
        }

        // =is:

        private @Nullable Boolean isFlag(String flag, @Nullable String arg) {
            if (!flag.equals(ON_COOLDOWN)) {
                return null;
            }
            return subjectKit(arg).map(subject -> kits.isOnCooldown(viewer, subject)).orElse(null);
        }

        // =can:

        private @Nullable Boolean canFlag(String flag, @Nullable String arg) {
            if (!flag.equals(CLAIM)) {
                return null;
            }
            if (arg == null) {
                return kit != null ? kits.hasInventorySpace(viewer, kit) : null;
            }
            return subjectKit(arg).map(subject -> kits.canClaim(viewer, subject)).orElse(null);
        }

        // the kit a flag is about: the screen's own, or the one named by the arg
        private Optional<Kit> subjectKit(@Nullable String arg) {
            if (arg == null) {
                return Optional.ofNullable(kit);
            }
            return arg.isBlank() ? Optional.empty() : kits.find(arg);
        }
    }

    // Teleport

    public static Function<String, String> forWarp(Player viewer, TeleportModule teleport, @Nullable NamedLocation warp) {
        return new TeleportContext(viewer, teleport, warp).resolver();
    }

    public static Function<String, String> forBack(Player viewer, TeleportModule teleport) {
        return new TeleportContext(viewer, teleport, null).resolver();
    }

    private static final class TeleportContext {

        private static final String WARP_TO = "warp_to";
        private static final String BACK_TO = "back_to";

        private final Player viewer;
        private final TeleportModule teleport;
        private final @Nullable NamedLocation warp;

        private TeleportContext(Player viewer, TeleportModule teleport, @Nullable NamedLocation warp) {
            this.viewer = viewer;
            this.teleport = teleport;
            this.warp = warp;
        }

        private Function<String, String> resolver() {
            return builder(viewer).can(this::canFlag).build();
        }

        // =can:

        private @Nullable Boolean canFlag(String flag, @Nullable String arg) {
            return switch (flag) {
                case WARP_TO -> subjectWarp(arg).map(subject -> teleport.canWarp(viewer, subject)).orElse(null);
                case BACK_TO -> teleport.canBack(viewer);
                default -> null;
            };
        }

        // the warp a flag is about: the screen's own, or the one named by the arg
        private Optional<NamedLocation> subjectWarp(@Nullable String arg) {
            if (arg == null) {
                return Optional.ofNullable(warp);
            }
            return arg.isBlank() ? Optional.empty() : teleport.findWarp(arg);
        }
    }

    // Item holding (disposal / sell)

    public static Function<String, String> forDisposal(Player viewer, HoldingService holding) {
        return new DisposalContext(viewer, holding).resolver();
    }

    private static final class DisposalContext {

        private static final String DISPOSE = "dispose";
        private static final String DISPOSE_ALL = "dispose_all";

        private final Player viewer;
        private final HoldingService holding;

        private DisposalContext(Player viewer, HoldingService holding) {
            this.viewer = viewer;
            this.holding = holding;
        }

        private Function<String, String> resolver() {
            return builder(viewer).can(this::canFlag).build();
        }

        private @Nullable Boolean canFlag(String flag, @Nullable String arg) {
            return switch (flag) {
                case DISPOSE -> holding.count(viewer.getUniqueId(), HoldingKind.DISPOSAL) > 0;
                case DISPOSE_ALL -> holding.count(viewer.getUniqueId(), HoldingKind.DISPOSAL) > 0 || hasOwnedItems(viewer);
                default -> null;
            };
        }
    }

    // can:sell / can:sell_all
    public static Function<String, String> forSell(Player viewer, SellHandler sell, ItemStack[] selected, ItemStack[] all, boolean skipInvalidItems) {
        return new SellContext(viewer, sell, selected, all, skipInvalidItems).resolver();
    }

    private static final class SellContext {

        private static final String SELL = "sell";
        private static final String SELL_ALL = "sell_all";

        private final Player viewer;
        private final SellHandler sell;
        private final ItemStack[] selected;
        private final ItemStack[] all;
        private final boolean skipInvalidItems;

        private SellContext(Player viewer, SellHandler sell, ItemStack[] selected, ItemStack[] all, boolean skipInvalidItems) {
            this.viewer = viewer;
            this.sell = sell;
            this.selected = selected;
            this.all = all;
            this.skipInvalidItems = skipInvalidItems;
        }

        private Function<String, String> resolver() {
            return builder(viewer).can(this::canFlag).build();
        }

        private @Nullable Boolean canFlag(String flag, @Nullable String arg) {
            return switch (flag) {
                case SELL -> sell.previewBatch(viewer, selected, skipInvalidItems) instanceof SellHandler.Verdict.Sold;
                case SELL_ALL -> sell.previewBatch(viewer, all, skipInvalidItems) instanceof SellHandler.Verdict.Sold;
                default -> null;
            };
        }
    }

    private static boolean hasOwnedItems(Player viewer) {
        for (ItemStack item : viewer.getInventory().getStorageContents()) {
            if (item != null && !item.getType().isAir()) {
                return true;
            }
        }
        return false;
    }
}