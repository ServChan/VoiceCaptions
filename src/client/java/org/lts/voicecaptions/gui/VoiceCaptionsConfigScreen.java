package org.lts.voicecaptions.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lts.voicecaptions.VoiceCaptionsConfig;
import org.lts.voicecaptions.VoiceCaptionsManager;
import org.lts.voicecaptions.command.VoiceCaptionsCommands;
import org.lts.voicecaptions.stt.TranscriptionClient;

public final class VoiceCaptionsConfigScreen extends Screen {
    private static final int BG_CONTAINER = 0xF00D141F;
    private static final int BG_HEADER = 0xFF182638;
    private static final int BORDER_PRIMARY = 0xFF2E435E;
    private static final int TEXT_LABEL = 0xFFD8DEE9;
    private static final int TEXT_DIM = 0xFF8FA7BF;
    private static final int TEXT_NORMAL = 0xFFE0E0E0;
    private static final int STATUS_ON = 0xFF55FF55;
    private static final int STATUS_OFF = 0xFFFF5555;
    private static final int FIELD_HEIGHT = 20;
    private static final int ROW_START = 60;
    private static final int RECOGNITION_TAB = 0;
    private static final int APPEARANCE_TAB = 1;

    private static int lastTab = RECOGNITION_TAB;

    private record Field(EditBox box, String labelKey, int labelX, int labelY, Predicate<String> valid,
                         BiConsumer<VoiceCaptionsConfig, String> commit, Component rangeHint) {
    }

    private final Screen parent;
    private final VoiceCaptionsConfig draft;
    private final List<Field> fields = new ArrayList<>();
    private int tab = lastTab;
    private Button saveButton;
    private String validationError = "";
    private Component status = Component.empty();
    private int statusColor = TEXT_DIM;

    public VoiceCaptionsConfigScreen(Screen parent) {
        super(Component.translatable("voicecaptions.screen.title"));
        this.parent = parent;
        this.draft = VoiceCaptionsConfig.currentCopy();
    }

    @Override
    protected void init() {
        fields.clear();
        int contentWidth = Math.min(520, this.width - 24);
        int left = (this.width - contentWidth) / 2;
        int gap = 10;
        int half = (contentWidth - gap) / 2;
        int right = left + half + gap;
        int rowHeight = Math.max(22, Math.min(28, (this.height - ROW_START - 64) / 6));

        addRenderableWidget(Button.builder(tabLabel(RECOGNITION_TAB), button -> switchTab(RECOGNITION_TAB))
                .bounds(left, 32, half, FIELD_HEIGHT).build());
        addRenderableWidget(Button.builder(tabLabel(APPEARANCE_TAB), button -> switchTab(APPEARANCE_TAB))
                .bounds(right, 32, half, FIELD_HEIGHT).build());

        int y = ROW_START;
        if (tab == RECOGNITION_TAB) {
            textField(left, y, contentWidth, 110, "voicecaptions.option.endpoint", draft.endpoint, 256,
                    value -> !value.isBlank() && (value.startsWith("http://") || value.startsWith("https://")),
                    (config, value) -> config.endpoint = value.strip(), "voicecaptions.error.endpoint");
            y += rowHeight;
            textField(left, y, half, 70, "voicecaptions.option.model", draft.model, 96,
                    value -> true, (config, value) -> config.model = value.strip(), null);
            textField(right, y, half, 70, "voicecaptions.option.language", draft.language, 3,
                    VoiceCaptionsConfig::isValidLanguage, (config, value) -> config.language = value.strip(),
                    "voicecaptions.error.language");
            y += rowHeight;
            intField(left, y, half, "voicecaptions.option.timeout", draft.requestTimeoutSeconds, 5, 120,
                    (config, value) -> config.requestTimeoutSeconds = value);
            intField(right, y, half, "voicecaptions.option.max_queue", draft.maxQueue, 1, 20,
                    (config, value) -> config.maxQueue = value);
            y += rowHeight;
            intField(left, y, half, "voicecaptions.option.silence_gap", draft.silenceGapMs, 300, 2000,
                    (config, value) -> config.silenceGapMs = value);
            intField(right, y, half, "voicecaptions.option.max_phrase", draft.maxPhraseSeconds, 3, 20,
                    (config, value) -> config.maxPhraseSeconds = value);
            y += rowHeight;
            toggle(left, y, half, "voicecaptions.option.enabled", config -> config.enabled,
                    (config, value) -> config.enabled = value);
            toggle(right, y, half, "voicecaptions.option.only_audible", config -> config.onlyAudible,
                    (config, value) -> config.onlyAudible = value);
        } else {
            intField(left, y, half, "voicecaptions.option.max_width", draft.bubbleMaxWidth, 60, 400,
                    (config, value) -> config.bubbleMaxWidth = value);
            intField(right, y, half, "voicecaptions.option.max_lines", draft.maxLines, 1, 8,
                    (config, value) -> config.maxLines = value);
            y += rowHeight;
            doubleField(left, y, half, "voicecaptions.option.caption_seconds", draft.captionSeconds, 1.0, 30.0,
                    (config, value) -> config.captionSeconds = value);
            intField(right, y, half, "voicecaptions.option.max_distance", draft.maxDistance, 4, 128,
                    (config, value) -> config.maxDistance = value);
            y += rowHeight;
            doubleField(left, y, half, "voicecaptions.option.text_scale", draft.textScale, 0.5, 2.0,
                    (config, value) -> config.textScale = value);
            doubleField(right, y, half, "voicecaptions.option.far_scale", draft.farScale, 0.3, 1.0,
                    (config, value) -> config.farScale = value);
            y += rowHeight;
            doubleField(left, y, half, "voicecaptions.option.height_offset", draft.heightOffset, 0.0, 3.0,
                    (config, value) -> config.heightOffset = value);
            intField(right, y, half, "voicecaptions.option.background_opacity", draft.backgroundOpacity, 0, 255,
                    (config, value) -> config.backgroundOpacity = value);
            y += rowHeight;
            toggle(left, y, half, "voicecaptions.option.through_walls", config -> config.showThroughWalls,
                    (config, value) -> config.showThroughWalls = value);
            toggle(right, y, half, "voicecaptions.option.speaking_indicator", config -> config.showSpeakingIndicator,
                    (config, value) -> config.showSpeakingIndicator = value);
            y += rowHeight;
            toggle(left, y, half, "voicecaptions.option.show_name", config -> config.showName,
                    (config, value) -> config.showName = value);
        }

        int buttonY = this.height - 28;
        int buttonWidth = Math.min(150, (contentWidth - 20) / 3);
        int buttonsLeft = this.width / 2 - (buttonWidth * 3 + 20) / 2;
        Button action = tab == RECOGNITION_TAB
                ? Button.builder(Component.translatable("voicecaptions.button.check"), button -> check())
                .tooltip(Tooltip.create(Component.translatable("voicecaptions.button.check.tooltip"))).build()
                : Button.builder(Component.translatable("voicecaptions.button.preview"), button -> preview())
                .tooltip(Tooltip.create(Component.translatable("voicecaptions.button.preview.tooltip"))).build();
        action.setRectangle(buttonWidth, FIELD_HEIGHT, buttonsLeft, buttonY);
        addRenderableWidget(action);
        saveButton = addRenderableWidget(Button.builder(Component.translatable("voicecaptions.button.save"),
                button -> saveAndClose()).bounds(buttonsLeft + buttonWidth + 10, buttonY, buttonWidth, FIELD_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("voicecaptions.button.cancel"),
                button -> onClose()).bounds(buttonsLeft + (buttonWidth + 10) * 2, buttonY, buttonWidth, FIELD_HEIGHT).build());
        validate();
    }

