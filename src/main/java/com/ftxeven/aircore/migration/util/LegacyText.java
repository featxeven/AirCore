package com.ftxeven.aircore.migration.util;

import com.ftxeven.aircore.util.MiniText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class LegacyText {

    public record Converted(String tagged, String plain) {}

    private LegacyText() {
    }

    public static Converted convert(String raw) {
        String normalized = raw.replace('\u00a7', '&');

        Component component;
        try {
            component = LegacyComponentSerializer.legacyAmpersand().deserialize(normalized);
        } catch (Exception e) {
            component = Component.text(raw);
        }

        String plain = MiniText.plain(component);

        String tagged;
        try {
            tagged = MiniText.mini().serialize(component);
        } catch (Exception e) {
            tagged = plain;
        }

        return new Converted(tagged.strip(), plain.strip());
    }
}