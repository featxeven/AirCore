package com.ftxeven.aircore.core.hook;

import com.ftxeven.aircore.core.hook.impl.*;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class HookManager {

    private final List<ItemHook> hooks = new ArrayList<>();

    public HookManager() {
        if (isPlugin("Nexo")) hooks.add(new NexoHook());
        if (isPlugin("ItemsAdder")) hooks.add(new ItemsAdderHook());
        if (isPlugin("CraftEngine")) hooks.add(new CraftEngineHook());
    }

    private boolean isPlugin(String name) {
        return Bukkit.getPluginManager().isPluginEnabled(name);
    }

    public String match(ItemStack item, Set<String> targetIds, String prefix) {
        for (ItemHook hook : hooks) {
            if (!hook.getPrefix().equalsIgnoreCase(prefix)) continue;
            String id = hook.getItemId(item);
            if (id == null) continue;

            id = id.toLowerCase();
            if (targetIds.contains(id)) return id;

            if (id.contains(":")) {
                String bare = id.substring(id.indexOf(':') + 1);
                if (targetIds.contains(bare)) return bare;
            }
        }
        return null;
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