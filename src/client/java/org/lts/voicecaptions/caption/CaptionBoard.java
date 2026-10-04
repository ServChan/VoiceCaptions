package org.lts.voicecaptions.caption;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CaptionBoard {
    public record Settings(int baseMs, int perCharMs, int maxMs, int fadeMs, int maxPhrases) {
        public static Settings defaults() {
            return new Settings(3500, 60, 15000, 600, 4);
        }
    }

    public record SpeakerView(UUID speaker, String name, List<String> phrases, float alpha,
                              boolean speaking, boolean transcribing) {
    }

    private record Phrase(String text, long expiresAtMs) {
    }

    private static final class Speaker {
        private String name = "";
        private final ArrayDeque<Phrase> phrases = new ArrayDeque<>();
        private int transcribing;
    }

    private final Map<UUID, Speaker> speakers = new HashMap<>();
    private volatile Settings settings = Settings.defaults();

    public void configure(Settings settings) {
        this.settings = settings;
    }

    public synchronized void add(UUID speakerId, String name, String text, long nowMs) {
        if (speakerId == null || text == null || text.isBlank()) {
            return;
        }
        Settings current = settings;
        Speaker speaker = speakers.computeIfAbsent(speakerId, id -> new Speaker());
        if (name != null && !name.isBlank()) {
            speaker.name = name;
        }
        long lifetime = Math.min(current.maxMs(), current.baseMs() + (long) text.length() * current.perCharMs());
        speaker.phrases.addLast(new Phrase(text, nowMs + lifetime));
        while (speaker.phrases.size() > Math.max(1, current.maxPhrases())) {
            speaker.phrases.removeFirst();
        }
    }

    public synchronized void transcriptionStarted(UUID speakerId, String name) {
        Speaker speaker = speakers.computeIfAbsent(speakerId, id -> new Speaker());
        if (name != null && !name.isBlank()) {
            speaker.name = name;
        }
        speaker.transcribing++;
    }

    public synchronized void transcriptionFinished(UUID speakerId) {
        Speaker speaker = speakers.get(speakerId);
        if (speaker != null && speaker.transcribing > 0) {
            speaker.transcribing--;
        }
    }

    public synchronized List<SpeakerView> snapshot(long nowMs, Set<UUID> speaking, Map<UUID, String> speakingNames) {
        Settings current = settings;
        List<SpeakerView> views = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        Iterator<Map.Entry<UUID, Speaker>> iterator = speakers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Speaker> entry = iterator.next();
            Speaker speaker = entry.getValue();
            speaker.phrases.removeIf(phrase -> phrase.expiresAtMs() <= nowMs);
            boolean isSpeaking = speaking.contains(entry.getKey());
            boolean isTranscribing = speaker.transcribing > 0;
            if (speaker.phrases.isEmpty() && !isSpeaking && !isTranscribing) {
                iterator.remove();
                continue;
            }
            seen.add(entry.getKey());
            long latest = 0L;
            List<String> texts = new ArrayList<>(speaker.phrases.size());
            for (Phrase phrase : speaker.phrases) {
                texts.add(phrase.text());
                latest = Math.max(latest, phrase.expiresAtMs());
            }
            float alpha = 1.0F;
            if (!isSpeaking && !isTranscribing && current.fadeMs() > 0) {
                alpha = (float) Math.max(0.0, Math.min(1.0, (latest - nowMs) / (double) current.fadeMs()));
            }
            String name = speaker.name.isBlank() ? speakingNames.getOrDefault(entry.getKey(), "") : speaker.name;
            views.add(new SpeakerView(entry.getKey(), name, List.copyOf(texts), alpha, isSpeaking, isTranscribing));
        }
        for (UUID id : speaking) {
            if (!seen.contains(id)) {
                views.add(new SpeakerView(id, speakingNames.getOrDefault(id, ""), List.of(), 1.0F, true, false));
            }
        }
        return views;
    }

    public synchronized void clear() {
        speakers.clear();
    }
}
