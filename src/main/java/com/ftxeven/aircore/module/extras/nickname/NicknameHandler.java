package com.ftxeven.aircore.module.extras.nickname;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.model.PlayerProfile;
import com.ftxeven.aircore.module.NameValidator;
import com.ftxeven.aircore.module.chat.PlayerInputSanitizer;
import com.ftxeven.aircore.module.chat.filter.ProfanityFilter;
import com.ftxeven.aircore.module.extras.ExtrasConfig;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.service.PlayerService;
import com.ftxeven.aircore.util.MiniText;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.permissions.Permissible;

import java.util.Optional;
import java.util.UUID;

public final class NicknameHandler {

    public sealed interface Verdict {
        record Allow(String nickname) implements Verdict {}
        record Invalid(NameValidator.Reason reason) implements Verdict {}
        record AlreadyTaken(String existingNickname) implements Verdict {}
        record Unchanged(String currentNickname) implements Verdict {}
    }

    public sealed interface FormatVerdict {
        record Valid(String tagged, String plain) implements FormatVerdict {}
        record Invalid(NameValidator.Reason reason, String tagged, String plain) implements FormatVerdict {}
    }

    public enum ResetVerdict { CLEARED, NOT_SET }

    private final ConfigManager configs;
    private final PlayerService players;
    private final ProfanityFilter profanity;

    public NicknameHandler(ConfigManager configs, PlayerService players, ProfanityFilter profanity) {
        this.configs = configs;
        this.players = players;
        this.profanity = profanity;
    }

    public Verdict set(UUID uuid, Permissible sender, String requested) {
        Verdict verdict = validate(uuid, sender, requested);
        if (verdict instanceof Verdict.Allow(String nickname)) {
            players.updateNickname(uuid, nickname);
        }
        return verdict;
    }

    public Verdict validate(UUID uuid, Permissible sender, String requested) {
        FormatVerdict formatVerdict = validateFormat(sender, requested);
        if (formatVerdict instanceof FormatVerdict.Invalid invalid) {
            return new Verdict.Invalid(invalid.reason());
        }
        return resolveUniqueness(uuid, (FormatVerdict.Valid) formatVerdict);
    }

    public FormatVerdict validateFormat(Permissible sender, String requested) {
        ExtrasConfig.Nicknames rules = configs.extras().nicknames();

        String sanitized = PlayerInputSanitizer.sanitize(requested, configs.main().formatting().messageFormat(), sender);

        String plain;
        try {
            plain = PlainTextComponentSerializer.plainText().serialize(MiniText.mini().deserialize(sanitized));
        } catch (Exception e) {
            return new FormatVerdict.Invalid(NameValidator.Reason.INVALID_FORMAT, sanitized.strip(), requested.strip());
        }

        NameValidator.Verdict plainVerdict = NameValidator.validate(sender, Permissions.Bypass.NICKNAME_BLACKLIST, plain,
                rules.maxLength(), rules.validationRegex(), rules.blacklist());
        if (plainVerdict instanceof NameValidator.Verdict.Reject(NameValidator.Reason reason)) {
            return new FormatVerdict.Invalid(reason, sanitized.strip(), plain);
        }

        String wordsFile = configs.chat().filters().profanity().wordsFile();
        if (profanity.containsExtendedProfanity(sender, rules.extendProfanityWords(), wordsFile, plain)) {
            return new FormatVerdict.Invalid(NameValidator.Reason.PROFANITY, sanitized.strip(), plain);
        }

        return new FormatVerdict.Valid(sanitized.strip(), plain);
    }

    public Verdict finishSet(UUID uuid, FormatVerdict.Valid validFormat) {
        Verdict verdict = resolveUniqueness(uuid, validFormat);
        if (verdict instanceof Verdict.Allow(String nickname)) {
            players.updateNickname(uuid, nickname);
        }
        return verdict;
    }

    private Verdict resolveUniqueness(UUID uuid, FormatVerdict.Valid validFormat) {
        String tagged = validFormat.tagged();
        String plain = validFormat.plain();

        Optional<PlayerProfile> existing = players.findByNickname(plain);
        if (existing.isPresent()) {
            String existingNickname = existing.get().nickname();
            if (!existing.get().uuid().equals(uuid)) {
                return new Verdict.AlreadyTaken(existingNickname);
            }
            if (tagged.equalsIgnoreCase(existingNickname)) {
                return new Verdict.Unchanged(existingNickname);
            }
        }

        return new Verdict.Allow(tagged);
    }

    public ResetVerdict reset(UUID uuid) {
        boolean hasNickname = players.find(uuid)
                .map(PlayerProfile::nickname)
                .filter(nickname -> !nickname.isEmpty())
                .isPresent();
        if (!hasNickname) {
            return ResetVerdict.NOT_SET;
        }
        players.updateNickname(uuid, null);
        return ResetVerdict.CLEARED;
    }

    public String langKeyFor(NameValidator.Reason reason) {
        return switch (reason) {
            case TOO_LONG -> "extras.nickname.errors.too-long";
            case INVALID_FORMAT, BLACKLISTED -> "extras.nickname.errors.invalid";
            case PROFANITY -> "extras.nickname.errors.profanity";
        };
    }
}