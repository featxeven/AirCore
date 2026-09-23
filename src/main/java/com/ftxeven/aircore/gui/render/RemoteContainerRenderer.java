package com.ftxeven.aircore.gui.render;

import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.core.gui.LiveContainer;
import com.ftxeven.aircore.service.inventory.InventoryHandle;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;

public final class RemoteContainerRenderer implements LiveContainer {

    public record Region(List<Integer> guiSlots, List<Integer> targetSlots, Predicate<ItemStack> validator) {
        public Region {
            guiSlots = List.copyOf(guiSlots);
            targetSlots = List.copyOf(targetSlots);
            if (guiSlots.size() != targetSlots.size()) {
                throw new IllegalArgumentException("guiSlots and targetSlots must be the same size");
            }
        }

        public static Region sequential(Set<Integer> guiSlots, int targetStart) {
            return sequential(guiSlots, targetStart, item -> true);
        }

        public static Region sequential(Set<Integer> guiSlots, int targetStart, Predicate<ItemStack> validator) {
            List<Integer> gui = List.copyOf(guiSlots);
            List<Integer> target = new ArrayList<>(gui.size());
            for (int i = 0; i < gui.size(); i++) {
                target.add(targetStart + i);
            }
            return new Region(gui, target, validator);
        }

        public static Region mapped(List<Integer> guiSlots, List<Integer> targetSlots, Predicate<ItemStack> validator) {
            return new Region(guiSlots, targetSlots, validator);
        }
    }

    private final InventoryHandle handle;
    private final Logger logger;
    private final boolean canModify;
    private final List<Region> regions; // priority order for receive()
    private final Map<Integer, ItemStack> lastKnown = new LinkedHashMap<>(); // real backing content, keyed by target slot
    private final Map<Integer, ItemStack> background = new LinkedHashMap<>(); // decorative fallback, keyed by gui slot

    public RemoteContainerRenderer(InventoryHandle handle, Logger logger, boolean canModify, List<Region> regions) {
        this.handle = handle;
        this.logger = logger;
        this.canModify = canModify;
        this.regions = List.copyOf(regions);
    }

    // Painting

    public void initialize(GuiSession session, ItemStack[] contents) {
        captureBackground(session);
        for (Region region : regions) {
            for (int i = 0; i < region.guiSlots().size(); i++) {
                int targetSlot = region.targetSlots().get(i);
                ItemStack item = targetSlot < contents.length ? contents[targetSlot] : null;
                paint(session, region.guiSlots().get(i), targetSlot, item);
            }
        }
    }

    /** repaints every bound slot from the last known state, without touching the handle */
    public void repaint(GuiSession session) {
        captureBackground(session);
        for (Region region : regions) {
            for (int i = 0; i < region.guiSlots().size(); i++) {
                int targetSlot = region.targetSlots().get(i);
                paint(session, region.guiSlots().get(i), targetSlot, lastKnown.get(targetSlot));
            }
        }
    }

    /** applies a single remote slot change (another viewer's edit, or the target's own live play) live. */
    public void applyRemoteChange(GuiSession session, InventoryHandle.SlotChange change) {
        for (Region region : regions) {
            int index = region.targetSlots().indexOf(change.slot());
            if (index >= 0) {
                paint(session, region.guiSlots().get(index), change.slot(), change.item());
            }
        }
    }

    private void captureBackground(GuiSession session) {
        for (Region region : regions) {
            for (int guiSlot : region.guiSlots()) {
                background.put(guiSlot, clone(session.inventory().getItem(guiSlot)));
            }
        }
    }

    private void paint(GuiSession session, int guiSlot, int targetSlot, @Nullable ItemStack item) {
        ItemStack clone = clone(item);
        session.inventory().setItem(guiSlot, clone != null ? clone : clone(background.get(guiSlot)));
        session.claim(guiSlot);
        lastKnown.put(targetSlot, clone);
    }

    // LiveContainer

    @Override
    public boolean isBound(int slot) {
        return regionFor(slot) != null;
    }

    @Override
    public boolean canModify() {
        return canModify;
    }

    @Override
    public boolean accepts(int slot, @Nullable ItemStack incoming) {
        if (isEmpty(incoming)) {
            return true;
        }
        Region region = regionFor(slot);
        return region != null && region.validator().test(incoming);
    }

    @Override
    public boolean isBacked(int slot) {
        int targetSlot = targetSlotFor(slot);
        return targetSlot >= 0 && !isEmpty(lastKnown.get(targetSlot));
    }

