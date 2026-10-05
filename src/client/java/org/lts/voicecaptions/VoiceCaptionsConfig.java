package org.lts.voicecaptions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import org.lts.voicecaptions.engine.EngineCatalog;

public final class VoiceCaptionsConfig {
    public static final String DEFAULT_ENDPOINT = EngineCatalog.NPU_ENDPOINT;
    public static final String DEFAULT_MODEL = EngineCatalog.NPU_MODEL;
    public static final String ENGINE_AUTO = "auto";
    public static final String ENGINE_CUSTOM = "custom";
    private static final int CURRENT_CONFIG_VERSION = 2;
    private static final Pattern LANGUAGE = Pattern.compile("[a-z]{0,3}");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile VoiceCaptionsConfig current = defaults();

    public int configVersion = CURRENT_CONFIG_VERSION;
    public boolean enabled = true;
    public String engineMode = ENGINE_AUTO;
    public String gpuModel = EngineCatalog.Model.TURBO.id();
    public String endpoint = DEFAULT_ENDPOINT;
    public String model = DEFAULT_MODEL;
    public String language = "ru";
    public int requestTimeoutSeconds = 20;
    public int maxQueue = 6;
    public boolean onlyAudible = true;
    public int silenceGapMs = 700;
    public int maxPhraseSeconds = 7;

    public int bubbleMaxWidth = 180;
    public int maxLines = 3;
    public double captionSeconds = 4.0;
    public int maxDistance = 32;
    public double textScale = 1.0;
    public double farScale = 0.6;
    public double heightOffset = 0.55;
    public int backgroundOpacity = 170;
    public boolean showThroughWalls = true;
    public boolean showSpeakingIndicator = true;
    public boolean showName = false;

    public static void load() {
        Path configPath = configPath();
        VoiceCaptionsConfig primary = read(configPath);
        if (primary != null) {
            boolean migrated = primary.configVersion < CURRENT_CONFIG_VERSION;
            current = sanitize(primary);
            if (migrated) {
                try {
                    writeAtomically(configPath, current, true);
                } catch (IOException exception) {
                    VoiceCaptionsClient.LOGGER.error("Could not persist migrated config {}", configPath, exception);
                }
            }
            return;
        }
        VoiceCaptionsConfig backup = read(backupPath(configPath));
        current = sanitize(backup == null ? defaults() : backup);
        try {
            writeAtomically(configPath, current, false);
        } catch (IOException exception) {
            VoiceCaptionsClient.LOGGER.error("Could not initialize config {}", configPath, exception);
        }
    }

    public static VoiceCaptionsConfig current() {
        return current;
    }

    public static VoiceCaptionsConfig currentCopy() {
        return copyOf(current);
    }

    public static boolean save(VoiceCaptionsConfig config) {
        VoiceCaptionsConfig clean = sanitize(copyOf(config));
        Path configPath = configPath();
        try {
            writeAtomically(configPath, clean, true);
            current = clean;
            return true;
        } catch (IOException exception) {
            VoiceCaptionsClient.LOGGER.error("Could not save config {}", configPath, exception);
            return false;
        }
    }

    public boolean isAutoEngine() {
        return !ENGINE_CUSTOM.equals(engineMode);
    }

    public static boolean isValidLanguage(String value) {
        return value != null && LANGUAGE.matcher(value.strip().toLowerCase(Locale.ROOT)).matches();
    }

    private static VoiceCaptionsConfig read(Path path) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, VoiceCaptionsConfig.class);
        } catch (IOException | RuntimeException exception) {
            VoiceCaptionsClient.LOGGER.warn("Could not read config {}", path, exception);
            return null;
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("voicecaptions.json");
    }

    private static void writeAtomically(Path path, VoiceCaptionsConfig config, boolean backupExisting) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        }
        if (backupExisting && Files.isRegularFile(path)) {
            Files.copy(path, backupPath(path), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path backupPath(Path path) {
        return path.resolveSibling(path.getFileName() + ".bak");
    }

    static VoiceCaptionsConfig sanitize(VoiceCaptionsConfig config) {
        if (config.configVersion < 2) {
            boolean defaultServer = (config.endpoint == null || config.endpoint.isBlank()
                    || DEFAULT_ENDPOINT.equals(config.endpoint.strip()))
                    && (config.model == null || DEFAULT_MODEL.equals(config.model.strip()));
            config.engineMode = defaultServer ? ENGINE_AUTO : ENGINE_CUSTOM;
        }
        config.configVersion = CURRENT_CONFIG_VERSION;
        config.engineMode = ENGINE_CUSTOM.equals(config.engineMode) ? ENGINE_CUSTOM : ENGINE_AUTO;
        config.gpuModel = EngineCatalog.Model.byId(config.gpuModel).id();
        config.endpoint = config.endpoint == null || config.endpoint.isBlank() ? DEFAULT_ENDPOINT : config.endpoint.strip();
        config.model = config.model == null ? DEFAULT_MODEL : config.model.strip();
        config.language = isValidLanguage(config.language) ? config.language.strip().toLowerCase(Locale.ROOT) : "";
        config.requestTimeoutSeconds = clamp(config.requestTimeoutSeconds, 5, 120);
        config.maxQueue = clamp(config.maxQueue, 1, 20);
        config.silenceGapMs = clamp(config.silenceGapMs, 300, 2000);
        config.maxPhraseSeconds = clamp(config.maxPhraseSeconds, 3, 20);
        config.bubbleMaxWidth = clamp(config.bubbleMaxWidth, 60, 400);
        config.maxLines = clamp(config.maxLines, 1, 8);
        config.captionSeconds = clamp(config.captionSeconds, 1.0, 30.0);
        config.maxDistance = clamp(config.maxDistance, 4, 128);
        config.textScale = clamp(config.textScale, 0.5, 2.0);
        config.farScale = clamp(config.farScale, 0.3, 1.0);
        config.heightOffset = clamp(config.heightOffset, 0.0, 3.0);
        config.backgroundOpacity = clamp(config.backgroundOpacity, 0, 255);
        return config;
    }

    private static VoiceCaptionsConfig defaults() {
        return new VoiceCaptionsConfig();
    }

    private static VoiceCaptionsConfig copyOf(VoiceCaptionsConfig source) {
        return GSON.fromJson(GSON.toJson(source), VoiceCaptionsConfig.class);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }
}
