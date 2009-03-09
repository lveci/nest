package org.esa.nest.dat.views.polarview;

import org.esa.beam.util.Debug;

import java.awt.*;

public class ReadoutCanvas {

    private Point origin = new Point(0, 0);
    private Font font = Font.decode("SansSerif-plain-9");
    private String[] readoutList = null;

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

        int x = 0;
        int y = 0;
        for(String str : readoutList) {
            g.drawString(str, x, y);
            y += 20;
        }
    }

}