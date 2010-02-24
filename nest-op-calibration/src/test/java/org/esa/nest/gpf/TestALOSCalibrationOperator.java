package org.esa.nest.gpf;

import junit.framework.TestCase;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.nest.util.TestUtils;

import java.io.File;

/**
 * Unit test for Calibration Operator.
 */
public class TestALOSCalibrationOperator extends TestCase {

    private OperatorSpi spi;
    private final static String inputPath =     TestUtils.rootPathExpectedProducts+"\\input\\FBS1.1\\l1data\\subset_0_of_ALOS-H1_1__A-ORBIT__ALPSRP037120700.dim";
    private final static String expectedPath =  TestUtils.rootPathExpectedProducts+"\\expected\\subset_0_of_ALOS-H1_1__A-ORBIT__ALPSRP037120700_Calib.dim";


    @Override
    protected void setUp() throws Exception {
        spi = new CalibrationOp.Spi();
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(spi);
    }

    @Override
    protected void tearDown() throws Exception {
        GPF.getDefaultInstance().getOperatorSpiRegistry().removeOperatorSpi(spi);
    }

    public void testProcessingLevel1_1() throws Exception {
        processFile(inputPath, expectedPath);
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

        final CalibrationOp op = (CalibrationOp)spi.createOperator();
        assertNotNull(op);
        op.setSourceProduct(sourceProduct);

        // get targetProduct: execute initialize()
        final Product targetProduct = op.getTargetProduct();
        TestUtils.verifyProduct(targetProduct, false, false);
        TestUtils.compareProducts(op, targetProduct, expectedPath, null);
    }    
}