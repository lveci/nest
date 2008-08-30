package org.esa.nest.dataio;

import junit.framework.TestCase;

import javax.imageio.ImageReader;
import java.io.File;
import java.io.IOException;
import java.awt.image.RenderedImage;

import it.geosolutions.imageio.plugins.geotiff.GeoTiffImageReaderSpi;
import org.gdal.gdal.*;
import org.gdal.ogr.ogr;

/**
 *
 * @author lveci
 */
public class TestGDALReader extends TestCase {

    String geoTiffFile = "P:\\nest\\nest\\ESA Data\\RADAR\\BEST_GEOTIF\\GEOTIF\\APM1.tif";

    public TestGDALReader(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        super.setUp();
    }

    public void tearDown() throws Exception {
        super.tearDown();
    }

    public void testGDAL() throws IOException {

        gdal.AllRegister();
        int cnt = gdal.GetDriverCount();
        for(int i=0; i < cnt; ++i) {
            String name = gdal.GetDriver(i).getLongName();
            System.out.println(name);
        }
        System.out.println();
    }

     public void testOGR() throws IOException {

        ogr.RegisterAll();
        int cnt = ogr.GetDriverCount();
        for(int i=0; i < cnt; ++i) {
            String name = ogr.GetDriver(i).GetName();
            System.out.println(name);
        }
        System.out.println();
    }

    /*
    public void testOpenFile() throws IOException {

        final File file = new File(geoTiffFile);
        final ImageReader reader = new GeoTiffImageReaderSpi().createReaderInstance();
        reader.setInput(file);
        final RenderedImage image = reader.read(0);
    } */


}