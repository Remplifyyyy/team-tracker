package com.teamtracker.render;

import com.teamtracker.config.MarkerSymbol;

/** Pure pixel maths for the procedural symbols (no Minecraft classes). */
final class MarkerShapes {
    private MarkerShapes() {
    }

    static int height(MarkerSymbol symbol, int s) {
        return switch (symbol) {
            case ARROW, TRIANGLE -> Math.max(2, Math.round(s * 0.62f));
            case CHEVRON -> Math.max(3, Math.round(s * 0.75f));
            default -> s;
        };
    }

    static boolean touches(MarkerSymbol symbol, int px, int py, int w, int h) {
        for (int oy = -1; oy <= 1; oy++) {
            for (int ox = -1; ox <= 1; ox++) {
                if ((ox != 0 || oy != 0) && inside(symbol, px + ox, py + oy, w, h)) return true;
            }
        }
        return false;
    }

    /** Pixel test for every procedural shape. (px, py) is in a w x h box, top-left origin. */
    static boolean inside(MarkerSymbol symbol, int px, int py, int w, int h) {
        if (px < 0 || py < 0 || px >= w || py >= h) return false;
        double fx = px + 0.5;
        double fy = py + 0.5;
        double dx = fx - w / 2.0;
        double dy = fy - h / 2.0;
        double r = w / 2.0;
        double thick = Math.max(1.0, Math.round(w / 3.0));

        return switch (symbol) {
            case SQUARE -> true;
            case DOT -> dx * dx + dy * dy <= r * r + 0.25;
            case RING -> {
                double d2 = dx * dx + dy * dy;
                double inner = Math.max(0.0, r - Math.max(1.25, w / 4.0));
                yield d2 <= r * r + 0.25 && d2 >= inner * inner;
            }
            case DIAMOND -> Math.abs(dx) + Math.abs(dy) <= r + 0.25;
            case ARROW -> downTriangle(dx, fy, w, h);
            case TRIANGLE -> downTriangle(dx, h - fy, w, h);
            case CHEVRON -> {
                int t = Math.max(2, Math.round(w / 3.0f));
                yield downTriangle(dx, fy, w, h) && !(fy + t < h && downTriangle(dx, fy + t, w, h));
            }
            case PLUS -> {
                double arm = Math.max(1.0, Math.round(w / 4.0)) / 2.0;
                yield Math.abs(dx) <= arm || Math.abs(dy) <= arm;
            }
            case CROSS -> Math.abs(Math.abs(dx) - Math.abs(dy)) <= Math.max(0.75, thick * 0.4);
            default -> false;
        };
    }

    /** Full width at the top, narrowing to a point at the bottom. */
    private static boolean downTriangle(double dx, double fy, int w, int h) {
        double half = (w / 2.0) * (1.0 - (fy - 0.5) / h) + 0.15;
        return Math.abs(dx) <= half;
    }
}
