package com.ftxeven.aircore.core.hook;

import com.ftxeven.aircore.core.hook.impl.*;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;
public final class HookManager {

    private final List<ItemHook> hooks = new ArrayList<>();

    public HookManager() {
        if (isPlugin("Nexo")) hooks.add(new NexoHook());
        if (isPlugin("ItemsAdder")) hooks.add(new ItemsAdderHook());
    }

    private boolean isPlugin(String name) {
        return Bukkit.getPluginManager().isPluginEnabled(name);
    }

    public ItemStack getItem(String id, String prefix) {
        for (ItemHook hook : hooks) {
            if (hook.getPrefix().equalsIgnoreCase(prefix)) {
                return hook.getItem(id);
            }
        }
        return null;
    }

    public List<ItemHook> getHooks() {
        return hooks;
    }
}