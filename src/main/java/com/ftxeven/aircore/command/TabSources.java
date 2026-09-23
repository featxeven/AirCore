package com.ftxeven.aircore.command;

import com.ftxeven.aircore.command.player.PlayerTargetResolver;
import com.ftxeven.aircore.command.player.Selectors;
import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.core.command.DurationUnits;
import com.ftxeven.aircore.core.command.tabcomplete.TabSourceRegistry;
import com.ftxeven.aircore.model.Home;
import com.ftxeven.aircore.module.extras.ExtrasModule;
import com.ftxeven.aircore.module.homes.HomesModule;
import com.ftxeven.aircore.module.kits.KitsModule;
import com.ftxeven.aircore.module.teleport.TeleportModule;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

public final class TabSources {

    private enum ItemScope {
        ACCESSIBLE, ALL;

        static Optional<ItemScope> parse(String param) {
            if ("accessible".equalsIgnoreCase(param)) return Optional.of(ACCESSIBLE);
            if ("all".equalsIgnoreCase(param)) return Optional.of(ALL);
            return Optional.empty();
        }
    }

    private static final List<String> RELATIVE_COORDINATE = List.of("~");
    private static final double TARGET_BLOCK_MAX_DISTANCE = 5.0D;

    private TabSources() {
    }

    public static TabSourceRegistry build(ConfigManager configs, DurationUnits durationUnits, Selectors selectors,
                                          PlayerTargetResolver resolver, Supplier<ExtrasModule> extrasModule,
                                          Supplier<KitsModule> kitsModule, Supplier<TeleportModule> teleportModule,
                                          Supplier<HomesModule> homesModule, Consumer<String> warn) {
        return TabSourceRegistry.withBuiltins(durationUnits)
                .register("SELECTOR", (context, param) -> selectors.token(param).map(List::of).orElse(List.of()))
                .register("WORLD_IDS", (context, param) -> Bukkit.getWorlds().stream().map(World::getName).toList())
                .register("CHANNEL_IDS", (context, param) -> configs.chat().channels().joinableBy(context.sender()))
                .register("KIT_IDS", (context, param) -> resolveScoped("KIT_IDS", param, warn,
                        () -> kitsModule.get().accessibleKitNames(context.sender()),
                        () -> kitsModule.get().allKitNames()))
                .register("WARP_IDS", (context, param) -> resolveScoped("WARP_IDS", param, warn,
                        () -> teleportModule.get().accessibleWarpNames(context.sender()),
                        () -> teleportModule.get().allWarpNames()))
                .register("HOME_IDS", (context, param) -> homeIds(context.sender(), param, homesModule, resolver))
                .register("BLOCKED_PLAYERS", (context, param) -> {
                    if (!(context.sender() instanceof Player viewer)) {
                        return List.of();
                    }
                    return extrasModule.get().blocks().blockedPlayerNames(viewer.getUniqueId(), param);
                })
                .register("COORDS_X", (context, param) -> coordinateSuggestions(context.sender(), Block::getX))
                .register("COORDS_Y", (context, param) -> coordinateSuggestions(context.sender(), Block::getY))
                .register("COORDS_Z", (context, param) -> coordinateSuggestions(context.sender(), Block::getZ));
    }

    private static List<String> coordinateSuggestions(CommandSender sender, ToIntFunction<Block> axis) {
        if (!(sender instanceof Player player)) {
            return RELATIVE_COORDINATE;
        }
        return targetedBlock(player)
                .map(block -> List.of(String.valueOf(axis.applyAsInt(block))))
                .orElse(RELATIVE_COORDINATE);
    }

    private static Optional<Block> targetedBlock(Player player) {
        return Optional.ofNullable(player.rayTraceBlocks(TARGET_BLOCK_MAX_DISTANCE, FluidCollisionMode.NEVER))
                .map(RayTraceResult::getHitBlock);
    }

    private static List<String> resolveScoped(String sourceName, String param, Consumer<String> warn,
                                              Supplier<List<String>> accessible, Supplier<List<String>> all) {
        Optional<ItemScope> scope = ItemScope.parse(param);
        if (scope.isEmpty()) {
            warn.accept("Tab-complete source '" + sourceName + "' requires an explicit scope of "
                    + "':accessible' or ':all' (got '" + param + "') - no suggestions will be offered "
                    + "for this argument until commands.yml is corrected.");
            return List.of();
        }
        return scope.get() == ItemScope.ALL ? all.get() : accessible.get();
    }

    private static List<String> homeIds(CommandSender sender, String param, Supplier<HomesModule> homesModule,
                                        PlayerTargetResolver resolver) {
        if (param == null || param.isBlank()) {
            return sender instanceof Player player
                    ? homeNamesOf(homesModule.get(), player.getUniqueId())
                    : List.of();
        }
        return resolver.offline(param)
                .map(target -> homeNamesOf(homesModule.get(), target.uuid()))
                .orElse(List.of());
    }

    private static List<String> homeNamesOf(HomesModule homesModule, UUID owner) {
        return homesModule.findAll(owner).stream().map(Home::name).toList();
    }
}