    private void textField(int x, int y, int width, int labelWidth, String key, String value, int maxLength,
                           Predicate<String> valid, BiConsumer<VoiceCaptionsConfig, String> commit, String errorKey) {
        EditBox box = new EditBox(this.font, x + labelWidth, y, width - labelWidth, FIELD_HEIGHT, Component.translatable(key));
        box.setMaxLength(maxLength);
        box.setValue(value == null ? "" : value);
        box.setTooltip(Tooltip.create(Component.translatable(key + ".tooltip")));
        box.setResponder(ignored -> validate());
        addRenderableWidget(box);
        fields.add(new Field(box, key, x, y + 6, valid, commit,
                errorKey == null ? Component.empty() : Component.translatable(errorKey)));
    }

    private void intField(int x, int y, int width, String key, int value, int min, int max,
                          BiConsumer<VoiceCaptionsConfig, Integer> setter) {
        int boxWidth = Math.max(48, width / 3);
        EditBox box = new EditBox(this.font, x + width - boxWidth, y, boxWidth, FIELD_HEIGHT, Component.translatable(key));
        box.setMaxLength(6);
        box.setValue(Integer.toString(value));
        box.setTooltip(Tooltip.create(Component.translatable(key + ".tooltip")));
        box.setResponder(ignored -> validate());
        addRenderableWidget(box);
        fields.add(new Field(box, key, x, y + 6, text -> parseInt(text) != null && parseInt(text) >= min && parseInt(text) <= max,
                (config, text) -> setter.accept(config, parseInt(text)),
                Component.translatable("voicecaptions.error.range", min, max)));
    }

    private void doubleField(int x, int y, int width, String key, double value, double min, double max,
                             BiConsumer<VoiceCaptionsConfig, Double> setter) {
        int boxWidth = Math.max(48, width / 3);
        EditBox box = new EditBox(this.font, x + width - boxWidth, y, boxWidth, FIELD_HEIGHT, Component.translatable(key));
        box.setMaxLength(6);
        box.setValue(formatDouble(value));
        box.setTooltip(Tooltip.create(Component.translatable(key + ".tooltip")));
        box.setResponder(ignored -> validate());
        addRenderableWidget(box);
        fields.add(new Field(box, key, x, y + 6,
                text -> parseDouble(text) != null && parseDouble(text) >= min && parseDouble(text) <= max,
                (config, text) -> setter.accept(config, parseDouble(text)),
                Component.translatable("voicecaptions.error.range", formatDouble(min), formatDouble(max))));
    }

