package com.ftxeven.aircore.command.player.extras;

import com.ftxeven.aircore.command.BaseCommand;
import com.ftxeven.aircore.command.player.PlayerTargetResolver;
import com.ftxeven.aircore.core.command.CommandDispatch;
import com.ftxeven.aircore.core.command.CommandDispatch.Availability;
import com.ftxeven.aircore.module.NameValidator;
import com.ftxeven.aircore.module.extras.nickname.NicknameHandler;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.command.CommandSender;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class NicknameCommand extends BaseCommand {

    private static final String KEY = "nickname";
    private static final String ACTION_RESET = "reset";
    private static final String OTHERS_PERMISSION = Permissions.Command.others(KEY);

    public NicknameCommand(Context ctx) {
        super(ctx, KEY);
    }

    @Override
    public String permission() { return Permissions.Command.of(KEY); }

    @Override
    public int minArgs() { return 1; }

    @Override
    public int maxArgs(CommandSender sender, String[] args) {
        boolean isReset = args.length > 0 && resolveAction(args[0]).filter(ACTION_RESET::equals).isPresent();
        int required = isReset ? 1 : 2;
        return CommandDispatch.maxArgs(required, Availability.ofPermission(sender, OTHERS_PERMISSION));
    }

    @Override
    public String usage(CommandSender sender) {
        return CommandDispatch.usage(config(), sender.hasPermission(OTHERS_PERMISSION));
    }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Optional<String> action = resolveAction(args[0]);
        if (action.isEmpty()) {
            sendUsageError(sender, label, subLabel);
            return;
        }
        if (action.get().equals("set")) {
            handleSet(sender, label, subLabel, args);
        } else {
            handleReset(sender, args);
        }
    }

    private NicknameHandler nicknames() {
        return modules().extras().nicknames();
    }

    private void handleSet(CommandSender sender, String label, String subLabel, String[] args) {
        if (args.length < 2) {
            sendUsageError(sender, label, subLabel);
            return;
        }
        String rawNick = args[1];
        String typedPlayer = args.length >= 3 ? args[2] : null;

        NicknameHandler.FormatVerdict formatVerdict = nicknames().validateFormat(sender, rawNick);
        if (formatVerdict instanceof NicknameHandler.FormatVerdict.Invalid(NameValidator.Reason reason, String tagged, String plain)) {
            sendRejection(sender, reason, tagged, plain);
            return;
        }
        NicknameHandler.FormatVerdict.Valid validFormat = (NicknameHandler.FormatVerdict.Valid) formatVerdict;

        resolveNicknameTarget(sender, typedPlayer, target -> {
            UUID targetUuid = target.uuid();
            switch (nicknames().finishSet(targetUuid, validFormat)) {
                case NicknameHandler.Verdict.AlreadyTaken(String existingNickname) ->
                        messenger().send(sender, configs().lang().get("extras.nickname.errors.already-taken"), Map.of("nickname", existingNickname));
                case NicknameHandler.Verdict.Unchanged(String currentNickname) -> sendUnchanged(sender, targetUuid, currentNickname);
                case NicknameHandler.Verdict.Allow(String nickname) -> {
                    completeCooldown(sender, args);
                    announce(sender, targetUuid, isSelf(sender, targetUuid),
                            "extras.nickname.set.self", "extras.nickname.set.other", "extras.nickname.set.by",
                            Map.of("nick", nickname));
                }
                case NicknameHandler.Verdict.Invalid ignored -> {
                }
            }
        });
    }

    private void handleReset(CommandSender sender, String[] args) {
        String typedPlayer = args.length >= 2 ? args[1] : null;

        resolveNicknameTarget(sender, typedPlayer, target -> {
            UUID targetUuid = target.uuid();
            if (nicknames().reset(targetUuid) == NicknameHandler.ResetVerdict.NOT_SET) {
                sendNoneSet(sender, targetUuid);
                return;
            }

            completeCooldown(sender, args);
            announce(sender, targetUuid, isSelf(sender, targetUuid),
                    "extras.nickname.reset.self", "extras.nickname.reset.other", "extras.nickname.reset.by",
                    Map.of());
        });
    }

    private void resolveNicknameTarget(CommandSender sender, String typedPlayer, Consumer<PlayerTargetResolver.Target> onFound) {
        if (typedPlayer == null) {
            requirePlayer(sender).flatMap(player -> resolver().online(player.getName())).ifPresent(onFound);
            return;
        }
        if (!checkPermission(sender, OTHERS_PERMISSION)) {
            return;
        }
        resolveTarget(sender, typedPlayer, onFound);
    }

    private void sendUnchanged(CommandSender sender, UUID targetUuid, String currentNickname) {
        if (isSelf(sender, targetUuid)) {
            messenger().send(sender, configs().lang().get("extras.nickname.errors.same"), Map.of("nickname", currentNickname));
        } else {
            messenger().send(sender, configs().lang().get("extras.nickname.errors.same-for"), targetPlaceholders(targetUuid, currentNickname));
        }
    }

    private Map<String, String> targetPlaceholders(UUID targetUuid, String nickname) {
        Map<String, String> placeholders = targetPlaceholders(targetUuid);
        placeholders.put("nickname", nickname);
        return placeholders;
    }

    private void sendRejection(CommandSender sender, NameValidator.Reason reason, String tagged, String plain) {
        String langKey = nicknames().langKeyFor(reason);
        Map<String, String> placeholders = switch (reason) {
            case TOO_LONG -> Map.of(
                    "length", String.valueOf(plain.length()),
                    "max", String.valueOf(configs().extras().nicknames().maxLength())
            );
            case INVALID_FORMAT, BLACKLISTED -> Map.of("name", tagged);
            case PROFANITY -> Map.of();
        };
        messenger().send(sender, configs().lang().get(langKey), placeholders);
    }

    private void sendNoneSet(CommandSender sender, UUID targetUuid) {
        if (isSelf(sender, targetUuid)) {
            messenger().send(sender, configs().lang().get("extras.nickname.errors.none-set"), Map.of());
        } else {
            messenger().send(sender, configs().lang().get("extras.nickname.errors.none-set-for"), targetPlaceholders(targetUuid));
        }
    }
}