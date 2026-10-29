package com.teamtracker.screen;

import com.teamtracker.TeamTrackerClient;
import com.teamtracker.config.ColorMode;
import com.teamtracker.config.Config;
import com.teamtracker.config.MarkerSymbol;
import com.teamtracker.render.TeamMarkerRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/** Tabbed settings menu with a live preview, so nothing needs commands. */
public final class SettingsScreen extends Screen {
    private enum Tab {
        TEXT("Text"), SYMBOL("Symbol"), COLOURS("Colours"), VISIBILITY("Visibility"), TEAM("Team");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private static final int PANEL_W = 404;
    private static final int PANEL_H = 236;
    private static final int ROW_H = 20;
    private static final int STEP = 24;
    private static final int COL_W = 226;

    private static final int PANEL_BG = 0xE0101318;
    private static final int PANEL_EDGE = 0xFF2C3340;
    private static final int PREVIEW_BG = 0xFF1A2230;
    private static final int TEXT_MAIN = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFF9AA3B2;
    private static final int ACCENT = 0xFF55FFFF;

    private static Tab lastTab = Tab.TEXT;

    private final Config config;
    private final TeamTrackerClient mod;
    private final TeamMarkerRenderer renderer;

    private Tab tab = lastTab;
    private String pendingTeamCode;
    private Button colourButton;
    private final List<Label> labels = new ArrayList<>();

    private int left;
    private int top;

    private record Label(String text, int x, int y, int color) {
    }

    public SettingsScreen(Config config, TeamTrackerClient mod, TeamMarkerRenderer renderer) {
        super(Component.literal("Team Tracker"));
        this.config = config;
        this.mod = mod;
        this.renderer = renderer;
        this.pendingTeamCode = config.teamCode == null ? "" : config.teamCode;
    }

    @Override
    protected void init() {
        labels.clear();
        colourButton = null;
        left = Math.max(0, this.width / 2 - PANEL_W / 2);
        top = Math.max(0, this.height / 2 - PANEL_H / 2);

        // Tabs. The open tab is shown greyed out.
        Tab[] tabs = Tab.values();
        int tabW = (PANEL_W - 20 - (tabs.length - 1) * 2) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            Button b = Button.builder(Component.literal(t.label), btn -> switchTab(t))
                    .bounds(left + 10 + i * (tabW + 2), top + 22, tabW, ROW_H).build();
            b.active = t != tab;
            addRenderableWidget(b);
        }

        int x = left + 10;
        int y = top + 52;
        switch (tab) {
            case TEXT -> buildText(x, y);
            case SYMBOL -> buildSymbol(x, y);
            case COLOURS -> buildColours(x, y);
            case VISIBILITY -> buildVisibility(x, y);
            case TEAM -> buildTeam(x, y);
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(this.width / 2 - 75, top + PANEL_H - 28, 150, ROW_H).build());
    }

    // ------------------------------------------------------------------ tabs

    private void buildText(int x, int y) {
        addToggle("Show Name", x, y, COL_W, () -> config.showName, v -> config.showName = v);
        addSlider(x, y + STEP, COL_W, 0.5, 3.0, 0.05, () -> config.nameScale, v -> config.nameScale = v,
                v -> String.format(Locale.ROOT, "Name Size: %.2fx", v));
        addToggle("Show Distance", x, y + STEP * 2, COL_W, () -> config.showDistance, v -> config.showDistance = v);
        addSlider(x, y + STEP * 3, COL_W, 0.5, 3.0, 0.05, () -> config.distanceScale, v -> config.distanceScale = v,
                v -> String.format(Locale.ROOT, "Distance Size: %.2fx", v));
        addChoice(x, y + STEP * 4, COL_W, () -> "Layout: " + (config.inlineDistance ? "One Line" : "Stacked"),
                () -> config.inlineDistance = !config.inlineDistance);
        int half = (COL_W - 4) / 2;
        addToggle("Shadow", x, y + STEP * 5, half, () -> config.textShadow, v -> config.textShadow = v);
        addToggle("Backing", x + half + 4, y + STEP * 5, half, () -> config.textBackground, v -> config.textBackground = v);
    }

