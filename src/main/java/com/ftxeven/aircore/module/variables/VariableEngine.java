package com.ftxeven.aircore.module.variables;

import com.ftxeven.aircore.core.command.CommandExecution;
import com.ftxeven.aircore.core.condition.ConditionEvaluator;
import com.ftxeven.aircore.core.condition.ExprEvaluator;
import com.ftxeven.aircore.database.cache.VariableBucket;
import com.ftxeven.aircore.database.cache.VariableStore;
import com.ftxeven.aircore.model.Variable;
import com.ftxeven.aircore.module.announcements.AnnouncementsModule;
import com.ftxeven.aircore.module.variables.VariableCatalog.Binding;
import com.ftxeven.aircore.module.variables.VariableCatalog.EventHook;
import com.ftxeven.aircore.module.variables.VariableCatalog.Spec;
import com.ftxeven.aircore.module.variables.VariablesConfig.Hook;
import com.ftxeven.aircore.module.variables.VariablesConfig.OtherVariableWrite;
import com.ftxeven.aircore.module.variables.VariablesConfig.VariableHookEntry;
import com.ftxeven.aircore.module.variables.VariablesModule.WriteResult;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.Placeholders;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

final class VariableEngine {

    // guards against hooks that write each other forever
    private static final int MAX_DEPTH = 8;

    private final Logger logger;
    private final VariableStore store;
    private final Messenger messenger;
    private final AnnouncementsModule announcements;
    private final ConditionEvaluator conditions;

    private volatile VariableCatalog catalog = VariableCatalog.EMPTY;

    VariableEngine(Logger logger, VariableStore store, Messenger messenger, AnnouncementsModule announcements) {
        this.logger = logger;
        this.store = store;
        this.messenger = messenger;
        this.announcements = announcements;
        this.conditions = new ConditionEvaluator(logger::warning);
    }

    VariableCatalog catalog() {
        return catalog;
    }

    void catalog(VariableCatalog catalog) {
        this.catalog = catalog;
    }

    // ranking column projection used by the store when flushing
    @Nullable Double numeric(String key, String value) {
        Spec spec = catalog.spec(key);
        if (spec == null || !spec.isNumeric()) {
            return null;
        }
        double parsed = ExprEvaluator.parseNumber(value);
        return Double.isFinite(parsed) ? parsed : null;
    }

    // Reads

    private @Nullable VariableBucket bucketFor(Spec spec, @Nullable UUID owner) {
        if (!spec.isPlayer()) {
            return store.global();
        }
        return owner == null ? null : store.resident(owner);
    }

    private String read(Spec spec, @Nullable UUID owner) {
        VariableBucket bucket = bucketFor(spec, owner);
        String value = bucket != null ? bucket.get(spec.key()) : null;
        return value != null ? value : spec.defaultValue();
    }

    // empty when the variable is unknown or the owner's data isn't resident
    Optional<String> get(String key, @Nullable UUID owner) {
        Spec spec = catalog.spec(key);
        if (spec == null) {
            return Optional.empty();
        }
        VariableBucket bucket = bucketFor(spec, owner);
        return bucket == null ? Optional.empty() : Optional.of(orDefault(bucket, spec));
    }

