package com.ftxeven.aircore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class MiniText {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    public static MiniMessage mini() { return MINI; }

    /** for one-shot, user-controlled text. Never cached. */
    public static Component parseDynamic(String miniMessageText) {
        return MINI.deserialize(miniMessageText);
    }

    public static Component parse(String miniMessageText, TagResolver resolver) {
        return MINI.deserialize(miniMessageText, resolver);
    }

    public static String plain(Component component) {
        return component != null ? PlainTextComponentSerializer.plainText().serialize(component) : "";
    }

    public static String plain(String miniMessageText) {
        return plain(parseDynamic(miniMessageText));
    }
}