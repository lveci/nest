package org.esa.nest.gpf;

import junit.framework.TestCase;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.nest.util.TestUtils;

/**
 * Unit test for EllipsoidCorrectionRDOp.
 */
public class TestEllipsoidCorrectionRDOp extends TestCase {

    private OperatorSpi spi;

    private String[] productTypeExemptions = { "_BP", "XCA", "WVW", "WVI", "WVS", "WSS", "DOR_VOR_AX" };
    private String[] exceptionExemptions = { "not supported", "already map projected" };

    @Override
    protected void setUp() throws Exception {
        spi = new EllipsoidCorrectionRDOp.Spi();
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(spi);
    }

    @Override
    protected void tearDown() throws Exception {
        GPF.getDefaultInstance().getOperatorSpiRegistry().removeOperatorSpi(spi);
    }

    public void testProcessAllASAR() throws Exception
    {
        TestUtils.testProcessAllInPath(spi, TestUtils.rootPathASAR, productTypeExemptions, exceptionExemptions);
    }

    public void testProcessAllERS() throws Exception
    {
        TestUtils.testProcessAllInPath(spi, TestUtils.rootPathERS, productTypeExemptions, exceptionExemptions);
    }

    public void testProcessAllALOS() throws Exception
    {
        TestUtils.testProcessAllInPath(spi, TestUtils.rootPathALOS, null, exceptionExemptions);
    }

    public void testProcessAllRadarsat2() throws Exception
    {
        TestUtils.testProcessAllInPath(spi, TestUtils.rootPathRadarsat2, null, exceptionExemptions);
    }

    public void testProcessAllTerraSARX() throws Exception
    {
        TestUtils.testProcessAllInPath(spi, TestUtils.rootPathTerraSarX, null, exceptionExemptions);
    }

    public void testProcessAllCosmo() throws Exception
    {
        TestUtils.testProcessAllInPath(spi, TestUtils.rootPathCosmoSkymed, null, exceptionExemptions);
    }

    public void testProcessAllNestBox() throws Exception
    {
        TestUtils.testProcessAllInPath(spi, TestUtils.rootPathMixProducts, productTypeExemptions, exceptionExemptions);
    }
}