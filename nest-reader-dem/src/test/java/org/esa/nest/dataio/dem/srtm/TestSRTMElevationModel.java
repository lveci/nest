package org.esa.nest.dataio.dem.srtm;

import junit.framework.TestCase;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.dataio.ProductReader;

import java.io.IOException;
import java.io.File;
import java.util.Arrays;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Apr 25, 2008
 * To change this template use File | Settings | File Templates.
 */
public class TestSRTMElevationModel extends TestCase {


    final SRTMElevationModelDescriptor demDescriptor = new SRTMElevationModelDescriptor();
    String[] expectedValues = {
            "w180n90.dem",
            "w180n40.dem",
            "w180s10.dem",
            "w140n90.dem",
            "w140n40.dem",
            "w140s10.dem",
            "w100n90.dem",
            "w100n40.dem",
            "w100s10.dem",
            "w060n90.dem",
            "w060n40.dem",
            "w060s10.dem",
            "w020n90.dem",
            "w020n40.dem",
            "w020s10.dem",
            "e020n90.dem",
            "e020n40.dem",
            "e020s10.dem",
            "e060n90.dem",
            "e060n40.dem",
            "e060s10.dem",
            "e100n90.dem",
            "e100n40.dem",
            "e100s10.dem",
            "e140n90.dem",
            "e140n40.dem",
            "e140s10.dem"
    };

    public void testCreateEleveationTiles()
    {
        if(!demDescriptor.isDemInstalled()) return;
        String[] fileNames = new String[expectedValues.length];
        int count = 0;

        for (int i = 0; i < SRTMElevationModelDescriptor.NUM_X_TILES; i++) {
            for (int j = 0; j < SRTMElevationModelDescriptor.NUM_Y_TILES; j++) {

                final int minLon = i * SRTMElevationModelDescriptor.DEGREE_RES_Y - 180;
                final int minLat = j * -SRTMElevationModelDescriptor.DEGREE_RES_X + 90;

                File file = demDescriptor.getTileFile(minLon, minLat);
                fileNames[count++] = file.getName();

                assertTrue(file.exists());
            }
        }

        assertTrue(Arrays.equals(expectedValues, fileNames));
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