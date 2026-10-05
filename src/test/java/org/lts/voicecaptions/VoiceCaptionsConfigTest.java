package org.lts.voicecaptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VoiceCaptionsConfigTest {
    @Test
    void oldConfigWithDefaultServerBecomesAuto() {
        VoiceCaptionsConfig config = new VoiceCaptionsConfig();
        config.configVersion = 1;
        config.engineMode = null;
        VoiceCaptionsConfig.sanitize(config);
        assertTrue(config.isAutoEngine());
        assertEquals("turbo", config.gpuModel);
    }

    @Test
    void oldConfigWithOwnServerStaysCustom() {
        VoiceCaptionsConfig config = new VoiceCaptionsConfig();
        config.configVersion = 1;
        config.endpoint = "http://127.0.0.1:8080/inference";
        VoiceCaptionsConfig.sanitize(config);
        assertFalse(config.isAutoEngine());
        assertEquals("http://127.0.0.1:8080/inference", config.endpoint);
    }

    @Test
    void sanitizeNormalizesEngineFields() {
        VoiceCaptionsConfig config = new VoiceCaptionsConfig();
        config.engineMode = "weird";
        config.gpuModel = "SMALL";
        config.language = "Russian";
        config.maxLines = 99;
        VoiceCaptionsConfig.sanitize(config);
        assertTrue(config.isAutoEngine());
        assertEquals("small", config.gpuModel);
        assertEquals("", config.language);
        assertEquals(8, config.maxLines);
    }
}
