package org.esa.nest.dataio.dem.srtm;

import junit.framework.TestCase;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.dataio.ProductReader;

import java.io.IOException;
import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Apr 25, 2008
 * To change this template use File | Settings | File Templates.
 */
public class TestSRTMElevationModel extends TestCase {


    final SRTMElevationModelDescriptor demDescriptor = new SRTMElevationModelDescriptor();

    public void testCreateEleveationTiles()
    {
        if(!demDescriptor.isDemInstalled()) return;

        for (int i = 0; i < SRTMElevationModelDescriptor.NUM_X_TILES; i++) {
            for (int j = 0; j < SRTMElevationModelDescriptor.NUM_Y_TILES; j++) {

                final int minLon = i * SRTMElevationModelDescriptor.DEGREE_RES_Y - 180;
                final int minLat = j * -SRTMElevationModelDescriptor.DEGREE_RES_X + 90;

                File file = demDescriptor.getTileFile(minLon, minLat);
                System.out.print(file.getName());
                System.out.println();

                assertTrue(file.exists());
            }
        }
    }

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
                    System.out.print("srtm30 elev " + elevation + ", ");
                } catch (Exception e) {
                    assertFalse("Get Elevation threw", true);
                }
            }
        }
    }    
}