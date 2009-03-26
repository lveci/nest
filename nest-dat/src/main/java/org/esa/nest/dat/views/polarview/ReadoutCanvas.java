package org.esa.nest.dat.views.polarview;

import org.esa.beam.util.Debug;

import java.awt.*;

public class ReadoutCanvas {

    private Point origin = new Point(0, 0);
    private final Font font = Font.decode("SansSerif-plain-9");
    private String[] readoutList = null;

    private static final int marginX = 50;
    private static final int marginY = 50;

    public ReadoutCanvas() {

    }

    public void setReadout(String[] readout) {
        this.readoutList = readout;
    }

    public void paint(Graphics g) {
        try {
            drawSynchronised(g);
        } catch (Throwable e) {
            Debug.trace(e);
        }
    }

    private synchronized void drawSynchronised(Graphics g) {
        draw(g);
    }

    private void draw(Graphics g) {
        if(readoutList == null)
            return;

        g.translate(origin.x, origin.y);

        int x = marginX;
        int y = marginY;
        for(String str : readoutList) {
            g.drawString(str, x, y);
            y += 20;
        }
    }

}