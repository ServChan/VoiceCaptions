package org.lts.voicecaptions.plasmo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.lts.voicecaptions.VoiceCaptionsClient;
import org.lts.voicecaptions.VoiceCaptionsConfig;
import org.lts.voicecaptions.VoiceCaptionsManager;
import su.plo.slib.api.entity.player.McGameProfile;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.audio.source.ClientAudioSource;
import su.plo.voice.api.client.connection.ServerInfo;
import su.plo.voice.api.client.event.audio.source.AudioSourceResetEvent;
import su.plo.voice.api.client.event.audio.source.AudioSourceWriteEvent;
import su.plo.voice.api.event.EventPriority;
import su.plo.voice.proto.data.audio.source.DirectSourceInfo;
import su.plo.voice.proto.data.audio.source.PlayerSourceInfo;
import su.plo.voice.proto.data.audio.source.SourceInfo;
import su.plo.voice.proto.data.player.VoicePlayerInfo;

@Addon(id = "voicecaptions", name = "Voice Captions", scope = AddonLoaderScope.CLIENT, version = "1.1.0",
        authors = {"LTS_Server"})
public final class VoiceCaptionsAddon implements AddonInitializer {
    private static final int DEFAULT_SAMPLE_RATE = 48_000;
    private static final long CLAIM_TIMEOUT_MS = 1_000L;

    private final Map<UUID, SourceClaim> claims = new ConcurrentHashMap<>();

    @InjectPlasmoVoice
    private PlasmoVoiceClient voiceClient;

    public static void install() {
        PlasmoVoiceClient.getAddonsLoader().load(new VoiceCaptionsAddon());
    }

    @Override
    public void onAddonInitialize() {
        voiceClient.getEventBus().register(this, AudioSourceWriteEvent.class, EventPriority.LOWEST, this::onWrite);
        voiceClient.getEventBus().register(this, AudioSourceResetEvent.class, EventPriority.LOWEST, this::onReset);
        VoiceCaptionsClient.LOGGER.info("Plasmo Voice addon registered");
    }

    private void onWrite(AudioSourceWriteEvent event) {
        if (!VoiceCaptionsConfig.current().enabled) {
            return;
        }
        ClientAudioSource<?> source = event.getSource();
        Speaker speaker = speakerOf(source.getSourceInfo());
        if (speaker == null) {
            return;
        }
        if (VoiceCaptionsConfig.current().onlyAudible && source.getEffectiveVolume() <= 0.001F) {
            return;
        }
        short[] samples = event.getSamples();
        if (samples == null || samples.length == 0 || !ownsSpeaker(speaker.id(), source.getSourceInfo().getId())) {
            return;
        }
        int sampleRate = DEFAULT_SAMPLE_RATE;
        int frameSize = 0;
        ServerInfo info = voiceClient.getServerInfo().orElse(null);
        if (info != null) {
            sampleRate = info.getVoiceInfo().getCaptureInfo().getSampleRate();
            frameSize = info.getVoiceInfo().getFrameSize();
        }
        int channels = frameSize > 0 && samples.length >= frameSize * 2 ? 2 : 1;
        VoiceCaptionsManager.getInstance().onVoiceFrame(speaker.id(), speaker.name(), samples.clone(), channels, sampleRate);
    }

    private void onReset(AudioSourceResetEvent event) {
        if (event.getCause() != AudioSourceResetEvent.Cause.VOICE_END
                && event.getCause() != AudioSourceResetEvent.Cause.SOURCE_STOPPED) {
            return;
        }
        Speaker speaker = speakerOf(event.getSource().getSourceInfo());
        if (speaker != null) {
            VoiceCaptionsManager.getInstance().onVoiceEnd(speaker.id());
        }
    }

    private boolean ownsSpeaker(UUID speaker, UUID sourceId) {
        long now = System.currentTimeMillis();
        SourceClaim claim = claims.compute(speaker, (id, existing) -> {
            if (existing == null || existing.sourceId().equals(sourceId) || now - existing.lastSeenMs() > CLAIM_TIMEOUT_MS) {
                return new SourceClaim(sourceId, now);
            }
            return existing;
        });
        return claim.sourceId().equals(sourceId);
    }

    private static Speaker speakerOf(SourceInfo info) {
        if (info instanceof PlayerSourceInfo playerSource) {
            VoicePlayerInfo player = playerSource.getPlayerInfo();
            if (player != null && player.getPlayerId() != null) {
                return new Speaker(player.getPlayerId(), player.getPlayerNick());
            }
        } else if (info instanceof DirectSourceInfo directSource) {
            McGameProfile sender = directSource.getSender();
            if (sender != null && sender.getId() != null) {
                return new Speaker(sender.getId(), sender.getName());
            }
        }
        return null;
    }

    private record Speaker(UUID id, String name) {
    }

    private record SourceClaim(UUID sourceId, long lastSeenMs) {
    }
}
