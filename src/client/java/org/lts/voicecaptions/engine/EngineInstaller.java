package org.lts.voicecaptions.engine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.lts.voicecaptions.ModLog;

public final class EngineInstaller {
    private static final ModLog LOGGER = ModLog.of("Engine");
    private static final String MARKER = ".installed";
    private static final int BUFFER = 64 * 1024;

    public enum Phase {
        IDLE,
        RUNTIME,
        MODEL,
        DONE,
        FAILED,
        CANCELLED
    }

    public record Progress(Phase phase, long done, long total, String error) {
        static final Progress IDLE = new Progress(Phase.IDLE, 0, 0, "");

        public boolean running() {
            return phase == Phase.RUNTIME || phase == Phase.MODEL;
        }

        public int percent() {
            return total <= 0 ? 0 : (int) Math.min(100, done * 100 / total);
        }
    }

    public static final class CancelledException extends IOException {
        CancelledException() {
            super("cancelled");
        }
    }

    interface ProgressSink {
        void update(long done) throws CancelledException;
    }

    private List<HttpClient> clients = List.of();
    private volatile Progress progress = Progress.IDLE;
    private volatile boolean cancelRequested;
    private Thread worker;

    public Progress progress() {
        return progress;
    }

    public synchronized boolean start(EngineFiles files, EngineCatalog.Model model, Runnable onSuccess) {
        if (worker != null && worker.isAlive()) {
            return false;
        }
        cancelRequested = false;
        progress = new Progress(Phase.RUNTIME, 0, 0, "");
        worker = new Thread(() -> run(files, model, onSuccess), "VoiceCaptions installer");
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    public void cancel() {
        cancelRequested = true;
    }

    public static boolean runtimeInstalled(EngineFiles files) {
        Path marker = files.runtimeDir().resolve(MARKER);
        try {
            return Files.isRegularFile(files.serverExecutable()) && Files.isRegularFile(marker)
                    && Files.readString(marker, StandardCharsets.UTF_8).strip().equals(EngineCatalog.RUNTIME.sha256());
        } catch (IOException exception) {
            return false;
        }
    }

    public static boolean modelInstalled(EngineFiles files, EngineCatalog.Model model) {
        Path path = files.model(model);
        try {
            return Files.isRegularFile(path) && Files.size(path) == model.file().size();
        } catch (IOException exception) {
            return false;
        }
    }

    private void run(EngineFiles files, EngineCatalog.Model model, Runnable onSuccess) {
        try {
            clients = List.of(client(DownloadProxy.selector()), client(HttpClient.Builder.NO_PROXY));
            if (!runtimeInstalled(files)) {
                installRuntime(files);
            }
            if (!modelInstalled(files, model)) {
                EngineCatalog.Download download = model.file();
                progress = new Progress(Phase.MODEL, 0, download.size(), "");
                Path part = files.modelsDir().resolve(download.fileName() + ".part");
                fetch(download, part, Phase.MODEL);
                moveAtomically(part, files.model(model));
            }
            progress = new Progress(Phase.DONE, 1, 1, "");
            onSuccess.run();
        } catch (CancelledException exception) {
            progress = new Progress(Phase.CANCELLED, 0, 0, "");
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not install the speech engine", exception);
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            progress = new Progress(Phase.FAILED, 0, 0, message);
        }
    }

    private void installRuntime(EngineFiles files) throws IOException {
        EngineCatalog.Download download = EngineCatalog.RUNTIME;
        progress = new Progress(Phase.RUNTIME, 0, download.size(), "");
        Path zip = files.downloadsDir().resolve(download.fileName() + ".part");
        fetch(download, zip, Phase.RUNTIME);
        Path staging = files.runtimeDir().resolveSibling(EngineCatalog.RUNTIME_DIR + ".tmp");
        deleteTree(staging);
        try (InputStream input = Files.newInputStream(zip)) {
            extract(input, staging);
        }
        if (!Files.isRegularFile(staging.resolve(EngineCatalog.SERVER_EXE))) {
            throw new IOException("archive has no " + EngineCatalog.SERVER_EXE);
        }
        Files.writeString(staging.resolve(MARKER), download.sha256(), StandardCharsets.UTF_8);
        deleteTree(files.runtimeDir());
        Files.move(staging, files.runtimeDir());
        Files.deleteIfExists(zip);
    }

    private void fetch(EngineCatalog.Download download, Path target, Phase phase) throws IOException {
        Files.createDirectories(target.getParent());
        HttpRequest request = HttpRequest.newBuilder(URI.create(download.url()))
                .timeout(Duration.ofMinutes(30))
                .header("User-Agent", "VoiceCaptions-Minecraft-Mod")
                .GET()
                .build();
        HttpResponse<InputStream> response = null;
        IOException connectFailure = null;
        for (HttpClient http : clients) {
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                break;
            } catch (ConnectException | HttpConnectTimeoutException exception) {
                connectFailure = exception;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new CancelledException();
            }
        }
        if (response == null) {
            throw connectFailure == null ? new IOException("no connection") : connectFailure;
        }
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("HTTP " + response.statusCode() + " for " + download.fileName());
        }
        try (InputStream body = response.body(); OutputStream out = Files.newOutputStream(target)) {
            copyVerified(body, out, download.size(), download.sha256(), done -> {
                if (cancelRequested) {
                    throw new CancelledException();
                }
                progress = new Progress(phase, done, download.size(), "");
            });
        } catch (IOException exception) {
            Files.deleteIfExists(target);
            throw exception;
        }
    }

    private static HttpClient client(java.net.ProxySelector proxy) {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .proxy(proxy)
                .build();
    }

    static void copyVerified(InputStream input, OutputStream output, long expectedSize, String expectedSha256,
                             ProgressSink sink) throws IOException {
        MessageDigest digest = sha256();
        byte[] buffer = new byte[BUFFER];
        long total = 0;
        long reported = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
            digest.update(buffer, 0, read);
            total += read;
            if (total > expectedSize) {
                throw new IOException("download is larger than expected");
            }
            if (total - reported >= 256 * 1024) {
                reported = total;
                sink.update(total);
            }
        }
        sink.update(total);
        if (total != expectedSize) {
            throw new IOException("download is incomplete: " + total + " of " + expectedSize + " bytes");
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equalsIgnoreCase(expectedSha256)) {
            throw new IOException("checksum mismatch");
        }
    }

    static void extract(InputStream input, Path target) throws IOException {
        Path root = target.toAbsolutePath().normalize();
        Files.createDirectories(root);
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path resolved = root.resolve(entry.getName()).normalize();
                if (!resolved.startsWith(root) || resolved.equals(root)) {
                    throw new IOException("unsafe archive entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    Files.createDirectories(resolved.getParent());
                    Files.copy(zip, resolved, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void moveAtomically(Path from, Path to) throws IOException {
        Files.createDirectories(to.getParent());
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path item : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
