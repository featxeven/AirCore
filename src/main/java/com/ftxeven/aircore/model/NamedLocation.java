package com.ftxeven.aircore.model;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

// A single named position - a warp, or a spawn point
public record NamedLocation(String key, Position position, Instant createdAt, @Nullable UUID createdBy) {}