package com.alexcomc.elytrarebalance.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class TemplateUtil {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private TemplateUtil() {
    }

    public static Component apply(String template, String placeholder, String value, TextColor valueColor) {
        if (template == null) template = placeholder;

        int idx = template.indexOf(placeholder);
        if (idx < 0) {
            return legacy(template);
        }

        String left = template.substring(0, idx);
        String right = template.substring(idx + placeholder.length());

        return legacy(left)
                .append(Component.text(value).color(valueColor))
                .append(legacy(right));
    }

    private static Component legacy(String raw) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        return LEGACY.deserialize(raw.replace('&', '§'));
    }
}
