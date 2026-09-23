package com.ftxeven.aircore.command.player.kits;

import com.ftxeven.aircore.command.AbstractCommand;
import com.ftxeven.aircore.command.ConfirmationFlow;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.core.command.CommandDispatch.Availability;
import com.ftxeven.aircore.core.gui.OpenOptions;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.gui.action.GuiActions;
import com.ftxeven.aircore.gui.render.GuiFlags;
import com.ftxeven.aircore.model.Kit;
import com.ftxeven.aircore.module.kits.KitClaimHandler;
import com.ftxeven.aircore.module.kits.KitClaimMessages;
import com.ftxeven.aircore.module.kits.KitsModule;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.service.Eligibility;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public final class KitCommand extends AbstractCommand {

    private static final String KEY = "kit";

    private final Supplier<KitsModule> kitsModule;
    private final PluginGuiManager guis;
    private final ConfirmationFlow confirmations;
    private final String othersPermission = Permissions.Command.others(KEY);
    private final String allPermission = Permissions.Command.all(KEY);

    public KitCommand(Context ctx, Supplier<KitsModule> kitsModule, PluginGuiManager guis) {
        super(ctx, KEY);
        this.kitsModule = kitsModule;
        this.guis = guis;
        this.confirmations = new ConfirmationFlow(guis);
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public int maxArgs(CommandSender sender) {
        return CommandDispatch.maxArgs(1, Availability.ofConfig(canTargetOthers(sender)));
    }

    @Override
    public String usage(CommandSender sender) {
        return CommandDispatch.usage(config(), canTargetOthers(sender));
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        if (args.length == 0) {
            openMainOrList(sender, args);
            return;
        }

        String kitName = args[0];
        Optional<Kit> resolved = kitsModule.get().find(kitName);
        if (resolved.isEmpty()) {
            messenger().send(sender, configs().lang().get("kits.errors.not-found"), Map.of("name", escapeUserInput(kitName)));
            return;
        }
        Kit kit = resolved.get();

        if (args.length == 1) {
            claimSelf(sender, kit, args);
            return;
        }

        String targetToken = args[1];

        if (selectors().isAll(targetToken)) {
            if (!checkPermission(sender, allPermission)) return;
            claimAll(sender, kit, args);
            return;
        }

        if (!checkPermission(sender, othersPermission)) return;

        if (sender instanceof Player player && resolver().matchesSelf(player, targetToken)) {
            claimSelf(sender, kit, args);
            return;
        }

        claimOther(sender, kit, targetToken, args);
    }

    // GUI resolution

    private Optional<String> resolveGui(String key, String[] args) {
        return config().gui(key, args).filter(guiId -> GuiActions.guiEnabled(guis.guis(), guiId));
    }

    // Listing / main menu

    private boolean canTargetOthers(CommandSender sender) {
        return sender.hasPermission(othersPermission) || sender.hasPermission(allPermission);
    }

    private void openMainOrList(CommandSender sender, String[] args) {
        Optional<String> menuGui = resolveGui("menu", args);
        if (sender instanceof Player player && menuGui.isPresent()) {
            Function<String, String> flags = GuiFlags.forKit(player, kitsModule.get(), null);
            guis.guis().open(player, menuGui.get(), new LinkedHashMap<>(), new OpenOptions(flags, Map.of(), OpenOptions.Kind.ENTRY, List.of()));
            return;
        }
        listKits(sender);
    }

    private void listKits(CommandSender sender) {
        List<Kit> visible = kitsModule.get().findAll().stream()
                .filter(kit -> kitsModule.get().canAccess(sender, kit))
                .toList();
        if (visible.isEmpty()) {
            messenger().send(sender, configs().lang().get("kits.list.empty"));
            return;
        }

        String availableEntry = configs().lang().get("kits.list.entry-available").getFirst();
        String unavailableEntry = configs().lang().get("kits.list.entry-unavailable").getFirst();
        String separator = configs().lang().get("kits.list.separator").getFirst();

        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < visible.size(); i++) {
            if (i > 0) {
                joined.append(separator);
            }
            Kit kit = visible.get(i);
            boolean available = !(sender instanceof Player player) || kitsModule.get().isAvailable(player, kit);
            joined.append((available ? availableEntry : unavailableEntry).replace("%name%", kit.name()));
        }

        messenger().send(sender, configs().lang().get("kits.list.header"), Map.of(
                "count", String.valueOf(visible.size()),
                "kits", joined.toString()
        ));
    }

    // Self-claim

    private void claimSelf(CommandSender sender, Kit kit, String[] args) {
        Optional<Player> resolved = requirePlayer(sender);
        if (resolved.isEmpty()) return;
        Player player = resolved.get();

        Optional<String> confirmGui = resolveGui("confirm", args);

        switch (kitsModule.get().checkSelfClaim(player, kit, confirmGui.isPresent())) {
            case KitsModule.SelfClaimAttempt.Eligible ignored ->
                    applyClaimedVerdict(player, kit, kitsModule.get().claim(player, kit), args);
            case KitsModule.SelfClaimAttempt.Denied(var denial) -> denial.send(player, configs(), messenger());
            case KitsModule.SelfClaimAttempt.NeedsConfirmation ignored -> confirmations.openGui(player, confirmGui.get(),
                    Map.of("id", kit.name()), KitClaimMessages.claimedPlaceholders(kit), GuiFlags.forKit(player, kitsModule.get(), kit));
        }
    }

    private void applyClaimedVerdict(Player player, Kit kit, KitClaimHandler.Verdict verdict, String[] args) {
        if (KitClaimMessages.applySelfVerdict(player, kit, verdict, configs(), messenger())) {
            completeCooldown(player, args);
        }
    }

    // Admin: single target

    private void claimOther(CommandSender sender, Kit kit, String targetToken, String[] args) {
        Optional<Player> target = requireFound(resolver().onlinePlayer(targetToken), sender, targetToken);
        if (target.isEmpty()) return;
        Player recipient = target.get();

        Scheduler.runEntity(recipient, () -> {
            KitClaimHandler.Verdict verdict = kitsModule.get().give(recipient, kit);
            if (KitClaimMessages.target(recipient, kit, verdict, services().players()) instanceof Eligibility.Denied denial) {
                denial.send(sender, configs(), messenger());
                return;
            }

            completeCooldown(sender, args);
            announceClaim(sender, recipient.getUniqueId(), false, kit);
            if (verdict instanceof KitClaimHandler.Verdict.Dropped) {
                messenger().send(recipient, configs().lang().get("kits.dropped"));
            }
        });
    }

    // Admin: selector target

    private void claimAll(CommandSender sender, Kit kit, String[] args) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            Scheduler.runEntity(online, () -> {
                KitClaimHandler.Verdict verdict = kitsModule.get().give(online, kit);
                if (verdict instanceof KitClaimHandler.Verdict.Claimed || verdict instanceof KitClaimHandler.Verdict.Dropped) {
                    notifyTarget(sender, online.getUniqueId(), configs().lang().get("kits.claim.by"), KitClaimMessages.claimedPlaceholders(kit));
                    if (verdict instanceof KitClaimHandler.Verdict.Dropped) {
                        messenger().send(online, configs().lang().get("kits.dropped"));
                    }
                }
            });
        }

        completeCooldown(sender, args);
        messenger().send(sender, configs().lang().get("kits.claim.all"), Map.of("name", kit.name()));
    }

    // Shared

    private void announceClaim(CommandSender sender, UUID targetUuid, boolean self, Kit kit) {
        announce(sender, targetUuid, self, "kits.claim.self", "kits.claim.other", "kits.claim.by", KitClaimMessages.claimedPlaceholders(kit));
    }
}