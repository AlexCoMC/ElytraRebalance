package com.alexcomc.elytrarebalance.util;

import java.util.Locale;

public final class DurationUtil {

    private DurationUtil() {
    }

    public static double parseToSeconds(String input, double defaultSeconds) {
        if (input == null || input.isBlank()) return defaultSeconds;

        String trimmed = input.trim().toLowerCase(Locale.ROOT);
        char unit = trimmed.charAt(trimmed.length() - 1);
        String numberPart = trimmed;
        double multiplier = 1.0;

        switch (unit) {
            case 's' -> {
                multiplier = 1.0;
                numberPart = trimmed.substring(0, trimmed.length() - 1);
            }
            case 'm' -> {
                multiplier = 60.0;
                numberPart = trimmed.substring(0, trimmed.length() - 1);
            }
            case 'h' -> {
                multiplier = 3600.0;
                numberPart = trimmed.substring(0, trimmed.length() - 1);
            }
            case 'd' -> {
                multiplier = 86400.0;
                numberPart = trimmed.substring(0, trimmed.length() - 1);
            }
            default -> {
                multiplier = 1.0;
                numberPart = trimmed;
            }
        }

        try {
            double value = Double.parseDouble(numberPart.trim());
            return value * multiplier;
        } catch (NumberFormatException ex) {
            return defaultSeconds;
        }
    }

    public static long parseToMillis(String input, long defaultMillis) {
        return Math.round(parseToSeconds(input, defaultMillis / 1000.0) * 1000.0);
    }

    public static String formatSeconds(double totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;

        if (totalSeconds < 10) {
            return String.format(Locale.US, "%.1fs", totalSeconds);
        }

        long secs = Math.round(totalSeconds);

        if (secs < 60) {
            return secs + "s";
        }

        long minutes = secs / 60;
        long remSecs = secs % 60;
        if (minutes < 60) {
            return remSecs == 0 ? minutes + "m" : minutes + "m" + remSecs + "s";
        }

        long hours = minutes / 60;
        long remMinutes = minutes % 60;
        if (hours < 24) {
            return remMinutes == 0 ? hours + "h" : hours + "h" + remMinutes + "m";
        }

        long days = hours / 24;
        long remHours = hours % 24;
        return remHours == 0 ? days + "d" : days + "d" + remHours + "h";
    }
}
