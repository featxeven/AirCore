package com.ftxeven.aircore.core.animation;

import com.ftxeven.aircore.util.Scheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;

public final class AnimationManager {

    private final Supplier<Map<String, Animation>> animations;
    private final JavaPlugin plugin;
    private final AtomicLong tick = new AtomicLong();
    private ScheduledTask task;

    public AnimationManager(JavaPlugin plugin, Supplier<Map<String, Animation>> animations) {
        this.plugin = plugin;
        this.animations = animations;
    }

    public void start() {
        task = Scheduler.runGlobalTimer(tick::incrementAndGet, 1, 1);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public long currentTick() {
        return tick.get();
    }

    public boolean has(String key) {
        return animations.get().containsKey(key);
    }

    public boolean isAnimated(String text) {
        return AnimationTag.PATTERN.matcher(text).find();
    }

    public String resolve(String text, long startTick) {
        Matcher matcher = AnimationTag.PATTERN.matcher(text);
        StringBuilder out = new StringBuilder();
        long now = currentTick();

        while (matcher.find()) {
            String key = matcher.group(1);
            Animation animation = animations.get().get(key);
            String replacement = animation == null
                    ? matcher.group()
                    : frame(key, animation, now, startTick, matcher.group(2));
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public String resolve(String text) {
        return resolve(text, 0);
    }

    public List<String> resolve(List<String> lines, long startTick) {
        List<String> resolved = new ArrayList<>(lines.size());
        for (String line : lines) {
            resolved.add(resolve(line, startTick));
        }
        return resolved;
    }

    public long duration(String text) {
        Matcher matcher = AnimationTag.PATTERN.matcher(text);
        long longest = 0;
        while (matcher.find()) {
            String loopsGroup = matcher.group(2);
            if (loopsGroup == null) {
                continue;
            }
            Animation animation = animations.get().get(matcher.group(1));
            if (animation == null || animation.frames().size() == 1) {
                continue;
            }
            long ticks = Integer.parseInt(loopsGroup) * animation.mode().cycleLength(animation.frames().size()) * animation.interval();
            longest = Math.max(longest, ticks);
        }
        return longest;
    }

    public int minInterval(String text) {
        Matcher matcher = AnimationTag.PATTERN.matcher(text);
        int smallest = -1;
        while (matcher.find()) {
            Animation animation = animations.get().get(matcher.group(1));
            if (animation == null || animation.frames().size() == 1) {
                continue;
            }
            smallest = smallest < 0 ? animation.interval() : Math.min(smallest, animation.interval());
        }
        return smallest;
    }

    public String ensureLoopCount(String text, String context) {
        Matcher matcher = AnimationTag.PATTERN.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        matcher.reset();
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            result.append(text, last, matcher.start());
            if (matcher.group(2) == null) {
                plugin.getLogger().warning("Animation tag " + matcher.group() + " used in " + context + " without a loop count, defaulting to 1");
                result.append("<anim:").append(matcher.group(1)).append(":1>");
            } else {
                result.append(matcher.group());
            }
            last = matcher.end();
        }
        result.append(text, last, text.length());
        return result.toString();
    }

    // redraws once per tick for a fixed span, then calls onComplete and stops
    public ScheduledTask scheduleRedrawLoop(CommandSender target, long refStart, long durationTicks,
                                            LongConsumer onTick, Runnable onComplete) {
        AtomicReference<ScheduledTask> holder = new AtomicReference<>();
        AtomicBoolean finished = new AtomicBoolean();

        ScheduledTask task = Scheduler.runTargetAwareTimer(target, () -> {
            long elapsed = currentTick() - refStart;
            if (elapsed >= durationTicks) {
                if (finished.compareAndSet(false, true)) {
                    onComplete.run();
                }
                ScheduledTask running = holder.getAndSet(null);
                if (running != null) {
                    running.cancel();
                }
                return;
            }
            onTick.accept(elapsed);
        }, 0, 1);

        if (task == null) {
            return null; // target is gone; nothing was scheduled
        }
        holder.set(task);
        if (finished.get()) {
            task.cancel(); // completed before its recorded
        }
        return task;
    }

    // redraws once per tick forever, until the caller cancels the returned task
    public ScheduledTask scheduleInfiniteRedrawLoop(CommandSender target, long refStart, LongConsumer onTick) {
        return Scheduler.runTargetAwareTimer(target, () -> onTick.accept(currentTick() - refStart), 0, 1);
    }

    private String frame(String key, Animation animation, long now, long startTick, String loopsGroup) {
        List<String> frames = animation.frames();
        if (frames.size() == 1) {
            return frames.getFirst();
        }

        Animation.Mode mode = animation.mode();
        long step = Math.max(0, now - startTick) / animation.interval();

        if (loopsGroup != null && step >= Integer.parseInt(loopsGroup) * mode.cycleLength(frames.size())) {
            return frames.get(mode.settledIndex(frames.size()));
        }

        return frames.get(mode.frameIndex(key, step, frames.size()));
    }
}