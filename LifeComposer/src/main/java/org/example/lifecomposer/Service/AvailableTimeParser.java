package org.example.lifecomposer.Service;

import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses free-text available_time ("6 hours/week"、"每周 6 小时") into hours/week. */
public final class AvailableTimeParser {

    private static final Pattern NUMBER = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    private AvailableTimeParser() {
    }

    public static OptionalDouble parseHoursPerWeek(String text) {
        if (text == null || text.isBlank()) {
            return OptionalDouble.empty();
        }
        String normalized = text.trim().toLowerCase();
        Matcher matcher = NUMBER.matcher(normalized);
        if (!matcher.find()) {
            return OptionalDouble.empty();
        }
        double value;
        try {
            value = Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
        if (normalized.contains("day") || normalized.contains("天")) {
            value = value * 7;
        } else if (normalized.contains("month") || normalized.contains("月")) {
            value = value / 4.0;
        }
        if (value <= 0 || value > 168) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(value);
    }
}
