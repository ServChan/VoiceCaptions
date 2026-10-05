package org.lts.voicecaptions.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EngineTest {
    @TempDir
    Path temp;

    @Test
    void acceptsDownloadWithMatchingSizeAndChecksum() throws Exception {
        byte[] data = "whisper".repeat(100_000).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long[] reported = {0};
        EngineInstaller.copyVerified(new ByteArrayInputStream(data), out, data.length, sha256(data),
                done -> reported[0] = done);
        assertEquals(data.length, out.size());
        assertEquals(data.length, reported[0]);
    }

    @Test
    void rejectsWrongChecksumTruncatedAndOversizedDownloads() {
        byte[] data = new byte[1000];
        String sha = sha256(data);
        assertThrows(IOException.class, () -> EngineInstaller.copyVerified(new ByteArrayInputStream(data),
                new ByteArrayOutputStream(), data.length, "00" + sha.substring(2), done -> { }));
        assertThrows(IOException.class, () -> EngineInstaller.copyVerified(new ByteArrayInputStream(data),
                new ByteArrayOutputStream(), data.length + 1, sha, done -> { }));
        assertThrows(IOException.class, () -> EngineInstaller.copyVerified(new ByteArrayInputStream(data),
                new ByteArrayOutputStream(), data.length - 1, sha, done -> { }));
    }

    @Test
    void cancellationStopsCopy() {
        byte[] data = new byte[2_000_000];
        assertThrows(EngineInstaller.CancelledException.class, () -> EngineInstaller.copyVerified(
                new ByteArrayInputStream(data), new ByteArrayOutputStream(), data.length, sha256(data), done -> {
                    throw new EngineInstaller.CancelledException();
                }));
    }

    @Test
    void extractsArchiveIntoTarget() throws Exception {
        byte[] zip = zip(List.of("whisper-server.exe", "ggml-vulkan.dll", "sub/readme.txt"));
        Path target = temp.resolve("runtime");
        EngineInstaller.extract(new ByteArrayInputStream(zip), target);
        assertTrue(Files.isRegularFile(target.resolve("whisper-server.exe")));
        assertTrue(Files.isRegularFile(target.resolve("sub/readme.txt")));
    }

    @Test
    void refusesEntriesThatEscapeTheTarget() throws Exception {
        byte[] zip = zip(List.of("../evil.exe"));
        Path target = temp.resolve("runtime");
        assertThrows(IOException.class, () -> EngineInstaller.extract(new ByteArrayInputStream(zip), target));
        assertFalse(Files.exists(temp.resolve("evil.exe")));
    }

    @Test
    void installedChecksNeedMarkerAndExactModelSize() throws Exception {
        EngineFiles files = new EngineFiles(temp);
        assertFalse(EngineInstaller.runtimeInstalled(files));
        Files.createDirectories(files.runtimeDir());
        Files.writeString(files.serverExecutable(), "exe");
        assertFalse(EngineInstaller.runtimeInstalled(files));
        Files.writeString(files.runtimeDir().resolve(".installed"), EngineCatalog.RUNTIME.sha256());
        assertTrue(EngineInstaller.runtimeInstalled(files));

        Files.createDirectories(files.modelsDir());
        Files.write(files.model(EngineCatalog.Model.BASE), new byte[10]);
        assertFalse(EngineInstaller.modelInstalled(files, EngineCatalog.Model.BASE));
    }

    @Test
    void serverCommandBindsLoopbackWithOpenAiPath() {
        List<String> command = LocalEngine.command(Path.of("C:/engine/whisper-server.exe"),
                Path.of("C:/models/ggml-base-q5_1.bin"), 41234, 4);
        assertTrue(command.get(0).endsWith("whisper-server.exe"));
        assertEquals("127.0.0.1", command.get(command.indexOf("--host") + 1));
        assertEquals("41234", command.get(command.indexOf("--port") + 1));
        assertEquals(EngineCatalog.INFERENCE_PATH, command.get(command.indexOf("--inference-path") + 1));
        assertEquals("auto", command.get(command.indexOf("-l") + 1));
        assertTrue(LocalEngine.threads() >= 2 && LocalEngine.threads() <= 8);
    }

    @Test
    void modelLookupFallsBackToTurbo() {
        assertEquals(EngineCatalog.Model.SMALL, EngineCatalog.Model.byId(" Small "));
        assertEquals(EngineCatalog.Model.TURBO, EngineCatalog.Model.byId("unknown"));
        assertEquals(EngineCatalog.Model.TURBO, EngineCatalog.Model.byId(null));
        assertEquals(EngineCatalog.Model.SMALL, EngineCatalog.Model.TURBO.next());
        assertEquals(EngineCatalog.Model.TURBO, EngineCatalog.Model.BASE.next());
        for (EngineCatalog.Model model : EngineCatalog.Model.values()) {
            assertEquals("voicecaptions.gpu_model." + model.id(), model.translationKey());
            assertEquals(64, model.file().sha256().length());
            assertTrue(model.file().url().startsWith("https://huggingface.co/ggerganov/whisper.cpp/"));
        }
        assertEquals(64, EngineCatalog.RUNTIME.sha256().length());
    }

    private static byte[] zip(List<String> names) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String name : names) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(name.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
