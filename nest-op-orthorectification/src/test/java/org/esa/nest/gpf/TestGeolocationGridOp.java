package org.esa.nest.gpf;

import junit.framework.TestCase;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.nest.util.TestUtils;

import java.io.File;

/**
 * Unit test for Geolocation Grid.
 */
public class TestGeolocationGridOp extends TestCase {

    private OperatorSpi spi;
    private final static String inputPathWSM =     TestUtils.rootPathExpectedProducts+"\\input\\ASA_WSM_1PNPDE20080119_093446_000000852065_00165_30780_2977.N1";
    private final static String expectedPathWSM =  TestUtils.rootPathExpectedProducts+"\\expected\\ENVISAT-ASA_WSM_1PNPDE20080119_093446_000000852065_00165_30780_2977.N1_EC.dim";

    @Override
    protected void setUp() throws Exception {
        spi = new GeolocationGridGeocodingOp.Spi();
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

        final File inputFile = new File(inputPathWSM);
        if(!inputFile.exists()) return;

        final ProductReader reader = ProductIO.getProductReaderForFile(inputFile);
        final Product sourceProduct = reader.readProductNodes(inputFile, null);

        final GeolocationGridGeocodingOp op = (GeolocationGridGeocodingOp)spi.createOperator();
        assertNotNull(op);
        op.setSourceProduct(sourceProduct);

        final String[] excemptionList = { "total_size" };
        // get targetProduct: execute initialize()
        final Product targetProduct = op.getTargetProduct();
        TestUtils.verifyProduct(targetProduct, false, false);
        TestUtils.compareProducts(op, targetProduct, expectedPathWSM, excemptionList);
    }

    /**
     * Processes all products in a folder
     * @throws Exception general exception
     */
    public void testProcessAllASAR() throws Exception
    {
        final File folder = new File(TestUtils.asarLazioPath);
        if(!folder.exists()) return;

        if(TestUtils.canTestProcessingOnAllProducts())
            recurseFolder(folder);
    }

    /**
     * Processes all products in a folder
     * @throws Exception general exception
     */
    public void testProcessAllERS() throws Exception
    {
        final File folder = new File(TestUtils.ersLazioPath);
        if(!folder.exists()) return;

        if(TestUtils.canTestProcessingOnAllProducts())
            recurseFolder(folder);
    }

    private void recurseFolder(File folder) throws Exception {
        for(File file : folder.listFiles()) {
            if(file.isDirectory()) {
                recurseFolder(file);
            } else {
                try {
                    final ProductReader reader = ProductIO.getProductReaderForFile(file);
                    if(reader != null) {
                        System.out.println("Processing "+ file.toString());

                        final Product sourceProduct = reader.readProductNodes(file, null);

                        final GeolocationGridGeocodingOp op = (GeolocationGridGeocodingOp)spi.createOperator();
                        assertNotNull(op);
                        op.setSourceProduct(sourceProduct);

                        TestUtils.executeOperator(op);
                    }
                } catch(Exception e) {
                    if(e.getMessage().contains("already map projected")) {
                        continue;
                    } else {
                        System.out.println("Failed to process "+ file.toString());
                        throw e;
                    }
                }
            }
        }
    }
}