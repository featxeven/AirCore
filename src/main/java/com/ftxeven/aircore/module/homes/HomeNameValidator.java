package com.ftxeven.aircore.module.homes;

import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.module.NameValidator;
import com.ftxeven.aircore.module.chat.ChatModule;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.permissions.Permissible;

import java.util.function.Supplier;

public final class HomeNameValidator {

    private final ConfigManager configs;
    private final Supplier<ChatModule> chatModule;

    public HomeNameValidator(ConfigManager configs, Supplier<ChatModule> chatModule) {
        this.configs = configs;
        this.chatModule = chatModule;
    }

    public NameValidator.Verdict validate(Permissible sender, String requested) {
        HomesConfig.Naming rules = configs.homes().naming();
        NameValidator.Verdict verdict = NameValidator.validate(sender, Permissions.Bypass.HOME_BLACKLIST, requested,
                rules.maxLength(), rules.validationRegex(), rules.blacklist());
        if (verdict instanceof NameValidator.Verdict.Reject) {
            return verdict;
        }

        if (containsProfanity(sender, rules.extendProfanityWords(), requested)) {
            return new NameValidator.Verdict.Reject(NameValidator.Reason.PROFANITY);
        }
        return verdict;
    }

    public String langKeyFor(NameValidator.Reason reason) {
        return switch (reason) {
            case TOO_LONG -> "homes.errors.name-too-long";
            case INVALID_FORMAT, BLACKLISTED -> "homes.errors.invalid-name";
            case PROFANITY -> "homes.errors.profanity";
        };
    }

    private boolean containsProfanity(Permissible sender, boolean extendProfanityWords, String requested) {
        ChatModule chat = chatModule.get();
        if (chat == null) {
            return false;
        }
        String wordsFile = configs.chat().filters().profanity().wordsFile();
        return chat.filters().profanity().containsExtendedProfanity(sender, extendProfanityWords, wordsFile, requested);
    }
}