/*
 * $Id: RasterDataEvalEnvTest.java,v 1.1 2009-04-28 14:39:33 lveci Exp $
 *
 * Copyright (c) 2003 Brockmann Consult GmbH. All right reserved.
 * http://www.brockmann-consult.de
 */
package org.esa.beam.framework.dataop.barithm;

import junit.framework.TestCase;

public class RasterDataEvalEnvTest extends TestCase {

    public void testDefaultConstructor() {
        final RasterDataEvalEnv env = new RasterDataEvalEnv(0, 0, 1, 1);
        assertEquals(0, env.getPixelX());
        assertEquals(0, env.getPixelY());
        assertEquals(0, env.getElemIndex());
        assertEquals(0, env.getOffsetX());
        assertEquals(0, env.getOffsetY());
        assertEquals(1, env.getRegionWidth());
        assertEquals(1, env.getRegionHeight());
    }

    public void testConstructor() {
        final RasterDataEvalEnv env = new RasterDataEvalEnv(20, 14, 238, 548);
        assertEquals(0, env.getPixelX());
        assertEquals(0, env.getPixelY());
        assertEquals(0, env.getElemIndex());
        assertEquals(20, env.getOffsetX());
        assertEquals(14, env.getOffsetY());
        assertEquals(238, env.getRegionWidth());
        assertEquals(548, env.getRegionHeight());
    }
}
