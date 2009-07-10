package org.esa.nest.gpf.filtering;

import com.bc.ceres.core.ProgressMonitor;
import junit.framework.TestCase;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.util.TestUtils;

import java.io.File;
import java.util.Arrays;

/**
 * Unit test for SpeckleFilterOperator.
 */
public class SpeckleFilterOperatorTest extends TestCase {

    private OperatorSpi spi;
    private final static String inputPathWSM =     "P:\\nest\\nest\\test\\input\\ASA_WSM_1PNPDE20080119_093446_000000852065_00165_30780_2977.N1";
    private final static String expectedPathWSM =  "P:\\nest\\nest\\test\\expected\\ENVISAT-ASA_WSM_1PNPDE20080119_093446_000000852065_00165_30780_2977.N1_Spk.dim";


    @Override
    protected void setUp() throws Exception {
        spi = new SpeckleFilterOp.Spi();
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(spi);
    }

    @Override
    protected void tearDown() throws Exception {
        GPF.getDefaultInstance().getOperatorSpiRegistry().removeOperatorSpi(spi);
    }

    /**
     * Tests Mean speckle filter with a 4-by-4 test product.
     * @throws Exception The exception.
     */
    public void testMeanFilter() throws Exception {
        final Product sourceProduct = createTestProduct(4, 4);

        final SpeckleFilterOp op = (SpeckleFilterOp)spi.createOperator();
        assertNotNull(op);
        op.setSourceProduct(sourceProduct);
        op.SetFilter("Mean");

        // get targetProduct gets initialize to be executed
        final Product targetProduct = op.getTargetProduct();
        TestUtils.verifyProduct(targetProduct, true, true);

        final Band band = targetProduct.getBandAt(0);
        assertNotNull(band);

        // readPixels gets computeTiles to be executed
        final float[] floatValues = new float[16];
        band.readPixels(0, 0, 4, 4, floatValues, ProgressMonitor.NULL);

        // compare with expected outputs:
        final float[] expectedValues = {2.6666667f, 3.3333333f, 4.3333335f, 5.0f, 5.3333335f, 6.0f, 7.0f, 7.6666667f,
                                  9.333333f, 10.0f, 11.0f, 11.666667f, 12.0f, 12.666667f, 13.666667f, 14.333333f};
        assertTrue(Arrays.equals(expectedValues, floatValues));
    }

    /**
     * Tests Median speckle filter with a 4-by-4 test product.
     * @throws Exception anything
     */
    public void testMedianFilter() throws Exception {
        final Product sourceProduct = createTestProduct(4, 4);

        final SpeckleFilterOp op = (SpeckleFilterOp)spi.createOperator();
        assertNotNull(op);
        op.setSourceProduct(sourceProduct);
        op.SetFilter("Median");

        // get targetProduct gets initialize to be executed
        final Product targetProduct = op.getTargetProduct();
        TestUtils.verifyProduct(targetProduct, true, true);

        final Band band = targetProduct.getBandAt(0);
        assertNotNull(band);

        // readPixels gets computeTiles to be executed
        final float[] floatValues = new float[16];
        band.readPixels(0, 0, 4, 4, floatValues, ProgressMonitor.NULL);

        // compare with expected outputs
        final float[] expectedValues = {2.0f, 3.0f, 4.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f,
                                  9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 13.0f, 14.0f, 15.0f};
        assertTrue(Arrays.equals(expectedValues, floatValues));
    }

    /**
     * Tests Frost speckle filter with a 4-by-4 test product.
     * @throws Exception anything
     */
    public void testFrostFilter() throws Exception {
        final Product sourceProduct = createTestProduct(4, 4);

        final SpeckleFilterOp op = (SpeckleFilterOp)spi.createOperator();
        assertNotNull(op);
        op.setSourceProduct(sourceProduct);
        op.SetFilter("Frost");

        // get targetProduct gets initialize to be executed
        final Product targetProduct = op.getTargetProduct();
        TestUtils.verifyProduct(targetProduct, true, true);

        final Band band = targetProduct.getBandAt(0);
        assertNotNull(band);

        // readPixels gets computeTiles to be executed
        final float[] floatValues = new float[16];
        band.readPixels(0, 0, 4, 4, floatValues, ProgressMonitor.NULL);

        // compare with expected outputs
        final float[] expectedValues = {2.3268945f, 3.1592662f, 4.2424283f, 4.956943f, 5.289399f, 6.0f, 7.0f, 7.684779f,
                                  9.321723f, 10.0f, 11.0f, 11.673815f, 12.006711f, 12.675643f, 13.674353f, 14.34112f};
        assertTrue(Arrays.equals(expectedValues, floatValues));
    }

