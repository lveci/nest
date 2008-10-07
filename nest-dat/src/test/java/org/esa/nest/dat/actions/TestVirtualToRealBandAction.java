package org.esa.nest.dat.actions;

import junit.framework.TestCase;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.datamodel.*;

import java.util.Arrays;

import com.bc.ceres.core.ProgressMonitor;

/**
 * Unit test for VirtualToRealBandAction.
 */
public class TestVirtualToRealBandAction extends TestCase {

    private Product targetProduct;
    private Band virtualBand;
    private String bandName = "bandName";

    @Override
    protected void setUp() throws Exception {
        targetProduct = createTestProduct(4,4);
        String expression = targetProduct.getBandAt(0).getName() + " * 2";
        virtualBand = createVirtualBand(targetProduct, bandName, expression);
        targetProduct.addBand(virtualBand);
    }

    @Override
    protected void tearDown() throws Exception {
        targetProduct = null;
        virtualBand = null;
    }

    public void testConvertVirtualToReal() throws Exception {

        Band virtband = targetProduct.getBand(bandName);
        assertNotNull(virtband);
        assertEquals(2, targetProduct.getNumBands());
        assertTrue(virtband.isSynthetic());

        // readPixels gets computeTiles to be executed
        float[] floatValues = new float[16];
        virtband.readPixels(0, 0, 4, 4, floatValues, ProgressMonitor.NULL);

        // compare with expected outputs:
        float[] expectedValues = {4,4,4,4,
                                  4,4,4,4,
                                  4,4,4,4,
                                  4,4,4,4 };
        assertTrue(Arrays.equals(expectedValues, floatValues));

        VirtualToRealBandAction.convertVirtualToRealBand(targetProduct, virtualBand, null);

        Band newBand = targetProduct.getBand(bandName);
        assertNotNull(newBand);
        assertEquals(2, targetProduct.getNumBands());
        //assertFalse(newBand.isSynthetic());

        newBand.readPixels(0, 0, 4, 4, floatValues, ProgressMonitor.NULL);

        assertTrue(Arrays.equals(expectedValues, floatValues));
    }

    private static Product createTestProduct(int w, int h) {
        Product testProduct = new Product("p", "t", w, h);
        Band band1 = testProduct.addBand("band1", ProductData.TYPE_INT32);
        //band1.setSynthetic(true);
        int[] intValues = new int[w * h];
        Arrays.fill(intValues, 2);
        band1.setData(ProductData.createInstance(intValues));

        return testProduct;
    }

    private static Band createVirtualBand(Product product, String name, String expression) {

        VirtualBand band = new VirtualBand(name,
                ProductData.TYPE_FLOAT32,
                product.getSceneRasterWidth(),
                product.getSceneRasterHeight(),
                expression);
        band.setSynthetic(true);
        band.setUnit("");
        return band;
    }
}