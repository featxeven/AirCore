package com.ftxeven.aircore.model;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record Variable(@Nullable UUID owner, String key, String value, @Nullable Double score) {

    public Variable(@Nullable UUID owner, String key, String value) {
        this(owner, key, value, null);
    }
}