package org.lts.voicecaptions.stt;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class TranscriptCleaner {
    private static final Pattern BRACKETED = Pattern.compile("[\\[(*♪][^\\])*♪]{0,60}[\\])*♪]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern NON_LETTERS = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final int MAX_LENGTH = 400;

    private static final List<String> HALLUCINATIONS = List.of(
            "продолжение следует",
            "субтитры сделал",
            "субтитры создавал",
            "субтитры подготовил",
            "редактор субтитров",
            "корректор а кулакова",
            "спасибо за просмотр",
            "спасибо за внимание",
            "подписывайтесь на канал",
            "ставьте лайки",
            "dimatorzok",
            "amara org",
            "thank you for watching",
            "thanks for watching",
            "please subscribe",
            "subtitles by",
            "transcribed by");

    private static final List<String> FILLER_ONLY = List.of(
            "you", "thank you", "bye", "ok", "hmm", "uh", "um", "ah", "oh",
            "а", "э", "м", "ну", "угу", "ага", "мм", "хм");

    private TranscriptCleaner() {
    }

    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String text = BRACKETED.matcher(raw).replaceAll(" ");
        text = WHITESPACE.matcher(text).replaceAll(" ").strip();
        text = stripEdgeDots(text);
        if (text.isEmpty()) {
            return "";
        }
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return "";
        }
        for (String phrase : HALLUCINATIONS) {
            if (normalized.contains(phrase)) {
                return "";
            }
        }
        if (FILLER_ONLY.contains(normalized)) {
            return "";
        }
        text = collapseRepeats(text);
        if (text.length() > MAX_LENGTH) {
            text = text.substring(0, MAX_LENGTH - 1).strip() + "…";
        }
        return text;
    }

    static String normalize(String text) {
        return NON_LETTERS.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }

    static String collapseRepeats(String text) {
        String[] words = text.split(" ");
        if (words.length < 6) {
            return text;
        }
        for (int size = 1; size <= 4; size++) {
            int repeats = 1;
            int index = words.length - size;
            while (index - size >= 0 && sameRun(words, index - size, index, size)) {
                repeats++;
                index -= size;
            }
            if (repeats >= 3) {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < index + size; i++) {
                    if (!builder.isEmpty()) {
                        builder.append(' ');
                    }
                    builder.append(words[i]);
                }
                return builder.append('…').toString();
            }
        }
        return text;
    }

    private static boolean sameRun(String[] words, int first, int second, int size) {
        for (int i = 0; i < size; i++) {
            if (!normalize(words[first + i]).equals(normalize(words[second + i]))) {
                return false;
            }
        }
        return true;
    }

    private static String stripEdgeDots(String text) {
        int start = 0;
        while (start < text.length() && (text.charAt(start) == '.' || text.charAt(start) == '…'
                || text.charAt(start) == '-' || text.charAt(start) == ' ')) {
            start++;
        }
        return text.substring(start).strip();
    }
}
