package com.ftxeven.aircore.module.extras.afk;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

record AfkSession(Instant since, @Nullable String reason, boolean auto, List<ScheduledTask> scheduledActions) {
}