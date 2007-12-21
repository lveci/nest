package org.esa.nest.dataio.dem.ace;

import junit.framework.TestCase;

public class TestACEElevationModelDescriptor extends TestCase {

    final ACEElevationModelDescriptor _descriptor = new ACEElevationModelDescriptor();

    public void testConstantProperties() {
        assertEquals("ACE", _descriptor.getName());
    }

    public void testFilenameCreation() {

        assertEquals("45S004W.ACE", _descriptor.createTileFilename(-45, -4));
        assertEquals("45S004E.ACE", _descriptor.createTileFilename(-45, +4));
        assertEquals("45N004W.ACE", _descriptor.createTileFilename(+45, -4));
        assertEquals("45N004E.ACE", _descriptor.createTileFilename(+45, +4));

        assertEquals("05S045W.ACE", _descriptor.createTileFilename(-5, -45));
        assertEquals("05S045E.ACE", _descriptor.createTileFilename(-5, +45));
        assertEquals("05N045W.ACE", _descriptor.createTileFilename(+5, -45));
        assertEquals("05N045E.ACE", _descriptor.createTileFilename(+5, +45));

        assertEquals("90S180W.ACE", _descriptor.createTileFilename(-90, -180));
        assertEquals("90S180E.ACE", _descriptor.createTileFilename(-90, +180));
        assertEquals("90N180W.ACE", _descriptor.createTileFilename(+90, -180));
        assertEquals("90N180E.ACE", _descriptor.createTileFilename(+90, +180));
    }

}
