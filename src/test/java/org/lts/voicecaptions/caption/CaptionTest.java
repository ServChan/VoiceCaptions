package org.lts.voicecaptions.caption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

class CaptionTest {
    private static final ToIntFunction<String> SIX_PX = text -> text.length() * 6;
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void wrapsByWordsWithinMaxWidth() {
        List<String> lines = CaptionLayout.wrap("one two three four five", 60, SIX_PX);
        assertEquals(List.of("one two", "three four", "five"), lines);
        for (String line : lines) {
            assertTrue(SIX_PX.applyAsInt(line) <= 60);
        }
    }

    @Test
    void breaksWordsLongerThanALine() {
        List<String> lines = CaptionLayout.wrap("abcdefghijklmnopqrstuvwxyz", 60, SIX_PX);
        assertTrue(lines.size() >= 3);
        for (String line : lines) {
            assertTrue(SIX_PX.applyAsInt(line) <= 60, line);
        }
        assertEquals("abcdefghijklmnopqrstuvwxyz", String.join("", lines).replace("-", ""));
    }

    @Test
    void keepsNewestLinesAndMarksTruncation() {
        CaptionLayout.Block block = CaptionLayout.layout(
                List.of("old words here", "newer phrase", "latest"), 60, 2, SIX_PX);
        assertTrue(block.truncated());
        assertEquals(2, block.lines().size());
        assertEquals("latest", block.lines().get(1));
        assertTrue(block.lines().get(0).startsWith(CaptionLayout.ELLIPSIS));
        for (String line : block.lines()) {
            assertTrue(SIX_PX.applyAsInt(line) <= 60, line);
        }
        assertEquals(block.lines().stream().mapToInt(SIX_PX).max().orElse(0), block.width());
    }

    @Test
    void emptyInputGivesEmptyBlock() {
        assertTrue(CaptionLayout.layout(List.of(), 100, 3, SIX_PX).isEmpty());
        assertTrue(CaptionLayout.layout(List.of("   "), 100, 3, SIX_PX).isEmpty());
        assertTrue(CaptionLayout.layout(List.of("text"), 100, 0, SIX_PX).isEmpty());
    }

    @Test
    void phrasesExpireAndFade() {
        CaptionBoard board = new CaptionBoard();
        board.configure(new CaptionBoard.Settings(1000, 0, 5000, 400, 3));
        board.add(ALICE, "Alice", "привет", 0);
        List<CaptionBoard.SpeakerView> early = board.snapshot(100, Set.of(), Map.of());
        assertEquals(1, early.size());
        assertEquals(1.0F, early.get(0).alpha());
        float fading = board.snapshot(800, Set.of(), Map.of()).get(0).alpha();
        assertTrue(fading > 0.0F && fading < 1.0F, "alpha " + fading);
        assertTrue(board.snapshot(1000, Set.of(), Map.of()).isEmpty());
    }

    @Test
    void keepsOnlyNewestPhrasesAndShowsSpeakersWithoutText() {
        CaptionBoard board = new CaptionBoard();
        board.configure(new CaptionBoard.Settings(10_000, 0, 20_000, 400, 2));
        board.add(ALICE, "Alice", "one", 0);
        board.add(ALICE, "Alice", "two", 1);
        board.add(ALICE, "Alice", "three", 2);
        assertEquals(List.of("two", "three"), board.snapshot(10, Set.of(), Map.of()).get(0).phrases());

        UUID bob = UUID.fromString("00000000-0000-0000-0000-000000000002");
        List<CaptionBoard.SpeakerView> views = board.snapshot(10, Set.of(bob), Map.of(bob, "Bob"));
        CaptionBoard.SpeakerView bobView = views.stream().filter(view -> view.speaker().equals(bob)).findFirst().orElseThrow();
        assertTrue(bobView.speaking());
        assertTrue(bobView.phrases().isEmpty());
        assertEquals("Bob", bobView.name());
    }

    @Test
    void transcribingKeepsBubbleOpaque() {
        CaptionBoard board = new CaptionBoard();
        board.configure(new CaptionBoard.Settings(1000, 0, 5000, 400, 3));
        board.add(ALICE, "Alice", "hi", 0);
        board.transcriptionStarted(ALICE, "Alice");
        CaptionBoard.SpeakerView view = board.snapshot(900, Set.of(), Map.of()).get(0);
        assertTrue(view.transcribing());
        assertEquals(1.0F, view.alpha());
        board.transcriptionFinished(ALICE);
        assertFalse(board.snapshot(900, Set.of(), Map.of()).get(0).transcribing());
    }
}
