package works.nuty.bastion;

import java.awt.Color;

public class DistinctColorGenerator {
    private float currentHue = 0f;

    private static final float GOLDEN_RATIO_CONJUGATE = 0.618033988749895f;

    public int nextColor() {
        currentHue += GOLDEN_RATIO_CONJUGATE;
        currentHue %= 1.0f;

        int rgb = Color.HSBtoRGB(currentHue, 0.8f, 0.9f);

        return 0xFF000000 | rgb;
    }

    public int nextTranslucentColor(int alpha) {
        int color = nextColor();
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
