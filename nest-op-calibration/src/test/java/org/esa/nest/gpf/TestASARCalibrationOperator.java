package org.esa.nest.gpf;

import junit.framework.TestCase;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.datamodel.*;
import org.esa.nest.datamodel.AbstractMetadata;

import java.util.Arrays;

import com.bc.ceres.core.ProgressMonitor;

/**
 * Unit test for SingleTileOperator.
 */
public class TestASARCalibrationOperator extends TestCase {

    private OperatorSpi spi;

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
        Product sourceProduct = createTestProduct(4, 4);

        Operator op = spi.createOperator();
        assertNotNull(op);
        op.setSourceProduct(sourceProduct);

        // get targetProduct gets initialize to be executed
        Product targetProduct = op.getTargetProduct();
        assertNotNull(targetProduct);

        Band band = targetProduct.getBandAt(0);
        assertNotNull(band);

        // readPixels gets computeTiles to be executed
        float[] floatValues = new float[16];
        band.readPixels(0, 0, 4, 4, floatValues, ProgressMonitor.NULL);

        // compare with expected outputs
        float[] expectedValues = {0.5f,2.828427f,7.7942286f,15.454813f,12.5f,25.455845f,42.435246f,
                61.819252f,40.5f,70.71068f,104.78907f,139.09332f,84.5f,138.59293f,194.85571f,247.27701f};
        assertTrue(Arrays.equals(expectedValues, floatValues));
    }


    private Product createTestProduct(int w, int h) {

        Product testProduct = new Product("p", "ASA_APG_1P", w, h);

        // create a Band: band1
        Band band1 = testProduct.addBand("band1", ProductData.TYPE_INT32);
        band1.setUnit("amplitude");
        band1.setSynthetic(true);
        int[] intValues = new int[w * h];
        for (int i = 0; i < w * h; i++) {
            intValues[i] = i + 1;
        }
        band1.setData(ProductData.createInstance(intValues));

        // create 2 TiePointGrid for band1: incidence_angle and slant_rage_time
        float[] incidence_angle = {30F,45F,60F,75F,30F,45F,60F,75F,30F,45F,60F,75F,30F,45F,60F,75F};
        float[] slant_rage_time = {0.005337F,0.006535F,0.009244F,0.017858F,
                                   0.005337F,0.006535F,0.009244F,0.017858F,
                                   0.005337F,0.006535F,0.009244F,0.017858F,
                                   0.005337F,0.006535F,0.009244F,0.017858F};

        testProduct.addTiePointGrid(new TiePointGrid("incident_angle", 4, 4, 0, 0, 2, 2, incidence_angle));
        testProduct.addTiePointGrid(new TiePointGrid("slant_range_time", 4, 4, 0, 0, 2, 2, slant_rage_time));

        MetadataElement abs = new MetadataElement("Abstracted Metadata");
        abs.addAttribute(new MetadataAttribute(AbstractMetadata.PRODUCT_TYPE,
                ProductData.createInstance("ASA_APG_1P"), false));
        abs.addAttribute(new MetadataAttribute(AbstractMetadata.SAMPLE_TYPE,
                ProductData.createInstance("DETECTED"), false));
        abs.addAttribute(new MetadataAttribute(AbstractMetadata.mds1_tx_rx_polar,
                ProductData.createInstance("HH"), false));
        abs.addAttribute(new MetadataAttribute(AbstractMetadata.mds2_tx_rx_polar,
                ProductData.createInstance(""), false));
        abs.addAttribute(new MetadataAttribute(AbstractMetadata.SWATH,
                ProductData.createInstance("IS1"), false));
        abs.addAttribute(new MetadataAttribute(AbstractMetadata.ant_elev_corr_flag,
                ProductData.createInstance(new byte[] {1}), false));
        abs.addAttribute(new MetadataAttribute(AbstractMetadata.range_spread_comp_flag,
                ProductData.createInstance(new byte[] {1}), false));
        abs.addAttribute(new MetadataAttribute(AbstractMetadata.abs_calibration_flag,
                ProductData.createInstance(new byte[] {0}), false));

        testProduct.getMetadataRoot().addElement(abs);

        // create MAIN_PROCESSING_PARAMS_ADS MetadataElement with attributes: ant_elev_corr_flag, range_spread_comp_flag and ext_cal_fact
        MetadataElement ads = new MetadataElement("MAIN_PROCESSING_PARAMS_ADS");
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
        MetadataElement dsd3 = new MetadataElement("DSD.3");
        dsd3.addAttribute(new MetadataAttribute("num_records",
                ProductData.createInstance(new int[] {1}), false));
        MetadataElement dsd = new MetadataElement("DSD");
        dsd.addElement(dsd3);
        testProduct.getMetadataRoot().addElement(dsd);

        Band band2 = testProduct.addBand("band2", ProductData.TYPE_FLOAT32);
        band2.setSynthetic(true);
        band2.setUnit("amplitude");
        float[] floatValues = new float[w * h];
        Arrays.fill(floatValues, 2.5f);
        band2.setData(ProductData.createInstance(floatValues));

        Band band3 = testProduct.addBand("band3", ProductData.TYPE_INT16);
        band3.setScalingFactor(0.5);
        band3.setSynthetic(true);
        band3.setUnit("amplitude");
        short[] shortValues = new short[w * h];
        Arrays.fill(shortValues, (short) 6);
        band3.setData(ProductData.createInstance(shortValues));
        return testProduct;
    }
}
