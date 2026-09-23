package com.ftxeven.aircore.module.variables;

import com.ftxeven.aircore.module.variables.VariablesConfig.CaptureField;
import com.ftxeven.aircore.module.variables.VariablesConfig.CustomEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

final class CustomEventBridge implements Listener {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final VariablesModule module;

    CustomEventBridge(JavaPlugin plugin, VariablesModule module) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.module = module;
    }

    void start(Map<String, CustomEvent> customEvents) {
        customEvents.forEach(this::register);
    }

    void stop() {
        HandlerList.unregisterAll(this);
    }

    @SuppressWarnings("unchecked")
    private void register(String key, CustomEvent customEvent) {
        Class<?> eventClass;
        try {
            eventClass = Class.forName(customEvent.eventClass());
        } catch (ClassNotFoundException e) {
            logger.warning("Custom event '" + key + "' references unknown class '" + customEvent.eventClass() + "', skipping");
            return;
        }
        if (!Event.class.isAssignableFrom(eventClass)) {
            logger.warning("Custom event '" + key + "' class '" + customEvent.eventClass() + "' does not extend org.bukkit.event.Event, skipping");
            return;
        }

        Class<? extends Event> typed = (Class<? extends Event>) eventClass;
        EventExecutor executor = (listener, event) -> {
            if (typed.isInstance(event)) {
                handle(key, customEvent, event);
            }
        };

        try {
            Bukkit.getPluginManager().registerEvent(typed, this, EventPriority.MONITOR, executor, plugin, false);
        } catch (RuntimeException e) {
            logger.warning("Could not register custom event '" + key + "' (" + customEvent.eventClass() + "): " + e.getMessage());
        }
    }

    private void handle(String key, CustomEvent customEvent, Event event) {
        try {
            Player player = customEvent.playerMethod().isBlank() ? null : asPlayer(invokeChain(event, customEvent.playerMethod()));

            Map<String, String> captured = new LinkedHashMap<>();
            for (CaptureField field : customEvent.capture()) {
                Object value = invokeChain(event, field.method());
                captured.put(field.name(), value != null ? String.valueOf(value) : "");
            }

            module.handleCustom(key, player, captured);
        } catch (ReflectiveOperationException e) {
            logger.warning("Failed to read custom event '" + key + "' (" + event.getClass().getName() + "): " + e.getMessage());
        }
    }

    private @Nullable Player asPlayer(@Nullable Object value) {
        return value instanceof Player player ? player : null;
    }

    // resolves a dotted chain of zero-arg getters
    private @Nullable Object invokeChain(Object target, String chain) throws ReflectiveOperationException {
        Object current = target;
        for (String methodName : chain.split("\\(\\)\\.?")) {
            if (current == null || methodName.isBlank()) {
                return null;
            }
            current = current.getClass().getMethod(methodName).invoke(current);
        }
        return current;
    }
}