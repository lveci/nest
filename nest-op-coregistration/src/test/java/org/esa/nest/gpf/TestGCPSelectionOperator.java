package org.esa.nest.gpf;

import junit.framework.TestCase;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.GPF;
import com.bc.ceres.core.ProgressMonitor;

/**
 * Unit test for SingleTileOperator.
 */
public class TestGCPSelectionOperator extends TestCase {

    private OperatorSpi spi;

    @Override
    protected void setUp() throws Exception {
        spi = new GCPSelectionOperator.Spi();
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(spi);
    }

    @Override
    protected void tearDown() throws Exception {
        GPF.getDefaultInstance().getOperatorSpiRegistry().removeOperatorSpi(spi);
    }

    public void testOperator() throws Exception {

        Product[] products = new Product[2];
        products[0] = createTestMasterProduct(40,40);
        products[1] = createTestSlaveProduct(40,40);

        ProductNodeGroup<Pin> masterGcpGroup = products[0].getGcpGroup();
        assertTrue(masterGcpGroup.getNodeCount() == 1);

        GCPSelectionOperator op = (GCPSelectionOperator)spi.createOperator();
        assertNotNull(op);

        op.setSourceProducts(products);                                                              
        op.setTestParameters("32", "32", "2", "2", 2, 0.5);

        // get targetProduct gets initialize to be executed
        Product targetProduct = op.getTargetProduct();
        assertNotNull(targetProduct);

        Band band = targetProduct.getBandAt(0);
        assertNotNull(band);

        // readPixels gets computeTiles to be executed
        float[] floatValues = new float[1600];
        band.readPixels(0, 0, 40, 40, floatValues, ProgressMonitor.NULL);

        ProductNodeGroup<Pin> targetGcpGroup = targetProduct.getGcpGroup();
        assertTrue(targetGcpGroup.getNodeCount() == 1);

        Pin pin = targetGcpGroup.get(0);
        PixelPos pixelPos = pin.getPixelPos();
        assertTrue(Float.compare(pixelPos.x, 16.0f) == 0);
        assertTrue(Float.compare(pixelPos.y, 21.0f) == 0);
    }

    private Product createTestMasterProduct(int w, int h) {

        Product masterProduct = new Product("p", "ASA_IMP_1P", w, h);

        // create a band: sinc function centre is at (19, 19)
        Band band = masterProduct.addBand("amplitude", ProductData.TYPE_FLOAT32);
        band.setUnit("amplitude");
        band.setSynthetic(true);
        float[] floatValues = new float[w * h];
        int i;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                i = y*w + x;
                floatValues[i] = sinc((float)(x - w/2 + 1) / 4.0f)*sinc((float)(y - h/2 + 1) / 4.0f);
            }
        }
        band.setData(ProductData.createInstance(floatValues));

        // create lat/lon tie point grids
        float[] lat = new float[w*h];
        float[] lon = new float[w*h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                i = y*w + x;
                lon[i] = 13.20f;
                lat[i] = 51.60f;
            }
        }
        TiePointGrid latGrid = new TiePointGrid("latitude", w, h, 0, 0, 1, 1, lat);
        TiePointGrid lonGrid = new TiePointGrid("longitude", w, h, 0, 0, 1, 1, lon);
        masterProduct.addTiePointGrid(latGrid);
        masterProduct.addTiePointGrid(lonGrid);

        // create Geo coding
        masterProduct.setGeoCoding(new TiePointGeoCoding(latGrid, lonGrid));

        // create GCP
        ProductNodeGroup<Pin> masterGcpGroup = masterProduct.getGcpGroup();
        Pin pin1 = new Pin("gcp_1",
                           "GCP 1",
                           "",
                           new PixelPos(19.0f, 19.0f),
                           new GeoPos(lat[w*h/2], lon[w*h/2]),
                           PinSymbol.createDefaultGcpSymbol());

        masterGcpGroup.add(pin1);

        return masterProduct;
    }

    private Product createTestSlaveProduct(int w, int h) {

        Product slaveProduct = new Product("p", "ASA_IMP_1P", w, h);

        // create a band: sinc function centre is at (16, 21)
        Band band = slaveProduct.addBand("amplitude", ProductData.TYPE_FLOAT32);
        band.setUnit("amplitude");
        band.setSynthetic(true);
        float[] floatValues = new float[w * h];
        int i;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                i = y*w + x;
                floatValues[i] = sinc((float)(x - w/2 + 4) / 4.0f)*sinc((float)(y - h/2 - 1) / 4.0f);
            }
        }
        band.setData(ProductData.createInstance(floatValues));

        // create lat/lon tie point grids
        float[] lat = new float[w*h];
        float[] lon = new float[w*h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                i = y*w + x;
                lon[i] = 13.20f;// + x/10000.0f;
                lat[i] = 51.60f;// + y/10000.0f;
            }
        }
        TiePointGrid latGrid = new TiePointGrid("latitude", w, h, 0, 0, 1, 1, lat);
        TiePointGrid lonGrid = new TiePointGrid("longitude", w, h, 0, 0, 1, 1, lon);
        slaveProduct.addTiePointGrid(latGrid);
        slaveProduct.addTiePointGrid(lonGrid);

        // create Geo coding
        slaveProduct.setGeoCoding(new TiePointGeoCoding(latGrid, lonGrid));

        return slaveProduct;
    }

    private float sinc(float x) {

        if (Float.compare(x, 0.0f) == 0) {
            return 0.0f;
        } else {
            return (float)(Math.sin(x*Math.PI) / (x*Math.PI));
        }
    }
}
