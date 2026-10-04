package org.lts.voicecaptions.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BubblePlacerTest {
    @Test
    void sitsAboveAnchorWhenAlone() {
        BubblePlacer.Box box = new BubblePlacer.Box("a", 5, 200, 150, 80, 30);
        BubblePlacer.place(List.of(box), 400, 300, 4, 2);
        assertEquals(160.0F, box.left);
        assertEquals(120.0F, box.top);
        assertFalse(box.lifted);
    }

    @Test
    void farBubbleMovesAboveNearOne() {
        BubblePlacer.Box near = new BubblePlacer.Box("near", 4, 200, 150, 80, 30);
        BubblePlacer.Box far = new BubblePlacer.Box("far", 10, 210, 150, 80, 30);
        BubblePlacer.place(List.of(far, near), 400, 300, 4, 2);
        assertFalse(near.lifted);
        assertTrue(far.lifted);
        assertTrue(far.top + far.height + 2 <= near.top + 0.001F);
    }

    @Test
    void clampsToScreenEdges() {
        BubblePlacer.Box box = new BubblePlacer.Box("edge", 5, 10, 20, 80, 30);
        BubblePlacer.place(List.of(box), 400, 300, 4, 2);
        assertEquals(4.0F, box.left);
        assertEquals(4.0F, box.top);
        BubblePlacer.Box wide = new BubblePlacer.Box("wide", 5, 200, 150, 500, 30);
        BubblePlacer.place(List.of(wide), 400, 300, 4, 2);
        assertEquals(-50.0F, wide.left);
    }
}
