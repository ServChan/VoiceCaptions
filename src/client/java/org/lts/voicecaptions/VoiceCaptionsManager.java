package org.lts.voicecaptions;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lts.voicecaptions.audio.Resampler;
import org.lts.voicecaptions.audio.UtteranceAssembler;
import org.lts.voicecaptions.caption.CaptionBoard;
import org.lts.voicecaptions.stt.TranscriptCleaner;
import org.lts.voicecaptions.stt.TranscriptionClient;

public final class VoiceCaptionsManager {
    private static final VoiceCaptionsManager INSTANCE = new VoiceCaptionsManager();
    private static final long NOTICE_INTERVAL_MS = 60_000L;
    private static final int PER_CHAR_MS = 60;
    private static final int MAX_CAPTION_MS = 15_000;
    private static final int FADE_MS = 600;
    private static final int MAX_PHRASES = 4;

    private final UtteranceAssembler assembler = new UtteranceAssembler();
    private final CaptionBoard board = new CaptionBoard();
    private final TranscriptionClient client = new TranscriptionClient();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private volatile Set<UUID> speaking = Set.of();
    private volatile Set<UUID> occluded = Set.of();
    private volatile TranscriptionClient.Failure lastFailure;
    private volatile long lastLatencyMs = -1L;
    private volatile boolean voiceConnected;
    private VoiceCaptionsConfig appliedConfig;
    private long lastNoticeAtMs;
    private boolean hadLevel;

    private VoiceCaptionsManager() {
    }

    public static VoiceCaptionsManager getInstance() {
        return INSTANCE;
    }

    public void onVoiceFrame(UUID speaker, String name, short[] samples, int channels, int sampleRate) {
        VoiceCaptionsConfig config = VoiceCaptionsConfig.current();
        if (!config.enabled || speaker == null) {
            return;
        }
        voiceConnected = true;
        if (name != null && !name.isBlank()) {
            names.put(speaker, name);
        }
        short[] mono = Resampler.toMono16k(samples, channels, sampleRate);
        for (UtteranceAssembler.Utterance utterance : assembler.append(speaker, name, mono, System.currentTimeMillis())) {
            transcribe(utterance);
        }
    }

    public void onVoiceEnd(UUID speaker) {
        if (speaker == null) {
            return;
        }
        assembler.end(speaker).ifPresent(this::transcribe);
    }

