package com.teamtracker.config;

import java.util.Locale;

/** How teammates are coloured. UNIQUE gives every player their own colour; the rest colour everyone the same. */
public enum ColorMode {
    UNIQUE("Unique per player", -1),
    RAINBOW("Rainbow", -1),
    RED("Red", 0xFF5555),
    ORANGE("Orange", 0xFFAA33),
    YELLOW("Yellow", 0xFFFF55),
    GREEN("Green", 0x55FF55),
    LIME("Lime", 0xA8FF3E),
    AQUA("Aqua", 0x55FFFF),
    BLUE("Blue", 0x5599FF),
    PURPLE("Purple", 0xB266FF),
    PINK("Pink", 0xFF77C8),
    WHITE("White", 0xFFFFFF),
    CUSTOM("Custom", -1);

    public final String label;
    public final int rgb;

    ColorMode(String label, int rgb) {
        this.label = label;
        this.rgb = rgb;
    }

    public ColorMode next(int dir) {
        ColorMode[] all = values();
        return all[Math.floorMod(ordinal() + dir, all.length)];
    }

    public static ColorMode parse(String name) {
        if (name != null) {
            try {
                return valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return UNIQUE;
    }
}
