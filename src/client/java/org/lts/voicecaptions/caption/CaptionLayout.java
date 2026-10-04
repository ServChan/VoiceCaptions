package org.lts.voicecaptions.caption;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

public final class CaptionLayout {
    public static final String ELLIPSIS = "…";

    public record Block(List<String> lines, int width, boolean truncated) {
        public static final Block EMPTY = new Block(List.of(), 0, false);

        public boolean isEmpty() {
            return lines.isEmpty();
        }
    }

    private CaptionLayout() {
    }

    public static Block layout(List<String> phrases, int maxWidth, int maxLines, ToIntFunction<String> width) {
        if (phrases == null || phrases.isEmpty() || maxLines < 1 || maxWidth < 8) {
            return Block.EMPTY;
        }
        List<String> lines = new ArrayList<>();
        for (String phrase : phrases) {
            if (phrase != null && !phrase.isBlank()) {
                lines.addAll(wrap(phrase.strip(), maxWidth, width));
            }
        }
        if (lines.isEmpty()) {
            return Block.EMPTY;
        }
        boolean truncated = lines.size() > maxLines;
        if (truncated) {
            lines = new ArrayList<>(lines.subList(lines.size() - maxLines, lines.size()));
            lines.set(0, prefixEllipsis(lines.get(0), maxWidth, width));
        }
        int widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, width.applyAsInt(line));
        }
        return new Block(List.copyOf(lines), widest, truncated);
    }

    public static List<String> wrap(String text, int maxWidth, ToIntFunction<String> width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (width.applyAsInt(candidate) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (width.applyAsInt(word) <= maxWidth) {
                line.append(word);
                continue;
            }
            List<String> pieces = breakWord(word, maxWidth, width);
            for (int i = 0; i < pieces.size() - 1; i++) {
                lines.add(pieces.get(i));
            }
            line.append(pieces.get(pieces.size() - 1));
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    static List<String> breakWord(String word, int maxWidth, ToIntFunction<String> width) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (start < word.length()) {
            int end = start + 1;
            while (end < word.length() && width.applyAsInt(word.substring(start, end + 1) + "-") <= maxWidth) {
                end++;
            }
            if (end < word.length()) {
                pieces.add(word.substring(start, end) + "-");
            } else {
                pieces.add(word.substring(start, end));
            }
            start = end;
        }
        return pieces;
    }

    static String prefixEllipsis(String line, int maxWidth, ToIntFunction<String> width) {
        String candidate = ELLIPSIS + line;
        if (width.applyAsInt(candidate) <= maxWidth) {
            return candidate;
        }
        String body = line;
        while (!body.isEmpty() && width.applyAsInt(ELLIPSIS + body) > maxWidth) {
            int space = body.indexOf(' ');
            body = space > 0 ? body.substring(space + 1) : body.substring(1);
        }
        return ELLIPSIS + body;
    }
}
