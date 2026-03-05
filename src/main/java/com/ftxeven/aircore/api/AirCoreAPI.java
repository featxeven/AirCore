package com.ftxeven.aircore.api;

import com.ftxeven.aircore.api.manager.BlockManager;
import org.jetbrains.annotations.NotNull;

public interface AirCoreAPI {
    @NotNull BlockManager blocks();
}