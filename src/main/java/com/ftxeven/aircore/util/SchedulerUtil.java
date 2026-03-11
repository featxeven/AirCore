package com.ftxeven.aircore.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

public final class SchedulerUtil {

    private final JavaPlugin plugin;
    private final boolean folia;

    public SchedulerUtil(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
        this.folia = isFoliaPresent();
    }

    private boolean isFoliaPresent() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public boolean isFoliaServer() {
        return folia;
    }

    public interface CancellableTask {
        void cancel();
        boolean isCancelled();
    }

    public void cancelAll() {
        if (folia) {
            Bukkit.getAsyncScheduler().cancelTasks(plugin);
            Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        } else {
            Bukkit.getScheduler().cancelTasks(plugin);
        }
    }

    private CancellableTask wrap(BukkitTask task) {
        return new CancellableTask() {
            @Override public void cancel() { task.cancel(); }
            @Override public boolean isCancelled() { return task.isCancelled(); }
        };
    }

    private CancellableTask wrap(ScheduledTask task) {
        return new CancellableTask() {
            @Override public void cancel() { task.cancel(); }
            @Override public boolean isCancelled() { return task.isCancelled(); }
        };
    }

    private CancellableTask wrap(BukkitRunnable task) {
        return new CancellableTask() {
            @Override public void cancel() { task.cancel(); }
            @Override public boolean isCancelled() { return task.isCancelled(); }
        };
    }

    public void runTask(@NotNull Runnable task) {
        if (folia) {
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public @NotNull CancellableTask runDelayed(@NotNull Runnable task, long delayTicks) {
        if (folia) {
            return wrap(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), delayTicks));
        }
        return wrap(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
    }

    public @NotNull CancellableTask runTimer(@NotNull Runnable task, long delayTicks, long periodTicks) {
        if (folia) {
            return wrap(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), delayTicks, periodTicks));
        }
        return wrap(Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks));
    }

    public void runAsync(@NotNull Runnable task) {
        if (folia) {
            Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    public void runAsyncDelayed(@NotNull Runnable task, long delayTicks) {
        if (folia) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> Bukkit.getAsyncScheduler().runNow(plugin, asyncT -> task.run()), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
        }
    }

    public void runAsyncTimer(@NotNull Runnable task, long delayTicks, long periodTicks) {
        if (folia) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> Bukkit.getAsyncScheduler().runNow(plugin, asyncT -> task.run()), delayTicks, periodTicks);
        } else {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
        }
    }

    public @NotNull CancellableTask runEntityTask(@NotNull Entity entity, @NotNull Runnable task) {
        if (folia) {
            ScheduledTask scheduledTask = entity.getScheduler().run(plugin, t -> task.run(), null);
            return wrap(scheduledTask);
        }
        return wrap(Bukkit.getScheduler().runTask(plugin, task));
    }

    public @NotNull CancellableTask runEntityTaskDelayed(@NotNull Entity entity, @NotNull Runnable task, long delayTicks) {
        if (folia) {
            ScheduledTask scheduledTask = entity.getScheduler().runDelayed(plugin, t -> task.run(), null, delayTicks);
            return wrap(scheduledTask);
        }
        return wrap(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
    }

    public void runEntityTimer(@NotNull Entity entity, @NotNull java.util.function.Consumer<CancellableTask> taskConsumer, long delayTicks, long periodTicks) {
        if (folia) {
            entity.getScheduler().runAtFixedRate(plugin, t -> taskConsumer.accept(wrap(t)), null, delayTicks, periodTicks);
        } else {
            new BukkitRunnable() {
                private final CancellableTask wrapper = wrap(this);
                @Override public void run() { taskConsumer.accept(wrapper); }
            }.runTaskTimer(plugin, delayTicks, periodTicks);
        }
    }

    public void runLocationTask(@NotNull Location location, @NotNull Runnable task) {
        if (folia) {
            Bukkit.getRegionScheduler().run(plugin, location, t -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public @NotNull CancellableTask runLocationTaskDelayed(@NotNull Location location, @NotNull Runnable task, long delayTicks) {
        if (folia) {
            return wrap(Bukkit.getRegionScheduler().runDelayed(plugin, location, t -> task.run(), delayTicks));
        }
        return wrap(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
    }

    public @NotNull CancellableTask runLocationTimer(@NotNull Location location, @NotNull Runnable task, long delayTicks, long periodTicks) {
        if (folia) {
            return wrap(Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, t -> task.run(), delayTicks, periodTicks));
        }
        return wrap(Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks));
    }
}