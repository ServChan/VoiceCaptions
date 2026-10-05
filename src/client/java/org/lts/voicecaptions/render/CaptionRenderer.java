package org.lts.voicecaptions.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lts.voicecaptions.VoiceCaptionsConfig;
import org.lts.voicecaptions.VoiceCaptionsManager;
import org.lts.voicecaptions.caption.CaptionBoard;
import org.lts.voicecaptions.caption.CaptionLayout;

public final class CaptionRenderer {
    private static final int PAD_X = 5;
    private static final int PAD_Y = 4;
    private static final int TAIL = 3;
    private static final int MIN_WIDTH = 26;
    private static final int DOT_SPACING = 6;
    private static final float SCREEN_MARGIN = 4.0F;
    private static final float BUBBLE_GAP = 2.0F;
    private static final double NEAR_DISTANCE = 4.0;
    private static final double NAME_TAG_HEIGHT = 0.5;
    private static final int BACKGROUND_RGB = 0x0E1621;
    private static final int TEXT_RGB = 0xFFFFFF;
    private static final int OLDER_TEXT_RGB = 0xAFC0D2;
    private static final int NAME_RGB = 0x7FD9FF;
    private static final int DOT_RGB = 0xC8D6E5;
    private static final int PROBLEM_RGB = 0xE8B86A;
    private static final String DOT = "•";
    private static final float MIN_ALPHA = 0.08F;

    private record Bubble(CaptionBoard.SpeakerView view, CaptionLayout.Block block, int newestLines,
                          String name, boolean dots, String problem, int width, int height, float scale,
                          BubblePlacer.Box box) {
    }

    private CaptionRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        VoiceCaptionsConfig config = VoiceCaptionsConfig.current();
        if (!config.enabled) {
            return;
        }
        VoiceCaptionsManager manager = VoiceCaptionsManager.getInstance();
        List<CaptionBoard.SpeakerView> views = manager.views(System.currentTimeMillis());
        if (views.isEmpty()) {
            return;
        }

        Map<UUID, AbstractClientPlayer> players = new HashMap<>();
        for (AbstractClientPlayer player : minecraft.level.players()) {
            players.put(player.getUUID(), player);
        }
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        Camera camera = minecraft.gameRenderer.mainCamera();
        Vec3 cameraPos = camera.position();
        Matrix4f viewProjection = camera.getViewRotationProjectionMatrix(new Matrix4f());
        boolean firstPerson = minecraft.options.getCameraType() == CameraType.FIRST_PERSON;
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        Font font = minecraft.font;
        int lineHeight = font.lineHeight + 1;
        Component problemHint = manager.problemHint();

        List<Bubble> bubbles = new ArrayList<>();
        for (CaptionBoard.SpeakerView view : views) {
            if (view.alpha() < MIN_ALPHA) {
                continue;
            }
            AbstractClientPlayer player = players.get(view.speaker());
            if (player == null || player.isInvisibleTo(minecraft.player)) {
                continue;
            }
            if (player == minecraft.getCameraEntity() && firstPerson) {
                continue;
            }
            if (!config.showThroughWalls && manager.isOccluded(view.speaker())) {
                continue;
            }
            Vec3 feet = player.getPosition(partialTick);
            Vec3 anchor = new Vec3(feet.x, feet.y + player.getBbHeight() + NAME_TAG_HEIGHT + config.heightOffset, feet.z);
            double distance = anchor.distanceTo(cameraPos);
            if (distance > config.maxDistance) {
                continue;
            }
            Vector4f clip = new Vector4f((float) (anchor.x - cameraPos.x), (float) (anchor.y - cameraPos.y),
                    (float) (anchor.z - cameraPos.z), 1.0F).mul(viewProjection);
            if (clip.w <= 0.05F) {
                continue;
            }
            float ndcX = clip.x / clip.w;
            float ndcY = clip.y / clip.w;
            if (Math.abs(ndcX) > 1.25F || Math.abs(ndcY) > 1.25F) {
                continue;
            }
            float anchorX = (ndcX + 1.0F) * 0.5F * screenWidth;
            float anchorY = (1.0F - ndcY) * 0.5F * screenHeight;

            CaptionLayout.Block block = CaptionLayout.layout(view.phrases(), config.bubbleMaxWidth, config.maxLines,
                    font::width);
            int newestLines = 0;
            if (!block.isEmpty()) {
                String newest = view.phrases().get(view.phrases().size() - 1);
                newestLines = Math.min(block.lines().size(),
                        CaptionLayout.wrap(newest, config.bubbleMaxWidth, font::width).size());
            }
            boolean active = view.speaking() || view.transcribing();
            String problem = problemHint != null && active
                    ? fit(font, problemHint.getString(), config.bubbleMaxWidth) : "";
            boolean dots = problem.isEmpty() && config.showSpeakingIndicator && active;
            if (block.isEmpty() && !dots && problem.isEmpty()) {
                continue;
            }
            String name = config.showName && !block.isEmpty() ? displayName(view, player) : "";
            int contentWidth = block.width();
            if (!name.isEmpty()) {
                contentWidth = Math.max(contentWidth, Math.min(font.width(name), config.bubbleMaxWidth));
            }
            if (dots) {
                contentWidth = Math.max(contentWidth, font.width(DOT) + DOT_SPACING * 2);
            }
            if (!problem.isEmpty()) {
                contentWidth = Math.max(contentWidth, font.width(problem));
            }
            int width = Math.max(MIN_WIDTH, contentWidth + PAD_X * 2);
            int rows = block.lines().size() + (name.isEmpty() ? 0 : 1) + (dots || !problem.isEmpty() ? 1 : 0);
            int height = PAD_Y * 2 + rows * lineHeight - 1;

            double t = Math.max(0.0, Math.min(1.0, (distance - NEAR_DISTANCE) / Math.max(1.0, config.maxDistance - NEAR_DISTANCE)));
            float scale = (float) (config.textScale * (1.0 - t * (1.0 - config.farScale)));
            BubblePlacer.Box box = new BubblePlacer.Box(view.speaker(), distance, anchorX, anchorY - TAIL * scale,
                    width * scale, height * scale);
            bubbles.add(new Bubble(view, block, newestLines, name, dots, problem, width, height, scale, box));
        }
        if (bubbles.isEmpty()) {
            return;
        }

