package org.lts.voicecaptions.stt;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.lts.voicecaptions.ModLog;
import org.lts.voicecaptions.audio.Resampler;
import org.lts.voicecaptions.audio.WavEncoder;
import org.lts.voicecaptions.engine.EngineRouter;

public final class TranscriptionClient {
    private static final ModLog LOGGER = ModLog.of("Transcription");
    private static final long UNAVAILABLE_BACKOFF_MS = 10_000L;

    public record Request(String language, int timeoutSeconds) {
    }

    public interface Router {
        EngineRouter.Route route() throws EngineRouter.RouteException;

        void unreachable(EngineRouter.Route route);
    }

    public enum FailureKind {
        UNAVAILABLE,
        TIMEOUT,
        HTTP_ERROR,
        BAD_RESPONSE,
        BAD_ENDPOINT,
        NOT_INSTALLED,
        UNSUPPORTED,
        ENGINE_FAILED,
        DROPPED
    }

    public sealed interface Result permits Success, Failure {
    }

    public record Success(String text, long latencyMs, EngineRouter.Backend backend, String model) implements Result {
    }

    public record Failure(FailureKind kind, String detail) implements Result {
    }

    private record Job(short[] samples, Request request, Consumer<Result> callback) {
    }

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private final Router router;
    private final LinkedBlockingDeque<Job> queue = new LinkedBlockingDeque<>();
    private final Thread worker;
    private volatile int maxQueue = 6;
    private volatile long unavailableUntilMs;
    private volatile boolean running = true;
    private EngineRouter.Backend lastAttemptBackend;
    private final Set<String> reported = new HashSet<>();

    public TranscriptionClient(Router router) {
        this.router = router;
        worker = new Thread(this::run, "VoiceCaptions STT");
        worker.setDaemon(true);
        worker.start();
    }

    public void setMaxQueue(int maxQueue) {
        this.maxQueue = Math.max(1, maxQueue);
    }

    public int pending() {
        return queue.size();
    }

    public void submit(short[] mono16k, Request request, Consumer<Result> callback) {
        queue.offerLast(new Job(mono16k, request, callback));
        while (queue.size() > maxQueue) {
            Job dropped = queue.pollFirst();
            if (dropped != null) {
                dropped.callback().accept(new Failure(FailureKind.DROPPED, "queue full"));
            }
        }
    }

    public void probe(Request request, Consumer<Result> callback) {
        unavailableUntilMs = 0L;
        queue.offerFirst(new Job(new short[Resampler.TARGET_RATE], request, callback));
    }

    public void warmUp(Consumer<Result> callback) {
        queue.offerFirst(new Job(new short[Resampler.TARGET_RATE], new Request("", 60), callback));
    }

    public void shutdown() {
        running = false;
        worker.interrupt();
    }

    private void run() {
        while (running) {
            Job job;
            try {
                job = queue.pollFirst(1, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            if (job == null) {
                continue;
            }
            Result result;
            try {
                result = execute(job);
            } catch (RuntimeException exception) {
                if (firstReport("transcribe", exception)) {
                    LOGGER.warn("Could not transcribe a phrase; repeats of this error are not logged", exception);
                }
                result = new Failure(FailureKind.BAD_RESPONSE, exception.getClass().getSimpleName());
            }
            try {
                job.callback().accept(result);
            } catch (RuntimeException exception) {
                if (firstReport("callback", exception)) {
                    LOGGER.warn("Could not deliver a transcription result; repeats of this error are not logged", exception);
                }
            }
        }
    }

    private boolean firstReport(String scope, RuntimeException exception) {
        StackTraceElement[] trace = exception.getStackTrace();
        return reported.add(scope + " " + exception.getClass().getName() + " " + (trace.length > 0 ? trace[0] : ""));
    }

    private Result execute(Job job) {
        lastAttemptBackend = null;
        Result result = attempt(job);
        if (result instanceof Failure failure && failure.kind() == FailureKind.UNAVAILABLE
                && lastAttemptBackend == EngineRouter.Backend.NPU) {
            return attempt(job);
        }
        return result;
    }

    private Result attempt(Job job) {
        if (System.currentTimeMillis() < unavailableUntilMs) {
            return new Failure(FailureKind.UNAVAILABLE, "backoff");
        }
        Request request = job.request();
        EngineRouter.Route route;
        try {
            route = router.route();
            lastAttemptBackend = route.backend();
        } catch (EngineRouter.RouteException exception) {
            return switch (exception.problem()) {
                case NOT_INSTALLED -> new Failure(FailureKind.NOT_INSTALLED, exception.getMessage());
                case UNSUPPORTED -> new Failure(FailureKind.UNSUPPORTED, exception.getMessage());
                case ENGINE_FAILED -> new Failure(FailureKind.ENGINE_FAILED, exception.getMessage());
            };
        }
        URI uri;
        try {
            uri = URI.create(route.endpoint().strip());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return new Failure(FailureKind.BAD_ENDPOINT, route.endpoint());
            }
        } catch (IllegalArgumentException exception) {
            return new Failure(FailureKind.BAD_ENDPOINT, route.endpoint());
        }
        MultipartBody body = new MultipartBody()
                .file("file", "speech.wav", "audio/wav", WavEncoder.encodeMono16(job.samples(), Resampler.TARGET_RATE))
                .field("model", route.model())
                .field("language", request.language())
                .field("response_format", "json");
        HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(request.timeoutSeconds()))
                .header("Content-Type", body.contentType())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.build()))
                .build();
        long started = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException exception) {
            return new Failure(FailureKind.TIMEOUT, Integer.toString(request.timeoutSeconds()));
        } catch (ConnectException exception) {
            return unreachable(route, uri.getHost() + ":" + uri.getPort());
        } catch (IOException exception) {
            return unreachable(route, exception.getClass().getSimpleName());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new Failure(FailureKind.DROPPED, "interrupted");
        }
        long latencyMs = (System.nanoTime() - started) / 1_000_000L;
        if (response.statusCode() / 100 != 2) {
            return new Failure(FailureKind.HTTP_ERROR, Integer.toString(response.statusCode()));
        }
        String text = extractText(response.body());
        if (text == null) {
            return new Failure(FailureKind.BAD_RESPONSE, abbreviate(response.body()));
        }
        return new Success(text, latencyMs, route.backend(), route.model());
    }

    private Failure unreachable(EngineRouter.Route route, String detail) {
        router.unreachable(route);
        if (route.backend() == EngineRouter.Backend.CUSTOM) {
            unavailableUntilMs = System.currentTimeMillis() + UNAVAILABLE_BACKOFF_MS;
        }
        return new Failure(FailureKind.UNAVAILABLE, route.endpoint());
    }

    static String extractText(String body) {
        if (body == null) {
            return null;
        }
        String trimmed = body.strip();
        if (!trimmed.startsWith("{")) {
            return trimmed;
        }
        try {
            JsonElement element = JsonParser.parseString(trimmed);
            if (!element.isJsonObject()) {
                return null;
            }
            JsonObject object = element.getAsJsonObject();
            JsonElement text = object.get("text");
            if (text == null || !text.isJsonPrimitive()) {
                return null;
            }
            return text.getAsString();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        String flat = body.replaceAll("\\s+", " ").strip();
        return flat.length() > 80 ? flat.substring(0, 80) + "…" : flat;
    }
}
