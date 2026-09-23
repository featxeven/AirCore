package com.ftxeven.aircore.model;

import java.time.Instant;
import java.util.UUID;

public record Cooldown(UUID owner, CooldownScope scope, String key, String arg, Instant expiresAt) {}