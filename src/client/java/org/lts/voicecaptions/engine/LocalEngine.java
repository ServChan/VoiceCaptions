package org.lts.voicecaptions.engine;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LocalEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger("voicecaptions");
    private static final String HOST = "127.0.0.1";
    private static final long START_TIMEOUT_MS = 120_000L;
    private static final long RETRY_AFTER_FAILURE_MS = 30_000L;

    public enum State {
        STOPPED,
        STARTING,
        READY,
        FAILED
    }

    public record Status(State state, String detail) {
    }

    public static final class StartException extends Exception {
        StartException(String message) {
            super(message);
        }
    }

    private volatile Process process;
    private volatile Status status = new Status(State.STOPPED, "");
    private EngineCatalog.Model runningModel;
    private int port;
    private long failedAtMs;
    private String lastError = "";

    public Status status() {
        Process current = process;
        if (status.state() == State.READY && (current == null || !current.isAlive())) {
            return new Status(State.FAILED, "exited");
        }
        return status;
    }

    public synchronized String ensureRunning(EngineFiles files, EngineCatalog.Model model) throws StartException {
        Process current = process;
        if (current != null && current.isAlive() && model == runningModel && status.state() == State.READY) {
            return endpoint();
        }
        long now = System.currentTimeMillis();
        if (status.state() == State.FAILED && model == runningModel && now - failedAtMs < RETRY_AFTER_FAILURE_MS) {
            throw new StartException(lastError);
        }
        stop();
        runningModel = model;
        try {
            start(files, model);
            return endpoint();
        } catch (StartException exception) {
            fail(exception.getMessage());
            throw exception;
        }
    }

    public void stop() {
        Process current = process;
        process = null;
        if (current != null) {
            current.descendants().forEach(ProcessHandle::destroy);
            current.destroy();
            try {
                if (!current.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    current.destroyForcibly();
                }
            } catch (InterruptedException exception) {
                current.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
        if (status.state() != State.FAILED) {
            status = new Status(State.STOPPED, "");
        }
    }

    private void start(EngineFiles files, EngineCatalog.Model model) throws StartException {
        Path exe = files.serverExecutable();
        Path modelPath = files.model(model);
        if (!Files.isRegularFile(exe) || !Files.isRegularFile(modelPath)) {
            throw new StartException("not installed");
        }
        killStale(files);
        port = freePort();
        List<String> command = command(exe, modelPath, port, threads());
        status = new Status(State.STARTING, model.id());
        Process started;
        try {
            Files.createDirectories(files.root());
            ProcessBuilder builder = new ProcessBuilder(command)
                    .directory(files.runtimeDir().toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(files.logFile().toFile());
            started = builder.start();
        } catch (IOException exception) {
            throw new StartException(exception.getMessage() == null ? "cannot start" : exception.getMessage());
        }
        process = started;
        writePid(files, started.pid());
        LOGGER.info("Started whisper-server pid {} on port {} with {}", started.pid(), port, model.id());
        long deadline = System.currentTimeMillis() + START_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (process != started) {
                throw new StartException("stopped");
            }
            if (!started.isAlive()) {
                throw new StartException("exit code " + started.exitValue() + ": " + lastLogLine(files));
            }
            if (portOpen(port)) {
                status = new Status(State.READY, model.id());
                return;
            }
            try {
                Thread.sleep(250L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new StartException("interrupted");
            }
        }
        throw new StartException("did not start in " + START_TIMEOUT_MS / 1000 + " s");
    }

    static List<String> command(Path exe, Path model, int port, int threads) {
        List<String> command = new ArrayList<>();
        command.add(exe.toAbsolutePath().toString());
        command.add("-m");
        command.add(model.toAbsolutePath().toString());
        command.add("--host");
        command.add(HOST);
        command.add("--port");
        command.add(Integer.toString(port));
        command.add("--inference-path");
        command.add(EngineCatalog.INFERENCE_PATH);
        command.add("-l");
        command.add("auto");
        command.add("-t");
        command.add(Integer.toString(threads));
        return command;
    }

    static int threads() {
        return Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors() / 2));
    }

    private String endpoint() {
        return "http://" + HOST + ":" + port + EngineCatalog.INFERENCE_PATH;
    }

    private void fail(String message) {
        Process current = process;
        process = null;
        if (current != null) {
            current.destroyForcibly();
        }
        lastError = message == null ? "" : message;
        failedAtMs = System.currentTimeMillis();
        status = new Status(State.FAILED, lastError);
        LOGGER.warn("whisper-server failed: {}", lastError);
    }

    private static int freePort() throws StartException {
        try (ServerSocket socket = new ServerSocket(0, 1, java.net.InetAddress.getByName(HOST))) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new StartException("no free port");
        }
    }

    static boolean portOpen(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, port), 200);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static void writePid(EngineFiles files, long pid) {
        try {
            Files.writeString(files.root().resolve("engine.pid"), Long.toString(pid), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.debug("Could not write engine pid", exception);
        }
    }

    private static void killStale(EngineFiles files) {
        Path pidFile = files.root().resolve("engine.pid");
        try {
            if (!Files.isRegularFile(pidFile)) {
                return;
            }
            long pid = Long.parseLong(Files.readString(pidFile, StandardCharsets.UTF_8).strip());
            Optional<ProcessHandle> handle = ProcessHandle.of(pid);
            handle.filter(ProcessHandle::isAlive)
                    .filter(candidate -> candidate.info().command()
                            .map(command -> command.toLowerCase(java.util.Locale.ROOT).endsWith(EngineCatalog.SERVER_EXE))
                            .orElse(false))
                    .ifPresent(stale -> {
                        LOGGER.info("Stopping leftover whisper-server pid {}", stale.pid());
                        stale.destroyForcibly();
                    });
            Files.deleteIfExists(pidFile);
        } catch (IOException | NumberFormatException exception) {
            LOGGER.debug("Could not check leftover engine", exception);
        }
    }

    private static String lastLogLine(EngineFiles files) {
        try {
            List<String> lines = Files.readAllLines(files.logFile(), StandardCharsets.UTF_8);
            for (int i = lines.size() - 1; i >= 0; i--) {
                String line = lines.get(i).strip();
                if (!line.isEmpty()) {
                    return line.length() > 120 ? line.substring(0, 120) + "…" : line;
                }
            }
        } catch (IOException | RuntimeException exception) {
            return "see voicecaptions/engine.log";
        }
        return "see voicecaptions/engine.log";
    }
}