    private void toggle(int x, int y, int width, String key, Function<VoiceCaptionsConfig, Boolean> getter,
                        BiConsumer<VoiceCaptionsConfig, Boolean> setter) {
        addRenderableWidget(Button.builder(toggleLabel(key, getter.apply(draft)), button -> {
            setter.accept(draft, !getter.apply(draft));
            button.setMessage(toggleLabel(key, getter.apply(draft)));
        }).tooltip(Tooltip.create(Component.translatable(key + ".tooltip"))).bounds(x, y, width, FIELD_HEIGHT).build());
    }

    private static Component toggleLabel(String key, boolean enabled) {
        return Component.translatable(key, Component.translatable(enabled ? "voicecaptions.state.on" : "voicecaptions.state.off")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private Component tabLabel(int target) {
        Component label = Component.translatable(target == RECOGNITION_TAB
                ? "voicecaptions.tab.recognition" : "voicecaptions.tab.appearance");
        return tab == target ? label.copy().withStyle(ChatFormatting.UNDERLINE) : label;
    }

    private void switchTab(int target) {
        if (target == tab) {
            return;
        }
        commitValid();
        tab = target;
        lastTab = target;
        clearWidgets();
        init();
    }

    private void commitValid() {
        for (Field field : fields) {
            String text = field.box().getValue();
            if (field.valid().test(text)) {
                field.commit().accept(draft, text);
            }
        }
    }

    private boolean validate() {
        validationError = "";
        boolean ok = true;
        for (Field field : fields) {
            boolean valid = field.valid().test(field.box().getValue());
            field.box().setTextColor(valid ? TEXT_NORMAL : STATUS_OFF);
            if (!valid) {
                if (ok) {
                    validationError = Component.translatable(field.labelKey()).getString() + ": "
                            + field.rangeHint().getString();
                }
                ok = false;
            }
        }
        if (saveButton != null) {
            saveButton.active = ok;
        }
        return ok;
    }

    private boolean commitAndSave() {
        if (!validate()) {
            return false;
        }
        commitValid();
        if (!VoiceCaptionsConfig.save(draft)) {
            validationError = Component.translatable("voicecaptions.error.save").getString();
            return false;
        }
        return true;
    }

    private void saveAndClose() {
        if (commitAndSave()) {
            onClose();
        }
    }

    private void check() {
        if (!commitAndSave()) {
            return;
        }
        status = Component.translatable("voicecaptions.check.started", VoiceCaptionsConfig.current().endpoint);
        statusColor = TEXT_DIM;
        VoiceCaptionsManager.getInstance().probe(result -> {
            if (this.minecraft == null) {
                return;
            }
            this.minecraft.execute(() -> {
                if (result instanceof TranscriptionClient.Success success) {
                    status = Component.translatable("voicecaptions.check.ok", success.latencyMs());
                    statusColor = STATUS_ON;
                } else {
                    status = VoiceCaptionsManager.failureText((TranscriptionClient.Failure) result);
                    statusColor = STATUS_OFF;
                }
            });
        });
    }

    private void preview() {
        if (!commitAndSave() || this.minecraft == null) {
            return;
        }
        Minecraft client = this.minecraft;
        onClose();
        VoiceCaptionsCommands.showTest(client, Component.translatable("voicecaptions.test.sample").getString());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        graphics.fill(0, 0, this.width, this.height, BG_CONTAINER);
        graphics.fill(0, 0, this.width, 28, BG_HEADER);
        graphics.fill(0, 27, this.width, 28, BORDER_PRIMARY);
        graphics.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);
        for (Field field : fields) {
            int labelRight = field.box().getX() - 6;
            String label = Component.translatable(field.labelKey()).getString();
            int available = labelRight - field.labelX() - 4;
            if (this.font.width(label) > available && available > 12) {
                label = this.font.plainSubstrByWidth(label, available - this.font.width("…")) + "…";
            }
            graphics.text(this.font, label, field.labelX() + 4, field.labelY(), TEXT_LABEL, false);
        }
        int messageY = this.height - 44;
        if (!validationError.isBlank()) {
            graphics.centeredText(this.font, Component.literal(validationError), this.width / 2, messageY, STATUS_OFF);
        } else if (!status.getString().isEmpty()) {
            graphics.centeredText(this.font, status, this.width / 2, messageY, statusColor);
        } else if (tab == RECOGNITION_TAB) {
            graphics.centeredText(this.font, Component.translatable("voicecaptions.screen.hint"), this.width / 2,
                    messageY, TEXT_DIM);
        }
        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(parent);
        }
    }

    private static Integer parseInt(String text) {
        try {
            return Integer.parseInt(text.strip());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Double parseDouble(String text) {
        try {
            double value = Double.parseDouble(text.strip().replace(',', '.'));
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String formatDouble(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(Math.round(value * 100.0) / 100.0);
    }
}
