package org.lts.voicecaptions.audio;

public final class Resampler {
    public static final int TARGET_RATE = 16_000;

    private Resampler() {
    }

    public static short[] toMono16k(short[] samples, int channels, int sampleRate) {
        if (samples == null || samples.length == 0 || channels < 1 || sampleRate <= 0) {
            return new short[0];
        }
        short[] mono = downmix(samples, channels);
        if (sampleRate == TARGET_RATE) {
            return mono;
        }
        double ratio = (double) sampleRate / TARGET_RATE;
        int outLength = (int) Math.floor(mono.length / ratio);
        short[] out = new short[outLength];
        if (ratio > 1.0) {
            for (int i = 0; i < outLength; i++) {
                double start = i * ratio;
                int from = (int) Math.floor(start);
                int to = Math.min(mono.length, (int) Math.ceil(start + ratio));
                long sum = 0;
                for (int j = from; j < to; j++) {
                    sum += mono[j];
                }
                out[i] = clamp(sum / (double) Math.max(1, to - from));
            }
        } else {
            for (int i = 0; i < outLength; i++) {
                double position = i * ratio;
                int index = (int) Math.floor(position);
                double fraction = position - index;
                int next = Math.min(mono.length - 1, index + 1);
                out[i] = clamp(mono[index] + (mono[next] - mono[index]) * fraction);
            }
        }
        return out;
    }

    static short[] downmix(short[] samples, int channels) {
        if (channels == 1) {
            return samples.clone();
        }
        int frames = samples.length / channels;
        short[] mono = new short[frames];
        for (int frame = 0; frame < frames; frame++) {
            int sum = 0;
            for (int channel = 0; channel < channels; channel++) {
                sum += samples[frame * channels + channel];
            }
            mono[frame] = (short) (sum / channels);
        }
        return mono;
    }

    private static short clamp(double value) {
        long rounded = Math.round(value);
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, rounded));
    }
}
