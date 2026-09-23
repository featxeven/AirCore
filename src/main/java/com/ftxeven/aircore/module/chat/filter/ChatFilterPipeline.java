package com.ftxeven.aircore.module.chat.filter;

import com.ftxeven.aircore.module.chat.ChatConfig;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class ChatFilterPipeline {

    @FunctionalInterface
    private interface Stage {
        FilterVerdict check(ChatConfig.Filters filters, Player sender, String body);
    }

    private record NamedStage(String name, Stage stage) {}

    public record FilterOutcome(FilterVerdict verdict, List<String> triggeredFilters) {
        public FilterOutcome { triggeredFilters = List.copyOf(triggeredFilters); }
    }

    private final Supplier<ChatConfig> config;
    private final UnicodeFilter unicode = new UnicodeFilter();
    private final CapsFilter caps = new CapsFilter();
    private final AdvertisingFilter advertising = new AdvertisingFilter();
    private final ProfanityFilter profanity;
    private final SpamFilter spam = new SpamFilter();
    private final List<NamedStage> stages;

    public ChatFilterPipeline(Supplier<ChatConfig> config, ProfanityFilter profanity) {
        this.config = config;
        this.profanity = profanity;
        this.stages = List.of(
                new NamedStage("unicode", (filters, sender, body) -> unicode.apply(filters.unicode(), sender, body)),
                new NamedStage("caps", (filters, sender, body) -> caps.apply(filters.caps(), sender, body)),
                new NamedStage("advertising", (filters, sender, body) -> advertising.apply(filters.advertising(), sender, body)),
                new NamedStage("profanity", (filters, sender, body) -> profanity.apply(filters.profanity(), sender, body)),
                new NamedStage("spam", (filters, sender, body) -> spam.apply(filters.spam(), sender, body))
        );
    }

    public FilterOutcome apply(Player sender, String rawBody) {
        ChatConfig.Filters filters = config.get().filters();
        String body = rawBody;
        List<String> triggered = new ArrayList<>(2);

        for (NamedStage stage : stages) {
            FilterVerdict verdict = stage.stage().check(filters, sender, body);
            if (verdict instanceof FilterVerdict.Block) {
                triggered.add(stage.name());
                return new FilterOutcome(verdict, triggered);
            }
            String next = ((FilterVerdict.Allow) verdict).body();
            if (!next.equals(body)) {
                triggered.add(stage.name());
            }
            body = next;
        }
        return new FilterOutcome(FilterVerdict.allow(body), triggered);
    }

    public void reload() {
        profanity.reload();
    }

    public void handleQuit(UUID uuid) {
        spam.handleQuit(uuid);
    }

    public ProfanityFilter profanity() {
        return profanity;
    }
}