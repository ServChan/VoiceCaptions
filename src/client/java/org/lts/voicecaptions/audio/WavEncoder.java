package org.lts.voicecaptions.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class WavEncoder {
    private static final int HEADER_BYTES = 44;

    private WavEncoder() {
    }

    public static byte[] encodeMono16(short[] samples, int sampleRate) {
        int dataBytes = samples.length * 2;
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_BYTES + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(new byte[]{'R', 'I', 'F', 'F'});
        buffer.putInt(36 + dataBytes);
        buffer.put(new byte[]{'W', 'A', 'V', 'E'});
        buffer.put(new byte[]{'f', 'm', 't', ' '});
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        buffer.putInt(sampleRate);
        buffer.putInt(sampleRate * 2);
        buffer.putShort((short) 2);
        buffer.putShort((short) 16);
        buffer.put(new byte[]{'d', 'a', 't', 'a'});
        buffer.putInt(dataBytes);
        for (short sample : samples) {
            buffer.putShort(sample);
        }
        return buffer.array();
    }
}
