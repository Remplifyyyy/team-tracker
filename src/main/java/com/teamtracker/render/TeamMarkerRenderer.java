package com.teamtracker.render;

import com.teamtracker.config.ColorMode;
import com.teamtracker.config.Config;
import com.teamtracker.config.MarkerSymbol;
import com.teamtracker.model.InterpolatedPosition;
import com.teamtracker.model.TeamStore;
import com.teamtracker.model.Teammate;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

public final class TeamMarkerRenderer {
    private static final int OUTLINE = 0xB0000000;
    private static final int TEXT_BG = 0x70000000;

    private final Minecraft client;
    private final Config config;
    private final TeamStore store;
    private final BooleanSupplier revealHeld;

    private final Map<String, Integer> playerColors = new ConcurrentHashMap<>();

    public TeamMarkerRenderer(Minecraft client, Config config, TeamStore store, BooleanSupplier revealHeld) {
        this.client = client;
        this.config = config;
        this.store = store;
        this.revealHeld = revealHeld;
    }

    public void render(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
        if (!config.enabled || client.player == null || client.level == null) return;
        String currentDimension = client.level.dimension().identifier().toString();
        long now = System.currentTimeMillis();
        boolean forceReveal = revealHeld.getAsBoolean();

        for (Teammate teammate : store.values()) {
            InterpolatedPosition position = teammate.sample(
                    now,
                    config.interpolationBacktrackMs,
                    config.interpolationMinMs,
                    config.interpolationMaxMs,
                    config.maxExtrapolationMs
            );
            if (position == null || !currentDimension.equals(position.dimension())) continue;

            double dx = position.x() - client.player.getX();
            double dy = position.y() - client.player.getY();
            double dz = position.z() - client.player.getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance <= Math.max(0.0, config.hideWithinBlocks)) continue;
            if (!config.distantMarkers && distance > config.distantMarkerBlocks) continue;

            ScreenPoint point = project(position.x(), position.y() + 2.1, position.z(), graphics);
            if (point == null || !point.onScreen()) continue;

            float reveal = forceReveal ? 1.0f : revealAmount(point, graphics);
            drawMarker(graphics, store.displayName(teammate), teammate.username(),
                    Math.round(point.x()), Math.round(point.y()), distance, reveal);
        }
    }

    /** Used by the settings menu so you can see your changes live. */
    public void drawPreview(GuiGraphicsExtractor graphics, int x, int y, String displayName, String username, double distance) {
        drawMarker(graphics, displayName, username, x, y, distance, 1.0f);
    }

    /** 1 = fully visible, 0 = hidden. Only matters when "only when looking" is on. */
    private float revealAmount(ScreenPoint point, GuiGraphicsExtractor graphics) {
        if (!config.lookToReveal) return 1.0f;
        double cx = graphics.guiWidth() / 2.0;
        double cy = graphics.guiHeight() / 2.0;
        double d = Math.hypot(point.x() - cx, point.y() - cy);
        double r = config.lookRadiusPx;
        double fade = Math.max(4.0, r * 0.35);
        if (d <= r) return 1.0f;
        if (d >= r + fade) return 0.0f;
        return (float) (1.0 - (d - r) / fade);
    }

    private ScreenPoint project(double worldX, double worldY, double worldZ, GuiGraphicsExtractor graphics) {
        Camera camera = client.gameRenderer.mainCamera();
        Vec3 cameraPos = camera.position();

        Vector3f local = new Vector3f(
                (float) (worldX - cameraPos.x),
                (float) (worldY - cameraPos.y),
                (float) (worldZ - cameraPos.z)
        );
        Quaternionf inverseCamera = new Quaternionf(camera.rotation()).conjugate();
        inverseCamera.transform(local);

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        if (width <= 0 || height <= 0) return null;

        boolean inFront = -local.z > 0.05f;
        if (!inFront) return null;

        double fovDegrees = camera.getFov();
        double tanHalfFov = Math.tan(Math.toRadians(fovDegrees * 0.5));
        double aspect = (double) width / (double) height;

        double ndcX = (local.x / -local.z) / (tanHalfFov * aspect);
        double ndcY = (local.y / -local.z) / tanHalfFov;
        float x = (float) ((ndcX * 0.5 + 0.5) * width);
        float y = (float) ((0.5 - ndcY * 0.5) * height);

        int margin = 20;
        boolean onScreen = x >= margin && x <= width - margin && y >= margin && y <= height - margin;
        return new ScreenPoint(x, y, onScreen, 0.0f, false);
    }

    // ------------------------------------------------------------------ marker layout

    private void drawMarker(GuiGraphicsExtractor graphics, String displayName, String username,
                            int x, int y, double distance, float reveal) {
        int rgb = colorFor(username);
        MarkerSymbol symbol = config.symbol();

        // Symbol sits at the bottom, just above the head.
        int symbolHeight = drawSymbol(graphics, symbol, x, y, rgb, username);
        int cursor = y - symbolHeight - (symbolHeight > 0 ? 2 : 0);

        if (reveal <= 0.02f) return;
        int alpha = Math.max(0, Math.min(255, Math.round(reveal * 255.0f)));

        boolean name = config.showName;
        boolean dist = config.showDistance;
        if (!name && !dist) return;

        int distRgb = config.distanceUsesColor ? rgb : 0xFFFFFF;
        String distanceText = formatDistance(distance);

        if (config.inlineDistance || !name || !dist) {
            // One line: "Name 42m", or whichever of the two is on.
            MutableComponent line = Component.empty();
            float scale;
            if (name) {
                line.append(styled(displayName, rgb));
                if (dist) line.append(styled("  " + distanceText, distRgb));
                scale = (float) config.nameScale;
            } else {
                line.append(styled(distanceText, distRgb));
                scale = (float) config.distanceScale;
            }
            drawLine(graphics, line, x, cursor, scale, alpha, true);
            return;
        }

        // Stacked: name on top, distance underneath.
        float distScale = (float) config.distanceScale;
        float nameScale = (float) config.nameScale;
        int distTop = drawLine(graphics, styled(distanceText, distRgb), x, cursor, distScale, alpha, true);
        drawLine(graphics, styled(displayName, rgb), x, distTop - 1, nameScale, alpha, true);
    }

    /**
     * Draws one centred line of text whose bottom edge sits at {@code bottom}. Returns the top y.
     */
    private int drawLine(GuiGraphicsExtractor graphics, Component text, int x, int bottom, float scale, int alpha, boolean center) {
        int lineHeight = client.font.lineHeight;
        float height = lineHeight * scale;
        float top = bottom - height;
        int width = client.font.width(text);
        int left = center ? -width / 2 : 0;

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate((float) x, top);
        pose.scale(scale, scale);
        if (config.textBackground) {
            int bgAlpha = (int) (((TEXT_BG >>> 24) & 0xFF) * (alpha / 255.0f));
            graphics.fill(left - 2, -2, left + width + 2, lineHeight, (bgAlpha << 24));
        }
        graphics.text(client.font, text, left, 0, (alpha << 24) | 0xFFFFFF, config.textShadow);
        pose.popMatrix();
        return (int) Math.floor(top) - (config.textBackground ? 2 : 0);
    }

    private static Component styled(String text, int rgb) {
        return Component.literal(text).withStyle(Style.EMPTY
                .withBold(false)
                .withColor(rgb & 0xFFFFFF));
    }

    // ------------------------------------------------------------------ symbols

    /** Draws the symbol with its bottom-centre at (cx, bottom). Returns the height used. */
    private int drawSymbol(GuiGraphicsExtractor graphics, MarkerSymbol symbol, int cx, int bottom, int rgb, String username) {
        int s = Math.max(3, config.symbolSize);
        if (symbol == MarkerSymbol.NONE) return 0;

        if (symbol == MarkerSymbol.HEAD) {
            int size = Math.max(6, s + 1);
            int left = cx - size / 2;
            int top = bottom - size;
            if (config.symbolOutline) {
                graphics.fill(left - 1, top - 1, left + size + 1, top + size + 1, 0xFF000000 | rgb);
            }
            drawHead(graphics, left, top, size, findPlayerInfo(username));
            return size + (config.symbolOutline ? 1 : 0);
        }

        int w = s;
        int h = MarkerShapes.height(symbol, s);
        int left = cx - w / 2;
        int top = bottom - h;
        int fill = 0xFF000000 | rgb;

        if (config.symbolOutline) {
            for (int py = -1; py <= h; py++) {
                int runStart = Integer.MIN_VALUE;
                for (int px = -1; px <= w + 1; px++) {
                    boolean edge = px <= w && !MarkerShapes.inside(symbol, px, py, w, h) && MarkerShapes.touches(symbol, px, py, w, h);
                    if (edge && runStart == Integer.MIN_VALUE) runStart = px;
                    if (!edge && runStart != Integer.MIN_VALUE) {
                        graphics.fill(left + runStart, top + py, left + px, top + py + 1, OUTLINE);
                        runStart = Integer.MIN_VALUE;
                    }
                }
            }
        }

        for (int py = 0; py < h; py++) {
            int runStart = -1;
            for (int px = 0; px <= w; px++) {
                boolean in = px < w && MarkerShapes.inside(symbol, px, py, w, h);
                if (in && runStart < 0) runStart = px;
                if (!in && runStart >= 0) {
                    graphics.fill(left + runStart, top + py, left + px, top + py + 1, fill);
                    runStart = -1;
                }
            }
        }
        return h + (config.symbolOutline ? 1 : 0);
    }

    // ------------------------------------------------------------------ colours

    /** Colour for a teammate under the current colour mode. */
    public int colorFor(String username) {
        ColorMode mode = config.colorMode();
        return switch (mode) {
            case UNIQUE -> uniqueColor(username);
            case RAINBOW -> {
                float hue = (System.currentTimeMillis() % 4000L) / 4000.0f;
                yield java.awt.Color.HSBtoRGB(hue, 0.7f, 1.0f) & 0xFFFFFF;
            }
            case CUSTOM -> config.customColor & 0xFFFFFF;
            default -> mode.rgb;
        };
    }

    /**
     * Each teammate gets their own colour. It is derived from the username, so it looks random
     * across the team but stays the same for that player every session.
     */
    private int uniqueColor(String username) {
        String key = username == null ? "" : username.toLowerCase(Locale.ROOT);
        return playerColors.computeIfAbsent(key, k -> {
            int h = k.hashCode();
            h ^= (h >>> 16);
            h *= 0x45d9f3b;
            h ^= (h >>> 16);
            float hue = (h & 0xFFFF) / 65535.0f;
            float sat = 0.55f + ((h >>> 16) & 0xFF) / 255.0f * 0.35f; // 0.55 - 0.90
            return java.awt.Color.HSBtoRGB(hue, sat, 1.0f) & 0xFFFFFF;
        });
    }

    // ------------------------------------------------------------------ heads

    private PlayerInfo findPlayerInfo(String username) {
        ClientPacketListener connection = client.getConnection();
        if (connection == null || username == null || username.isBlank()) return null;

        PlayerInfo exact = connection.getPlayerInfo(username);
        if (exact != null) return exact;

        for (PlayerInfo entry : connection.getOnlinePlayers()) {
            if (entry.getProfile() != null
                    && entry.getProfile().name() != null
                    && entry.getProfile().name().equalsIgnoreCase(username)) {
                return entry;
            }
        }
        return null;
    }

    private void drawHead(GuiGraphicsExtractor graphics, int x, int y, int size, PlayerInfo entry) {
        if (entry != null) {
            PlayerFaceExtractor.extractRenderState(graphics, entry.getSkin(), x, y, size);
            return;
        }

        // Neutral fallback if the server's tab list does not expose a skin yet.
        int fill = 0xFF454A57;
        int accent = 0xFFBFC6D7;
        int eye = Math.max(1, size / 6);
        graphics.fill(x, y, x + size, y + size, fill);
        graphics.fill(x + eye, y + size / 3, x + eye * 2, y + size / 3 + eye, accent);
        graphics.fill(x + size - eye * 2, y + size / 3, x + size - eye, y + size / 3 + eye, accent);
        graphics.fill(x + eye, y + size - eye * 2, x + size - eye, y + size - eye, accent);
    }

    private static String formatDistance(double distance) {
        if (distance >= 1000.0) {
            double km = distance / 1000.0;
            return String.format(Locale.ROOT, km >= 10.0 ? "%.0fkm" : "%.1fkm", km);
        }
        return String.format(Locale.ROOT, distance >= 100.0 ? "%.0fm" : "%.1fm", distance);
    }
}