    CompletableFuture<Optional<String>> getAsync(String key, UUID owner) {
        Spec spec = catalog.spec(key);
        if (spec == null || !spec.isPlayer()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return store.acquire(owner).thenApply(bucket -> Optional.of(orDefault(bucket, spec)));
    }

    private static String orDefault(VariableBucket bucket, Spec spec) {
        String value = bucket.get(spec.key());
        return value != null ? value : spec.defaultValue();
    }

    List<Variable> listGlobal() {
        return toVariables(null, store.global());
    }

    List<Variable> listForPlayer(UUID owner) {
        VariableBucket bucket = store.resident(owner);
        return bucket == null ? List.of() : toVariables(owner, bucket);
    }

    CompletableFuture<List<Variable>> listAsync(UUID owner) {
        return store.acquire(owner).thenApply(bucket -> toVariables(owner, bucket));
    }

    private static List<Variable> toVariables(@Nullable UUID owner, VariableBucket bucket) {
        List<Variable> result = new ArrayList<>();
        bucket.snapshot().forEach((key, value) -> result.add(new Variable(owner, key, value)));
        return result;
    }

    // PlaceholderAPI bridge

    @Nullable String resolve(@Nullable OfflinePlayer viewer, String params) {
        VariableCatalog catalog = this.catalog;
        Spec direct = catalog.spec(params);
        if (direct != null) {
            if (!direct.isPlayer()) {
                return read(direct, null);
            }
            return viewer == null ? null : read(direct, viewer.getUniqueId());
        }

        // keys may contain underscores, so the longest player-scoped prefix wins
        Spec best = null;
        int cut = -1;
        for (int i = params.indexOf('_'); i >= 0; i = params.indexOf('_', i + 1)) {
            Spec candidate = catalog.spec(params.substring(0, i));
            if (candidate != null && candidate.isPlayer()) {
                best = candidate;
                cut = i;
            }
        }
        if (best == null || cut + 1 >= params.length()) {
            return null;
        }
        UUID target = uuidOf(params.substring(cut + 1));
        if (target == null) {
            return null;
        }
        VariableBucket bucket = store.resident(target);
        if (bucket == null) {
            store.acquire(target); // prefetch
            return best.defaultValue();
        }
        return orDefault(bucket, best);
    }

    private @Nullable UUID uuidOf(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached != null ? cached.getUniqueId() : null;
    }

    // Writes

    CompletableFuture<WriteResult> write(String key, @Nullable UUID owner, VariableWrite op) {
        Spec spec = catalog.spec(key);
        if (spec == null) {
            logger.warning("Attempted to write unknown variable '" + key + "'");
            return CompletableFuture.completedFuture(WriteResult.rejected("unknown variable '" + key + "'"));
        }
        return submit(spec, owner, op, null, Map.of(), 0);
    }

    // Accept/reject is decided synchronously
    private CompletableFuture<WriteResult> submit(Spec spec, @Nullable UUID owner, VariableWrite op,
                                                  @Nullable CommandSender target, Map<String, String> inherited, int depth) {
        if (spec.isPlayer() != (owner != null)) {
            return rejected(spec, spec.isPlayer() ? "it requires a player owner" : "it is global and takes no owner");
        }
        VariableMutator.Plan plan = VariableMutator.plan(spec, op);
        if (!plan.accepted()) {
            return rejected(spec, plan.rejection());
        }

        VariableBucket bucket = bucketFor(spec, owner);
        if (bucket != null) {
            return CompletableFuture.completedFuture(apply(spec, bucket, owner, op, plan, target, inherited, depth));
        }
        return store.acquire(owner).handle((loaded, failure) -> {
            if (failure != null) {
                logger.log(Level.WARNING, "Could not load variable data for " + owner + ", write to '" + spec.key() + "' dropped", failure);
                return WriteResult.rejected("could not load data for " + owner);
            }
            return apply(spec, loaded, owner, op, plan, target, inherited, depth);
        });
    }

    private CompletableFuture<WriteResult> rejected(Spec spec, @Nullable String reason) {
        logger.warning("Rejected write to variable '" + spec.key() + "': " + reason);
        return CompletableFuture.completedFuture(WriteResult.rejected(reason));
    }

    private WriteResult apply(Spec spec, VariableBucket bucket, @Nullable UUID owner, VariableWrite op, VariableMutator.Plan plan,
                              @Nullable CommandSender target, Map<String, String> inherited, int depth) {
        VariableMutator.Change[] change = new VariableMutator.Change[1];
        String stored = bucket.update(spec.key(), current -> {
            change[0] = plan.operation().apply(current != null ? current : spec.defaultValue());
            return change[0].value();
        });
        String effective = stored != null ? stored : spec.defaultValue();

        Hook hook = spec.mutationHook(op);
        if (hook != null) {
            fire(spec, hook.entries(), owner, target != null ? target : defaultTarget(owner), effective, change[0].delta(), inherited, depth + 1);
        }
        return new WriteResult(true, effective, null);
    }

    private CommandSender defaultTarget(@Nullable UUID owner) {
        Player online = owner != null ? Bukkit.getPlayer(owner) : null;
        return online != null ? online : Bukkit.getConsoleSender();
    }

    // Hook firing

    void fireEvent(EventHook type, Player player, Map<String, String> inherited) {
        for (Binding binding : catalog.event(type)) {
            Spec spec = binding.spec();
            fire(spec, binding.entries(), spec.isPlayer() ? player.getUniqueId() : null, player, null, null, inherited, 0);
        }
    }

    void fireCustom(String eventKey, @Nullable Player player, Map<String, String> captured) {
        for (Binding binding : catalog.custom(eventKey)) {
            Spec spec = binding.spec();
            if (spec.isPlayer() && player == null) {
                continue; // no owner to attribute the write to
            }
            CommandSender target = player != null ? player : Bukkit.getConsoleSender();
            fire(spec, binding.entries(), spec.isPlayer() ? player.getUniqueId() : null, target, null, null, captured, 0);
        }
    }

    void fireStartup() {
        for (Binding binding : catalog.startup()) {
            Spec spec = binding.spec();
            if (spec.isPlayer()) {
                continue;
            }
            fire(spec, binding.entries(), null, Bukkit.getConsoleSender(), null, null, Map.of(), 0);
        }
    }

    void fireInterval(Binding binding) {
        fireScoped(binding);
    }

    private void fireScoped(Binding binding) {
        Spec spec = binding.spec();
        if (!spec.isPlayer()) {
            fire(spec, binding.entries(), null, Bukkit.getConsoleSender(), null, null, Map.of(), 0);
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            fire(spec, binding.entries(), player.getUniqueId(), player, null, null, Map.of(), 0);
        }
    }

    private void fire(Spec spec, List<VariableHookEntry> entries, @Nullable UUID owner, CommandSender target,
                      @Nullable String snapshot, @Nullable Double delta, Map<String, String> inherited, int depth) {
        if (depth > MAX_DEPTH) {
            logger.warning("Hooks on variable '" + spec.key() + "' nested more than " + MAX_DEPTH + " writes deep, stopping (write loop?)");
            return;
        }
        dispatch(target, () -> {
            try {
                run(spec, entries, owner, target, snapshot, delta, inherited, depth);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "A hook of variable '" + spec.key() + "' failed", e);
            }
        });
    }

