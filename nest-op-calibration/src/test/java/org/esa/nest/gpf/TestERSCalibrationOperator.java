package org.esa.nest.gpf;

import junit.framework.TestCase;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.nest.util.TestUtils;

import java.io.File;

/**
 * Unit test for Calibration Operator.
 */
public class TestERSCalibrationOperator extends TestCase {

    private OperatorSpi spi;
    private final static String inputPathIMP =     "P:\\nest\\nest\\test\\input\\ER01_SAR_IMP_1P_19971002T145343_19971002T145400_ESR_32506_0000.CEOS\\VDF_DAT.001";
    private final static String expectedPathIMP =  "P:\\nest\\nest\\test\\expected\\ERS-1.SAR.PRI-ORBIT_32506_DATE__02-OCT-1997_14_53_43_Calib.dim";
    private final static String inputPathIMS =     "P:\\nest\\nest\\test\\input\\ER02_SAR_IMS_1P_19970406T030935_19970406T030952_DPA_10249_0000.CEOS\\VDF_DAT.001";
    private final static String expectedPathIMS =  "P:\\nest\\nest\\test\\expected\\ERS-2.SAR.SLC-ORBIT_10249_DATE__06-APR-1997_03_09_34_Calib.dim";
    private final static String ersLazioPath =    "P:\\nest\\nest\\ESA Data\\NestBox\\GTC_dataset\\ERS_LAZIO";

    @Override
    protected void setUp() throws Exception {
        spi = new ERSCalibrationOperator.Spi();
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(spi);
    }

    @Override
    protected void tearDown() throws Exception {
        GPF.getDefaultInstance().getOperatorSpiRegistry().removeOperatorSpi(spi);
    }

    public void testProcessingIMP() throws Exception {
        processFile(inputPathIMP, expectedPathIMP);
    }

    public void testProcessingIMS() throws Exception {
        processFile(inputPathIMS, expectedPathIMS);
    }

    /**
     * Processes a product and compares it to processed product known to be correct
     * @param inputPath the path to the input product
     * @param expectedPath the path to the expected product
     * @throws Exception general exception
     */
    public void processFile(String inputPath, String expectedPath) throws Exception {

        final File inputFile = new File(inputPath);
        if(!inputFile.exists()) return;

        final ProductReader reader = ProductIO.getProductReaderForFile(inputFile);
        assertNotNull(reader);
        final Product sourceProduct = reader.readProductNodes(inputFile, null);

        final ERSCalibrationOperator op = (ERSCalibrationOperator)spi.createOperator();
        assertNotNull(op);
        op.setSourceProduct(sourceProduct);

        // get targetProduct: execute initialize()
        final Product targetProduct = op.getTargetProduct();
        TestUtils.verifyProduct(targetProduct, false, false);
        TestUtils.compareProducts(op, targetProduct, expectedPath, null);
    }

    /**
     * Processes all products in a folder
     * @throws Exception general exception
     */
    public void testProcessAllERS() throws Exception
    {
        final File folder = new File(ersLazioPath);
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

                        final ERSCalibrationOperator op = (ERSCalibrationOperator)spi.createOperator();
                        assertNotNull(op);
                        op.setSourceProduct(sourceProduct);

                        TestUtils.executeOperator(op);
                    }
                } catch(Exception e) {
                    System.out.println("Failed to process "+ file.toString());
                    throw e;
                }
            }
        }
    }
}
