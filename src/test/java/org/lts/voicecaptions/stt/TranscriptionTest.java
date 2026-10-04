package org.lts.voicecaptions.stt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TranscriptionTest {
    @Test
    void keepsNormalSpeech() {
        assertEquals("Иду к тебе, жди у портала", TranscriptCleaner.clean("  Иду к тебе,   жди у портала "));
        assertEquals("Ok, let's go", TranscriptCleaner.clean("Ok, let's go"));
    }

    @Test
    void removesKnownWhisperHallucinations() {
        assertEquals("", TranscriptCleaner.clean("Продолжение следует..."));
        assertEquals("", TranscriptCleaner.clean("Субтитры сделал DimaTorzok"));
        assertEquals("", TranscriptCleaner.clean("Thank you for watching!"));
        assertEquals("", TranscriptCleaner.clean(" you "));
        assertEquals("", TranscriptCleaner.clean("..."));
        assertEquals("", TranscriptCleaner.clean("[музыка]"));
        assertEquals("", TranscriptCleaner.clean(null));
    }

    @Test
    void stripsBracketedSoundTagsButKeepsWords() {
        assertEquals("ну давай", TranscriptCleaner.clean("[смех] ну давай"));
        assertEquals("корректор текста сломался", TranscriptCleaner.clean("корректор текста сломался"));
    }

    @Test
    void collapsesRepetitionLoops() {
        String looped = "давай давай давай давай давай давай давай";
        assertEquals("давай…", TranscriptCleaner.clean(looped));
        String phrase = "я тут я тут я тут я тут";
        assertEquals("я тут…", TranscriptCleaner.clean(phrase));
        assertEquals("раз два три четыре пять шесть", TranscriptCleaner.clean("раз два три четыре пять шесть"));
    }

    @Test
    void capsVeryLongText() {
        String text = "слово ".repeat(5) + "a".repeat(600);
        String cleaned = TranscriptCleaner.clean(text);
        assertTrue(cleaned.length() <= 400);
        assertTrue(cleaned.endsWith("…"));
    }

    @Test
    void extractsTextFromOpenAiAndPlainResponses() {
        assertEquals("привет", TranscriptionClient.extractText("{\"model\":\"whisper-v3:turbo\",\"text\":\"привет\"}"));
        assertEquals("plain text", TranscriptionClient.extractText("plain text\n"));
        assertNull(TranscriptionClient.extractText("{\"error\":\"boom\"}"));
        assertNull(TranscriptionClient.extractText("{broken"));
        assertNull(TranscriptionClient.extractText(null));
    }

    @Test
    void multipartContainsFieldsAndClosingBoundary() {
        MultipartBody body = new MultipartBody()
                .file("file", "speech.wav", "audio/wav", new byte[]{1, 2, 3})
                .field("model", "whisper-v3:turbo")
                .field("language", "");
        String text = new String(body.build(), StandardCharsets.ISO_8859_1);
        String boundary = body.contentType().substring(body.contentType().indexOf("boundary=") + 9);
        assertTrue(text.contains("name=\"file\"; filename=\"speech.wav\""));
        assertTrue(text.contains("name=\"model\"\r\n\r\nwhisper-v3:turbo\r\n"));
        assertTrue(!text.contains("name=\"language\""));
        assertTrue(text.endsWith("--" + boundary + "--\r\n"));
    }
}
