package org.jdoris.core;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

public class BaselineTest {

    private static final File masterResFile = new File("/d2/test_cr.res");
    private static final File slaveResFile = new File("/d2/test_cr_slave.res");

    private static final SLCImage master = new SLCImage();
    private static final SLCImage slave = new SLCImage();
    private static Orbit masterOrbit;
    private static Orbit slaveOrbit;

    private static Baseline baseline;

    private static final int polyOrbitDegree = 4;


    public static final double line_1 = 11111;
    public static final double line_2 = 22222;
    public static final double pix_1 = 111;
    public static final double pix_2 = 2222;


    // default values
    private static final double rangeToPixel_1_EXPECTED = 830539.479;
    private static final double rangeToPixel_5175_EXPECTED = 870917.239;

    private static final Point point_1_EXPECTED = new Point(111, 11111);
    private static final Point point_2_EXPECTED = new Point(2222, 22222);

    private static final double b_1_EXPECTED = 221.8803693227291;
    private static final double b_2_EXPECTED = 217.7050912335464;

    private static final double bHor_1_EXPECTED = 221.7451841412342;
    private static final double bHor_2_EXPECTED = 217.567170499463;

    private static final double bPar_1_EXPECTED = 71.00136421451475;
    private static final double bPar_2_EXPECTED = 80.48010310092947;

    private static final double bPerp_1_EXPECTED = 210.2134738080994;
    private static final double bPerp_2_EXPECTED = 202.2831178172576;

    private static final double bVert_1_EXPECTED = -7.744133325351279;
    private static final double bVert_2_EXPECTED = -7.748101048925052;

    private static final double alpha_1_EXPECTED = -0.03490938152809497; //[rad]
    private static final double alpha_2_EXPECTED = -0.03559741122500076; //[rad]

    private static final double hAmb_1_EXPECTED = -35.85469444147813;
    private static final double hAmb_2_EXPECTED = -44.57683627752631;

    // deltas
    private static final double eps_01 = 2E-01;
    private static final double eps_03 = 1E-03;
    private static final double eps_04 = 1E-04;
    private static final double eps_06 = 1E-06;


    private static Logger initLog() {
        String filePathToLog4JProperties = "/d3/checkouts/jdoris/log4j.properties";
        Logger logger = Logger.getLogger(Baseline.class);
        PropertyConfigurator.configure(filePathToLog4JProperties);
        return logger;
    }


    @BeforeClass
    public static void setUp() throws Exception {

        initLog();

        master.parseResFile(masterResFile);
        slave.parseResFile(slaveResFile);

        masterOrbit = new Orbit();
        masterOrbit.parseOrbit(masterResFile);
        masterOrbit.computeCoefficients(polyOrbitDegree);

        slaveOrbit = new Orbit();
        slaveOrbit.parseOrbit(slaveResFile);
        slaveOrbit.computeCoefficients(polyOrbitDegree);


        baseline = new Baseline();
        baseline.model(master, slave, masterOrbit, slaveOrbit);

    }

    @Test
    public void testGetRange() throws Exception {

        Assert.assertEquals(rangeToPixel_1_EXPECTED, baseline.getRange(1), eps_03);
        Assert.assertEquals(rangeToPixel_5175_EXPECTED, baseline.getRange(5175), eps_03);

    }


    @Test
    public void testGetB() throws Exception {
        Assert.assertEquals(b_1_EXPECTED, baseline.getB(line_1,pix_1,0),eps_06);
        Assert.assertEquals(b_2_EXPECTED, baseline.getB(line_2,pix_2,0),eps_06);
    }

    @Test
    public void testGetBhor() throws Exception {
        Assert.assertEquals(bHor_1_EXPECTED, baseline.getBhor(line_1, pix_1, 0),eps_06);
        Assert.assertEquals(bHor_2_EXPECTED, baseline.getBhor(line_2, pix_2, 0),eps_06);
    }

    @Test
    public void testGetBpar() throws Exception {
        Assert.assertEquals(bPar_1_EXPECTED, baseline.getBpar(line_1, pix_1, 0),eps_06);
        Assert.assertEquals(bPar_2_EXPECTED, baseline.getBpar(line_2, pix_2, 0),eps_06);
    }


    @Test
    public void testGetBperp() throws Exception {
        Assert.assertEquals(bPerp_1_EXPECTED, baseline.getBperp(line_1,pix_1),eps_06);
        Assert.assertEquals(bPerp_2_EXPECTED, baseline.getBperp(line_2,pix_2),eps_06);
    }

    @Test
    public void testGetBperpPoint() throws Exception {
        Assert.assertEquals(bPerp_1_EXPECTED, baseline.getBperp(new Point(pix_1,line_1,0)),eps_06);
        Assert.assertEquals(bPerp_2_EXPECTED, baseline.getBperp(new Point(pix_2,line_2,0)),eps_06);
    }

    @Test
    public void testGetBvert() throws Exception {
        Assert.assertEquals(bVert_1_EXPECTED, baseline.getBvert(line_1,pix_1,0),eps_06);
        Assert.assertEquals(bVert_2_EXPECTED, baseline.getBvert(line_2,pix_2,0),eps_06);
    }

    @Test
    public void testGetAlpha() throws Exception {
        Assert.assertEquals(alpha_1_EXPECTED, baseline.getAlpha(line_1,pix_1,0),eps_06);
        Assert.assertEquals(alpha_2_EXPECTED, baseline.getAlpha(line_2,pix_2,0),eps_06);
    }

    @Test
    public void testGetHamb() throws Exception {
        Assert.assertEquals(hAmb_1_EXPECTED, baseline.getHamb(line_1,pix_1,0),eps_06);
        Assert.assertEquals(hAmb_2_EXPECTED, baseline.getHamb(line_2,pix_2,0),eps_06);
    }


}
