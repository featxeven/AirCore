package com.ftxeven.aircore.command.player.utilities;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.service.item.ItemTextService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * The shape shared by /itemname and /itemlore
 */
public abstract class ItemTextCommand extends BaseCommand {

    private final String key;
    private final ItemTextService.Field field;

    protected ItemTextCommand(Context ctx, String key, ItemTextService.Field field) {
        super(ctx, key);
        this.key = key;
        this.field = field;
    }

    @Override
    public String permission() { return Permissions.Command.of(key); }

    @Override
    public boolean playerOnly() { return true; }

    // Arguments

    // the config key of the typed action (e.g. "set"), or "" when it isn't one
    protected final String actionOf(String[] args) {
        return args.length == 0 ? "" : resolveAction(args[0]).orElse("");
    }

    protected static String joinFrom(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    // Held item

    protected final Optional<ItemStack> requireHeldItem(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            reply(player, "errors.no-item", Map.of());
            return Optional.empty();
        }
        return Optional.of(held);
    }

    // Player text

    // empty once the player has been told why the text was rejected
    protected final Optional<ItemTextService.Verdict.Valid> validated(Player player, String text) {
        return switch (itemText().validate(player, field, text)) {
            case ItemTextService.Verdict.Valid valid -> Optional.of(valid);
            case ItemTextService.Verdict.Invalid invalid -> {
                sendRejection(player, invalid);
                yield Optional.empty();
            }
        };
    }

    private void sendRejection(Player player, ItemTextService.Verdict.Invalid invalid) {
        Map<String, String> placeholders = switch (invalid.reason()) {
            case TOO_LONG -> Map.of(
                    "length", String.valueOf(invalid.plain().length()),
                    "max", String.valueOf(itemText().rules(field).maxLength())
            );
            case INVALID_FORMAT, BLACKLISTED -> Map.of("text", invalid.tagged());
            case PROFANITY -> Map.of();
        };
        messenger().send(player, configs().lang().get(field.langKeyFor(invalid.reason())), placeholders);
    }

    // Messages

    // sends utilities.<command>.<suffix>
    protected final void reply(Player player, String suffix, Map<String, String> placeholders) {
        messenger().send(player, configs().lang().get(field.langBase() + "." + suffix), placeholders);
    }

    private ItemTextService itemText() {
        return services().itemText();
    }
}