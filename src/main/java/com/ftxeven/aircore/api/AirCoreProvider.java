package com.ftxeven.aircore.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public final class AirCoreProvider {
    private static AirCoreAPI instance = null;

    @NotNull
    public static AirCoreAPI get() {
        if (instance == null) {
            throw new IllegalStateException("AirCore API is not initialized! Is AirCore loaded?");
        }
        return instance;
    }

    @ApiStatus.Internal
    public static void register(@NotNull AirCoreAPI api) {
        AirCoreProvider.instance = api;
    }

    @ApiStatus.Internal
    public static void unregister() {
        AirCoreProvider.instance = null;
    }

    private AirCoreProvider() {}
}