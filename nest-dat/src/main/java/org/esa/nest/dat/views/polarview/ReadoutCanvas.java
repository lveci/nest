package org.esa.nest.dat.views.polarview;

import org.esa.beam.util.Debug;

import java.awt.*;

public class ReadoutCanvas {

    private Point readoutOrigin = new Point(20, 40);
    private Point metadataOrigin = new Point(20, 40);
    private final Font font = Font.decode("SansSerif-plain-9");
    private String[] readoutList = null;
    private String[] metadataList = null;

    public ReadoutCanvas() {

    }

    public void setReadout(String[] readout) {
        this.readoutList = readout;
    }

    public void setMetadata(String[] metadata) {
        this.metadataList = metadata;
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

        g.translate(readoutOrigin.x, readoutOrigin.y);

        int x = 0, y = 0;
        for(String str : readoutList) {
            g.drawString(str, x, y);
            y += 20;
        }

        if(metadataList == null)
            return;

        g.translate((int)g.getClipBounds().getWidth() - 220, 0);

        x = 0;
        y = 0;
        for(String str : metadataList) {
            g.drawString(str, x, y);
            y += 20;
        }
    }

}