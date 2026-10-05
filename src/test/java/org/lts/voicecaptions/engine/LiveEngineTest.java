package org.lts.voicecaptions.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.lts.voicecaptions.stt.TranscriptCleaner;
import org.lts.voicecaptions.stt.TranscriptionClient;

@EnabledIfSystemProperty(named = "voicecaptions.live", matches = ".+")
class LiveEngineTest {
    private static final int RUNS = 4;

    private record Clip(String name, short[] samples, String language) {
    }

    @Test
    void installsStartsAndTranscribesOnThisMachine() throws Exception {
        Path root = Path.of(System.getProperty("voicecaptions.live"));
        EngineFiles files = new EngineFiles(root.resolve("engine-root"));
        List<Clip> clips = new ArrayList<>();
        for (String name : System.getProperty("voicecaptions.live.clips", "ru,short,en").split(",")) {
            short[] samples = readWav(root.resolve(name + ".wav"));
            clips.add(new Clip(name, samples, name.equals("en") ? "en" : "ru"));
            if (name.equals("ru")) {
                clips.add(new Clip("ru-auto", samples, ""));
            }
        }
        StringBuilder report = new StringBuilder();
        for (String id : System.getProperty("voicecaptions.live.models", "base,turbo").split(",")) {
            EngineCatalog.Model model = EngineCatalog.Model.byId(id);
            long installStart = System.nanoTime();
            install(files, model);
            report.append(String.format(Locale.ROOT, "%n== %s (%s) ==%ninstall/verify: %d ms%n", model.id(),
                    model.file().fileName(), (System.nanoTime() - installStart) / 1_000_000L));

            LocalEngine engine = new LocalEngine();
            long startAt = System.nanoTime();
            String endpoint = engine.ensureRunning(files, model);
            report.append(String.format(Locale.ROOT, "server start (model load): %d ms%n",
                    (System.nanoTime() - startAt) / 1_000_000L));
            TranscriptionClient client = new TranscriptionClient(new TranscriptionClient.Router() {
                @Override
                public EngineRouter.Route route() {
                    return new EngineRouter.Route(endpoint, model.id(), EngineRouter.Backend.GPU);
                }

                @Override
                public void unreachable(EngineRouter.Route route) {
                }
            });
            try {
                for (Clip clip : clips) {
                    long[] latencies = new long[RUNS];
                    String text = "";
                    for (int run = 0; run < RUNS; run++) {
                        TranscriptionClient.Result result = transcribe(client, clip);
                        assertTrue(result instanceof TranscriptionClient.Success, clip.name() + ": " + result);
                        TranscriptionClient.Success success = (TranscriptionClient.Success) result;
                        latencies[run] = success.latencyMs();
                        text = TranscriptCleaner.clean(success.text());
                    }
                    long[] warm = Arrays.copyOfRange(latencies, 1, RUNS);
                    Arrays.sort(warm);
                    report.append(String.format(Locale.ROOT, "%-8s %5.1f s audio | first %5d ms | warm median %5d ms | \"%s\"%n",
                            clip.name(), clip.samples().length / 16000.0, latencies[0], warm[warm.length / 2], text));
                }
            } finally {
                client.shutdown();
                engine.stop();
            }
        }
        Files.writeString(root.resolve("report.txt"), report.toString(), StandardCharsets.UTF_8);
        System.out.println(report);
    }

    private static TranscriptionClient.Result transcribe(TranscriptionClient client, Clip clip) throws Exception {
        CompletableFuture<TranscriptionClient.Result> future = new CompletableFuture<>();
        client.submit(clip.samples(), new TranscriptionClient.Request(clip.language(), 60), future::complete);
        return future.get(90, TimeUnit.SECONDS);
    }

    private static void install(EngineFiles files, EngineCatalog.Model model) throws Exception {
        EngineInstaller installer = new EngineInstaller();
        CompletableFuture<Void> done = new CompletableFuture<>();
        assertTrue(installer.start(files, model, () -> done.complete(null)));
        long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30);
        while (!done.isDone() && System.currentTimeMillis() < deadline) {
            EngineInstaller.Progress progress = installer.progress();
            if (progress.phase() == EngineInstaller.Phase.FAILED || progress.phase() == EngineInstaller.Phase.CANCELLED) {
                break;
            }
            Thread.sleep(500L);
        }
        assertEquals(EngineInstaller.Phase.DONE, installer.progress().phase(), installer.progress().error());
        assertTrue(EngineInstaller.runtimeInstalled(files));
        assertTrue(EngineInstaller.modelInstalled(files, model));
    }

    private static short[] readWav(Path path) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(Files.readAllBytes(path)).order(ByteOrder.LITTLE_ENDIAN);
        int position = 12;
        while (position + 8 <= buffer.limit()) {
            String id = new String(buffer.array(), position, 4, StandardCharsets.US_ASCII);
            int size = buffer.getInt(position + 4);
            if (id.equals("fmt ")) {
                assertEquals(1, buffer.getShort(position + 10), "mono");
                assertEquals(16000, buffer.getInt(position + 12), "16 kHz");
            }
            if (id.equals("data")) {
                short[] samples = new short[size / 2];
                buffer.position(position + 8);
                buffer.asShortBuffer().get(samples);
                return samples;
            }
            position += 8 + size + (size & 1);
        }
        throw new IOException("no data chunk in " + path);
    }
}
