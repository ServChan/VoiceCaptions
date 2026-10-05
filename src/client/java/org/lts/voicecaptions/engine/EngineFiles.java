package org.lts.voicecaptions.engine;

import java.nio.file.Path;

public record EngineFiles(Path root) {
    public Path runtimeDir() {
        return root.resolve("engine").resolve(EngineCatalog.RUNTIME_DIR);
    }

    public Path serverExecutable() {
        return runtimeDir().resolve(EngineCatalog.SERVER_EXE);
    }

    public Path modelsDir() {
        return root.resolve("models");
    }

    public Path model(EngineCatalog.Model model) {
        return modelsDir().resolve(model.file().fileName());
    }

    public Path downloadsDir() {
        return root.resolve("downloads");
    }

    public Path logFile() {
        return root.resolve("engine.log");
    }
}
