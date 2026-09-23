package com.ftxeven.aircore.module.chat.displaytag;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DisplayTagClickRegistry {

    private record Entry(String tagKey, UUID ownerUuid, DisplayTagClickContext context, long expiresAtMillis) {}

    public sealed interface ClickResolution {
        record Valid(String tagKey, UUID ownerUuid, DisplayTagClickContext context) implements ClickResolution {}
        record Expired(String tagKey) implements ClickResolution {}
        record NotFound() implements ClickResolution {}
    }

    private static final int PURGE_THRESHOLD = 256;

    private final ConcurrentHashMap<String, Entry> tokens = new ConcurrentHashMap<>();

    public String mint(String tagKey, UUID ownerUuid, DisplayTagClickContext context, long ttlMillis) {
        purgeExpiredIfLarge();
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, new Entry(tagKey, ownerUuid, context, System.currentTimeMillis() + ttlMillis));
        return token;
    }

    public ClickResolution resolve(String token) {
        Entry entry = tokens.get(token);
        if (entry == null) {
            return new ClickResolution.NotFound();
        }
        if (System.currentTimeMillis() > entry.expiresAtMillis()) {
            tokens.remove(token);
            return new ClickResolution.Expired(entry.tagKey());
        }
        return new ClickResolution.Valid(entry.tagKey(), entry.ownerUuid(), entry.context());
    }

    private void purgeExpiredIfLarge() {
        if (tokens.size() < PURGE_THRESHOLD) {
            return;
        }
        long now = System.currentTimeMillis();
        tokens.values().removeIf(e -> now > e.expiresAtMillis());
    }
}