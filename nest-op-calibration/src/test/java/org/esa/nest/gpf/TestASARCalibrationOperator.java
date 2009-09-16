package org.esa.nest.gpf;

import com.bc.ceres.core.ProgressMonitor;
import junit.framework.TestCase;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.util.TestUtils;

import java.util.Arrays;
import java.io.File;

/**
 * Unit test for ASARCalibrationOp.
 */
public class TestASARCalibrationOperator extends TestCase {

    private OperatorSpi spi;
    private final static String inputPathWSM =     "P:\\nest\\nest\\test\\input\\ASA_WSM_1PNPDE20080119_093446_000000852065_00165_30780_2977.N1";
    private final static String expectedPathWSM =  "P:\\nest\\nest\\test\\expected\\ENVISAT-ASA_WSM_1PNPDE20080119_093446_000000852065_00165_30780_2977.N1_Calib.dim";
    private final static String asarLazioPath =    "P:\\nest\\nest\\ESA Data\\NestBox\\GTC_dataset\\ASAR_LAZIO";

    @Override
    protected void setUp() throws Exception {
        spi = new ASARCalibrationOperator.Spi();
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(spi);
    }

    @Override
    protected void tearDown() throws Exception {
        GPF.getDefaultInstance().getOperatorSpiRegistry().removeOperatorSpi(spi);
    }

    public void testOperator() throws Exception {
        final Product sourceProduct = createTestProduct(4, 4);

        final ASARCalibrationOperator op = (ASARCalibrationOperator)spi.createOperator();
        assertNotNull(op);
        op.setSourceProduct(sourceProduct);
        op.setExternalAntennaPatternFile("ASA_XCA_AXVIEC20021217_150852_20020413_000000_20031231_000000.zip");

        // get targetProduct gets initialize to be executed
        final Product targetProduct = op.getTargetProduct();
        TestUtils.verifyProduct(targetProduct, true, true);

        final Band band = targetProduct.getBandAt(0);
        assertNotNull(band);

        // readPixels gets computeTiles to be executed
        final float[] floatValues = new float[16];
        band.readPixels(0, 0, 4, 4, floatValues, ProgressMonitor.NULL);

        // compare with expected outputs
        final float[] expectedValues = {0.50000006f, 2.8284273f, 7.7942286f, 15.454814f, 12.5f, 25.455845f, 42.435246f,
                61.819256f, 40.499996f, 70.71068f, 104.78907f, 139.09332f, 84.49999f, 138.59294f, 194.85571f, 247.27702f};
        assertTrue(Arrays.equals(expectedValues, floatValues));
    }

