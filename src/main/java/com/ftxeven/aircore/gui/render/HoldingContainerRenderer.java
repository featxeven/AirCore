package com.ftxeven.aircore.gui.render;

import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.core.gui.LiveContainer;
import com.ftxeven.aircore.service.item.HoldingKind;
import com.ftxeven.aircore.service.item.HoldingService;
import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class HoldingContainerRenderer implements LiveContainer {

    public static final String ATTR_KIND = "holding-kind";

    private final Player viewer;
    private final Function<Player, GuiSession> activeSession;
    private final HoldingService holding;
    private final HoldingKind kind;
    private final List<Integer> slots;
    private final ItemStack[] buffer;
    private final Map<Integer, ItemStack> background = new LinkedHashMap<>(); // decorative fallback, keyed by slot

    public HoldingContainerRenderer(Player viewer, Function<Player, GuiSession> activeSession,
                                    HoldingService holding, HoldingKind kind, List<Integer> slots) {
        this.viewer = viewer;
        this.activeSession = activeSession;
        this.holding = holding;
        this.kind = kind;
        this.slots = slots;
        this.buffer = holding.snapshot(viewer.getUniqueId(), kind, slots.size());
    }

    public void initialize(GuiSession session) {
        session.attribute(ATTR_KIND, kind);
        captureBackground(session);
        for (int i = 0; i < slots.size(); i++) {
            paint(session, slots.get(i), buffer[i]);
        }
    }

    private void captureBackground(GuiSession session) {
        for (int slot : slots) {
            background.put(slot, clone(session.inventory().getItem(slot)));
        }
    }

    private void paint(GuiSession session, int slot, @Nullable ItemStack item) {
        ItemStack clone = clone(item);
        session.inventory().setItem(slot, clone != null ? clone : clone(background.get(slot)));
        session.claim(slot);
    }

    private int indexOf(int slot) {
        return slots.indexOf(slot);
    }

    @Override
    public boolean isBound(int slot) {
        return indexOf(slot) >= 0;
    }

    @Override
    public boolean canModify() {
        return true;
    }

    @Override
    public boolean accepts(int slot, @Nullable ItemStack incoming) {
        return isBound(slot);
    }

    @Override
    public boolean isBacked(int slot) {
        int index = indexOf(slot);
        return index >= 0 && !isEmpty(buffer[index]);
    }

    @Override
    public @Nullable ItemStack receive(GuiSession session, ItemStack incoming) {
        ItemStack remaining = incoming.clone();

        // pass 1: top up compatible stacks already placed
        for (int i = 0; i < slots.size() && remaining.getAmount() > 0; i++) {
            ItemStack current = buffer[i];
            if (isEmpty(current) || !current.isSimilar(remaining)) {
                continue;
            }
            int room = current.getMaxStackSize() - current.getAmount();
            if (room <= 0) {
                continue;
            }
            int move = Math.min(room, remaining.getAmount());
            current.setAmount(current.getAmount() + move);
            remaining.setAmount(remaining.getAmount() - move);
            paint(session, slots.get(i), current);
        }

        // pass 2: drop the rest into the first empty slot
        for (int i = 0; i < slots.size() && remaining.getAmount() > 0; i++) {
            if (!isEmpty(buffer[i])) {
                continue;
            }
            int move = Math.min(remaining.getMaxStackSize(), remaining.getAmount());
            ItemStack placed = remaining.clone();
            placed.setAmount(move);
            buffer[i] = placed;
            remaining.setAmount(remaining.getAmount() - move);
            paint(session, slots.get(i), placed);
        }

        persist();
        return remaining.getAmount() > 0 ? remaining : null;
    }

    @Override
    public void placeDirect(GuiSession session, int slot, ItemStack incoming) {
        int index = indexOf(slot);
        if (index < 0) {
            return;
        }
        buffer[index] = incoming.clone();
        paint(session, slot, buffer[index]);
        persist();
    }

    @Override
    public void reconcile(GuiSession session) {
        boolean changed = false;
        for (int i = 0; i < slots.size(); i++) {
            int slot = slots.get(i);
            ItemStack current = normalizeForCompare(slot, session.inventory().getItem(slot));
            if (!itemsMatch(current, buffer[i])) {
                buffer[i] = clone(current);
                paint(session, slot, buffer[i]);
                changed = true;
            }
        }
        if (changed) {
            persist();
        }
    }

    private @Nullable ItemStack normalizeForCompare(int slot, @Nullable ItemStack current) {
        if (isEmpty(current)) {
            return null;
        }
        return itemsMatch(current, background.get(slot)) ? null : current;
    }

    @Override
    public void close() {
        Scheduler.runEntityLater(viewer, this::returnIfAbandoned, 1L);
    }

    private void returnIfAbandoned() {
        GuiSession current = activeSession.apply(viewer);
        HoldingKind stillClaimed = current != null ? current.attribute(ATTR_KIND, HoldingKind.class) : null;
        if (stillClaimed != kind) {
            holding.returnAll(viewer, kind);
        }
    }

    private void persist() {
        holding.store(viewer.getUniqueId(), kind, buffer);
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
}