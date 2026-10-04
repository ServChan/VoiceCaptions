package org.lts.voicecaptions.audio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AudioPipelineTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final int FRAME_16K = 320;

    @Test
    void resamples48kFrameTo320Samples() {
        short[] frame = new short[960];
        java.util.Arrays.fill(frame, (short) 1000);
        short[] out = Resampler.toMono16k(frame, 1, 48_000);
        assertEquals(320, out.length);
        assertEquals(1000, out[0]);
        assertEquals(1000, out[319]);
    }

    @Test
    void downmixesInterleavedStereo() {
        short[] stereo = {100, 300, -200, 200, 0, 0};
        assertArrayEquals(new short[]{200, 0, 0}, Resampler.downmix(stereo, 2));
        assertEquals(320, Resampler.toMono16k(new short[960 * 2], 2, 48_000).length);
    }

    @Test
    void upsamples8kAndHandlesEmptyInput() {
        assertEquals(320, Resampler.toMono16k(new short[160], 1, 8_000).length);
        assertEquals(0, Resampler.toMono16k(new short[0], 1, 48_000).length);
        assertEquals(0, Resampler.toMono16k(new short[960], 1, 0).length);
    }

    @Test
    void wavHeaderDescribes16BitMono() {
        byte[] wav = WavEncoder.encodeMono16(new short[]{1, -1, 32767}, 16_000);
        assertEquals(44 + 6, wav.length);
        assertEquals("RIFF", new String(wav, 0, 4, StandardCharsets.US_ASCII));
        assertEquals("WAVE", new String(wav, 8, 4, StandardCharsets.US_ASCII));
        ByteBuffer buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(1, buffer.getShort(22));
        assertEquals(16_000, buffer.getInt(24));
        assertEquals(16, buffer.getShort(34));
        assertEquals(6, buffer.getInt(40));
        assertEquals(32767, buffer.getShort(48));
    }

    @Test
    void phraseEndsAfterSilenceGap() {
        UtteranceAssembler assembler = new UtteranceAssembler();
        long now = 0;
        for (int i = 0; i < 50; i++) {
            assertTrue(assembler.append(ALICE, "Alice", tone(FRAME_16K, 3000), now).isEmpty());
            now += 20;
        }
        assertTrue(assembler.speaking(now).contains(ALICE));
        assertTrue(assembler.sweep(now + 100).isEmpty());
        List<UtteranceAssembler.Utterance> done = assembler.sweep(now + 800);
        assertEquals(1, done.size());
        assertEquals("Alice", done.get(0).name());
        assertTrue(done.get(0).durationMs() >= 900, "duration " + done.get(0).durationMs());
        assertTrue(assembler.speaking(now + 800).isEmpty());
    }

    @Test
    void voiceEndFlushesImmediatelyAndSpeakersStaySeparate() {
        UtteranceAssembler assembler = new UtteranceAssembler();
        for (int i = 0; i < 30; i++) {
            assembler.append(ALICE, "Alice", tone(FRAME_16K, 3000), i * 20L);
            assembler.append(BOB, "Bob", tone(FRAME_16K, 2500), i * 20L);
        }
        UtteranceAssembler.Utterance alice = assembler.end(ALICE).orElseThrow();
        assertEquals(ALICE, alice.speaker());
        assertTrue(assembler.speaking(600).contains(BOB));
        assertTrue(assembler.end(ALICE).isEmpty());
    }

    @Test
    void quietOrShortPhrasesAreDropped() {
        UtteranceAssembler assembler = new UtteranceAssembler();
        for (int i = 0; i < 50; i++) {
            assembler.append(ALICE, "Alice", tone(FRAME_16K, 40), i * 20L);
        }
        assertTrue(assembler.end(ALICE).isEmpty());
        for (int i = 0; i < 5; i++) {
            assembler.append(BOB, "Bob", tone(FRAME_16K, 3000), i * 20L);
        }
        assertTrue(assembler.end(BOB).isEmpty());
    }

    @Test
    void longSpeechSplitsAtQuietMoment() {
        UtteranceAssembler assembler = new UtteranceAssembler();
        assembler.configure(new UtteranceAssembler.Settings(700, 3000, 6000, 350, 140.0));
        long now = 0;
        int emitted = 0;
        for (int i = 0; i < 160; i++) {
            int amplitude = i == 140 || i == 141 ? 20 : 3000;
            emitted += assembler.append(ALICE, "Alice", tone(FRAME_16K, amplitude), now).size();
            now += 20;
        }
        assertEquals(1, emitted);
    }

    @Test
    void hardLimitSplitsWithoutQuietMoment() {
        UtteranceAssembler assembler = new UtteranceAssembler();
        assembler.configure(new UtteranceAssembler.Settings(700, 3000, 4000, 350, 140.0));
        int emitted = 0;
        for (int i = 0; i < 250; i++) {
            emitted += assembler.append(ALICE, "Alice", tone(FRAME_16K, 3000), i * 20L).size();
        }
        assertEquals(1, emitted);
    }

    @Test
    void trimsLeadingSilence() {
        short[] samples = new short[16_000];
        short[] loud = tone(8_000, 3000);
        System.arraycopy(loud, 0, samples, 8_000, loud.length);
        short[] trimmed = UtteranceAssembler.trimSilence(samples, 140.0);
        assertTrue(trimmed.length < 12_000, "length " + trimmed.length);
        assertTrue(trimmed.length >= 8_000);
    }

    private static short[] tone(int length, int amplitude) {
        short[] samples = new short[length];
        for (int i = 0; i < length; i++) {
            samples[i] = (short) (amplitude * Math.sin(i * 2 * Math.PI * 220 / 16_000.0));
        }
        return samples;
    }
}
