package org.esa.nest.gpf;

import junit.framework.TestCase;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.datamodel.*;
import org.esa.nest.datamodel.AbstractMetadata;

import java.util.Arrays;

import com.bc.ceres.core.ProgressMonitor;

/**
 * Unit test for MultilookOperator.
 */
public class TestMultilookOperator extends TestCase {

    private OperatorSpi spi;

    @Override
    protected void setUp() throws Exception {
        spi = new MultilookOp.Spi();
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(spi);
    }

    @Override
    protected void tearDown() throws Exception {
        GPF.getDefaultInstance().getOperatorSpiRegistry().removeOperatorSpi(spi);
    }

    /**
     * Tests multi-look operator with a 4x16 "DETECTED" test product.
     * @throws Exception general exception
     */
    public void testMultilookOfRealImage() throws Exception {

        final Product sourceProduct = createTestProduct(16, 4);

        final MultilookOp op = (MultilookOp)spi.createOperator();
        assertNotNull(op);
        op.setSourceProduct(sourceProduct);
        op.setNumRangeLooks(4);

        // get targetProduct: execute initialize()
        final Product targetProduct = op.getTargetProduct();
        TestOperator.verifyProduct(targetProduct);

        final Band band = targetProduct.getBandAt(0);
        assertNotNull(band);

        // readPixels: execute computeTiles()
        final float[] floatValues = new float[8];
        band.readPixels(0, 0, 4, 2, floatValues, ProgressMonitor.NULL);

        // compare with expected outputs:
        final float[] expectedValues = {11.0f, 15.0f, 19.0f, 23.0f, 43.0f, 47.0f, 51.0f, 55.0f};
        assertTrue(Arrays.equals(expectedValues, floatValues));

        // compare updated metadata
        final MetadataElement abs = targetProduct.getMetadataRoot().getElement(Product.ABSTRACTED_METADATA_ROOT_NAME);

        TestOperator.attributeEquals(abs, AbstractMetadata.azimuth_looks, 2.0);
        TestOperator.attributeEquals(abs, AbstractMetadata.range_looks, 4.0);
        TestOperator.attributeEquals(abs, AbstractMetadata.azimuth_spacing, 4.0);
        TestOperator.attributeEquals(abs, AbstractMetadata.range_spacing, 2.0);
        TestOperator.attributeEquals(abs, AbstractMetadata.line_time_interval, 0.02);
        TestOperator.attributeEquals(abs, AbstractMetadata.first_line_time, "10-MAY-2008 20:32:46.890683");
    }


    /**
     * Creates a 4-by-16 test product as shown below:
     *  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16
     * 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32
     * 33 34 35 36 37 38 39 40 41 42 43 44 45 46 47 48
     * 49 50 51 52 53 54 55 56 57 58 59 60 61 62 63 64
     * @param w width
     * @param h height
     * @return the created product
     */
    private static Product createTestProduct(final int w, final int h) {

        final Product testProduct = TestOperator.createProduct("ASA_APG_1P", w, h);

        // create a Band: band1
        final Band band1 = testProduct.addBand("band1", ProductData.TYPE_INT32);
        band1.setUnit("amplitude");
        band1.setSynthetic(true);
        final int[] intValues = new int[w * h];
        for (int i = 0; i < w * h; i++) {
            intValues[i] = i + 1;
        }
        band1.setData(ProductData.createInstance(intValues));

        // create abstracted metadata
        final MetadataElement abs = testProduct.getMetadataRoot().getElement(Product.ABSTRACTED_METADATA_ROOT_NAME);

        AbstractMetadata.setAttribute(abs, AbstractMetadata.SAMPLE_TYPE, "DETECTED");
        AbstractMetadata.setAttribute(abs, AbstractMetadata.MISSION, "ENVISAT");
        AbstractMetadata.setAttribute(abs, AbstractMetadata.srgr_flag, 0);
        AbstractMetadata.setAttribute(abs, AbstractMetadata.range_spacing, 0.5F);
        AbstractMetadata.setAttribute(abs, AbstractMetadata.azimuth_spacing, 2.0F);
        AbstractMetadata.setAttribute(abs, AbstractMetadata.azimuth_looks, 1);
        AbstractMetadata.setAttribute(abs, AbstractMetadata.range_looks, 1);
        AbstractMetadata.setAttribute(abs, AbstractMetadata.line_time_interval, 0.01F);
        AbstractMetadata.setAttribute(abs, AbstractMetadata.first_line_time,
                AbstractMetadata.parseUTC("10-MAY-2008 20:32:46.885684"));

        final float[] incidence_angle = new float[64];
        Arrays.fill(incidence_angle, 30.0f);
        testProduct.addTiePointGrid(new TiePointGrid("incident_angle", 16, 4, 0, 0, 1, 1, incidence_angle));

        return testProduct;
    }
}
