package org.lts.voicecaptions.engine;

import java.util.Locale;

public final class EngineCatalog {
    public static final String NPU_ENDPOINT = "http://127.0.0.1:52625/v1/audio/transcriptions";
    public static final String NPU_MODEL = "whisper-v3:turbo";
    public static final String NPU_HOST = "127.0.0.1";
    public static final int NPU_PORT = 52625;
    public static final String INFERENCE_PATH = "/v1/audio/transcriptions";

    public record Download(String fileName, String url, long size, String sha256) {
    }

    public static final Download RUNTIME = new Download(
            "whisper-v1.8.4-windows-vulkan-x64.zip",
            "https://github.com/lemonade-sdk/whisper.cpp-rocm/releases/download/v1.8.4/whisper-v1.8.4-windows-vulkan-x64.zip",
            21_203_995L,
            "e0d20a0f92e31b98adc0faf71172efc810b701e6391a9d858ca045bff26f77cd");
    public static final String RUNTIME_DIR = "whisper-1.8.4-vulkan";
    public static final String SERVER_EXE = "whisper-server.exe";

    public enum Model {
        TURBO("turbo", new Download("ggml-large-v3-turbo-q5_0.bin",
                "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin",
                574_041_195L, "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2")),
        SMALL("small", new Download("ggml-small-q5_1.bin",
                "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
                190_085_487L, "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb")),
        BASE("base", new Download("ggml-base-q5_1.bin",
                "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
                59_707_625L, "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898"));

        private final String id;
        private final Download file;

        Model(String id, Download file) {
            this.id = id;
            this.file = file;
        }

        public String id() {
            return id;
        }

        public Download file() {
            return file;
        }

        public String translationKey() {
            return "voicecaptions.gpu_model." + id;
        }

        public Model next() {
            Model[] all = values();
            return all[(ordinal() + 1) % all.length];
        }

        public static Model byId(String id) {
            if (id != null) {
                String normalized = id.strip().toLowerCase(Locale.ROOT);
                for (Model model : values()) {
                    if (model.id.equals(normalized)) {
                        return model;
                    }
                }
            }
            return TURBO;
        }
    }

    private EngineCatalog() {
    }

    public static boolean isSupportedPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return os.contains("win") && (arch.equals("amd64") || arch.equals("x86_64"));
    }

    public static long megabytes(long bytes) {
        return Math.round(bytes / 1_048_576.0);
    }
}
