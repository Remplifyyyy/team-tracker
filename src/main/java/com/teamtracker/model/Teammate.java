package com.teamtracker.model;

public final class Teammate {
    private final String username;
    private volatile PositionSample previous;
    private volatile PositionSample target;

    public Teammate(String username, PositionSample first) {
        this.username = username;
        this.previous = first;
        this.target = first;
    }

    public String username() {
        return username;
    }

    public synchronized void update(PositionSample next) {
        PositionSample current = this.target;
        if (current != null && !current.dimension().equals(next.dimension())) {
            this.previous = next;
            this.target = next;
            return;
        }
        this.previous = current == null ? next : current;
        this.target = next;
    }

    public PositionSample target() {
        return target;
    }

    public long lastUpdateMs() {
        PositionSample value = target;
        return value == null ? 0L : value.receivedAtMs();
    }

    public InterpolatedPosition sample(long nowMs,
                                       long backtrackMs,
                                       long minDurationMs,
                                       long maxDurationMs,
                                       long maxExtrapolationMs) {
        PositionSample a = previous;
        PositionSample b = target;
        if (b == null) return null;
        if (a == null || a == b || !a.dimension().equals(b.dimension())) {
            return new InterpolatedPosition(b.x(), b.y(), b.z(), b.dimension());
        }

        long spacingMs = Math.max(1L, b.receivedAtMs() - a.receivedAtMs());
        long durationMs = Math.max(minDurationMs, Math.min(maxDurationMs, spacingMs));
        long renderTimeMs = nowMs - Math.max(0L, backtrackMs);

        double t = (double) (renderTimeMs - a.receivedAtMs()) / (double) durationMs;
        if (t <= 1.0) {
            double s = smoothstep(clamp(t));
            return new InterpolatedPosition(
                    lerp(a.x(), b.x(), s),
                    lerp(a.y(), b.y(), s),
                    lerp(a.z(), b.z(), s),
                    b.dimension()
            );
        }

        long extrapolationMs = Math.min(Math.max(0L, renderTimeMs - b.receivedAtMs()), Math.max(0L, maxExtrapolationMs));
        if (extrapolationMs <= 0L) {
            return new InterpolatedPosition(b.x(), b.y(), b.z(), b.dimension());
        }

        double vx = (b.x() - a.x()) / (double) spacingMs;
        double vy = (b.y() - a.y()) / (double) spacingMs;
        double vz = (b.z() - a.z()) / (double) spacingMs;

        double predictedX = b.x() + vx * extrapolationMs;
        double predictedY = b.y() + vy * extrapolationMs;
        double predictedZ = b.z() + vz * extrapolationMs;

        return new InterpolatedPosition(predictedX, predictedY, predictedZ, b.dimension());
    }

    private static double clamp(double t) {
        return Math.max(0.0, Math.min(1.0, t));
    }

    private static double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
