package com.teamtracker.config;

import java.util.Locale;

/** The symbol drawn above a teammate's head. */
public enum MarkerSymbol {
    NONE("None"),
    ARROW("Arrow"),
    CHEVRON("Chevron"),
    DOT("Dot"),
    RING("Ring"),
    DIAMOND("Diamond"),
    SQUARE("Square"),
    TRIANGLE("Triangle"),
    PLUS("Plus"),
    CROSS("Cross"),
    HEAD("Player Head");

    public final String label;

    MarkerSymbol(String label) {
        this.label = label;
    }

    public MarkerSymbol next(int dir) {
        MarkerSymbol[] all = values();
        return all[Math.floorMod(ordinal() + dir, all.length)];
    }

    public static MarkerSymbol parse(String name) {
        if (name != null) {
            try {
                return valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return ARROW;
    }
}
