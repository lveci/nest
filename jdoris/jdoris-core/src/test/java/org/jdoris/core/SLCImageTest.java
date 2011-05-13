package org.jdoris.core;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

public class SLCImageTest {

    private static File resFile;
    private static SLCImage master = new SLCImage();
    private static final double eps05 = 1E-05;
    private static final double eps02 = 1E-02;
    private static final double line_EXPECTED = 18093.22191;
    private static final double azTime_EXPECTED =  36487.9539543639;
    private static final double pixel_EXPECTED = 3614.506571;
    private static final double rgTime_EXPECTED = 0.00286444560797666;
    private static final double doppler_EXPECTED = 178.068022732768;

    @BeforeClass
    public static void setUp() throws Exception {
        resFile = new File("/d2/test_cr.res");
        master.parseResFile(resFile);
    }

    @Test
    public void testPix2tr() throws Exception {
        double rgTime_ACTUAL = master.pix2tr(pixel_EXPECTED);
        Assert.assertEquals(rgTime_EXPECTED, rgTime_ACTUAL, eps05);
    }

    @Test
    public void testTr2pix() throws Exception {
        double pixel_ACTUAL = master.tr2pix(rgTime_EXPECTED);
        Assert.assertEquals(pixel_EXPECTED,pixel_ACTUAL, eps05);

    }

    @Test
    public void testPix2fdc() throws Exception {
        double doppler_ACTUAL = master.pix2fdc(pixel_EXPECTED);
        Assert.assertEquals(doppler_EXPECTED,doppler_ACTUAL, eps05);
    }

    @Test
    public void testLine2ta() throws Exception {
        double azTime_ACTUAL = master.line2ta(line_EXPECTED);
        Assert.assertEquals(azTime_EXPECTED, azTime_ACTUAL, eps05);

    }


    @Test
    public void testTa2line() throws Exception {
        double line_ACTUAL = master.ta2line(azTime_EXPECTED);
        Assert.assertEquals(line_EXPECTED, line_ACTUAL, eps02);

    }

}
