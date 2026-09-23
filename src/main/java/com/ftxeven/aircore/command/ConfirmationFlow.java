package com.ftxeven.aircore.command;

import com.ftxeven.aircore.command.AbstractCommand.Context;
import com.ftxeven.aircore.core.gui.OpenOptions;
import com.ftxeven.aircore.gui.PluginGuiManager;
import com.ftxeven.aircore.gui.action.GuiActions;
import com.ftxeven.aircore.util.TimeFormatter;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ConfirmationFlow {

    private final PluginGuiManager guis;

    public ConfirmationFlow(PluginGuiManager guis) {
        this.guis = guis;
    }

    public void openGui(Player viewer, String guiId, Map<String, Object> attributes,
                        Map<String, String> placeholders, Function<String, String> flags) {
        guis.guis().open(viewer, guiId, new LinkedHashMap<>(placeholders),
                new OpenOptions(flags, attributes, OpenOptions.Kind.ENTRY, List.of()));
    }

    private boolean tryOpenGui(Player viewer, Optional<String> guiId, Map<String, Object> attributes,
                               Map<String, String> placeholders, Function<String, String> flags) {
        if (guiId.isEmpty() || !GuiActions.guiEnabled(guis.guis(), guiId.get())) {
            return false;
        }
        openGui(viewer, guiId.get(), attributes, placeholders, flags);
        return true;
    }

    public void guiOnly(Player viewer, Optional<String> guiId, Map<String, Object> attributes,
                        Map<String, String> placeholders, Function<String, String> flags, Runnable proceed) {
        if (!tryOpenGui(viewer, guiId, attributes, placeholders, flags)) {
            proceed.run();
        }
    }

    public <K> void gated(Context ctx, Player sender, String confirmationType, K matchKey, boolean required,
                          Supplier<Optional<Runnable>> onPreviewBlocked,
                          Optional<String> guiId, Map<String, Object> attributes, Map<String, String> placeholders,
                          Function<String, String> flags, int timeoutSeconds,
                          String requestLangKey, String expiredLangKey, Runnable proceed) {
        if (!required) {
            proceed.run();
            return;
        }

        Optional<K> pending = ctx.services().confirmations().confirm(sender.getUniqueId(), confirmationType);
        if (pending.isPresent() && pending.get().equals(matchKey)) {
            proceed.run();
            return;
        }

        Optional<Runnable> blocked = onPreviewBlocked.get();
        if (blocked.isPresent()) {
            blocked.get().run();
            return;
        }

        if (tryOpenGui(sender, guiId, attributes, placeholders, flags)) {
            return;
        }

        ctx.services().confirmations().request(sender, confirmationType, timeoutSeconds, matchKey,
                () -> ctx.messenger().send(sender, ctx.configs().lang().get(expiredLangKey), placeholders));

        Map<String, String> withTimeout = new LinkedHashMap<>(placeholders);
        withTimeout.put("timeout", TimeFormatter.duration(Duration.ofSeconds(timeoutSeconds), ctx.configs().main().formatting(), ctx.configs().lang()));
        ctx.messenger().send(sender, ctx.configs().lang().get(requestLangKey), withTimeout);
    }
}