    private void buildSymbol(int x, int y) {
        addCycle(x, y, COL_W, () -> "Symbol: " + config.symbol().label,
                dir -> config.symbol = config.symbol().next(dir).name());
        addSlider(x, y + STEP, COL_W, 3, 24, 1, () -> config.symbolSize, v -> config.symbolSize = (int) Math.round(v),
                v -> "Symbol Size: " + Math.round(v) + "px");
        addToggle("Outline", x, y + STEP * 2, COL_W, () -> config.symbolOutline, v -> config.symbolOutline = v);
        label("Symbols: None, Arrow, Chevron, Dot, Ring,", x, y + STEP * 3 + 4, TEXT_DIM);
        label("Diamond, Square, Triangle, Plus, Cross, Head.", x, y + STEP * 3 + 15, TEXT_DIM);
        label("Symbols use the teammate's colour.", x, y + STEP * 4 + 10, TEXT_DIM);
    }

    private void buildColours(int x, int y) {
        colourButton = addCycle(x, y, COL_W, () -> "Colour: " + config.colorMode().label,
                dir -> config.colorMode = config.colorMode().next(dir).name());

        label("Custom", x, y + STEP + 6, TEXT_MAIN);
        EditBox hex = new EditBox(this.font, x + 60, y + STEP, COL_W - 60, ROW_H, Component.literal("Custom colour"));
        hex.setMaxLength(7);
        hex.setHint(Component.literal("#RRGGBB"));
        hex.setValue(String.format(Locale.ROOT, "#%06X", config.customColor & 0xFFFFFF));
        hex.setResponder(value -> {
            String v = value.trim();
            if (v.startsWith("#")) v = v.substring(1);
            if (v.matches("[0-9A-Fa-f]{6}")) {
                config.customColor = Integer.parseInt(v, 16);
                config.colorMode = ColorMode.CUSTOM.name();
                if (colourButton != null) colourButton.setMessage(Component.literal("Colour: " + config.colorMode().label));
            }
        });
        addRenderableWidget(hex);

        addChoice(x, y + STEP * 2, COL_W, () -> "Distance Colour: " + (config.distanceUsesColor ? "Match" : "White"),
                () -> config.distanceUsesColor = !config.distanceUsesColor);

        label("Unique = every teammate gets their own", x, y + STEP * 3 + 4, TEXT_DIM);
        label("colour. Any other option colours everyone", x, y + STEP * 3 + 15, TEXT_DIM);
        label("the same. Typing a hex code picks Custom.", x, y + STEP * 3 + 26, TEXT_DIM);
    }

    private void buildVisibility(int x, int y) {
        addToggle("Tracker", x, y, COL_W, () -> config.enabled, v -> config.enabled = v);
        addToggle("Only When Looking", x, y + STEP, COL_W, () -> config.lookToReveal, v -> config.lookToReveal = v);
        addSlider(x, y + STEP * 2, COL_W, 5, 300, 1, () -> config.lookRadiusPx, v -> config.lookRadiusPx = v,
                v -> "Look Radius: " + Math.round(v) + "px");
        addSlider(x, y + STEP * 3, COL_W, 0, 200, 1, () -> config.hideWithinBlocks, v -> config.hideWithinBlocks = v,
                v -> Math.round(v) == 0 ? "Hide Within: Off" : "Hide Within: " + Math.round(v) + " blocks");
        addToggle("Range Limit", x, y + STEP * 4, COL_W, () -> !config.distantMarkers, v -> config.distantMarkers = !v);
        addSlider(x, y + STEP * 5, COL_W, 25, 5000, 25, () -> config.distantMarkerBlocks, v -> config.distantMarkerBlocks = v,
                v -> "Max Range: " + Math.round(v) + " blocks");
    }

