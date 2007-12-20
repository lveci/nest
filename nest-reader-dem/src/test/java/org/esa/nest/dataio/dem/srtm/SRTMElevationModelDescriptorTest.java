package org.esa.nest.dataio.dem.srtm;

import junit.framework.TestCase;

public class SRTMElevationModelDescriptorTest extends TestCase {

    final SRTMElevationModelDescriptor _descriptor = new SRTMElevationModelDescriptor();

    public void testConstantProperties() {
        assertEquals("SRTM", _descriptor.getName());
    }

    public void testFilenameCreation() {

        assertEquals("45S004W.SRTM", _descriptor.createTileFilename(-45, -4));
        assertEquals("45S004E.SRTM", _descriptor.createTileFilename(-45, +4));
        assertEquals("45N004W.SRTM", _descriptor.createTileFilename(+45, -4));
        assertEquals("45N004E.SRTM", _descriptor.createTileFilename(+45, +4));

        assertEquals("05S045W.SRTM", _descriptor.createTileFilename(-5, -45));
        assertEquals("05S045E.SRTM", _descriptor.createTileFilename(-5, +45));
        assertEquals("05N045W.SRTM", _descriptor.createTileFilename(+5, -45));
        assertEquals("05N045E.SRTM", _descriptor.createTileFilename(+5, +45));

        assertEquals("90S180W.SRTM", _descriptor.createTileFilename(-90, -180));
        assertEquals("90S180E.SRTM", _descriptor.createTileFilename(-90, +180));
        assertEquals("90N180W.SRTM", _descriptor.createTileFilename(+90, -180));
        assertEquals("90N180E.SRTM", _descriptor.createTileFilename(+90, +180));
    }

}
