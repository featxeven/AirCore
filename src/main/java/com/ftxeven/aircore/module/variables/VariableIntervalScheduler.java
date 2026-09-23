package com.ftxeven.aircore.module.variables;

import com.ftxeven.aircore.module.variables.VariableCatalog.Binding;
import com.ftxeven.aircore.util.Scheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.ArrayList;
import java.util.List;

final class VariableIntervalScheduler {

    private final VariableEngine engine;
    private final List<ScheduledTask> active = new ArrayList<>();

    VariableIntervalScheduler(VariableEngine engine) {
        this.engine = engine;
    }

    void start(Binding[] intervals) {
        for (Binding binding : intervals) {
            long periodTicks = binding.intervalSeconds() * 20L;
            active.add(Scheduler.runGlobalTimer(() -> engine.fireInterval(binding), periodTicks, periodTicks));
        }
    }

    void cancelAll() {
        for (ScheduledTask task : active) {
            task.cancel();
        }
        active.clear();
    }
}