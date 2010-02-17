package org.esa.nest.gpf;

import com.bc.ceres.core.ProgressMonitor;
import junit.framework.TestCase;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.dataio.ReaderUtils;

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

        final Product mstProduct = createTestProduct(40,40, 30, 10, 10, 20);
        final Product slvProduct1 = createTestProduct(40,40, 35, 15, 15, 25);

        //ProductIO.writeProduct(mstProduct, "c:\\data\\out\\mstProduct", "BEAM-DIMAP");
        //ProductIO.writeProduct(slvProduct1, "c:\\data\\out\\slvProduct1", "BEAM-DIMAP");

        op.setSourceProducts(new Product[] {mstProduct, slvProduct1});
        op.setTestParameters(CreateStackOp.MIN_EXTENT);

        // get targetProduct gets initialize to be executed
        final Product targetProduct = op.getTargetProduct();
        assertNotNull(targetProduct);

        final Band band = targetProduct.getBandAt(0);
        assertNotNull(band);

        // readPixels gets computeTiles to be executed
        float[] floatValues = new float[1600];
        band.readPixels(0, 0, 40, 40, floatValues, ProgressMonitor.NULL);

        //ProductIO.writeProduct(targetProduct, "c:\\data\\out\\targetProduct", "BEAM-DIMAP");
    }

    private static Product createTestProduct(final int w, final int h,
                                             final float latTop, final float lonLeft,
                                             final float latBottom, final float lonRight) {

        final Product product = new Product("p", "ASA_IMP_1P", w, h);

        final Band band = product.addBand("amplitude", ProductData.TYPE_FLOAT32);
        band.setUnit(Unit.AMPLITUDE);
        band.setSynthetic(true);
        float[] floatValues = new float[w * h];
        int i;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                i = y*w + x;
                floatValues[i] = 0;
            }
        }
        band.setData(ProductData.createInstance(floatValues));

        final float[] latCorners = new float[]{latTop, latTop, latBottom, latBottom};
        final float[] lonCorners = new float[]{lonLeft, lonRight, lonLeft, lonRight};

        ReaderUtils.addGeoCoding(product, latCorners, lonCorners);

        return product;
    }

}