package com.ftxeven.aircore.service.item;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.config.MainConfig;
import com.ftxeven.aircore.module.NameValidator;
import com.ftxeven.aircore.module.chat.PlayerInputSanitizer;
import com.ftxeven.aircore.module.chat.filter.ProfanityFilter;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.MiniText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.permissions.Permissible;

// Validates and renders the text players type for /itemname and /itemlore
public final class ItemTextService {

    public enum Field {
        NAME("utilities.itemname", Permissions.Bypass.ITEM_NAME_BLACKLIST),
        LORE("utilities.itemlore", Permissions.Bypass.ITEM_LORE_BLACKLIST);

        private final String langBase;
        private final String blacklistBypass;

        Field(String langBase, String blacklistBypass) {
            this.langBase = langBase;
            this.blacklistBypass = blacklistBypass;
        }

        public String langBase() {
            return langBase;
        }

        public String langKeyFor(NameValidator.Reason reason) {
            return langBase + ".errors." + switch (reason) {
                case TOO_LONG -> "too-long";
                case INVALID_FORMAT, BLACKLISTED -> "invalid";
                case PROFANITY -> "profanity";
            };
        }
    }

    public sealed interface Verdict {
        record Valid(Component component, String tagged, String plain) implements Verdict {}
        record Invalid(NameValidator.Reason reason, String tagged, String plain) implements Verdict {}
    }

    private final ConfigManager configs;
    private final ProfanityFilter profanity;

    public ItemTextService(ConfigManager configs, ProfanityFilter profanity) {
        this.configs = configs;
        this.profanity = profanity;
    }

    public MainConfig.ItemText rules(Field field) {
        MainConfig.UtilityDefaults defaults = configs.main().utilityDefaults();
        return switch (field) {
            case NAME -> defaults.itemName();
            case LORE -> defaults.itemLore().text();
        };
    }

    public Verdict validate(Permissible sender, Field field, String raw) {
        MainConfig.ItemText rules = rules(field);

        String tagged = PlayerInputSanitizer.sanitize(raw, configs.main().formatting().messageFormat(), sender).strip();

        Component component;
        String plain;
        try {
            component = MiniText.mini().deserialize(tagged);
            plain = PlainTextComponentSerializer.plainText().serialize(component);
        } catch (Exception e) {
            return new Verdict.Invalid(NameValidator.Reason.INVALID_FORMAT, tagged, raw.strip());
        }

        if (plain.isBlank()) {
            return new Verdict.Invalid(NameValidator.Reason.INVALID_FORMAT, tagged, plain);
        }

        NameValidator.Verdict plainVerdict = NameValidator.validate(sender, field.blacklistBypass, plain,
                rules.maxLength(), rules.validationRegex(), rules.blacklist());
        if (plainVerdict instanceof NameValidator.Verdict.Reject(NameValidator.Reason reason)) {
            return new Verdict.Invalid(reason, tagged, plain);
        }

        String wordsFile = configs.chat().filters().profanity().wordsFile();
        if (profanity.containsExtendedProfanity(sender, rules.extendProfanityWords(), wordsFile, plain)) {
            return new Verdict.Invalid(NameValidator.Reason.PROFANITY, tagged, plain);
        }

        Component styled = rules.italic()
                ? component
                : component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        return new Verdict.Valid(styled, tagged, plain);
    }
}