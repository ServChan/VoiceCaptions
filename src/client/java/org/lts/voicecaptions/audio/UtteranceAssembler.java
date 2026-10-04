package org.lts.voicecaptions.audio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class UtteranceAssembler {
    public static final int RATE = Resampler.TARGET_RATE;
    private static final int WINDOW = RATE / 50;
    private static final int SPLIT_SEARCH_MS = 1500;
    private static final int TRIM_PADDING_MS = 200;
    private static final double QUIET_FRACTION = 0.35;

    public record Settings(int silenceGapMs, int softMaxMs, int hardMaxMs, int minSpeechMs, double minRms) {
        public static Settings defaults() {
            return new Settings(700, 7000, 12000, 350, 140.0);
        }
    }

    public record Utterance(UUID speaker, String name, short[] samples, long startedAtMs) {
        public int durationMs() {
            return (int) (samples.length * 1000L / RATE);
        }
    }

    private static final class Buffer {
        private String name;
        private short[] data = new short[RATE * 2];
        private int length;
        private long startedAtMs;
        private long lastFrameAtMs;

        private void append(short[] samples) {
            if (length + samples.length > data.length) {
                data = Arrays.copyOf(data, Math.max(data.length * 2, length + samples.length));
            }
            System.arraycopy(samples, 0, data, length, samples.length);
            length += samples.length;
        }

        private short[] take(int count) {
            short[] head = Arrays.copyOf(data, count);
            System.arraycopy(data, count, data, 0, length - count);
            length -= count;
            return head;
        }
    }

    private final Map<UUID, Buffer> buffers = new HashMap<>();
    private volatile Settings settings = Settings.defaults();

    public void configure(Settings settings) {
        this.settings = settings;
    }

    public synchronized List<Utterance> append(UUID speaker, String name, short[] mono16k, long nowMs) {
        List<Utterance> finished = new ArrayList<>(1);
        if (speaker == null || mono16k == null || mono16k.length == 0) {
            return finished;
        }
        Settings current = settings;
        Buffer buffer = buffers.get(speaker);
        if (buffer != null && nowMs - buffer.lastFrameAtMs > current.silenceGapMs()) {
            buffers.remove(speaker);
            finish(speaker, buffer, buffer.length, current).ifPresent(finished::add);
            buffer = null;
        }
        if (buffer == null) {
            buffer = new Buffer();
            buffer.startedAtMs = nowMs;
            buffers.put(speaker, buffer);
        }
        if (name != null && !name.isBlank()) {
            buffer.name = name;
        }
        buffer.append(mono16k);
        buffer.lastFrameAtMs = nowMs;

        int softMax = samplesFor(current.softMaxMs());
        int hardMax = samplesFor(Math.max(current.softMaxMs(), current.hardMaxMs()));
        if (buffer.length >= softMax) {
            int split = quietSplit(buffer.data, buffer.length);
            if (split < 0 && buffer.length >= hardMax) {
                split = buffer.length;
            }
            if (split > 0) {
                long startedAt = buffer.startedAtMs;
                short[] head = buffer.take(split);
                buffer.startedAtMs = nowMs - buffer.length * 1000L / RATE;
                Utterance candidate = new Utterance(speaker, buffer.name, head, startedAt);
                accept(candidate, current).ifPresent(finished::add);
                if (buffer.length == 0) {
                    buffers.remove(speaker);
                }
            }
        }
        return finished;
    }

    public synchronized Optional<Utterance> end(UUID speaker) {
        Buffer buffer = buffers.remove(speaker);
        if (buffer == null) {
            return Optional.empty();
        }
        return finish(speaker, buffer, buffer.length, settings);
    }

    public synchronized List<Utterance> sweep(long nowMs) {
        List<Utterance> finished = new ArrayList<>();
        Settings current = settings;
        Iterator<Map.Entry<UUID, Buffer>> iterator = buffers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Buffer> entry = iterator.next();
            Buffer buffer = entry.getValue();
            if (nowMs - buffer.lastFrameAtMs > current.silenceGapMs()) {
                iterator.remove();
                finish(entry.getKey(), buffer, buffer.length, current).ifPresent(finished::add);
            }
        }
        return finished;
    }

    public synchronized Set<UUID> speaking(long nowMs) {
        Set<UUID> result = new HashSet<>();
        int gap = settings.silenceGapMs();
        for (Map.Entry<UUID, Buffer> entry : buffers.entrySet()) {
            if (nowMs - entry.getValue().lastFrameAtMs <= gap) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public synchronized void clear() {
        buffers.clear();
    }

    private static Optional<Utterance> finish(UUID speaker, Buffer buffer, int count, Settings settings) {
        if (count <= 0) {
            return Optional.empty();
        }
        short[] samples = Arrays.copyOf(buffer.data, count);
        return accept(new Utterance(speaker, buffer.name, samples, buffer.startedAtMs), settings);
    }

    static Optional<Utterance> accept(Utterance utterance, Settings settings) {
        short[] trimmed = trimSilence(utterance.samples(), settings.minRms());
        if (trimmed.length < samplesFor(settings.minSpeechMs())) {
            return Optional.empty();
        }
        if (rms(trimmed, 0, trimmed.length) < settings.minRms()) {
            return Optional.empty();
        }
        return Optional.of(new Utterance(utterance.speaker(), utterance.name(), trimmed, utterance.startedAtMs()));
    }

    static short[] trimSilence(short[] samples, double minRms) {
        int windows = samples.length / WINDOW;
        if (windows == 0) {
            return samples;
        }
        double loudest = 0.0;
        double[] levels = new double[windows];
        for (int i = 0; i < windows; i++) {
            levels[i] = rms(samples, i * WINDOW, WINDOW);
            loudest = Math.max(loudest, levels[i]);
        }
        double threshold = Math.max(minRms * 0.5, loudest * 0.05);
        int first = 0;
        while (first < windows && levels[first] < threshold) {
            first++;
        }
        if (first == windows) {
            return new short[0];
        }
        int last = windows - 1;
        while (last > first && levels[last] < threshold) {
            last--;
        }
        int padding = samplesFor(TRIM_PADDING_MS);
        int from = Math.max(0, first * WINDOW - padding);
        int to = Math.min(samples.length, (last + 1) * WINDOW + padding);
        if (last == windows - 1) {
            to = samples.length;
        }
        return Arrays.copyOfRange(samples, from, to);
    }

    static int quietSplit(short[] data, int length) {
        int windows = length / WINDOW;
        int searchWindows = Math.min(windows, samplesFor(SPLIT_SEARCH_MS) / WINDOW);
        if (searchWindows < 2) {
            return -1;
        }
        double average = rms(data, 0, length);
        int bestWindow = -1;
        double bestLevel = Double.MAX_VALUE;
        for (int i = windows - searchWindows; i < windows; i++) {
            double level = rms(data, i * WINDOW, WINDOW);
            if (level < bestLevel) {
                bestLevel = level;
                bestWindow = i;
            }
        }
        if (bestWindow < 1 || bestLevel > average * QUIET_FRACTION) {
            return -1;
        }
        return bestWindow * WINDOW + WINDOW / 2;
    }

    static double rms(short[] data, int offset, int count) {
        if (count <= 0) {
            return 0.0;
        }
        double sum = 0.0;
        int end = Math.min(data.length, offset + count);
        for (int i = offset; i < end; i++) {
            sum += (double) data[i] * data[i];
        }
        return Math.sqrt(sum / Math.max(1, end - offset));
    }

    static int samplesFor(int millis) {
        return (int) ((long) Math.max(0, millis) * RATE / 1000L);
    }
}
