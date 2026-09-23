package com.ftxeven.aircore.model;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record Home(UUID owner, String name, Position position, @Nullable String icon, boolean favorite, Instant createdAt) {

    public Home withPosition(Position position) {
        return new Home(owner, name, position, icon, favorite, createdAt);
    }

    public Home withIcon(@Nullable String icon) {
        return new Home(owner, name, position, icon, favorite, createdAt);
    }

    public Home withFavorite(boolean favorite) {
        return new Home(owner, name, position, icon, favorite, createdAt);
    }
}