        List<BubblePlacer.Box> boxes = new ArrayList<>(bubbles.size());
        for (Bubble bubble : bubbles) {
            boxes.add(bubble.box());
        }
        BubblePlacer.place(boxes, screenWidth, screenHeight, SCREEN_MARGIN, BUBBLE_GAP);
        bubbles.sort((a, b) -> Double.compare(b.box().distance, a.box().distance));
        for (Bubble bubble : bubbles) {
            draw(graphics, font, lineHeight, bubble, config);
        }
    }

    private static void draw(GuiGraphicsExtractor graphics, Font font, int lineHeight, Bubble bubble,
                             VoiceCaptionsConfig config) {
        float alpha = bubble.view().alpha();
        int background = argb(alpha * config.backgroundOpacity / 255.0F, BACKGROUND_RGB);
        int width = bubble.width();
        int height = bubble.height();
        BubblePlacer.Box box = bubble.box();

        graphics.pose().pushMatrix();
        graphics.pose().translate(box.left, box.top);
        graphics.pose().scale(bubble.scale(), bubble.scale());

        graphics.fill(1, 0, width - 1, height, background);
        graphics.fill(0, 1, 1, height - 1, background);
        graphics.fill(width - 1, 1, width, height - 1, background);
        if (!box.lifted) {
            int tailX = Math.round((box.anchorX - box.left) / bubble.scale());
            tailX = Math.max(4, Math.min(width - 5, tailX));
            graphics.fill(tailX - 2, height, tailX + 3, height + 1, background);
            graphics.fill(tailX - 1, height + 1, tailX + 2, height + 2, background);
            graphics.fill(tailX, height + 2, tailX + 1, height + 3, background);
        }

        int y = PAD_Y;
        if (!bubble.name().isEmpty()) {
            String name = bubble.name();
            if (font.width(name) > width - PAD_X * 2) {
                name = font.plainSubstrByWidth(name, width - PAD_X * 2 - font.width(CaptionLayout.ELLIPSIS))
                        + CaptionLayout.ELLIPSIS;
            }
            graphics.text(font, name, PAD_X, y, argb(alpha, NAME_RGB), false);
            y += lineHeight;
        }
        List<String> lines = bubble.block().lines();
        int firstNewest = lines.size() - bubble.newestLines();
        for (int i = 0; i < lines.size(); i++) {
            int rgb = i >= firstNewest ? TEXT_RGB : OLDER_TEXT_RGB;
            graphics.text(font, lines.get(i), PAD_X, y, argb(alpha, rgb), false);
            y += lineHeight;
        }
        if (!bubble.problem().isEmpty()) {
            graphics.text(font, bubble.problem(), PAD_X, y, argb(alpha, PROBLEM_RGB), false);
        }
        if (bubble.dots()) {
            int phase = (int) ((System.currentTimeMillis() / 300L) % 3L);
            int dotsWidth = font.width(DOT) + DOT_SPACING * 2;
            int x = lines.isEmpty() && bubble.name().isEmpty() ? (width - dotsWidth) / 2 : PAD_X;
            for (int i = 0; i < 3; i++) {
                float dotAlpha = alpha * (i == phase ? 1.0F : 0.4F);
                graphics.text(font, DOT, x + i * DOT_SPACING, y, argb(dotAlpha, DOT_RGB), false);
            }
        }
        graphics.pose().popMatrix();
    }

    private static String fit(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, maxWidth - font.width(CaptionLayout.ELLIPSIS)) + CaptionLayout.ELLIPSIS;
    }

    private static String displayName(CaptionBoard.SpeakerView view, AbstractClientPlayer player) {
        if (view.name() != null && !view.name().isBlank()) {
            return view.name();
        }
        return player.getGameProfile().name();
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