    private static Product createTestProduct(int w, int h) {

        final Product testProduct = TestUtils.createProduct("ASA_APG_1P", w, h);

        // create a Band: band1
        final double calibrationFactor = 518800.03125;
        final Band band1 = testProduct.addBand("band1", ProductData.TYPE_FLOAT32);
        band1.setUnit("amplitude");
        band1.setSynthetic(true);
        final float [] values = new float[w * h];
        for (int i = 0; i < w * h; i++) {
            values[i] = (float)((i + 1)*Math.sqrt(calibrationFactor));
        }
        band1.setData(ProductData.createInstance(values));

        // create 2 TiePointGrid for band1: incidence_angle and slant_rage_time
        final float[] incidence_angle = {30F,45F,60F,75F,30F,45F,60F,75F,30F,45F,60F,75F,30F,45F,60F,75F};
        final float[] slant_rage_time = {0.005337F,0.006535F,0.009244F,0.017858F,
                                   0.005337F,0.006535F,0.009244F,0.017858F,
                                   0.005337F,0.006535F,0.009244F,0.017858F,
                                   0.005337F,0.006535F,0.009244F,0.017858F};

        testProduct.addTiePointGrid(new TiePointGrid(OperatorUtils.TPG_INCIDENT_ANGLE, 4, 4, 0, 0, 1, 1, incidence_angle));
        testProduct.addTiePointGrid(new TiePointGrid(OperatorUtils.TPG_SLANT_RANGE_TIME, 4, 4, 0, 0, 1, 1, slant_rage_time));

        final MetadataElement abs = AbstractMetadata.getAbstractedMetadata(testProduct);
        AbstractMetadata.setAttribute(abs, AbstractMetadata.PRODUCT_TYPE, "ASA_APG_1P");
        AbstractMetadata.setAttribute(abs, AbstractMetadata.SAMPLE_TYPE, "DETECTED");
        AbstractMetadata.setAttribute(abs, AbstractMetadata.mds1_tx_rx_polar, "HH");
        AbstractMetadata.setAttribute(abs, AbstractMetadata.mds2_tx_rx_polar, " ");
        AbstractMetadata.setAttribute(abs, AbstractMetadata.SWATH, "IS1");
        AbstractMetadata.setAttribute(abs, AbstractMetadata.ant_elev_corr_flag, 1);
        AbstractMetadata.setAttribute(abs, AbstractMetadata.range_spread_comp_flag, 1);
        AbstractMetadata.setAttribute(abs, AbstractMetadata.abs_calibration_flag, 0);

        // create MAIN_PROCESSING_PARAMS_ADS MetadataElement with attributes: ant_elev_corr_flag, range_spread_comp_flag and ext_cal_fact
        final MetadataElement ads = new MetadataElement("MAIN_PROCESSING_PARAMS_ADS");
        ads.addAttribute(new MetadataAttribute("first_zero_doppler_time",
                ProductData.createInstance(new int[] {0, 0, 0}), false));
        ads.addAttribute(new MetadataAttribute("detected_flag",
                ProductData.createInstance(new byte[] {1}), false));
        ads.addAttribute(new MetadataAttribute("ASAR_Main_ADSR.sd/calibration_factors.1.ext_cal_fact",
                ProductData.createInstance(new float[] {1.0F}), false));
        ads.addAttribute(new MetadataAttribute("ASAR_Main_ADSR.sd/calibration_factors.2.ext_cal_fact",
                ProductData.createInstance(new float[] {0.0F}), false));
        ads.addAttribute(new MetadataAttribute("ASAR_Main_ADSR.sd/orbit_state_vectors.3.x_pos_1",
                ProductData.createInstance(new float[] {1.0F}), false));
        ads.addAttribute(new MetadataAttribute("ASAR_Main_ADSR.sd/orbit_state_vectors.3.y_pos_1",
                ProductData.createInstance(new float[] {1.0F}), false));
        ads.addAttribute(new MetadataAttribute("ASAR_Main_ADSR.sd/orbit_state_vectors.3.z_pos_1",
                ProductData.createInstance(new float[] {1.0F}), false));
        ads.addAttribute(new MetadataAttribute("line_time_interval",
                ProductData.createInstance(new float[] {0.0F}), false));
        testProduct.getMetadataRoot().addElement(ads);

        // create DSD with attribute num_records
        final MetadataElement dsd3 = new MetadataElement("DSD.3");
        dsd3.addAttribute(new MetadataAttribute("num_records",
                ProductData.createInstance(new int[] {1}), false));
        final MetadataElement dsd = new MetadataElement("DSD");
        dsd.addElement(dsd3);
        testProduct.getMetadataRoot().addElement(dsd);

        final Band band2 = testProduct.addBand("band2", ProductData.TYPE_FLOAT32);
        band2.setSynthetic(true);
        band2.setUnit(Unit.AMPLITUDE);
        final float[] floatValues = new float[w * h];
        Arrays.fill(floatValues, 2.5f);
        band2.setData(ProductData.createInstance(floatValues));

        final Band band3 = testProduct.addBand("band3", ProductData.TYPE_INT16);
        band3.setScalingFactor(0.5);
        band3.setSynthetic(true);
        band3.setUnit(Unit.AMPLITUDE);
        final short[] shortValues = new short[w * h];
        Arrays.fill(shortValues, (short) 6);
        band3.setData(ProductData.createInstance(shortValues));
        return testProduct;
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

        final ASARCalibrationOperator op = (ASARCalibrationOperator)spi.createOperator();
        assertNotNull(op);
        op.setSourceProduct(sourceProduct);

        // get targetProduct: execute initialize()
        final Product targetProduct = op.getTargetProduct();
        TestUtils.verifyProduct(targetProduct, false, false);
        TestUtils.compareProducts(op, targetProduct, expectedPathWSM, null);
    }

    /**
     * Processes all products in a folder
     * @throws Exception general exception
     */
    public void testProcessAllASAR() throws Exception
    {
        final File folder = new File(asarLazioPath);
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

                        final ASARCalibrationOperator op = (ASARCalibrationOperator)spi.createOperator();
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
