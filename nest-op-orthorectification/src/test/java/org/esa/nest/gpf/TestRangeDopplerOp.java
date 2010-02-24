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
 * Unit test for Range Doppler.
 */
public class TestRangeDopplerOp extends TestCase {

    private OperatorSpi spi;
    private final static String inputPathWSM =     TestUtils.rootPathExpectedProducts+"\\input\\subset_1_of_ENVISAT-ASA_WSM_1PNPDE20080119_093446_000000852065_00165_30780_2977.dim";
    private final static String expectedPathWSM =  TestUtils.rootPathExpectedProducts+"\\expected\\subset_1_of_ENVISAT-ASA_WSM_1PNPDE20080119_093446_000000852065_00165_30780_2977_TC.dim";

    private final static String inputPathIMS =     TestUtils.rootPathExpectedProducts+"\\input\\ENVISAT-ASA_IMS_1PNDPA20050405_211952_000000162036_00115_16201_8523.dim";
    private final static String expectedPathIMS =  TestUtils.rootPathExpectedProducts+"\\expected\\ENVISAT-ASA_IMS_1PNDPA20050405_211952_000000162036_00115_16201_8523_TC.dim";

    private final static String inputPathAPM =     TestUtils.rootPathExpectedProducts+"\\input\\ASA_APM_1PNIPA20030327_091853_000000152015_00036_05601_5422.N1";
    private final static String expectedPathAPM =  TestUtils.rootPathExpectedProducts+"\\expected\\ENVISAT-ASA_APM_1PNIPA20030327_091853_000000152015_00036_05601_5422.N1_TC.dim";

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
     * Processes a WSM product and compares it to processed product known to be correct
     * @throws Exception general exception
     */
    public void testProcessWSM() throws Exception {

        final File inputFile = new File(inputPathWSM);
        if(!inputFile.exists()) return;

        final ProductReader reader = ProductIO.getProductReaderForFile(inputFile);
        final Product sourceProduct = reader.readProductNodes(inputFile, null);

        final RangeDopplerGeocodingOp op = (RangeDopplerGeocodingOp)spi.createOperator();
        assertNotNull(op);
        op.setSourceProduct(sourceProduct);
        op.setApplyRadiometricCalibration(true);
        String[] bandNames = {"Amplitude"};
        op.setSourceBandNames(bandNames);
        
        // get targetProduct: execute initialize()
        final Product targetProduct = op.getTargetProduct();
        TestUtils.verifyProduct(targetProduct, false, false);
        TestUtils.compareProducts(op, targetProduct, expectedPathWSM, null);
    }

    /**
     * Processes a IMS product and compares it to processed product known to be correct
     * @throws Exception general exception
     */
    public void testProcessIMS() throws Exception {

        final File inputFile = new File(inputPathIMS);
        if(!inputFile.exists()) return;

        final ProductReader reader = ProductIO.getProductReaderForFile(inputFile);
        final Product sourceProduct = reader.readProductNodes(inputFile, null);

        final RangeDopplerGeocodingOp op = (RangeDopplerGeocodingOp)spi.createOperator();
        assertNotNull(op);
        op.setSourceProduct(sourceProduct);
        op.setApplyRadiometricCalibration(true);
        String[] bandNames = {"i", "q"};
        op.setSourceBandNames(bandNames);
        
        // get targetProduct: execute initialize()
        final Product targetProduct = op.getTargetProduct();
        TestUtils.verifyProduct(targetProduct, false, false);
        TestUtils.compareProducts(op, targetProduct, expectedPathIMS, null);
    }

    /**
     * Processes a APM product and compares it to processed product known to be correct
     * @throws Exception general exception
     */
    public void testProcessAPM() throws Exception {

        final File inputFile = new File(inputPathAPM);
        if(!inputFile.exists()) return;

        final ProductReader reader = ProductIO.getProductReaderForFile(inputFile);
        final Product sourceProduct = reader.readProductNodes(inputFile, null);

        final RangeDopplerGeocodingOp op = (RangeDopplerGeocodingOp)spi.createOperator();
        assertNotNull(op);
        op.setSourceProduct(sourceProduct);
        op.setApplyRadiometricCalibration(true);
        String[] bandNames = {sourceProduct.getBandAt(0).getName()};
        op.setSourceBandNames(bandNames);

        // get targetProduct: execute initialize()
        final Product targetProduct = op.getTargetProduct();
        TestUtils.verifyProduct(targetProduct, false, false);
        TestUtils.compareProducts(op, targetProduct, expectedPathAPM, null);
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

                        final RangeDopplerGeocodingOp op = (RangeDopplerGeocodingOp)spi.createOperator();
                        assertNotNull(op);
                        op.setSourceProduct(sourceProduct);
                        op.setApplyRadiometricCalibration(true);

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