    private void buildTeam(int x, int y) {
        int labelW = 64;
        label("Team Code", x, y + 6, TEXT_MAIN);
        EditBox team = new EditBox(this.font, x + labelW, y, COL_W - labelW, ROW_H, Component.literal("Team code"));
        team.setMaxLength(64);
        team.setHint(Component.literal("e.g. DOGS"));
        team.setValue(pendingTeamCode);
        team.setResponder(v -> pendingTeamCode = v);
        addRenderableWidget(team);

        label("Nickname", x, y + STEP + 6, TEXT_MAIN);
        EditBox nick = new EditBox(this.font, x + labelW, y + STEP, COL_W - labelW, ROW_H, Component.literal("Nickname"));
        nick.setMaxLength(16);
        nick.setHint(Component.literal("blank = username"));
        nick.setValue(config.nickname == null ? "" : config.nickname);
        nick.setResponder(v -> {
            String clean = Config.cleanNickname(v);
            if (!clean.equals(config.nickname)) {
                config.nickname = clean;
                mod.onNicknameChanged();
            }
        });
        addRenderableWidget(nick);

        addRenderableWidget(Button.builder(Component.literal("Apply & Reconnect"), b -> {
            applyTeamCode();
            mod.reconnect();
        }).bounds(x, y + STEP * 2, COL_W, ROW_H).build());

        label("Teammates on this version see your", x, y + STEP * 4 + 4, TEXT_DIM);
        label("nickname. Older versions show your username.", x, y + STEP * 4 + 15, TEXT_DIM);
    }

    private void switchTab(Tab t) {
        tab = t;
        lastTab = t;
        config.save();
        this.rebuildWidgets();
    }

    // ------------------------------------------------------------------ widget helpers

    private void label(String text, int x, int y, int color) {
        labels.add(new Label(text, x, y, color));
    }

    private void addToggle(String name, int x, int y, int w, BooleanSupplier get, Consumer<Boolean> set) {
        addRenderableWidget(Button.builder(Component.literal(toggleText(name, get.getAsBoolean())), button -> {
            set.accept(!get.getAsBoolean());
            config.save();
            button.setMessage(Component.literal(toggleText(name, get.getAsBoolean())));
        }).bounds(x, y, w, ROW_H).build());
    }

    private static String toggleText(String name, boolean on) {
        return name + ": " + (on ? "ON" : "OFF");
    }

    /** A button that flips between options when clicked. */
    private void addChoice(int x, int y, int w, Supplier<String> text, Runnable flip) {
        addRenderableWidget(Button.builder(Component.literal(text.get()), button -> {
            flip.run();
            config.save();
            button.setMessage(Component.literal(text.get()));
        }).bounds(x, y, w, ROW_H).build());
    }

