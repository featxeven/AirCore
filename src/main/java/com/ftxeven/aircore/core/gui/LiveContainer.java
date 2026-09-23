package com.ftxeven.aircore.core.gui;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface LiveContainer {

    boolean isBound(int slot);

    boolean canModify();

    boolean accepts(int slot, @Nullable ItemStack incoming);

    boolean isBacked(int slot);

    @Nullable ItemStack receive(GuiSession session, ItemStack incoming);

    void placeDirect(GuiSession session, int slot, ItemStack incoming);

    void reconcile(GuiSession session);

    void close();
}