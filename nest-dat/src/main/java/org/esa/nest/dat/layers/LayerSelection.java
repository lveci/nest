package org.esa.nest.dat.layers;

import com.bc.ceres.swing.selection.SelectionContext;

import java.awt.*;

/**
 *
 */
public interface LayerSelection {

    public void selectRectangle(final Rectangle rect);

    public void selectPoint(final int x, final int y);
}
