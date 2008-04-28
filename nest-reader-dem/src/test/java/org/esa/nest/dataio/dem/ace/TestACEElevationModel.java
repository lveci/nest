package org.esa.nest.dataio.dem.ace;

import junit.framework.TestCase;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.datamodel.GeoPos;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Apr 25, 2008
 * To change this template use File | Settings | File Templates.
 */
public class TestACEElevationModel extends TestCase {


    final ACEElevationModelDescriptor demDescriptor = new ACEElevationModelDescriptor();

    public void testElevationModel() {

        if(!demDescriptor.isDemInstalled()) return;

        final ElevationModel dem = demDescriptor.createDem();

        final GeoPos geoPos = new GeoPos(-18, 20);
        float elevation;
        int height = 5;
        int width = 5;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                geoPos.setLocation(geoPos.getLat() + x, geoPos.getLon() + y);
                try {
                    elevation = dem.getElevation(geoPos);
                    System.out.print("ace elev " + elevation + ", ");
                } catch (Exception e) {
                    assertFalse("Get Elevation threw", true);
                }
            }
        }
    }
}
