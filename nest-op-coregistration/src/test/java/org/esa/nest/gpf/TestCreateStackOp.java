package org.esa.nest.gpf;

import com.bc.ceres.core.ProgressMonitor;
import junit.framework.TestCase;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.nest.datamodel.Unit;

/**
 * Unit test for CreateStackOp.
 */
public class TestCreateStackOp extends TestCase {

    private OperatorSpi spi;

    @Override
    protected void setUp() throws Exception {
        spi = new CreateStackOp.Spi();
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(spi);
    }

    @Override
    protected void tearDown() throws Exception {
        GPF.getDefaultInstance().getOperatorSpiRegistry().removeOperatorSpi(spi);
    }

    public void testOperator() throws Exception {

        final CreateStackOp op = (CreateStackOp)spi.createOperator();
        assertNotNull(op);

        final Product mstProduct = createTestProduct(40,40);
        final Product slvProduct1 = createTestProduct(40,40);

        op.setSourceProducts(new Product[] {mstProduct, slvProduct1});
        //op.setTestParameters("32", "32", "2", "2", 2, 0.5);

        // get targetProduct gets initialize to be executed
        final Product targetProduct = op.getTargetProduct();
        assertNotNull(targetProduct);

        final Band band = targetProduct.getBandAt(0);
        assertNotNull(band);

        // readPixels gets computeTiles to be executed
        float[] floatValues = new float[1600];
        band.readPixels(0, 0, 40, 40, floatValues, ProgressMonitor.NULL);
    }

    private static Product createTestProduct(int w, int h) {

        final Product product = new Product("p", "ASA_IMP_1P", w, h);

        final Band band = product.addBand("amplitude", ProductData.TYPE_FLOAT32);
        band.setUnit(Unit.AMPLITUDE);
        band.setSynthetic(true);

        // create lat/lon tie point grids
        final float[] lat = new float[w*h];
        final float[] lon = new float[w*h];
        int i;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                i = y*w + x;
                lon[i] = 13.20f;
                lat[i] = 51.60f;
            }
        }
        final TiePointGrid latGrid = new TiePointGrid("latitude", w, h, 0, 0, 1, 1, lat);
        final TiePointGrid lonGrid = new TiePointGrid("longitude", w, h, 0, 0, 1, 1, lon);
        product.addTiePointGrid(latGrid);
        product.addTiePointGrid(lonGrid);

        // create Geo coding
        product.setGeoCoding(new TiePointGeoCoding(latGrid, lonGrid));

        return product;
    }

}