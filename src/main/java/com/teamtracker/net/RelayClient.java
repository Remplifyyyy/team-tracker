package com.teamtracker.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teamtracker.config.Config;
import com.teamtracker.model.TeamStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RelayClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("teamtracker-relay");
    private static final int MAX_MESSAGE_CHARS = 16 * 1024;

    private final Minecraft client;
    private final Config config;
    private final TeamStore store;
    private final HttpClient httpClient;

    private volatile WebSocket socket;
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final AtomicBoolean positionSendInFlight = new AtomicBoolean(false);

    private volatile long nextReconnectAtMs;
    private volatile String connectedTeam = "";
    private volatile String connectedUsername = "";

    private long lastSendAtMs;
    private double lastSentX;
    private double lastSentY;
    private double lastSentZ;
    private String lastSentDimension = "";
    private boolean hasSentPosition;

    public RelayClient(Minecraft client, Config config, TeamStore store) {
        this.client = client;
        this.config = config;
        this.store = store;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public void tick() {
        // The visual toggle is local-only. Networking stays active while a team is configured.
        if (config.cleanTeamCode().isEmpty() || client.player == null || client.level == null) {
            if (socket != null || connecting.get()) disconnect(true);
            return;
        }

        long now = System.currentTimeMillis();
        store.removeStale(now, config.stalePlayerMs);

        String wantedTeam = config.cleanTeamCode();
        String username = client.player.getGameProfile().name();

        if (socket == null) {
            if (now >= nextReconnectAtMs) connect(wantedTeam, username);
            return;
        }

        if (!wantedTeam.equals(connectedTeam) || !username.equals(connectedUsername)) {
            disconnect(false);
            nextReconnectAtMs = 0L;
            return;
        }

        maybeSendPosition(now, client.player);
    }

    /** Resend straight away so teammates pick up a new nickname without waiting for the heartbeat. */
    public void nicknameChanged() {
        hasSentPosition = false;
    }

    public void reconnectNow() {
        disconnect(false);
        nextReconnectAtMs = 0L;
    }

    public boolean isConnected() {
        return socket != null;
    }

    public boolean isConnecting() {
        return connecting.get();
    }

    public void disconnect(boolean clearStore) {
        WebSocket old = socket;
        socket = null;
        connecting.set(false);
        positionSendInFlight.set(false);
        connectedTeam = "";
        connectedUsername = "";
        hasSentPosition = false;
        if (old != null) {
            try {
                old.sendClose(WebSocket.NORMAL_CLOSURE, "client disconnect");
            } catch (Exception ignored) {
                old.abort();
            }
        }
        if (clearStore) store.clear();
    }

    private void connect(String team, String username) {
        if (!connecting.compareAndSet(false, true)) return;

        URI uri;
        try {
            uri = buildUri(team);
        } catch (Exception e) {
            connecting.set(false);
            scheduleReconnect();
            LOGGER.warn("Invalid Team Tracker relay URL: {}", e.getMessage());
            return;
        }

        httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .buildAsync(uri, new Listener(team, username))
                .whenComplete((webSocket, error) -> {
                    if (error != null) {
                        connecting.set(false);
                        scheduleReconnect();
                        LOGGER.warn("Relay connect failed: {}", error.getMessage());
                    }
                });
    }

    private URI buildUri(String team) {
        String base = config.relayUrl == null ? "" : config.relayUrl.trim();
        if (base.startsWith("https://")) base = "wss://" + base.substring(8);
        if (base.startsWith("http://")) base = "ws://" + base.substring(7);
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (!base.startsWith("wss://") && !base.startsWith("ws://")) {
            throw new IllegalArgumentException("Relay URL must begin with wss:// or ws://");
        }
        return URI.create(base + "/ws?team=" + URLEncoder.encode(team, StandardCharsets.UTF_8));
    }

    private String currentDimension() {
        return client.level == null ? "unknown" : client.level.dimension().identifier().toString();
    }

    private void maybeSendPosition(long now, LocalPlayer player) {
        String dimension = currentDimension();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        double dx = x - lastSentX;
        double dy = y - lastSentY;
        double dz = z - lastSentZ;
        double movedSq = dx * dx + dy * dy + dz * dz;
        double thresholdSq = config.movementThresholdBlocks * config.movementThresholdBlocks;

        boolean dimensionChanged = !dimension.equals(lastSentDimension);
        boolean intervalReady = now - lastSendAtMs >= config.sendIntervalMs;
        boolean movedEnough = movedSq >= thresholdSq;
        boolean heartbeatDue = now - lastSendAtMs >= config.heartbeatMs;

        if (!hasSentPosition || dimensionChanged || (intervalReady && movedEnough) || heartbeatDue) {
            sendPosition(x, y, z, dimension, now);
        }
    }

    private void sendPosition(double x, double y, double z, String dimension, long now) {
        WebSocket current = socket;
        if (current == null || !positionSendInFlight.compareAndSet(false, true)) return;

        JsonObject json = new JsonObject();
        json.addProperty("t", "pos");
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("z", z);
        json.addProperty("dimension", dimension);
        addNickname(json);

        current.sendText(json.toString(), true).whenComplete((ignored, error) -> {
            positionSendInFlight.set(false);
            if (error != null) return;
            lastSentX = x;
            lastSentY = y;
            lastSentZ = z;
            lastSentDimension = dimension;
            lastSendAtMs = now;
            hasSentPosition = true;
        });
    }

    private void sendHello(WebSocket webSocket, String team, String username) {
        JsonObject hello = new JsonObject();
        hello.addProperty("t", "hello");
        hello.addProperty("u", username);
        hello.addProperty("team", team);
        hello.addProperty("dimension", currentDimension());
        addNickname(hello);
        webSocket.sendText(hello.toString(), true);
    }

    /**
     * Optional extra field. Only added when a nickname is set, so with no nickname the messages are
     * exactly the same as older versions send. Older versions ignore unknown fields.
     */
    private void addNickname(JsonObject json) {
        String nick = Config.cleanNickname(config.nickname);
        if (!nick.isEmpty()) json.addProperty("nick", nick);
    }

    private void scheduleReconnect() {
        nextReconnectAtMs = System.currentTimeMillis() + Math.max(1000L, config.reconnectDelayMs);
    }

    private final class Listener implements WebSocket.Listener {
        private final String team;
        private final String username;
        private final StringBuilder textBuffer = new StringBuilder();

        private Listener(String team, String username) {
            this.team = team;
            this.username = username;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            socket = webSocket;
            connecting.set(false);
            connectedTeam = team;
            connectedUsername = username;
            nextReconnectAtMs = 0L;
            hasSentPosition = false;
            sendHello(webSocket, team, username);
            webSocket.request(1);
            LOGGER.info("Connected to Team Tracker relay for team {}", team);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            synchronized (textBuffer) {
                textBuffer.append(data);
                if (textBuffer.length() > MAX_MESSAGE_CHARS) {
                    textBuffer.setLength(0);
                    webSocket.abort();
                    return CompletableFuture.completedFuture(null);
                }
                if (last) {
                    String message = textBuffer.toString();
                    textBuffer.setLength(0);
                    handleMessage(message);
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (socket == webSocket) socket = null;
            connecting.set(false);
            positionSendInFlight.set(false);
            scheduleReconnect();
            LOGGER.info("Team Tracker relay closed: {} {}", statusCode, reason);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            if (socket == webSocket) socket = null;
            connecting.set(false);
            positionSendInFlight.set(false);
            scheduleReconnect();
            LOGGER.warn("Team Tracker relay error: {}", error.getMessage());
        }

        private void handleMessage(String message) {
            try {
                JsonObject json = JsonParser.parseString(message).getAsJsonObject();
                String type = string(json, "t");
                if ("pos".equals(type)) {
                    String remoteUsername = string(json, "u");
                    String dimension = string(json, "dimension");
                    if (remoteUsername == null || remoteUsername.equalsIgnoreCase(username) || dimension == null) return;
                    if (!json.has("x") || !json.has("y") || !json.has("z")) return;
                    store.update(
                            remoteUsername,
                            json.get("x").getAsDouble(),
                            json.get("y").getAsDouble(),
                            json.get("z").getAsDouble(),
                            dimension,
                            System.currentTimeMillis()
                    );
                    // Every position carries the nickname (if any), so missing it = they cleared it.
                    store.setNickname(remoteUsername, Config.cleanNickname(string(json, "nick")));
                } else if ("hello".equals(type)) {
                    String remoteUsername = string(json, "u");
                    if (remoteUsername != null && !remoteUsername.equalsIgnoreCase(username)) {
                        store.setNickname(remoteUsername, Config.cleanNickname(string(json, "nick")));
                    }
                } else if ("leave".equals(type)) {
                    String remoteUsername = string(json, "u");
                    if (remoteUsername != null) store.remove(remoteUsername);
                }
            } catch (Exception ignored) {
            }
        }

        private String string(JsonObject json, String key) {
            return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : null;
        }
    }
}
