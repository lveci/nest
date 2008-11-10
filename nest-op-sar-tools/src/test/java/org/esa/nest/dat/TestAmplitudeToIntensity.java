package org.esa.nest.dat;

import junit.framework.TestCase;
import org.esa.beam.framework.datamodel.*;
import org.esa.nest.gpf.TestOperator;

import java.util.Arrays;

/**
 * Unit test for AmplitudeToIntensity.
 */
public class TestAmplitudeToIntensity extends TestCase {

    @Override
    protected void setUp() throws Exception {

    }

    @Override
    protected void tearDown() throws Exception {

    }

    public void testAmplitudeToIntensity() {

        Product product = createTestProduct("Amplitude", "amplitude",16, 4);
        Band band1 = product.getBandAt(0);

        AmplitudeToIntensityOpAction.convert(product, band1, false);
        assertTrue(product.getNumBands() == 2);

        Band band2 = product.getBandAt(1);
        assertTrue(band2.getUnit().contains("intensity"));
        assertTrue(band2.getName().endsWith("Intensity"));
    }

    public void testdIntensityToAmplitude() {

        Product product = createTestProduct("Intensity", "instensity", 16, 4);
        Band band1 = product.getBandAt(0);

        AmplitudeToIntensityOpAction.convert(product, band1, true);
        assertTrue(product.getNumBands() == 2);

        Band band2 = product.getBandAt(1);
        assertTrue(band2.getUnit().equals("amplitude"));
        assertTrue(band2.getName().equals("Amplitude"));     
    }

    /**
     * @param w width
     * @param h height
     * @return the created product
     */
    private static Product createTestProduct(String name, String unit, int w, int h) {

        Product testProduct = TestOperator.createProduct("ASA_APG_1P", w, h);

        // create a Band: band1
        Band band1 = testProduct.addBand(name, ProductData.TYPE_INT32);
        band1.setUnit(unit);
        band1.setSynthetic(true);
        int[] intValues = new int[w * h];
        for (int i = 0; i < w * h; i++) {
            intValues[i] = i + 1;
        }
        band1.setData(ProductData.createInstance(intValues));

        float[] incidence_angle = new float[64];
        Arrays.fill(incidence_angle, 30.0f);
        testProduct.addTiePointGrid(new TiePointGrid("incident_angle", 16, 4, 0, 0, 1, 1, incidence_angle));

        return testProduct;
    }
}