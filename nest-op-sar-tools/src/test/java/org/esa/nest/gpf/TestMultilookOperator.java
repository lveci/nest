package org.esa.nest.gpf;

import junit.framework.TestCase;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
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
     */
    public void testMultilookOfRealImage() throws Exception {

        Product sourceProduct = createTestProduct(16, 4);

        MultilookOp op = (MultilookOp)spi.createOperator();
        assertNotNull(op);
        op.setSourceProduct(sourceProduct);

        // get targetProduct: execute initialize()
        Product targetProduct = op.getTargetProduct();
        assertNotNull(targetProduct);

        Band band = targetProduct.getBandAt(0);
        assertNotNull(band);

        // readPixels: execute computeTiles()
        float[] floatValues = new float[8];
        band.readPixels(0, 0, 4, 2, floatValues, ProgressMonitor.NULL);

        // compare with expected outputs:
        float[] expectedValues = {175.5f, 275.5f, 407.5f, 571.5f, 1871.5f, 2227.5f, 2615.5f, 3035.5f};
        assertTrue(Arrays.equals(expectedValues, floatValues));

        // compare updated metadata
        MetadataElement abs = targetProduct.getMetadataRoot().getElement("Abstracted Metadata");

        MetadataAttribute azimuthLooksAttr = abs.getAttribute(AbstractMetadata.azimuth_looks);
        float azimuth_looks = azimuthLooksAttr.getData().getElemFloat();
        assertTrue(Float.compare(azimuth_looks, 2.0f) == 0);

        MetadataAttribute rangeLooksAttr = abs.getAttribute(AbstractMetadata.range_looks);
        float range_looks = rangeLooksAttr.getData().getElemFloat();
        assertTrue(Float.compare(range_looks, 4.0f) == 0);

        MetadataAttribute azimuthSpacingAttr = abs.getAttribute(AbstractMetadata.azimuth_spacing);
        float azimuth_spacing = azimuthSpacingAttr.getData().getElemFloat();
        assertTrue(Float.compare(azimuth_spacing, 4.0f) == 0);

        MetadataAttribute rangeSpacingAttr = abs.getAttribute(AbstractMetadata.range_spacing);
        float range_spacing = rangeSpacingAttr.getData().getElemFloat();
        assertTrue(Float.compare(range_spacing, 2.0f) == 0);

        MetadataAttribute lineTimeIntervalAttr = abs.getAttribute(AbstractMetadata.line_time_interval);
        float lineTimeInterval = lineTimeIntervalAttr.getData().getElemFloat();
        assertTrue(Float.compare(lineTimeInterval, 0.02f) == 0);

        MetadataAttribute firstLineTimeAttr = abs.getAttribute(AbstractMetadata.first_line_time);
        String firstLineTime = firstLineTimeAttr.getData().getElemString();
        assertTrue(firstLineTime.equals("10-MAY-2008 20:32:46.890683"));
    }


    /**
     * Creates a 4-by-16 test product as shown below:
     *  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16
     * 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32
     * 33 34 35 36 37 38 39 40 41 42 43 44 45 46 47 48
     * 49 50 51 52 53 54 55 56 57 58 59 60 61 62 63 64
     */
    private Product createTestProduct(int w, int h) {

        Product testProduct = new Product("p", "ASA_APG_1P", w, h);

        // create a Band: band1
        Band band1 = testProduct.addBand("band1", ProductData.TYPE_INT32);
        band1.setUnit("amplitude");
        band1.setSynthetic(true);
        int[] intValues = new int[w * h];
        for (int i = 0; i < w * h; i++) {
            intValues[i] = i + 1;
        }
        band1.setData(ProductData.createInstance(intValues));

        // create abstracted metadata
        MetadataElement abs = new MetadataElement("Abstracted Metadata");

        abs.addAttribute(new MetadataAttribute(AbstractMetadata.SAMPLE_TYPE,
                ProductData.createInstance("DETECTED"), false));

        abs.addAttribute(new MetadataAttribute(AbstractMetadata.MISSION,
                ProductData.createInstance("ENVISAT"), false));

        abs.addAttribute(new MetadataAttribute(AbstractMetadata.srgr_flag,
                ProductData.createInstance(new byte[] {0}), false));

        abs.addAttribute(new MetadataAttribute(AbstractMetadata.range_spacing,
                ProductData.createInstance(new float[] {0.5F}), false));

        abs.addAttribute(new MetadataAttribute(AbstractMetadata.azimuth_spacing,
                ProductData.createInstance(new float[] {2.0F}), false));

        abs.addAttribute(new MetadataAttribute(AbstractMetadata.azimuth_looks,
                ProductData.createInstance(new int[] {1}), false));

        abs.addAttribute(new MetadataAttribute(AbstractMetadata.range_looks,
                ProductData.createInstance(new int[] {1}), false));

        abs.addAttribute(new MetadataAttribute(AbstractMetadata.line_time_interval,
                ProductData.createInstance(new float[] {0.01F}), false));

        abs.addAttribute(new MetadataAttribute(AbstractMetadata.first_line_time,
                ProductData.createInstance("10-MAY-2008 20:32:46.885684"), false));

        float[] incidence_angle = new float[64];
        Arrays.fill(incidence_angle, 30.0f);
        testProduct.addTiePointGrid(new TiePointGrid("incident_angle", 16, 4, 0, 0, 1, 1, incidence_angle));

        testProduct.getMetadataRoot().addElement(abs);

        return testProduct;
    }
}
