package com.teamtracker;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teamtracker.config.ColorMode;
import com.teamtracker.config.Config;
import com.teamtracker.config.MarkerSymbol;
import com.teamtracker.model.TeamStore;
import com.teamtracker.net.RelayClient;
import com.teamtracker.render.TeamMarkerRenderer;
import com.teamtracker.screen.SettingsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class TeamTrackerClient implements ClientModInitializer {
    public static final String MOD_ID = "teamtracker";

    private Config config;
    private TeamStore store;
    private RelayClient relay;
    private TeamMarkerRenderer renderer;

    private KeyMapping toggleKey;
    private KeyMapping menuKey;
    private KeyMapping revealKey;
    private KeyMapping cycleSymbolKey;
    private KeyMapping cycleColourKey;
    private KeyMapping toggleNamesKey;
    private KeyMapping toggleDistanceKey;
    private KeyMapping toggleLookKey;
    private boolean openMenuNextTick;

    @Override
    public void onInitializeClient() {
        Minecraft client = Minecraft.getInstance();
        config = Config.load();
        store = new TeamStore();
        relay = new RelayClient(client, config, store);

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "main")
        );
        toggleKey = key("key.teamtracker.toggle", InputConstants.KEY_O, category);
        menuKey = key("key.teamtracker.menu", InputConstants.KEY_RSHIFT, category);
        // Unbound by default so they don't clash with anything. Bind them in Controls.
        revealKey = key("key.teamtracker.reveal", InputConstants.UNKNOWN.getValue(), category);
        cycleSymbolKey = key("key.teamtracker.cycle_symbol", InputConstants.UNKNOWN.getValue(), category);
        cycleColourKey = key("key.teamtracker.cycle_colour", InputConstants.UNKNOWN.getValue(), category);
        toggleNamesKey = key("key.teamtracker.toggle_names", InputConstants.UNKNOWN.getValue(), category);
        toggleDistanceKey = key("key.teamtracker.toggle_distance", InputConstants.UNKNOWN.getValue(), category);
        toggleLookKey = key("key.teamtracker.toggle_look", InputConstants.UNKNOWN.getValue(), category);

        renderer = new TeamMarkerRenderer(client, config, store, () -> revealKey.isDown());
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MOD_ID, "teammates"),
                renderer::render
        );

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (toggleKey.consumeClick()) {
                setEnabled(!config.enabled);
                notify("Team Tracker " + (config.enabled ? "ON" : "OFF"));
            }
            while (menuKey.consumeClick()) {
                openMenuNextTick = true;
            }
            while (cycleSymbolKey.consumeClick()) {
                MarkerSymbol next = config.symbol().next(1);
                config.symbol = next.name();
                config.save();
                notify("Symbol: " + next.label);
            }
            while (cycleColourKey.consumeClick()) {
                ColorMode next = config.colorMode().next(1);
                config.colorMode = next.name();
                config.save();
                notify("Colour: " + next.label);
            }
            while (toggleNamesKey.consumeClick()) {
                config.showName = !config.showName;
                config.save();
                notify("Names " + (config.showName ? "ON" : "OFF"));
            }
            while (toggleDistanceKey.consumeClick()) {
                config.showDistance = !config.showDistance;
                config.save();
                notify("Distance " + (config.showDistance ? "ON" : "OFF"));
            }
            while (toggleLookKey.consumeClick()) {
                config.lookToReveal = !config.lookToReveal;
                config.save();
                notify("Only show text when looking: " + (config.lookToReveal ? "ON" : "OFF"));
            }
            // Opened from the tick (not straight from the command) so the closing chat screen doesn't replace it.
            if (openMenuNextTick && mc.gui.screen() == null) {
                openMenuNextTick = false;
                mc.gui.setScreen(new SettingsScreen(config, this, renderer));
            }
            relay.tick();
        });

        registerCommands();
    }

    private static KeyMapping key(String name, int code, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, InputConstants.Type.KEYSYM, code, category));
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("teamtracker")
                    .executes(ctx -> {
                        openMenuNextTick = true;
                        return 1;
                    })
                    .then(ClientCommands.literal("status").executes(ctx -> {
                        ctx.getSource().sendFeedback(Component.literal("Team Tracker: " + (config.enabled ? "ON" : "OFF")
                                + " | " + statusLine()));
                        return 1;
                    }))
                    .then(ClientCommands.literal("on").executes(ctx -> {
                        setEnabled(true);
                        ctx.getSource().sendFeedback(Component.literal("Team Tracker enabled."));
                        return 1;
                    }))
                    .then(ClientCommands.literal("off").executes(ctx -> {
                        setEnabled(false);
                        ctx.getSource().sendFeedback(Component.literal("Team Tracker disabled."));
                        return 1;
                    }))
                    .then(ClientCommands.literal("toggle").executes(ctx -> {
                        setEnabled(!config.enabled);
                        ctx.getSource().sendFeedback(Component.literal("Team Tracker " + (config.enabled ? "enabled" : "disabled") + "."));
                        return 1;
                    }))
                    .then(ClientCommands.literal("menu").executes(ctx -> {
                        openMenuNextTick = true;
                        return 1;
                    }))
                    .then(ClientCommands.literal("team")
                            .then(ClientCommands.argument("code", StringArgumentType.word()).executes(ctx -> {
                                String code = StringArgumentType.getString(ctx, "code").trim();
                                applyTeamCode(code);
                                ctx.getSource().sendFeedback(Component.literal("Team Tracker team code set to: " + code));
                                return 1;
                            })))
                    .then(ClientCommands.literal("nick")
                            .executes(ctx -> {
                                config.nickname = "";
                                onNicknameChanged();
                                ctx.getSource().sendFeedback(Component.literal("Team Tracker nickname cleared."));
                                return 1;
                            })
                            .then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
                                config.nickname = Config.cleanNickname(StringArgumentType.getString(ctx, "name"));
                                onNicknameChanged();
                                ctx.getSource().sendFeedback(Component.literal("Team Tracker nickname set to: " + config.nickname));
                                return 1;
                            })))
                    .then(ClientCommands.literal("reconnect").executes(ctx -> {
                        reconnect();
                        ctx.getSource().sendFeedback(Component.literal("Team Tracker reconnect requested."));
                        return 1;
                    }))
            );
        });
    }

    // ------------------------------------------------------------------ used by the menu

    /** Same effect as /teamtracker team <code>. An empty code disconnects from the relay. */
    public void applyTeamCode(String code) {
        config.teamCode = code == null ? "" : code.trim();
        config.save();
        store.clear();
        relay.reconnectNow();
    }

    public void reconnect() {
        relay.reconnectNow();
    }

    public void onNicknameChanged() {
        config.save();
        relay.nicknameChanged();
    }

    public String statusLine() {
        String team = config.cleanTeamCode().isEmpty() ? "no team" : config.cleanTeamCode();
        String connection = relay.isConnected() ? "connected" : relay.isConnecting() ? "connecting" : "offline";
        int n = store.size();
        return team + " · " + connection + " · " + n + (n == 1 ? " teammate" : " teammates");
    }

    private void setEnabled(boolean enabled) {
        // Local visual toggle only. Keep the WebSocket connected so teammates can still see this player.
        config.enabled = enabled;
        config.save();
    }

    /** Short message above the hotbar. */
    private static void notify(String text) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.gui.hud.setOverlayMessage(Component.literal(text), false);
        }
    }
}
