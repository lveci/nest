package org.esa.nest.gpf;

import com.bc.ceres.core.ProgressMonitor;
import junit.framework.TestCase;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.util.TestUtils;

import java.io.File;
import java.util.Arrays;

/**
 * Unit test for Range Doppler.
 */
public class TestRangeDopplerOp extends TestCase {

    private OperatorSpi spi;
    private final static String inputPath =    "P:\\nest\\nest\\test\\input\\ASA_WSM_1PNPDE20080119_093446_000000852065_00165_30780_2977.N1";
    private final static String expectedPath = "P:\\nest\\nest\\test\\expected\\ENVISAT-ASA_WSM_1PNPDE20080119_093446_000000852065_00165_30780_2977.N1_TC.dim";

    @Override
    protected void setUp() throws Exception {
        spi = new RangeDopplerGeocodingOp.Spi();
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(spi);
    }

    @Override
    protected void tearDown() throws Exception {
        GPF.getDefaultInstance().getOperatorSpiRegistry().removeOperatorSpi(spi);
    }

    /**
     * Processes a product and compares it to processed product known to be correct
     * @throws Exception general exception
     */
    public void testProcessing() throws Exception {

        final File inputFile = new File(inputPath);
        if(!inputFile.exists()) return;

        final ProductReader reader = ProductIO.getProductReaderForFile(inputFile);
        final Product sourceProduct = reader.readProductNodes(inputFile, null);

        final RangeDopplerGeocodingOp op = (RangeDopplerGeocodingOp)spi.createOperator();
        assertNotNull(op);
        op.setSourceProduct(sourceProduct);

        // get targetProduct: execute initialize()
        final Product targetProduct = op.getTargetProduct();
        //TestUtils.verifyProduct(targetProduct);

        final Band targetBand = targetProduct.getBandAt(0);
        assertNotNull(targetBand);

        // readPixels: execute computeTiles()
        final float[] floatValues = new float[10000];
        targetBand.readPixels(100, 100, 100, 100, floatValues, ProgressMonitor.NULL);

        // compare with expected outputs:
        final File expectedFile = new File(expectedPath);
        if(!expectedFile.exists()) return;

        final ProductReader reader2 = ProductIO.getProductReaderForFile(expectedFile);

        final Product expectedProduct = reader2.readProductNodes(expectedFile, null);
        final Band expectedBand = expectedProduct.getBandAt(0);

        final float[] expectedValues = new float[10000];
        expectedBand.readPixels(100, 100, 100, 100, floatValues, ProgressMonitor.NULL);
        assertTrue(Arrays.equals(floatValues, expectedValues));

        // compare updated metadata
        //TestUtils.compareMetadata(targetProduct, expectedProduct);
    }
}