    /**
     * Tests Gamma speckle filter with a 4-by-4 test product.
     * @throws Exception anything
     */
    public void testGammaFilter() throws Exception {
        final Product sourceProduct = createTestProduct(4, 4);

        final SpeckleFilterOp op = (SpeckleFilterOp)spi.createOperator();
        assertNotNull(op);
        op.setSourceProduct(sourceProduct);
        op.SetFilter("Gamma Map");

        // get targetProduct gets initialize to be executed
        final Product targetProduct = op.getTargetProduct();
        TestUtils.verifyProduct(targetProduct, true, true);

        final Band band = targetProduct.getBandAt(0);
        assertNotNull(band);

        // readPixels gets computeTiles to be executed
        final float[] floatValues = new float[16];
        band.readPixels(0, 0, 4, 4, floatValues, ProgressMonitor.NULL);

        // compare with expected outputs
        final float[] expectedValues = {2.6666667f, 3.3333333f, 4.3333335f, 5.0f, 5.3333335f, 6.0f, 7.0f, 7.6666665f,
                                  9.333333f, 10.0f, 11.0f, 11.666667f, 12.0f, 12.666667f, 13.666667f, 14.333333f};
        assertTrue(Arrays.equals(expectedValues, floatValues));
    }

    /**
     * Tests Lee speckle filter with a 4-by-4 test product.
     * @throws Exception anything
     */
    public void testLeeFilter() throws Exception {
        final Product sourceProduct = createTestProduct(4, 4);

        final SpeckleFilterOp op = (SpeckleFilterOp)spi.createOperator();
        assertNotNull(op);
        op.setSourceProduct(sourceProduct);
        op.SetFilter("Lee");

        // get targetProduct gets initialize to be executed
        final Product targetProduct = op.getTargetProduct();
        TestUtils.verifyProduct(targetProduct, true, true);

        final Band band = targetProduct.getBandAt(0);
        assertNotNull(band);

        // readPixels gets computeTiles to be executed
        final float[] floatValues = new float[16];
        band.readPixels(0, 0, 4, 4, floatValues, ProgressMonitor.NULL);

        // compare with expected outputs
        final float[] expectedValues = {2.6666667f, 3.3333333f, 4.3333335f, 5.0f, 5.3333335f, 6.0f, 7.0f, 7.6666665f,
                                  9.333333f, 10.0f, 11.0f, 11.666667f, 12.0f, 12.666667f, 13.666667f, 14.333333f};

        assertTrue(Arrays.equals(expectedValues, floatValues));
    }

    /**
     * Creates a 4-by-4 test product as shown below for speckle filter tests:
     *  1  2  3  4
     *  5  6  7  8
     *  9 10 11 12
     * 13 14 15 16
     * @param w width
     * @param h height
     * @return the new test product
     */
    private static Product createTestProduct(int w, int h) {
        final Product testProduct = TestUtils.createProduct("type", w, h);
        final Band band1 = testProduct.addBand("band1", ProductData.TYPE_INT32);
        band1.setSynthetic(true);
        final int[] intValues = new int[w * h];
        for (int i = 0; i < w * h; i++) {
            intValues[i] = i + 1;
        }
        band1.setData(ProductData.createInstance(intValues));
        band1.setUnit(Unit.AMPLITUDE);
        return testProduct;
    }

    /**
     * Processes a product and compares it to processed product known to be correct
     * @throws Exception general exception
     */
    public void testProcessing() throws Exception {

        final File inputFile = new File(inputPathWSM);
        if(!inputFile.exists()) return;

        final ProductReader reader = ProductIO.getProductReaderForFile(inputFile);
        final Product sourceProduct = reader.readProductNodes(inputFile, null);

        final SpeckleFilterOp op = (SpeckleFilterOp)spi.createOperator();
        assertNotNull(op);
        op.setSourceProduct(sourceProduct);

        // get targetProduct: execute initialize()
        final Product targetProduct = op.getTargetProduct();
        TestUtils.verifyProduct(targetProduct, false, false);
        TestUtils.compareProducts(op, targetProduct, expectedPathWSM, null);
    }
}
