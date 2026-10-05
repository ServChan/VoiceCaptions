package org.lts.voicecaptions.engine;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.lts.voicecaptions.VoiceCaptionsConfig;

public final class EngineRouter {
    private static final long NPU_CACHE_MS = 10_000L;

    public enum Backend {
        NPU,
        GPU,
        CUSTOM
    }

    public record Route(String endpoint, String model, Backend backend) {
    }

    public enum Problem {
        NOT_INSTALLED,
        UNSUPPORTED,
        ENGINE_FAILED
    }

    public static final class RouteException extends Exception {
        private final Problem problem;

        RouteException(Problem problem, String detail) {
            super(detail);
            this.problem = problem;
        }

        public Problem problem() {
            return problem;
        }
    }

    private final EngineFiles files;
    private final LocalEngine engine = new LocalEngine();
    private final EngineInstaller installer = new EngineInstaller();
    private volatile boolean npuSeen;
    private volatile long npuCheckedAtMs;
    private volatile Backend lastBackend;

    public EngineRouter(EngineFiles files) {
        this.files = files;
    }

    public Route resolve(VoiceCaptionsConfig config) throws RouteException {
        if (!config.isAutoEngine()) {
            lastBackend = Backend.CUSTOM;
            engine.stop();
            return new Route(config.endpoint, config.model, Backend.CUSTOM);
        }
        if (npuAvailable()) {
            if (lastBackend != Backend.NPU) {
                engine.stop();
            }
            lastBackend = Backend.NPU;
            return new Route(EngineCatalog.NPU_ENDPOINT, EngineCatalog.NPU_MODEL, Backend.NPU);
        }
        lastBackend = Backend.GPU;
        if (!EngineCatalog.isSupportedPlatform()) {
            throw new RouteException(Problem.UNSUPPORTED, System.getProperty("os.name", ""));
        }
        EngineCatalog.Model model = EngineCatalog.Model.byId(config.gpuModel);
        if (!gpuInstalled(model)) {
            throw new RouteException(Problem.NOT_INSTALLED, model.id());
        }
        try {
            return new Route(engine.ensureRunning(files, model), model.id(), Backend.GPU);
        } catch (LocalEngine.StartException exception) {
            throw new RouteException(Problem.ENGINE_FAILED, exception.getMessage());
        }
    }

    public boolean gpuInstalled(EngineCatalog.Model model) {
        return EngineInstaller.runtimeInstalled(files) && EngineInstaller.modelInstalled(files, model);
    }

    public boolean install(EngineCatalog.Model model, Runnable onSuccess) {
        return installer.start(files, model, onSuccess);
    }

    public void cancelInstall() {
        installer.cancel();
    }

    public EngineInstaller.Progress installProgress() {
        return installer.progress();
    }

    public LocalEngine.Status engineStatus() {
        return engine.status();
    }

    public Backend lastBackend() {
        return lastBackend;
    }

    public boolean npuSeen() {
        return npuSeen;
    }

    public EngineFiles files() {
        return files;
    }

    public void stopEngine() {
        engine.stop();
    }

    public void forgetNpu() {
        npuCheckedAtMs = 0L;
    }

    private boolean npuAvailable() {
        long now = System.currentTimeMillis();
        if (now - npuCheckedAtMs < NPU_CACHE_MS) {
            return npuSeen;
        }
        boolean open;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(EngineCatalog.NPU_HOST, EngineCatalog.NPU_PORT), 200);
            open = true;
        } catch (IOException exception) {
            open = false;
        }
        npuSeen = open;
        npuCheckedAtMs = now;
        return open;
    }
}