    /** [<] [ current value ] [>]. Clicking the middle also goes forward. Returns the middle button. */
    private Button addCycle(int x, int y, int w, Supplier<String> text, Consumer<Integer> step) {
        int arrow = 20;
        Button middle = Button.builder(Component.literal(text.get()), b -> {
            step.accept(1);
            config.save();
            b.setMessage(Component.literal(text.get()));
        }).bounds(x + arrow + 2, y, w - (arrow + 2) * 2, ROW_H).build();

        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            step.accept(-1);
            config.save();
            middle.setMessage(Component.literal(text.get()));
        }).bounds(x, y, arrow, ROW_H).build());
        addRenderableWidget(middle);
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            step.accept(1);
            config.save();
            middle.setMessage(Component.literal(text.get()));
        }).bounds(x + w - arrow, y, arrow, ROW_H).build());
        return middle;
    }

    private void addSlider(int x, int y, int w, double min, double max, double step,
                           DoubleSupplier get, DoubleConsumer set, DoubleFunction<String> format) {
        addRenderableWidget(new ConfigSlider(x, y, w, ROW_H, min, max, step, get, set, format));
    }

    private static final class ConfigSlider extends AbstractSliderButton {
        private final double min;
        private final double max;
        private final double step;
        private final DoubleConsumer set;
        private final DoubleFunction<String> format;

        ConfigSlider(int x, int y, int w, int h, double min, double max, double step,
                     DoubleSupplier get, DoubleConsumer set, DoubleFunction<String> format) {
            super(x, y, w, h, Component.empty(), toSlider(get.getAsDouble(), min, max));
            this.min = min;
            this.max = max;
            this.step = step;
            this.set = set;
            this.format = format;
            updateMessage();
        }

        private static double toSlider(double v, double min, double max) {
            return Math.max(0.0, Math.min(1.0, (v - min) / (max - min)));
        }

        private double current() {
            double v = min + (max - min) * this.value;
            if (step > 0) v = Math.round(v / step) * step;
            return Math.max(min, Math.min(max, v));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(format.apply(current())));
        }

        @Override
        protected void applyValue() {
            set.accept(current());
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int r = left + PANEL_W;
        int b = top + PANEL_H;
        graphics.fill(left - 1, top - 1, r + 1, b + 1, PANEL_EDGE);
        graphics.fill(left, top, r, b, PANEL_BG);
        graphics.fill(left, top, r, top + 2, ACCENT);

        // Preview box on the right.
        int px = left + 10 + COL_W + 10;
        graphics.fill(px - 1, top + 51, left + PANEL_W - 9, top + 52 + STEP * 6 - 3, PANEL_EDGE);
        graphics.fill(px, top + 52, left + PANEL_W - 10, top + 52 + STEP * 6 - 4, PREVIEW_BG);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.text(this.font, "TEAM TRACKER", left + 10, top + 8, ACCENT, true);
        String status = mod.statusLine();
        graphics.text(this.font, status, left + PANEL_W - 10 - this.font.width(status), top + 8, TEXT_DIM, false);

        for (Label l : labels) {
            graphics.text(this.font, l.text(), l.x(), l.y(), l.color(), false);
        }

        drawPreview(graphics);

        if (tab == Tab.COLOURS) {
            // Colour swatch for the current mode.
            int sx = left + 10;
            int sy = top + 52 + ROW_H + 1;
            int rgb = renderer.colorFor(previewUsername());
            graphics.fill(sx, sy, sx + COL_W, sy + 2, 0xFF000000 | rgb);
        }

        String footer = "Keybinds: Options > Controls > Key Binds > Team Tracker";
        graphics.text(this.font, footer, this.width / 2 - this.font.width(footer) / 2, top + PANEL_H - 40, TEXT_DIM, false);
    }

    private void drawPreview(GuiGraphicsExtractor graphics) {
        int boxLeft = left + 10 + COL_W + 10;
        int boxRight = left + PANEL_W - 10;
        int boxTop = top + 52;
        int boxBottom = top + 52 + STEP * 6 - 4;
        int cx = (boxLeft + boxRight) / 2;

        graphics.text(this.font, "Preview", boxLeft + 4, boxTop + 4, TEXT_DIM, false);

        // Simple player silhouette so the marker sits "above a head".
        int headTop = boxBottom - 44;
        graphics.fill(cx - 4, headTop, cx + 4, headTop + 8, 0xFF3A4458);
        graphics.fill(cx - 4, headTop + 8, cx + 4, headTop + 20, 0xFF2E3747);
        graphics.fill(cx - 7, headTop + 8, cx - 4, headTop + 19, 0xFF283040);
        graphics.fill(cx + 4, headTop + 8, cx + 7, headTop + 19, 0xFF283040);
        graphics.fill(cx - 4, headTop + 20, cx + 4, headTop + 32, 0xFF242B38);

        String user = previewUsername();
        String shown = config.nickname == null || config.nickname.isBlank() ? user : config.nickname;
        renderer.drawPreview(graphics, cx, headTop - 3, shown, user, 42.5);
    }

    private String previewUsername() {
        if (this.minecraft != null && this.minecraft.player != null) {
            return this.minecraft.player.getGameProfile().name();
        }
        return "Steve";
    }

    // ------------------------------------------------------------------ closing

    private void applyTeamCode() {
        String code = pendingTeamCode == null ? "" : pendingTeamCode.replace(" ", "").trim();
        String current = config.teamCode == null ? "" : config.teamCode.trim();
        if (!code.equals(current)) mod.applyTeamCode(code);
    }

    /** Called whenever the screen closes (Done button, Esc, ...). */
    @Override
    public void removed() {
        super.removed();
        applyTeamCode();
        config.save();
    }
}
