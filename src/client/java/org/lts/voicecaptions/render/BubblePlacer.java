package org.lts.voicecaptions.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BubblePlacer {
    private static final int MAX_PASSES = 12;

    public static final class Box {
        public final Object key;
        public final double distance;
        public final float width;
        public final float height;
        public final float anchorX;
        public final float anchorY;
        public float left;
        public float top;
        public boolean lifted;

        public Box(Object key, double distance, float anchorX, float anchorY, float width, float height) {
            this.key = key;
            this.distance = distance;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.width = width;
            this.height = height;
            this.left = anchorX - width / 2.0F;
            this.top = anchorY - height;
        }

        boolean overlaps(Box other, float gap) {
            return left < other.left + other.width + gap && other.left < left + width + gap
                    && top < other.top + other.height + gap && other.top < top + height + gap;
        }
    }

    private BubblePlacer() {
    }

    public static List<Box> place(List<Box> boxes, float screenWidth, float screenHeight, float margin, float gap) {
        List<Box> ordered = new ArrayList<>(boxes);
        ordered.sort(Comparator.comparingDouble(box -> box.distance));
        List<Box> placed = new ArrayList<>(ordered.size());
        for (Box box : ordered) {
            clampHorizontally(box, screenWidth, margin);
            box.top = Math.min(box.top, screenHeight - margin - box.height);
            for (int pass = 0; pass < MAX_PASSES; pass++) {
                Box blocker = null;
                for (Box other : placed) {
                    if (box.overlaps(other, gap)) {
                        blocker = other;
                        break;
                    }
                }
                if (blocker == null) {
                    break;
                }
                box.top = blocker.top - gap - box.height;
                box.lifted = true;
            }
            box.top = Math.max(margin, box.top);
            placed.add(box);
        }
        return placed;
    }

    private static void clampHorizontally(Box box, float screenWidth, float margin) {
        float maxLeft = screenWidth - margin - box.width;
        if (maxLeft < margin) {
            box.left = (screenWidth - box.width) / 2.0F;
            return;
        }
        box.left = Math.max(margin, Math.min(maxLeft, box.left));
    }
}
