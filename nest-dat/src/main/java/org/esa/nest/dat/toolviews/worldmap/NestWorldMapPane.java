package org.esa.nest.dat.toolviews.worldmap;

import org.esa.beam.framework.ui.WorldMapPane;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Paints the given world map with the given products on top.
 * The selected product is painted highlighted.
 */
public class NestWorldMapPane extends WorldMapPane {

    public NestWorldMapPane(final BufferedImage image) {
        super(image);
    }

    @Override
    protected final void paintComponent(final Graphics g) {
        getPainter().paint(g);
    }
}
