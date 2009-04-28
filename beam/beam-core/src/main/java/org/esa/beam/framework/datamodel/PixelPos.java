/*
 * $Id: PixelPos.java,v 1.1 2009-04-28 14:39:33 lveci Exp $
 * Copyright (c) by Brockmann Consult 2003
 */

package org.esa.beam.framework.datamodel;

import java.awt.geom.Point2D;

/**
 * A <code>PixelPos</code> represents a position or point in a pixel coordinate system.
 */
public class PixelPos extends Point2D.Float {

    /**
     * Constructs and initializes a <code>PixelPos</code> with coordinate (0,&nbsp;0).
     */
    public PixelPos() {
    }

    /**
     * Constructs and initializes a <code>PixelPos</code> with the specified coordinate.
     *
     * @param x the x component of the coordinate
     * @param y the y component of the coordinate
     */
    public PixelPos(float x, float y) {
        super(x, y);
    }

    /**
     * Tests whether or not this pixel position is valid.
     *
     * @return true, if so
     */
    public boolean isValid() {
        return !(java.lang.Float.isNaN(x) || java.lang.Float.isNaN(y));
    }

    /**
     * Sets this pixel position so that is becomes invalid.
     */
    public void setInvalid() {
        x = java.lang.Float.NaN;
        y = java.lang.Float.NaN;
    }
}