    // hooks touch players (messages, placeholders), so they always run on the thread that owns the target
    private static void dispatch(CommandSender target, Runnable task) {
        if (target instanceof Player player) {
            if (Bukkit.isOwnedByCurrentRegion(player)) {
                task.run();
            } else {
                Scheduler.runEntity(player, task);
            }
        } else if (Bukkit.isGlobalTickThread()) {
            task.run();
        } else {
            Scheduler.runGlobal(task);
        }
    }

    private void run(Spec spec, List<VariableHookEntry> entries, @Nullable UUID owner, CommandSender target,
                     @Nullable String snapshot, @Nullable Double delta, Map<String, String> inherited, int depth) {
        // one snapshot per fire
        Map<String, String> placeholders = new LinkedHashMap<>(inherited);
        placeholders.put("aircore_var_" + spec.key(), snapshot != null ? snapshot : read(spec, owner));
        if (delta != null) {
            placeholders.put("aircore_var_" + spec.key() + "_delta", formatDelta(delta));
        }
        Function<String, String> resolver = Placeholders.resolver(target, placeholders);

        for (VariableHookEntry entry : entries) {
            if (!conditions.evaluate(entry.conditions(), resolver)) {
                continue;
            }
            VariableWrite self = toWrite(entry.add(), entry.subtract(), entry.set(), entry.reset(), entry.toggle(), target, placeholders);
            if (self != null) {
                submit(spec, owner, self, target, placeholders, depth);
            }
            runOtherWrite(entry.variable(), owner, target, placeholders, depth);
            runComponents(entry, target, placeholders, spec.key());
        }
    }

    private void runOtherWrite(@Nullable OtherVariableWrite other, @Nullable UUID owner, CommandSender target,
                               Map<String, String> placeholders, int depth) {
        if (other == null) {
            return;
        }
        Spec spec = catalog.spec(other.key());
        if (spec == null) {
            logger.warning("Hook references unknown variable '" + other.key() + "'");
            return;
        }
        UUID targetOwner = null;
        if (spec.isPlayer()) {
            // a global variable's event hook may still write the acting player's own variables
            targetOwner = owner != null ? owner : (target instanceof Player player ? player.getUniqueId() : null);
            if (targetOwner == null) {
                logger.warning("Hook tried to write player-scoped variable '" + other.key() + "' outside of a player context, skipping");
                return;
            }
        }
        VariableWrite write = toWrite(other.add(), other.subtract(), other.set(), other.reset(), other.toggle(), target, placeholders);
        if (write != null) {
            submit(spec, targetOwner, write, target, placeholders, depth);
        }
    }

    private static @Nullable VariableWrite toWrite(@Nullable Double add, @Nullable Double subtract, @Nullable String set,
                                                   boolean reset, boolean toggle, CommandSender target, Map<String, String> placeholders) {
        if (add != null) return new VariableWrite.Add(add);
        if (subtract != null) return new VariableWrite.Subtract(subtract);
        if (set != null) return new VariableWrite.Set(Placeholders.apply(target, set, placeholders));
        if (reset) return new VariableWrite.Reset();
        if (toggle) return new VariableWrite.Toggle();
        return null;
    }

    private void runComponents(VariableHookEntry entry, CommandSender target, Map<String, String> placeholders, String key) {
        messenger.send(target, entry.message(), placeholders, "variable:" + key);

        for (String raw : entry.command()) {
            String resolved = Placeholders.apply(target, raw, placeholders);
            CommandExecution.asConsole(resolved);
        }
        if (entry.announcement() != null && !announcements.trigger(entry.announcement())) {
            logger.warning("Hook referenced unknown announcement '" + entry.announcement() + "'");
        }
    }

    private static String formatDelta(double delta) {
        return delta == Math.rint(delta) ? String.valueOf((long) delta) : String.valueOf(delta);
    }
}