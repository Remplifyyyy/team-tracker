package com.teamtracker.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("team-tracker.json");
    private static final int CURRENT_VERSION = 2;

    public int configVersion = CURRENT_VERSION;

    public String relayUrl = "wss://team-tracker-relay.mhb6d56ts5.workers.dev";
    public String teamCode = "";
    public boolean enabled = true;

    // Network tuning.
    public long sendIntervalMs = 200;
    public long heartbeatMs = 1500;
    public double movementThresholdBlocks = 0.5;
    public long reconnectDelayMs = 5000;
    public long stalePlayerMs = 12000;

    // Rendering / smoothing.
    public long interpolationBacktrackMs = 125;
    public long interpolationMinMs = 100;
    public long interpolationMaxMs = 600;
    public long maxExtrapolationMs = 250;

    // ---- Text ----
    public boolean showName = true;
    public boolean showDistance = true;
    public double nameScale = 1.0;          // 0.5x - 3.0x
    public double distanceScale = 0.85;     // 0.5x - 3.0x
    public boolean inlineDistance = false;  // "Name 42m" on one line instead of stacked
    public boolean textShadow = true;
    public boolean textBackground = false;
    public boolean distanceUsesColor = false; // false = white distance text

    // Only show name/distance when your crosshair is near the teammate.
    public boolean lookToReveal = false;
    public double lookRadiusPx = 40.0;

    // ---- Symbol above the head ----
    public String symbol = MarkerSymbol.ARROW.name();
    public int symbolSize = 7;
    public boolean symbolOutline = true;

    // ---- Colours ----
    public String colorMode = ColorMode.UNIQUE.name();
    public int customColor = 0x55FF55;

    // ---- Range ----
    public boolean distantMarkers = true;   // off = hide markers farther than distantMarkerBlocks
    public double distantMarkerBlocks = 150.0;
    // Hide the tracker marker when a teammate is already close enough to see normally.
    public double hideWithinBlocks = 20.0;

    // ---- You ----
    // Sent to teammates who also run this version. Empty = your normal username.
    public String nickname = "";

    // Legacy fields from older versions, only read for migration.
    public Boolean nameplates;
    public Boolean showHeads;
    public Boolean showNames;

    public static Config load() {
        Config config = null;
        if (Files.exists(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE)) {
                config = GSON.fromJson(reader, Config.class);
            } catch (Exception ignored) {
            }
        }
        if (config == null) config = new Config();
        config.migrateAndClamp();
        config.save();
        return config;
    }

    private void migrateAndClamp() {
        // Older files have no configVersion (Gson keeps the field default), so detect them by legacy fields.
        if (nameplates != null) {
            // Old "nameplates" mode = name + distance text. Off = small head + distance.
            showName = nameplates;
            if (!nameplates) symbol = MarkerSymbol.HEAD.name();
        }
        configVersion = CURRENT_VERSION;
        nameplates = null;
        showHeads = null;
        showNames = null;

        nameScale = clamp(nameScale, 0.5, 3.0);
        distanceScale = clamp(distanceScale, 0.5, 3.0);
        lookRadiusPx = clamp(lookRadiusPx, 5.0, 300.0);
        symbolSize = (int) clamp(symbolSize, 3, 24);
        hideWithinBlocks = clamp(hideWithinBlocks, 0.0, 200.0);
        distantMarkerBlocks = clamp(distantMarkerBlocks, 25.0, 5000.0);
        customColor &= 0xFFFFFF;
        if (nickname == null) nickname = "";
        nickname = cleanNickname(nickname);
        symbol = symbol().name();
        colorMode = colorMode().name();
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public String cleanTeamCode() {
        String value = teamCode == null ? "" : teamCode.trim();
        if (value.length() > 64) value = value.substring(0, 64);
        return value;
    }

    public MarkerSymbol symbol() {
        return MarkerSymbol.parse(symbol);
    }

    public ColorMode colorMode() {
        return ColorMode.parse(colorMode);
    }

    /** Max 16 visible characters, no formatting codes or control characters. */
    public static String cleanNickname(String raw) {
        if (raw == null) return "";
        String value = raw.replaceAll("[§\\p{Cntrl}]", "").trim();
        if (value.length() > 16) value = value.substring(0, 16).trim();
        return value;
    }

    private static double clamp(double v, double min, double max) {
        if (Double.isNaN(v)) return min;
        return Math.max(min, Math.min(max, v));
    }
}
