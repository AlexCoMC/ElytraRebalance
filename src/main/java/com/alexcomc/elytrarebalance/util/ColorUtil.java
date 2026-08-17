package com.alexcomc.elytrarebalance.util;

import net.kyori.adventure.text.format.TextColor;

import java.awt.Color;

public final class ColorUtil {

    private ColorUtil() {
    }

    public static TextColor gradient(double fractionGood) {
        double f = clamp01(fractionGood);
        float hue = (float) (f * 120.0);
        int rgb = Color.HSBtoRGB(hue / 360f, 1f, 1f) & 0xFFFFFF;
        return TextColor.color(rgb);
    }

    public static TextColor countdownGradient(double secondsLeft, double totalSeconds) {
        if (totalSeconds <= 0) {
            return TextColor.color(0x55FF55);
        }
        return gradient(1.0 - clamp01(secondsLeft / totalSeconds));
    }

    public static TextColor reserveGradient(double remaining, double max) {
        if (max <= 0) {
            return TextColor.color(0xFF5555);
        }
        return gradient(clamp01(remaining / max));
    }

    private static double clamp01(double value) {
        if (value < 0) return 0;
        if (value > 1) return 1;
        return value;
    }
}
