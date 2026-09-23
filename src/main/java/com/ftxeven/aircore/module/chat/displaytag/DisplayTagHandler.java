package com.ftxeven.aircore.module.chat.displaytag;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;

public interface DisplayTagHandler {

    /** does this tag have anything to show for the sender right now? */
    boolean available(Player sender);

    /** adds this tag's placeholders */
    void contribute(Player sender, Map<String, String> placeholders);

    /** snapshot of what a click on this tag should reopen, if it supports clicking right now */
    Optional<DisplayTagClickContext> click(Player sender);

    /** whether this tag needs a 'gui:' configured to be useful at all */
    default boolean guiRequired() {
        return true;
    }
}