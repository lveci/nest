package org.esa.nest.util;

import java.awt.*;

/**
 * Java 2D graphic utils
 */
public class GraphicsUtils {


    public static void setHighQuality(final Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);

        g.setRenderingHint(RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY);
    }

    public static void shadowText(final Graphics2D g, final Color col, final String text, final int x, final int y) {
        g.setColor(Color.black);
        g.drawString(text, x + 2, y + 2);
        g.setColor(col);
        g.drawString(text, x, y);
    }

    public static void outlineText(final Graphics2D g, final Color col, final String text, final int x, final int y) {
        g.setColor(Color.black);
        g.drawString(text, x - 1, y - 1);
        g.drawString(text, x - 1, y + 1);
        g.drawString(text, x + 1, y - 1);
        g.drawString(text, x + 1, y + 1);
        g.setColor(col);
        g.drawString(text, x, y);
    }
}
