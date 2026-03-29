package com.ftxeven.aircore.core.module.home.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.gui.GuiManager;
import com.ftxeven.aircore.core.gui.homes.HomeManager;
import com.ftxeven.aircore.core.gui.homes.HomeTargetManager;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Stream;

public final class HomeCommand implements TabExecutor {

    private final AirCore plugin;
    private final GuiManager guiManager;

    private static final String PERM_BASE = "aircore.command.home";
    private static final String PERM_OTHERS = "aircore.command.home.others";

    public HomeCommand(AirCore plugin, GuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players may use this command");
            return true;
        }

        if (!player.hasPermission(PERM_BASE)) {
            MessageUtil.send(player, "errors.no-permission", Map.of("permission", PERM_BASE));
            return true;
        }

        String otherSelector = plugin.commandConfig().getSelector("home", "player");
        boolean isAttemptingOthers = args.length > 0 && args[0].equalsIgnoreCase(otherSelector);

        if (isAttemptingOthers && player.hasPermission(PERM_OTHERS)) {
            if (args.length < 2) {
                MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", plugin.commandConfig().getUsage("home", "others", label)));
                return true;
            }

            OfflinePlayer target = resolve(player, args[1]);
            if (target == null) return true;

            String realName = plugin.database().records().getRealName(args[1]);

            if (args.length == 2) {
                if (target.getUniqueId().equals(player.getUniqueId())) {
                    guiManager.openGui("homes", player, Map.of("page", "1", "player", player.getName()));
                } else {
                    var targetHomes = plugin.home().homes().getHomes(target.getUniqueId());
                    if (targetHomes.isEmpty()) {
                        targetHomes = plugin.database().homes().load(target.getUniqueId());
                    }

                    var mgr = guiManager.getManager("homes-target");
                    boolean allowEmpty = (mgr instanceof HomeTargetManager htm) && htm.definition().config().getBoolean("allow-empty-gui", true);

                    if (targetHomes.isEmpty() && !allowEmpty) {
                        MessageUtil.send(player, "homes.errors.none-yet-for", Map.of("player", realName));
                        return true;
                    }

                    guiManager.openGui("homes-target", player, Map.of("player", realName, "page", "1"));
                }
                return true;
            }

            handleTeleport(player, target, args[2]);
            return true;
        }

        if (args.length == 0) {
            var mgr = guiManager.getManager("homes");
            if (mgr instanceof HomeManager guiHome && guiHome.isEnabled()) {
                var cachedHomes = plugin.home().homes().getHomes(player.getUniqueId());
                boolean allowEmpty = guiHome.definition().config().getBoolean("allow-empty-gui", true);

                if (cachedHomes.isEmpty() && !allowEmpty) {
                    MessageUtil.send(player, "homes.errors.none-yet", Map.of());
                    return true;
                }

                guiManager.openGui("homes", player, Map.of("page", "1", "player", player.getName()));
                return true;
            }
        }

        if (args.length > 1 && plugin.config().errorOnExcessArgs()) {
            MessageUtil.send(player, "errors.too-many-arguments", Map.of("usage", plugin.commandConfig().getUsage("home", label)));
            return true;
        }

        var homes = plugin.home().homes().getHomes(player.getUniqueId());
        if (homes.isEmpty()) {
            plugin.scheduler().runAsync(() -> {
                var loaded = plugin.database().homes().load(player.getUniqueId());
                plugin.home().homes().loadFromDatabase(player.getUniqueId(), loaded);
                var reloadedHomes = plugin.home().homes().getHomes(player.getUniqueId());
                plugin.scheduler().runEntityTask(player, () -> processSelfTeleport(player, reloadedHomes, args, label));
            });
            return true;
        }

        processSelfTeleport(player, homes, args, label);
        return true;
    }

    private void processSelfTeleport(Player player, Map<String, Location> homes, String[] args, String label) {
        if (homes.isEmpty()) {
            MessageUtil.send(player, "homes.errors.none-yet", Map.of());
            return;
        }

        String homeName;
        if (args.length == 0) {
            if (homes.size() == 1) {
                homeName = homes.keySet().iterator().next();
            } else {
                MessageUtil.send(player, "errors.incorrect-usage", Map.of("usage", plugin.commandConfig().getUsage("home", label)));
                return;
            }
        } else {
            homeName = args[0].toLowerCase();
        }

        handleTeleport(player, player, homeName);
    }

    private void handleTeleport(Player player, OfflinePlayer target, String homeName) {
        UUID uuid = target.getUniqueId();
        String nameLower = homeName.toLowerCase();
        String realName = plugin.database().records().getRealName(target.getName() != null ? target.getName() : "");

        plugin.scheduler().runAsync(() -> {
            var homes = plugin.home().homes().getHomes(uuid);
            if (homes.isEmpty()) {
                var loaded = plugin.database().homes().load(uuid);
                plugin.home().homes().loadFromDatabase(uuid, loaded);
                homes = plugin.home().homes().getHomes(uuid);
            }

            final Map<String, Location> finalHomes = homes;
            plugin.scheduler().runEntityTask(player, () -> {
                if (!finalHomes.containsKey(nameLower)) {
                    String msgKey = uuid.equals(player.getUniqueId()) ? "homes.errors.not-found" : "homes.errors.not-found-for";
                    MessageUtil.send(player, msgKey, Map.of("player", realName, "name", homeName));
                    return;
                }

                Location loc = finalHomes.get(nameLower);

                plugin.core().teleports().startCountdown(player, player, () -> {
                    plugin.core().teleports().teleport(player, loc);
                    String successKey = uuid.equals(player.getUniqueId()) ? "homes.teleport.success" : "homes.teleport.success-other";
                    MessageUtil.send(player, successKey, Map.of("player", realName, "name", homeName));
                }, reason -> {
                    String cancelKey = uuid.equals(player.getUniqueId()) ? "homes.teleport.cancelled" : "homes.teleport.cancelled-other";
                    MessageUtil.send(player, cancelKey, Map.of("player", realName, "name", homeName));
                });
            });
        });
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();
        String input = args[args.length - 1].toLowerCase();
        String otherSelector = plugin.commandConfig().getSelector("home", "player");

        if (args.length == 1) {
            Stream<String> homes = plugin.home().homes().getHomes(player.getUniqueId()).keySet().stream();
            if (player.hasPermission(PERM_OTHERS)) {
                homes = Stream.concat(homes, Stream.of(otherSelector));
            }
            return homes.filter(n -> n.toLowerCase().startsWith(input)).toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase(otherSelector) && player.hasPermission(PERM_OTHERS)) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase().startsWith(input)).toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase(otherSelector) && player.hasPermission(PERM_OTHERS)) {
            return getHomeCompletions(args[1], input);
        }

        return Collections.emptyList();
    }

    private List<String> getHomeCompletions(String targetName, String input) {
        UUID id = plugin.database().records().uuidFromName(targetName);
        if (id == null) return Collections.emptyList();
        return plugin.home().homes().getHomes(id).keySet().stream().filter(n -> n.toLowerCase().startsWith(input)).toList();
    }

    private OfflinePlayer resolve(Player sender, String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online;

        UUID uuid = plugin.database().records().uuidFromName(name);
        if (uuid != null) return Bukkit.getOfflinePlayer(uuid);

        MessageUtil.send(sender, "errors.player-never-joined", Map.of());
        return null;
    }
}