package com.ftxeven.aircore.module.chat.filter;

import com.ftxeven.aircore.module.NameValidator;
import com.ftxeven.aircore.module.chat.ChatConfig;
import com.ftxeven.aircore.permission.Permissions;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public final class ProfanityFilter {

    private static final String LANG_KEY = "chat.errors.filters.profanity";
    private static final Pattern PLAIN_WORD = Pattern.compile("^[\\p{L}0-9_]+$");

    private record Loaded(Path path, long lastModified, List<Pattern> words, @Nullable Pattern combined) {}

    private final JavaPlugin plugin;
    private final Logger logger;
    private final AtomicReference<Loaded> cache = new AtomicReference<>();

    public ProfanityFilter(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public FilterVerdict apply(ChatConfig.ProfanityFilter config, Player sender, String body) {
        if (!config.enabled() || bypassed(sender)) {
            return FilterVerdict.allow(body);
        }
        Loaded loaded = load(config.wordsFile());
        if (loaded.words().isEmpty()) {
            return FilterVerdict.allow(body);
        }
        if (config.action() == ChatConfig.FilterAction.BLOCK) {
            return matches(loaded, body) ? FilterVerdict.block(LANG_KEY) : FilterVerdict.allow(body);
        }
        String replaced = loaded.combined() != null
                ? mask(body, loaded.combined(), config.replacement())
                : maskEach(body, loaded.words(), config.replacement());
        if (replaced.equals(body)) {
            return FilterVerdict.allow(body);
        }
        return replaced.isBlank() ? FilterVerdict.block(LANG_KEY) : FilterVerdict.allow(replaced);
    }

    private boolean matches(Loaded loaded, String body) {
        if (loaded.combined() != null) {
            return loaded.combined().matcher(body).find();
        }
        for (Pattern word : loaded.words()) {
            if (word.matcher(body).find()) {
                return true;
            }
        }
        return false;
    }

    private String maskEach(String input, List<Pattern> words, String unit) {
        String result = input;
        for (Pattern word : words) {
            result = mask(result, word, unit);
        }
        return result;
    }

    private String mask(String input, Pattern word, String unit) {
        Matcher matcher = word.matcher(input);
        if (!matcher.find()) {
            return input;
        }
        StringBuilder result = new StringBuilder(input.length());
        int last = 0;
        do {
            result.append(input, last, matcher.start());
            int codepoints = matcher.group().codePointCount(0, matcher.group().length());
            result.append(unit.repeat(Math.max(1, codepoints)));
            last = matcher.end();
        } while (matcher.find());
        return result.append(input, last, input.length()).toString();
    }

    private Loaded load(String relativePath) {
        Path file = plugin.getDataFolder().toPath().resolve(relativePath);
        long lastModified = lastModifiedOrMinusOne(file);

        Loaded current = cache.get();
        if (current != null && current.path().equals(file) && current.lastModified() == lastModified) {
            return current;
        }

        List<Pattern> words = readWords(file);
        Loaded loaded = new Loaded(file, lastModified, words, combine(words));
        cache.set(loaded);
        return loaded;
    }

    private static @Nullable Pattern combine(List<Pattern> words) {
        if (words.isEmpty()) {
            return null;
        }
        String joined = words.stream().map(Pattern::pattern).collect(Collectors.joining("|"));
        try {
            return Pattern.compile(joined, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            return null; // fall back to per-word scanning
        }
    }

    public List<Pattern> words(String relativePath) {
        return load(relativePath).words();
    }

    public boolean containsExtendedProfanity(Permissible sender, boolean extendProfanityWords, String wordsFile, String text) {
        if (!extendProfanityWords || bypassed(sender)) {
            return false;
        }
        return NameValidator.matchesAny(words(wordsFile), text);
    }

    private List<Pattern> readWords(Path file) {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        List<Pattern> words = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file)) {
                String word = line.strip();
                if (word.isEmpty() || word.startsWith("#")) {
                    continue;
                }
                compile(word).ifPresent(words::add);
            }
        } catch (IOException e) {
            logger.warning("Could not read " + file + ": " + e.getMessage());
        }
        return words;
    }

    private Optional<Pattern> compile(String word) {
        try {
            String regex = PLAIN_WORD.matcher(word).matches() ? "\\b" + Pattern.quote(word) + "\\b" : word;
            return Optional.of(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
        } catch (PatternSyntaxException e) {
            logger.warning("Invalid entry '" + word + "' in profanity words file: " + e.getMessage());
            return Optional.empty();
        }
    }

    private long lastModifiedOrMinusOne(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return -1L;
        }
    }

    public void reload() {
        cache.set(null);
    }

    private boolean bypassed(Permissible sender) {
        return sender.hasPermission(Permissions.Bypass.filter("profanity"));
    }
}