    public void tick(Minecraft minecraft) {
        VoiceCaptionsConfig config = VoiceCaptionsConfig.current();
        if (config != appliedConfig) {
            apply(config);
        }
        boolean hasLevel = minecraft.level != null && minecraft.player != null;
        if (!hasLevel) {
            if (hadLevel) {
                reset();
            }
            hadLevel = false;
            return;
        }
        hadLevel = true;
        if (!config.enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        for (UtteranceAssembler.Utterance utterance : assembler.sweep(now)) {
            transcribe(utterance);
        }
        speaking = Set.copyOf(assembler.speaking(now));
        occluded = config.showThroughWalls ? Set.of() : computeOccluded(minecraft, config);
        announceFailure(minecraft, now);
    }

    public List<CaptionBoard.SpeakerView> views(long nowMs) {
        return board.snapshot(nowMs, speaking, names);
    }

    public boolean isOccluded(UUID speaker) {
        return occluded.contains(speaker);
    }

    public boolean voiceConnected() {
        return voiceConnected;
    }

    public long lastLatencyMs() {
        return lastLatencyMs;
    }

    public int pendingTranscriptions() {
        return client.pending();
    }

    public void showTest(UUID speaker, String name, String text) {
        board.add(speaker, name, text, System.currentTimeMillis());
    }

    public void probe(Consumer<TranscriptionClient.Result> callback) {
        VoiceCaptionsConfig config = VoiceCaptionsConfig.current();
        client.probe(new TranscriptionClient.Request(config.endpoint, config.model, config.language,
                Math.max(30, config.requestTimeoutSeconds)), result -> {
            if (result instanceof TranscriptionClient.Success success) {
                lastLatencyMs = success.latencyMs();
                lastFailure = null;
            }
            callback.accept(result);
        });
    }

    public void shutdown() {
        client.shutdown();
    }

    private void transcribe(UtteranceAssembler.Utterance utterance) {
        VoiceCaptionsConfig config = VoiceCaptionsConfig.current();
        if (!config.enabled) {
            return;
        }
        UUID speaker = utterance.speaker();
        String name = utterance.name();
        board.transcriptionStarted(speaker, name);
        TranscriptionClient.Request request = new TranscriptionClient.Request(
                config.endpoint, config.model, config.language, config.requestTimeoutSeconds);
        client.submit(utterance.samples(), request, result -> {
            board.transcriptionFinished(speaker);
            if (result instanceof TranscriptionClient.Success success) {
                lastLatencyMs = success.latencyMs();
                lastFailure = null;
                String text = TranscriptCleaner.clean(success.text());
                if (!text.isEmpty()) {
                    board.add(speaker, name, text, System.currentTimeMillis());
                }
            } else if (result instanceof TranscriptionClient.Failure failure
                    && failure.kind() != TranscriptionClient.FailureKind.DROPPED) {
                lastFailure = failure;
            }
        });
    }

    private void apply(VoiceCaptionsConfig config) {
        appliedConfig = config;
        int soft = config.maxPhraseSeconds * 1000;
        assembler.configure(new UtteranceAssembler.Settings(config.silenceGapMs, soft, soft + 5000, 350, 140.0));
        board.configure(new CaptionBoard.Settings((int) Math.round(config.captionSeconds * 1000.0), PER_CHAR_MS,
                MAX_CAPTION_MS, FADE_MS, MAX_PHRASES));
        client.setMaxQueue(config.maxQueue);
        if (!config.enabled) {
            assembler.clear();
            board.clear();
        }
    }

    private void reset() {
        assembler.clear();
        board.clear();
        names.clear();
        speaking = Set.of();
        occluded = Set.of();
        voiceConnected = false;
    }

    private Set<UUID> computeOccluded(Minecraft minecraft, VoiceCaptionsConfig config) {
        List<CaptionBoard.SpeakerView> views = board.snapshot(System.currentTimeMillis(), speaking, names);
        if (views.isEmpty()) {
            return Set.of();
        }
        Vec3 eye = minecraft.gameRenderer.mainCamera().position();
        Set<UUID> hidden = new HashSet<>();
        for (CaptionBoard.SpeakerView view : views) {
            AbstractClientPlayer player = findPlayer(minecraft, view.speaker());
            if (player == null || player == minecraft.player) {
                continue;
            }
            Vec3 head = player.getEyePosition();
            if (head.distanceToSqr(eye) > (double) config.maxDistance * config.maxDistance) {
                continue;
            }
            HitResult hit = minecraft.level.clip(new ClipContext(eye, head, ClipContext.Block.VISUAL,
                    ClipContext.Fluid.NONE, minecraft.player));
            if (hit.getType() != HitResult.Type.MISS) {
                hidden.add(view.speaker());
            }
        }
        return Set.copyOf(hidden);
    }

    static AbstractClientPlayer findPlayer(Minecraft minecraft, UUID id) {
        if (minecraft.level == null) {
            return null;
        }
        for (AbstractClientPlayer player : minecraft.level.players()) {
            if (player.getUUID().equals(id)) {
                return player;
            }
        }
        return null;
    }

    private void announceFailure(Minecraft minecraft, long now) {
        TranscriptionClient.Failure failure = lastFailure;
        if (failure == null || minecraft.player == null || now - lastNoticeAtMs < NOTICE_INTERVAL_MS) {
            return;
        }
        lastNoticeAtMs = now;
        lastFailure = null;
        Component message = Component.translatable("voicecaptions.notice.prefix").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" "))
                .append(failureText(failure).copy().withStyle(ChatFormatting.GRAY));
        minecraft.player.sendSystemMessage(message);
    }

    public static Component failureText(TranscriptionClient.Failure failure) {
        return switch (failure.kind()) {
            case UNAVAILABLE -> Component.translatable("voicecaptions.error.unavailable",
                    VoiceCaptionsConfig.current().endpoint);
            case TIMEOUT -> Component.translatable("voicecaptions.error.timeout", failure.detail());
            case HTTP_ERROR -> Component.translatable("voicecaptions.error.http", failure.detail());
            case BAD_RESPONSE -> Component.translatable("voicecaptions.error.bad_response", failure.detail());
            case BAD_ENDPOINT -> Component.translatable("voicecaptions.error.bad_endpoint", failure.detail());
            case DROPPED -> Component.translatable("voicecaptions.error.dropped");
        };
    }
}