    @Override
    public @Nullable ItemStack receive(GuiSession session, ItemStack incoming) {
        ItemStack remaining = incoming.clone();

        // pass 1: top up compatible existing stacks, in region priority order
        for (Region region : regions) {
            List<Integer> guiSlots = region.guiSlots();
            List<Integer> targetSlots = region.targetSlots();
            for (int i = 0; i < targetSlots.size(); i++) {
                if (remaining.getAmount() <= 0) {
                    return null;
                }
                int targetSlot = targetSlots.get(i);
                ItemStack current = lastKnown.get(targetSlot);
                if (isEmpty(current) || !current.isSimilar(remaining)) {
                    continue;
                }
                int room = current.getMaxStackSize() - current.getAmount();
                if (room <= 0) {
                    continue;
                }
                int move = Math.min(room, remaining.getAmount());
                ItemStack merged = current.clone();
                merged.setAmount(current.getAmount() + move);
                remaining.setAmount(remaining.getAmount() - move);
                write(session, guiSlots.get(i), targetSlot, merged);
            }
        }

        // pass 2: drop whatever's left into the first empty slot that will accept it
        for (Region region : regions) {
            if (remaining.getAmount() <= 0) {
                return null;
            }
            if (!region.validator().test(remaining)) {
                continue;
            }
            List<Integer> guiSlots = region.guiSlots();
            List<Integer> targetSlots = region.targetSlots();
            for (int i = 0; i < targetSlots.size(); i++) {
                if (remaining.getAmount() <= 0) {
                    break;
                }
                int targetSlot = targetSlots.get(i);
                if (!isEmpty(lastKnown.get(targetSlot))) {
                    continue;
                }
                int move = Math.min(remaining.getMaxStackSize(), remaining.getAmount());
                ItemStack placed = remaining.clone();
                placed.setAmount(move);
                remaining.setAmount(remaining.getAmount() - move);
                write(session, guiSlots.get(i), targetSlot, placed);
            }
        }

        return remaining.getAmount() > 0 ? remaining : null;
    }

    @Override
    public void placeDirect(GuiSession session, int slot, ItemStack incoming) {
        Region region = regionFor(slot);
        if (region == null) {
            return;
        }
        write(session, slot, targetSlotFor(slot), incoming.clone());
    }

    @Override
    public void reconcile(GuiSession session) {
        for (Region region : regions) {
            for (int i = 0; i < region.guiSlots().size(); i++) {
                int guiSlot = region.guiSlots().get(i);
                int targetSlot = region.targetSlots().get(i);
                ItemStack current = normalizeForCompare(guiSlot, session.inventory().getItem(guiSlot));
                if (!itemsMatch(current, lastKnown.get(targetSlot))) {
                    write(session, guiSlot, targetSlot, current);
                }
            }
        }
    }

    @Override
    public void close() {
        handle.close();
    }

    private @Nullable ItemStack normalizeForCompare(int guiSlot, @Nullable ItemStack current) {
        if (isEmpty(current)) {
            return null;
        }
        return itemsMatch(current, background.get(guiSlot)) ? null : current;
    }

    private void write(GuiSession session, int guiSlot, int targetSlot, @Nullable ItemStack item) {
        paint(session, guiSlot, targetSlot, item);
        handle.set(targetSlot, item).whenComplete((success, error) -> {
            if (error != null) {
                logger.warning("Failed to sync live inventory edit (slot " + targetSlot + "): " + error.getMessage());
            } else if (!Boolean.TRUE.equals(success)) {
                logger.warning("Live inventory edit to slot " + targetSlot + " was rejected (target may have gone offline mid-edit)");
            }
        });
    }

    // Helpers

    private @Nullable Region regionFor(int guiSlot) {
        for (Region region : regions) {
            if (region.guiSlots().contains(guiSlot)) {
                return region;
            }
        }
        return null;
    }

    private int targetSlotFor(int guiSlot) {
        for (Region region : regions) {
            int index = region.guiSlots().indexOf(guiSlot);
            if (index >= 0) {
                return region.targetSlots().get(index);
            }
        }
        return -1;
    }

    private static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private static boolean itemsMatch(@Nullable ItemStack a, @Nullable ItemStack b) {
        boolean aEmpty = isEmpty(a);
        boolean bEmpty = isEmpty(b);
        if (aEmpty || bEmpty) {
            return aEmpty == bEmpty;
        }
        return a.isSimilar(b) && a.getAmount() == b.getAmount();
    }

    private static @Nullable ItemStack clone(@Nullable ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }

    // Equipment validation

    public static boolean isEquipmentType(@Nullable ItemStack item, EquipmentSlot expected) {
        return isEmpty(item) || classify(item.getType()) == expected;
    }

    private static @Nullable EquipmentSlot classify(Material material) {
        String name = material.name();
        if (name.endsWith("_HELMET") || material == Material.CARVED_PUMPKIN || isMobHead(material)) {
            return EquipmentSlot.HEAD;
        }
        if (name.endsWith("_CHESTPLATE") || material == Material.ELYTRA) {
            return EquipmentSlot.CHEST;
        }
        if (name.endsWith("_LEGGINGS")) {
            return EquipmentSlot.LEGS;
        }
        if (name.endsWith("_BOOTS")) {
            return EquipmentSlot.FEET;
        }
        return null;
    }

    private static boolean isMobHead(Material material) {
        return switch (material) {
            case PLAYER_HEAD, ZOMBIE_HEAD, SKELETON_SKULL, WITHER_SKELETON_SKULL,
                 CREEPER_HEAD, DRAGON_HEAD, PIGLIN_HEAD -> true;
            default -> false;
        };
    }
}