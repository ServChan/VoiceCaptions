package org.lts.voicecaptions.stt;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class MultipartBody {
    private final String boundary = "----VoiceCaptions" + UUID.randomUUID().toString().replace("-", "");
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    public MultipartBody field(String name, String value) {
        if (value == null || value.isBlank()) {
            return this;
        }
        write("--" + boundary + "\r\n");
        write("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        write(value + "\r\n");
        return this;
    }

    public MultipartBody file(String name, String fileName, String contentType, byte[] data) {
        write("--" + boundary + "\r\n");
        write("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fileName + "\"\r\n");
        write("Content-Type: " + contentType + "\r\n\r\n");
        out.writeBytes(data);
        write("\r\n");
        return this;
    }

    public byte[] build() {
        ByteArrayOutputStream copy = new ByteArrayOutputStream(out.size() + boundary.length() + 8);
        copy.writeBytes(out.toByteArray());
        copy.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return copy.toByteArray();
    }

    public String contentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    private void write(String text) {
        out.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }
}
