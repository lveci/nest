/*
 * Copyright (C) 2002-2007 by ?
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.esa.nest.gpf;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatAuxReader;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.math.MathUtils;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.datamodel.QuadInterpolator;
import org.esa.nest.util.Settings;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Calibration for ASAR data products.
 *
 * @todo automatically search aux file in local repository using time period
 */
@OperatorMetadata(alias = "ASAR-Calibration",
        description = "Calibration of ASAR data products")
public class ASARCalibrationOperator extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            sourceProductId="source", label="Source Band")
    String[] sourceBandNames;

    //@Parameter(description = "The calibration factor", interval = "(0, *)", defaultValue = "0",
    //            label="Calibration Factor")
    //private double extCalibrationFactor;

    @Parameter(description = "The antenne elevation pattern gain auxiliary data file.", label="External Aux File")
    private File externalAuxFile = null;

    @Parameter(description = "Output image scale", defaultValue = "false", label="Scale in dB")
    private boolean outputImageScaleInDb = false;

    @Parameter(description = "Create gamma0 virtual band", defaultValue = "false", label="Create gamma0 virtual band")
    private boolean createGammaBand = false;

    @Parameter(description = "Create beta0 virtual band", defaultValue = "false", label="Create beta0 virtual band")
    private boolean createBetaBand = false;

    protected MetadataElement abstractedMetadata = null;

    private String productType;
    private String sampleType;
    protected final String[] mdsPolar = new String[2]; // polarizations for the two bands in the product

    protected TiePointGrid incidenceAngle = null;
    protected TiePointGrid slantRangeTime = null;
    private QuadInterpolator slantRangeTimeQuadInterp = null;
    private QuadInterpolator incidenceAngleQuadInterp = null;

    private boolean extAuxFileAvailableFlag;
    private boolean antElevCorrFlag;
    private boolean rangeSpreadCompFlag;

    private double rangeSpreadingCompPower; // power in range spreading loss compensation calculation
    private double elevationAngle; // elevation angle for given swath, in degree
    protected double[] rSat = null; // the distance from satellite to the Earth center for each MPP ADSR record, in m
    private final double[] calibrationFactor = new double[2]; // calibration constants corresponding to 2 bands in product
    private float[][] newAntPat = null; // antenna pattern for two bands for given swath, in dB
    private double[] targetTileNewAntPat = null; // antenna pattern for a given tile, in linear scale

    protected int swath;
    protected int numMPPRecords; // number of MPP ADSR records
    protected int[] lastLineIndex = null; // the index of the last line covered by each MPP ADSR record

    private static final double refSlantRange = 800000.0; //  m
    protected static final double lightSpeed = 299792458.0; //  m / s
    protected static final double halfLightSpeed = lightSpeed / 2.0;
    protected static final double underFlowFloat = 1.0e-30;
    private final HashMap<String, String[]> targetBandNameToSourceBandName = new HashMap<String, String[]>();;

    /**
     * Default constructor. The graph processing framework
     * requires that an operator has a default constructor.
     */
    public ASARCalibrationOperator() {
    }

    /**
     * Initializes this operator and sets the one and only target product.
     * <p>The target product can be either defined by a field of type {@link org.esa.beam.framework.datamodel.Product} annotated with the
     * {@link org.esa.beam.framework.gpf.annotations.TargetProduct TargetProduct} annotation or
     * by calling {@link #setTargetProduct} method.</p>
     * <p>The framework calls this method after it has created this operator.
     * Any client code that must be performed before computation of tile data
     * should be placed here.</p>
     *
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs during operator initialisation.
     * @see #getTargetProduct()
     */
    @Override
    public void initialize() throws OperatorException {
        try {
            setExternalAuxFileAvailableFlag();
            abstractedMetadata = AbstractMetadata.getAbstractedMetadata(sourceProduct);
            getProductType();
            sampleType = abstractedMetadata.getAttributeString(AbstractMetadata.SAMPLE_TYPE);
            swath = getSwath(abstractedMetadata);
            getPolarization();
            getCalibrationFlags();
            numMPPRecords = getNumOfRecordsInMainProcParam(sourceProduct);
            getTiePointGridData(sourceProduct);
            getCalibrationFactor();

            if (!antElevCorrFlag) {
                getAntennaPatternGain();
                getSatelliteToEarthCenterDistance(sourceProduct);
            }

            if (sampleType.contains("COMPLEX")) {
                setPowerInRangeSpreadingLossComp();
            }

            createTargetProduct();

            if(createGammaBand) {
                createGammaVirtualBand(targetProduct, outputImageScaleInDb);
            }

            if(createBetaBand) {
                createBetaVirtualBand(targetProduct, outputImageScaleInDb);
            }

            targetProduct.setPreferredTileSize(targetProduct.getSceneRasterWidth(), 10);
        } catch(Exception e) {
            throw new OperatorException(getId() + ": " + e.getMessage());
        }
    }

    /**
     * Check if user specified aux data is available and set flag accordingly.
     */
    private void setExternalAuxFileAvailableFlag() {

        if (externalAuxFile != null) {
            if (!externalAuxFile.getName().contains("ASA_XCA")) {
                throw new OperatorException("Invalid XCA file for ASAR product");
            }
            extAuxFileAvailableFlag = true;
        }
    }

    /**
     * Get Product ID from MPH.
     */
    private void getProductType() {

        final MetadataAttribute productTypeAttr = abstractedMetadata.getAttribute(AbstractMetadata.PRODUCT_TYPE);
        if (productTypeAttr == null) {
            throw new OperatorException(AbstractMetadata.PRODUCT_TYPE + " not found");
        }

        productType = productTypeAttr.getData().getElemString();

        if (!productType.equals("ASA_IMP_1P") && !productType.equals("ASA_IMM_1P") &&
                !productType.equals("ASA_APP_1P") && !productType.equals("ASA_APM_1P") &&
                !productType.equals("ASA_WSM_1P") && !productType.equals("ASA_IMG_1P") &&
                !productType.equals("ASA_APG_1P") && !productType.equals("ASA_IMS_1P") &&
                !productType.equals("ASA_APS_1P")) {

            throw new OperatorException(productType + " is not a valid product ID.");
        }
        //System.out.println("product type is " + productType);
    }

    /**
     * Get product swath.
     * @param absMetadata
     * @return
     */
    static int getSwath(MetadataElement absMetadata) {

        final MetadataAttribute swathAttr = absMetadata.getAttribute(AbstractMetadata.SWATH);
        if (swathAttr == null) {
            throw new OperatorException(AbstractMetadata.SWATH + " not found");
        }

        int swath = 0;
        final String swathName = swathAttr.getData().getElemString();
        if (swathName.contains("IS1")) {
            swath = 0;
        } else if (swathName.contains("IS2")) {
            swath = 1;
        } else if (swathName.contains("IS3")) {
            swath = 2;
        } else if (swathName.contains("IS4")) {
            swath = 3;
        } else if (swathName.contains("IS5")) {
            swath = 4;
        } else if (swathName.contains("IS6")) {
            swath = 5;
        } else if (swathName.contains("IS7")) {
            swath = 6;
        } else if (swathName.contains("WS")) {
            swath = 7;
        } else {
            throw new OperatorException("Invalid swath");
        }
        //System.out.println("Swath is " + swath);

        return swath;
    }

    /**
     * Get polarizations for each band in the product.
     */
    private void getPolarization() {

        MetadataAttribute polarAttr = abstractedMetadata.getAttribute(AbstractMetadata.mds1_tx_rx_polar);
        if (polarAttr == null) {
            throw new OperatorException(AbstractMetadata.mds1_tx_rx_polar + " not found");
        }

        mdsPolar[0] = null;
        String polarName = polarAttr.getData().getElemString();
        if (polarName.contains("HH") || polarName.contains("HV") || polarName.contains("VH") || polarName.contains("VV")) {
            mdsPolar[0] = polarName.toLowerCase();
        }

        mdsPolar[1] = null;
        polarAttr = abstractedMetadata.getAttribute(AbstractMetadata.mds2_tx_rx_polar);
        if (polarAttr != null) {
            polarName = polarAttr.getData().getElemString();
            if (polarName.contains("HH") || polarName.contains("HV") || polarName.contains("VH") || polarName.contains("VV")) {
                mdsPolar[1] = polarName.toLowerCase();
            }
        }

        //System.out.println("MDS1 polarization is " + mdsPolar[0]);
        //System.out.println("MDS2 polarization is " + mdsPolar[1]);
    }

    /**
     * Get number of records in Main Processing Params data set.
     */
    static int getNumOfRecordsInMainProcParam(Product sourceProduct) throws OperatorException {

        final MetadataElement dsd = sourceProduct.getMetadataRoot().getElement("DSD").getElement("DSD.3");
        if (dsd == null) {
            throw new OperatorException("DSD not found");
        }

        final MetadataAttribute numRecordsAttr = dsd.getAttribute("num_records");
        if (numRecordsAttr == null) {
            throw new OperatorException("num_records not found");
        }
        int numMPPRecords = numRecordsAttr.getData().getElemInt();
        if (numMPPRecords < 1) {
            throw new OperatorException("Invalid num_records.");
        }
        //System.out.println("The number of Main Processing Params records is " + numMPPRecords);
        return numMPPRecords;
    }

    /**
     * Get antenna elevation correction flag and range spreading compensation flag from Metadata.
     * Here it is assumed that the flag values do not change in case that there are multiple records
     * in the Main Processing Parameters data set.
     */
    private void getCalibrationFlags() {

        final MetadataAttribute absCalibrationFlagAttr = abstractedMetadata.getAttribute(AbstractMetadata.abs_calibration_flag);
        if (absCalibrationFlagAttr == null) {
            throw new OperatorException(AbstractMetadata.abs_calibration_flag + " not found.");
        }

        if (absCalibrationFlagAttr.getData().getElemBoolean()) {
            throw new OperatorException("The product has already been calibrated.");
        }

        final MetadataAttribute antElevCorrFlagAttr = abstractedMetadata.getAttribute(AbstractMetadata.ant_elev_corr_flag);
        if (antElevCorrFlagAttr == null) {
            throw new OperatorException(AbstractMetadata.ant_elev_corr_flag + " not found");
        }
        antElevCorrFlag = antElevCorrFlagAttr.getData().getElemBoolean();
        //System.out.println("Antenna elevation corr flag is " + antElevCorrFlag);

        final MetadataAttribute rangeSpreadCompFlagAttr =
                abstractedMetadata.getAttribute(AbstractMetadata.range_spread_comp_flag);
        if (rangeSpreadCompFlagAttr == null) {
            throw new OperatorException(AbstractMetadata.range_spread_comp_flag + " not found");
        }
        rangeSpreadCompFlag = rangeSpreadCompFlagAttr.getData().getElemBoolean();
        //System.out.println("Range-spreading loss compensation flag is " + rangeSpreadCompFlag);

        if (!antElevCorrFlag || !rangeSpreadCompFlag) {
            if (!productType.equals("ASA_IMS_1P") && !productType.equals("ASA_APS_1P")) {
                throw new OperatorException("Antenna pattern correction or range spreading compensation" +
                        " has not been applied to the ground detected source product.");
            }
        }
    }

    /**
     * Get incidence angle and slant range time tie point grids.
     * @param sourceProduct the source
     */
    private void getTiePointGridData(Product sourceProduct) {

        slantRangeTime = OperatorUtils.getSlantRangeTime(sourceProduct);
        slantRangeTimeQuadInterp = new QuadInterpolator(slantRangeTime);

        incidenceAngle = OperatorUtils.getIncidenceAngle(sourceProduct);
        incidenceAngleQuadInterp = new QuadInterpolator(incidenceAngle);
        /*
        int w = 2000;
        int h = 2;
        float[] arrayQ = new float[w*h];
        slantRangeTimeQuadInterp.getPixelFloats(0,1000,w,h,arrayQ);
        for (int i = 0; i < w*h; i++) {
            System.out.print(arrayQ[i] + ",");
        }
        System.out.println();

        float[] arrayB = new float[w*h];
        slantRangeTime.getPixels(0,0,w,h,arrayB);
        for (int i = 0; i < w*h; i++) {
            System.out.print(arrayB[i] + ",");
        }
        System.out.println();

        incidenceAngleQuadInterp.getPixelFloats(0,0,w,h,arrayQ);
        incidenceAngle.getPixels(0,0,w,h,arrayB);
        for (int i = 0; i < w*h; i++) {
            System.out.print(arrayQ[i] + ",");
        }
        System.out.println();
        for (int i = 0; i < w*h; i++) {
            System.out.print(arrayB[i] + ",");
        }
        System.out.println();
        */
    }

    /**
     * Obtain from auxiliary data the elevation angles for given swath and the antenna elevation
     * pattern gains for the swath and the polarization of the product.
     *
     * @param fileName The auxiliary data file name.
     * @throws OperatorException
     */
    private void getAntennaPatternGainFromAuxData(String fileName) throws OperatorException {

        final EnvisatAuxReader reader = new EnvisatAuxReader();

        try {
            reader.readProduct(fileName);
        } catch (IOException e) {
            throw new OperatorException("Please provide external auxiliary file (ASAR_XCA_AXVIEC) which" +
                    " is available at http://earth.esa.int/services/auxiliary_data/asar/.");
        }

        try {
            final String[] swathName = {"is1", "is2", "is3_ss2", "is4_ss3", "is5_ss4", "is6_ss5", "is7", "ss1"};
            final String elevAngName = "elev_ang_" + swathName[swath];

            final ProductData elevAngleData = reader.getAuxData(elevAngName);
            elevationAngle = (double) elevAngleData.getElemFloat();
            //System.out.println("elevation angle is " + elevationAngle);
            //System.out.println();

            final String patternName = "pattern_" + swathName[swath];
            final ProductData patData = reader.getAuxData(patternName);
            final float[] pattern = ((float[]) patData.getElems());

            if (pattern.length != 804) {
                throw new OperatorException("Incorrect array length for " + patternName);
            }
            /*
            System.out.print("num values " + pattern.length);
            System.out.println();
            for (float val : pattern) {
                System.out.print(val + ", ");
            }
            System.out.println();
            */
            final int numOfGains = 201;
            newAntPat = new float[2][numOfGains];
            for (int i = 0; i < 2 && mdsPolar[i] != null && mdsPolar[i].length() != 0; i++) {
                if (mdsPolar[i].contains("hh")) {
                    System.arraycopy(pattern, 0, newAntPat[i], 0, numOfGains);
                } else if (mdsPolar[i].contains("vv")) {
                    System.arraycopy(pattern, numOfGains, newAntPat[i], 0, numOfGains);
                } else if (mdsPolar[i].contains("hv")) {
                    System.arraycopy(pattern, 2 * numOfGains, newAntPat[i], 0, numOfGains);
                } else if (mdsPolar[i].contains("vh")) {
                    System.arraycopy(pattern, 3 * numOfGains, newAntPat[i], 0, numOfGains);
                }
            }
            /*
            for (float val : newAntPat[0]) {
                System.out.print(val + ", ");
            }
            System.out.println();
            for (float val : newAntPat[1]) {
                System.out.print(val + ", ");
            }
            System.out.println();
            */
        } catch (IOException e) {
            throw new OperatorException(e);
        }
    }

    /**
     * Get calibration factor.
     */
    private void getCalibrationFactor() {

//        if (Double.compare(extCalibrationFactor, 0.0) != 0) {

//            calibrationFactor[0] = extCalibrationFactor;
//            calibrationFactor[1] = extCalibrationFactor;

//        } else {

            if (extAuxFileAvailableFlag) {
                getCalibrationFactorFromAuxData(externalAuxFile.getAbsolutePath());
            } else {
                getCalibrationFactorFromMetadata();
            }
//        }
    }

    /**
     * Get calibration factor from user specified auxiliary data.
     */
    private void getCalibrationFactorFromAuxData(String auxFilePath) {

        final EnvisatAuxReader reader = new EnvisatAuxReader();

        try {

            reader.readProduct(auxFilePath);

            final int numOfSwaths = 7;
            String calibrationFactorName;
            for (int i = 0; i < 2 && mdsPolar[i] != null && mdsPolar[i].length() != 0; i++) {

                calibrationFactor[i] = 0;

                if (productType.contains("ASA_IMP_1P")) {
                    calibrationFactorName = "ext_cal_im_pri_" + mdsPolar[i];
                } else if (productType.contains("ASA_IMM_1P")) {
                    calibrationFactorName = "ext_cal_im_med_" + mdsPolar[i];
                } else if (productType.contains("ASA_APP_1P")) {
                    calibrationFactorName = "ext_cal_ap_pri_" + mdsPolar[i];
                } else if (productType.contains("ASA_APM_1P")) {
                    calibrationFactorName = "ext_cal_ap_med_" + mdsPolar[i];
                } else if (productType.contains("ASA_WSM_1P")) {
                    calibrationFactorName = "ext_cal_ws_" + mdsPolar[i];
                } else if (productType.contains("ASA_IMG_1P")) {
                    calibrationFactorName = "ext_cal_im_geo_" + mdsPolar[i];
                } else if (productType.contains("ASA_APG_1P")) {
                    calibrationFactorName = "ext_cal_ap_geo_" + mdsPolar[i];
                } else if (productType.contains("ASA_IMS_1P")) {
                    calibrationFactorName = "ext_cal_im_" + mdsPolar[i];
                } else if (productType.contains("ASA_APS_1P")) {
                    calibrationFactorName = "ext_cal_ap_" + mdsPolar[i];
                } else {
                    throw new OperatorException("Invalid product ID.");
                }

                final ProductData factorData = reader.getAuxData(calibrationFactorName);
                final float[] factors = (float[]) factorData.getElems();

                if (productType.contains("ASA_WSM_1P")) {
                    calibrationFactor[i] = factors[0];
                } else {
                    if (factors.length != numOfSwaths) {
                        throw new OperatorException("Incorrect array length for " + calibrationFactorName);
                    }
                    calibrationFactor[i] = factors[swath];
                }
            }

        } catch (IOException e) {
            throw new OperatorException(e);
        }

        if (Double.compare(calibrationFactor[0], 0.0) == 0 && Double.compare(calibrationFactor[1], 0.0) == 0) {
            throw new OperatorException("Calibration factors in user provided auxiliary file are zero");
        }
    }

    /**
     * Get calibration factors from Metadata for each band in the product.
     * Here it is assumed that the calibration factor values do not change in case that there are
     * multiple records in the Main Processing Parameters data set.
     */
    private void getCalibrationFactorFromMetadata() {

        MetadataElement ads;

        if (numMPPRecords == 1) {
            ads = sourceProduct.getMetadataRoot().getElement("MAIN_PROCESSING_PARAMS_ADS");
        } else {
            ads = sourceProduct.getMetadataRoot().getElement("MAIN_PROCESSING_PARAMS_ADS").
                    getElement("MAIN_PROCESSING_PARAMS_ADS.1");
        }

        if (ads == null) {
            throw new OperatorException("MAIN_PROCESSING_PARAMS_ADS not found");
        }

        MetadataAttribute calibrationFactorsAttr =
                ads.getAttribute("ASAR_Main_ADSR.sd/calibration_factors.1.ext_cal_fact");

        if (calibrationFactorsAttr == null) {
            throw new OperatorException("calibration_factors.1.ext_cal_fact not found");
        }

        calibrationFactor[0] = (double) calibrationFactorsAttr.getData().getElemFloat();

        calibrationFactorsAttr = ads.getAttribute("ASAR_Main_ADSR.sd/calibration_factors.2.ext_cal_fact");

        if (calibrationFactorsAttr == null) {
            throw new OperatorException("calibration_factors.2.ext_cal_fact not found");
        }

        calibrationFactor[1] = (double) calibrationFactorsAttr.getData().getElemFloat();

        if (Double.compare(calibrationFactor[0], 0.0) == 0 && Double.compare(calibrationFactor[1], 0.0) == 0) {
            throw new OperatorException("Calibration factors in metadata are zero");
        }
        //System.out.println("calibration factor for band 1 is " + calibrationFactor[0]);
        //System.out.println("calibration factor for band 2 is " + calibrationFactor[1]);
    }

    /**
     * Get antenna pattern from aux file for each band in the product.
     */
    private void getAntennaPatternGain() {

        if (!productType.equals("ASA_IMS_1P") && !productType.equals("ASA_APS_1P")) {
            throw new OperatorException("Found ground detected product without antenna pattern correction.");
        }

        String fileName;
        if (extAuxFileAvailableFlag) {
            fileName = externalAuxFile.getAbsolutePath();
        } else {
            fileName = getDefaultAuxFile(sourceProduct);
        }

        getAntennaPatternGainFromAuxData(fileName);
    }

    /**
     * Get default aux file name.
     * @return string
     */
    static String getDefaultAuxFile(Product sourceProduct) {
        return Settings.instance().get("envisatAuxDataPath") + File.separator + getDefaultAuxFileName(sourceProduct);
    }

    /**
     * Get default aux file name for the external calibration auxiliary data from DSD in the SPH.
     *
     * @return auxFileName The default auxiliary data file name.
     */
    static String getDefaultAuxFileName(Product sourceProduct) {

        MetadataElement dsd = sourceProduct.getMetadataRoot().getElement("DSD").getElement("DSD.17");
        if (dsd == null) {
            throw new OperatorException("DSD not found");
        }

        MetadataAttribute auxFileNameAttr = dsd.getAttribute("file_name");
        if (auxFileNameAttr == null) {
            throw new OperatorException("file_name not found");
        }
        String auxFileName = auxFileNameAttr.getData().getElemString();
        //System.out.println("aux file name is " + auxFileName);

        return auxFileName;
    }

    /**
     * Compute distance from satellite to the Earth center using satellite corrodinate in Metadata.
     */
    private void getSatelliteToEarthCenterDistance(Product sourceProduct) {

        lastLineIndex = new int[numMPPRecords];
        rSat = new double[numMPPRecords];

        MetadataElement mppAds = sourceProduct.getMetadataRoot().getElement("MAIN_PROCESSING_PARAMS_ADS");
        if (mppAds == null) {
            throw new OperatorException("MAIN_PROCESSING_PARAMS_ADS not found");
        }

        MetadataElement ads;
        for (int i = 0; i < numMPPRecords; i++) {

            if (numMPPRecords == 1) {
                ads = mppAds;
            } else {
                ads = mppAds.getElement("MAIN_PROCESSING_PARAMS_ADS." + (i+1));
            }

            MetadataAttribute numOutputLinesAttr = ads.getAttribute("num_output_lines");
            if (numOutputLinesAttr == null) {
                throw new OperatorException("num_output_lines not found");
            }
            int numLinesPerRecord = numOutputLinesAttr.getData().getElemInt();

            if (i == 0) {
                lastLineIndex[i] = numLinesPerRecord - 1;
            } else {
                lastLineIndex[i] = lastLineIndex[i - 1] + numLinesPerRecord;
            }

            MetadataAttribute xPositionAttr = ads.getAttribute("ASAR_Main_ADSR.sd/orbit_state_vectors.3.x_pos_1");
            if (xPositionAttr == null) {
                throw new OperatorException("x_pos_1 not found");
            }
            float x_pos = xPositionAttr.getData().getElemInt() / 100.0f; // divide 100 to convert unit from 10^-2 m to m
            //System.out.println("x position is " + x_pos);

            MetadataAttribute yPositionAttr = ads.getAttribute("ASAR_Main_ADSR.sd/orbit_state_vectors.3.y_pos_1");
            if (yPositionAttr == null) {
                throw new OperatorException("y_pos_1 not found");
            }
            float y_pos = yPositionAttr.getData().getElemInt() / 100.0f; // divide 100 to convert unit from 10^-2 m to m
            //System.out.println("y position is " + y_pos);

            MetadataAttribute zPositionAttr = ads.getAttribute("ASAR_Main_ADSR.sd/orbit_state_vectors.3.z_pos_1");
            if (zPositionAttr == null) {
                throw new OperatorException("z_pos_1 not found");
            }
            float z_pos = zPositionAttr.getData().getElemInt() / 100.0f; // divide 100 to convert unit from 10^-2 m to m
            //System.out.println("z position is " + z_pos);

            rSat[i] = Math.sqrt(x_pos * x_pos + y_pos * y_pos + z_pos * z_pos); // in m
            //System.out.println("Rsat is " + rSat[i]);
        }
    }

    /**
     * Set power coefficient used in range spreading loss compensation computation for slant range images.
     */
    private void setPowerInRangeSpreadingLossComp() {

        rangeSpreadingCompPower = 0.0;
        if (productType.contains("ASA_IMS_1P")) {
            rangeSpreadingCompPower = 3.0;
        } else if (productType.contains("ASA_APS_1P")) {
            rangeSpreadingCompPower = 4.0;
        }
    }

    /**
     * Create target product.
     */
    private void createTargetProduct() {

        targetProduct = new Product(sourceProduct.getName(),
                sourceProduct.getProductType(),
                sourceProduct.getSceneRasterWidth(),
                sourceProduct.getSceneRasterHeight());

        addSelectedBands();

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyFlagCodings(sourceProduct, targetProduct);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        updateTargetProductMetadata();
    }

    /**
     * Create Gamma image as a virtual band.
     * @param trgProduct The target product
     * @param outputImageScaleInDb flag if output is in dB
     */
    public static void createGammaVirtualBand(Product trgProduct, boolean outputImageScaleInDb) {

        int count=1;
        final Band[] bands = trgProduct.getBands();
        for(Band trgBand : bands) {

            final String unit = trgBand.getUnit();
            if (trgBand.isSynthetic() || (unit != null && unit.contains("phase"))) {
                continue;
            }

            final String trgBandName = trgBand.getName();
            final String expression;
            if (outputImageScaleInDb) {
                expression = "(pow(10," + trgBandName + "/10.0)" + " / cos(incident_angle * PI/180.0)) "
                        + "==0 ? 0 : 10 * log10(abs("
                        +"(pow(10," + trgBandName + "/10.0)" + " / cos(incident_angle * PI/180.0))"
                        +"))";
            } else {
                expression = trgBandName + " / cos(incident_angle * PI/180.0)";
            }
            String gammeBandName = "Gamma0";

            if(bands.length > 1) {
                if(trgBandName.contains("_HH"))
                    gammeBandName += "_HH";
                else if(trgBandName.contains("_VV"))
                    gammeBandName += "_VV";
                else if(trgBandName.contains("_HV"))
                    gammeBandName += "_HV";
                else if(trgBandName.contains("_VH"))
                    gammeBandName += "_VH";
            }
            if(outputImageScaleInDb) {
                gammeBandName += "_dB";
            }

            while(trgProduct.getBand(gammeBandName) != null) {
                gammeBandName += "_" + ++count;
            }
            
            final VirtualBand band = new VirtualBand(gammeBandName,
                    ProductData.TYPE_FLOAT64,
                    trgProduct.getSceneRasterWidth(),
                    trgProduct.getSceneRasterHeight(),
                    expression);
            band.setSynthetic(true);
            band.setUnit(unit);
            band.setDescription("Gamma0 image");
            trgProduct.addBand(band);
        }
    }

    /**
     * Create Beta image as a virtual band.
     * @param trgProduct The target product
     * @param outputImageScaleInDb flag if output is in dB
     */
    public static void createBetaVirtualBand(Product trgProduct, boolean outputImageScaleInDb) {

        int count=1;
        final Band[] bands = trgProduct.getBands();
        for(Band trgBand : bands) {

            final String unit = trgBand.getUnit();
            if (trgBand.isSynthetic() || (unit != null && unit.contains("phase"))) {
                continue;
            }
            
            final String trgBandName = trgBand.getName();
            final String expression;
            if (outputImageScaleInDb) {
                expression = "(pow(10," + trgBandName + "/10.0)" + " / sin(incident_angle * PI/180.0)) "
                        + "==0 ? 0 : 10 * log10(abs("
                        +"(pow(10," + trgBandName + "/10.0)" + " / sin(incident_angle * PI/180.0))"
                        +"))";
            } else {
                expression = trgBandName + " / sin(incident_angle * PI/180.0)";
            }
            String betaBandName = "Beta0";

            if(bands.length > 1) {
                if(trgBandName.contains("_HH"))
                    betaBandName += "_HH";
                else if(trgBandName.contains("_VV"))
                    betaBandName += "_VV";
                else if(trgBandName.contains("_HV"))
                    betaBandName += "_HV";
                else if(trgBandName.contains("_VH"))
                    betaBandName += "_VH";
            }
            if(outputImageScaleInDb) {
                betaBandName += "_dB";
            }

            while(trgProduct.getBand(betaBandName) != null) {
                betaBandName += "_" + ++count;
            }

            final VirtualBand band = new VirtualBand(betaBandName,
                    ProductData.TYPE_FLOAT64,
                    trgProduct.getSceneRasterWidth(),
                    trgProduct.getSceneRasterHeight(),
                    expression);
            band.setSynthetic(true);
            band.setUnit(unit);
            band.setDescription("Beta0 image");
            trgProduct.addBand(band);
        }
    }

    /**
     * Add the user selected bands to the target product.
     */
    private void addSelectedBands() {

        if (sourceBandNames == null || sourceBandNames.length == 0) {
            final Band[] bands = sourceProduct.getBands();
            final ArrayList<String> bandNameList = new ArrayList<String>(sourceProduct.getNumBands());
            for (Band band : bands) {
                bandNameList.add(band.getName());
            }
            sourceBandNames = bandNameList.toArray(new String[bandNameList.size()]);
        }

        final Band[] sourceBands = new Band[sourceBandNames.length];
        for (int i = 0; i < sourceBandNames.length; i++) {
            final String sourceBandName = sourceBandNames[i];
            final Band sourceBand = sourceProduct.getBand(sourceBandName);
            if (sourceBand == null) {
                throw new OperatorException("Source band not found: " + sourceBandName);
            }
            sourceBands[i] = sourceBand;
        }

        String targetBandName;
        for (int i = 0; i < sourceBands.length; i++) {

            final Band srcBand = sourceBands[i];
            final String unit = srcBand.getUnit();
            if(unit == null) {
                throw new OperatorException("band "+srcBand.getName()+" requires a unit");
            }

            String targetUnit = Unit.INTENSITY;

            if(unit.contains(Unit.DB)) {

                throw new OperatorException("Calibration of bands in dB is not supported");
            } else if (unit.contains(Unit.PHASE)) {

                final String[] srcBandNames = {srcBand.getName()};
                targetBandName = srcBand.getName();
                targetUnit = Unit.PHASE;
                if(targetProduct.getBand(targetBandName) == null) {
                    targetBandNameToSourceBandName.put(targetBandName, srcBandNames);
                }

            } else if (unit.contains(Unit.IMAGINARY)) {

                throw new OperatorException("Real and imaginary bands should be selected in pairs");

            } else if (unit.contains(Unit.REAL)) {
                if(i+1 >= sourceBands.length)
                    throw new OperatorException("Real and imaginary bands should be selected in pairs");

                final String nextUnit = sourceBands[i+1].getUnit();
                if (nextUnit == null || !nextUnit.contains(Unit.IMAGINARY)) {
                    throw new OperatorException("Real and imaginary bands should be selected in pairs");
                }
                final String[] srcBandNames = new String[2];
                srcBandNames[0] = srcBand.getName();
                srcBandNames[1] = sourceBands[i+1].getName();
                final String pol = OperatorUtils.getPolarizationFromBandName(srcBandNames[0]);
                if (pol != null) {
                    targetBandName = "Sigma0_" + pol.toUpperCase();
                } else {
                    targetBandName = "Sigma0";
                }
                if(outputImageScaleInDb) {
                    targetBandName += "_dB";
                }
                ++i;
                if(targetProduct.getBand(targetBandName) == null) {
                    targetBandNameToSourceBandName.put(targetBandName, srcBandNames);
                }

            } else {

                final String[] srcBandNames = {srcBand.getName()};
                final String pol = OperatorUtils.getPolarizationFromBandName(srcBandNames[0]);
                if (pol != null) {
                    targetBandName = "Sigma0_" + pol.toUpperCase();  
                } else
                    targetBandName = "Sigma0";
                if(outputImageScaleInDb) {
                    targetBandName += "_dB";
                }
                if(targetProduct.getBand(targetBandName) == null) {
                    targetBandNameToSourceBandName.put(targetBandName, srcBandNames);
                }
            }

            // add band only if it doesn't already exist
            if(targetProduct.getBand(targetBandName) == null) {
                final Band targetBand = new Band(targetBandName,
                                           ProductData.TYPE_FLOAT64,
                                           sourceProduct.getSceneRasterWidth(),
                                           sourceProduct.getSceneRasterHeight());

                if (outputImageScaleInDb && !targetUnit.equals(Unit.PHASE)) {
                    targetUnit = Unit.INTENSITY_DB;
                }
                targetBand.setUnit(targetUnit);
                targetProduct.addBand(targetBand);
            }
        }
    }

    /**
     * Update the metadata in the target product.
     */
    private void updateTargetProductMetadata() {

        final MetadataElement abs = AbstractMetadata.getAbstractedMetadata(targetProduct);

        if (sampleType.contains("COMPLEX")) {
            MetadataAttribute sampleTypeAttr = abs.getAttribute(AbstractMetadata.SAMPLE_TYPE);
            abs.removeAttribute(sampleTypeAttr);
            abs.addAttribute(new MetadataAttribute(AbstractMetadata.SAMPLE_TYPE, ProductData.createInstance("DETECTED"), false));
        }

        // update ant_elev_corr_flag
        if (!antElevCorrFlag) {
            abs.getAttribute(AbstractMetadata.ant_elev_corr_flag).getData().setElemBoolean(true);
        }

        // update range_spread_comp_flag
        if (!rangeSpreadCompFlag) {
            abs.getAttribute(AbstractMetadata.range_spread_comp_flag).getData().setElemBoolean(true);
        }

        // add abs_calibration_flag
        abs.getAttribute(AbstractMetadata.abs_calibration_flag).getData().setElemBoolean(true);

        if (extAuxFileAvailableFlag) {
            String auxFileName = externalAuxFile.getName();
            MetadataAttribute attr = abs.getAttribute(AbstractMetadata.external_calibration_file);
            abs.removeAttribute(attr);
            abs.addAttribute(new MetadataAttribute(AbstractMetadata.external_calibration_file,
                                                   ProductData.createInstance(auxFileName),
                                                   false));
        }
    }

    /**
     * Called by the framework in order to compute a tile for the given target band.
     * <p>The default implementation throws a runtime exception with the message "not implemented".</p>
     *
     * @param targetBand The target band.
     * @param targetTile The current tile associated with the target band to be computed.
     * @param pm         A progress monitor which should be used to determine computation cancelation requests.
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs during computation of the target raster.
     */
    @Override

    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        final Rectangle targetTileRectangle = targetTile.getRectangle();
        final int x0 = targetTileRectangle.x;
        final int y0 = targetTileRectangle.y;
        final int w = targetTileRectangle.width;
        final int h = targetTileRectangle.height;

        Band sourceBand1;
        Tile sourceRaster1;
        ProductData srcData1;
        ProductData srcData2 = null;

        final String[] srcBandNames = targetBandNameToSourceBandName.get(targetBand.getName());
        if (srcBandNames.length == 1) {
            sourceBand1 = sourceProduct.getBand(srcBandNames[0]);
            sourceRaster1 = getSourceTile(sourceBand1, targetTileRectangle, pm);
            srcData1 = sourceRaster1.getDataBuffer();
        } else {
            sourceBand1 = sourceProduct.getBand(srcBandNames[0]);
            final Band sourceBand2 = sourceProduct.getBand(srcBandNames[1]);
            sourceRaster1 = getSourceTile(sourceBand1, targetTileRectangle, pm);
            final Tile sourceRaster2 = getSourceTile(sourceBand2, targetTileRectangle, pm);
            srcData1 = sourceRaster1.getDataBuffer();
            srcData2 = sourceRaster2.getDataBuffer();
        }

        final Unit.UnitType bandUnit = Unit.getUnitType(sourceBand1);

        // copy band if unit is phase
        if(bandUnit == Unit.UnitType.PHASE) {
            targetTile.setRawSamples(sourceRaster1.getRawSamples());
            return;
        }

        final String pol = OperatorUtils.getPolarizationFromBandName(srcBandNames[0]);
        int prodBand = 0;
        if (pol != null && mdsPolar[1] != null && pol.contains(mdsPolar[1])) {
            prodBand = 1;
        }

        final ProductData trgData = targetTile.getDataBuffer();

        final int maxY = y0 + h;
        final int maxX = x0 + w;

        final float[] incidenceAnglesArray = new float[w];
        final float[] slantRangeTimeArray = new float[w];

        if (!antElevCorrFlag) {
            computeTargetTileNewAntPat(x0, y0, w, h, prodBand);
        }

        double sigma, dn, i, q, time;
        final double halfLightSpeedByRefSlantRange = halfLightSpeed / refSlantRange;
        final double theCalibrationFactor = calibrationFactor[prodBand];

        int index;
        for (int y = y0; y < maxY; ++y) {
            /*
            incidenceAngle.getPixels(x0, y, w, 1,
                incidenceAnglesArray, ProgressMonitor.NULL);
            */
            incidenceAngleQuadInterp.getPixelFloats(x0, y, w, 1,incidenceAnglesArray);

            if (!rangeSpreadCompFlag) {
                /*
                slantRangeTime.getPixels(x0, y, w, 1,
                    slantRangeTimeArray, ProgressMonitor.NULL);
                */
                slantRangeTimeQuadInterp.getPixelFloats(x0, y, w, 1,slantRangeTimeArray);
            }
            
            for (int x = x0, xx = 0; x < maxX; ++x, ++xx) {

                index = sourceRaster1.getDataBufferIndex(x, y);

                if (bandUnit == Unit.UnitType.AMPLITUDE) {
                    dn = srcData1.getElemDoubleAt(index);
                    sigma = dn*dn;
                } else if (bandUnit == Unit.UnitType.INTENSITY) {
                    sigma = srcData1.getElemDoubleAt(index);
                } else if (bandUnit == Unit.UnitType.REAL || bandUnit == Unit.UnitType.IMAGINARY) {
                    i = srcData1.getElemDoubleAt(index);
                    q = srcData2.getElemDoubleAt(index);
                    sigma = i * i + q * q;
                } else {
                    throw new OperatorException("ASAR Calibration: unhandled unit");
                }

                // apply calibration constant and incidence angle corrections
                sigma *= Math.sin(incidenceAnglesArray[xx] * MathUtils.DTOR) / theCalibrationFactor;

                if (!rangeSpreadCompFlag) { // apply range spreading loss compensation
                    time = slantRangeTimeArray[xx] / 1000000000.0; //convert ns to s
                    sigma *= Math.pow(time * halfLightSpeedByRefSlantRange, rangeSpreadingCompPower);
                }

                if (!antElevCorrFlag) { // apply antenna pattern correction
                    //gain = targetTileNewAntPat[xx];
                    //sigma /= gain * gain;
                    sigma /= targetTileNewAntPat[xx];
                }

                if (outputImageScaleInDb) { // convert calibration result to dB
                    if (sigma < underFlowFloat) {
                        sigma = -underFlowFloat;
                    } else {
                        sigma = 10.0 * Math.log10(sigma);
                    }
                }

                trgData.setElemDoubleAt(targetTile.getDataBufferIndex(x,y), sigma);
            }
        }
    }

    /**
     * Compute antenna elevation pattern gain for pixels in the middle row of the given tile.
     * Here it is assumed that the elevation angles for pixels in the same column are the same.
     *
     * @param x0 The x coordinate of the upper left point in the current tile.
     * @param y0 The y coordinate of the upper left point in the current tile.
     * @param w The width of the current tile.
     * @param h The height of the current tile.
     * @param band the band
     */
    private void computeTargetTileNewAntPat(int x0, int y0, int w, int h, int band) {

        final int y = y0 + h / 2;

        final float[] incidenceAnlglesArray = new float[w];
        final float[] slantRangeTimeArray = new float[w];
        /*
        incidenceAngle.getPixels(x0, y, w, 1,
                incidenceAnlglesArray, ProgressMonitor.NULL);
        */
        incidenceAngleQuadInterp.getPixelFloats(x0, y, w, 1,incidenceAnlglesArray);
        /*
        slantRangeTime.getPixels(x0, y, w, 1,
                slantRangeTimeArray, ProgressMonitor.NULL);
        */
        slantRangeTimeQuadInterp.getPixelFloats(x0, y, w, 1,slantRangeTimeArray);

        // set rsat
        double rsat = 0.0;
        for (int i = 0; i < numMPPRecords; i++) {
            if (y <= lastLineIndex[i]) {
                rsat = rSat[i];
                break;
            }
        }
        if (Double.compare(rsat, 0.0) == 0) {
            throw new OperatorException("No Main Processing Parameters ADSR for range line " + y);
        }

        targetTileNewAntPat = new double[w];
        final double delta = 0.05;

        for (int x = x0; x < x0 + w; x++) {

            // compute elevation angle for each pixel in the middle row
            //double alpha = incidenceAngle.getPixelFloat(x + 0.5f, y + 0.5f) * MathUtils.DTOR; // in radian
            final double alpha = incidenceAnlglesArray[x-x0] * MathUtils.DTOR; // in radian
            //double time = slantRangeTime.getPixelFloat(x + 0.5f, y + 0.5f) / 1000000000.0; //convert ns to s
            final double time = slantRangeTimeArray[x-x0] / 1000000000.0; //convert ns to s
            final double r = time * halfLightSpeed; // in m
            final double theta = (alpha - (float) Math.asin(Math.sin(alpha) * r / rsat)) * MathUtils.RTOD; // in degree

            // compute antenna pattern gain for the given pixel using linear interpolation

            final int k = (int) ((theta - elevationAngle + 5.0) / delta);
            final double theta1 = elevationAngle - 5.0 + k * delta;
            final double theta2 = theta1 + delta;
            final double gain1 = Math.pow(10.0, (double) newAntPat[band][k] / 10.0); // convert dB to linear scale
            final double gain2 = Math.pow(10.0, (double) newAntPat[band][k + 1] / 10.0);
            targetTileNewAntPat[x - x0] = ((theta2 - theta) * gain1 + (theta - theta1) * gain2) / (theta2 - theta1);

            /*
            System.out.println("Reference elevation angle is " + elevationAngle);
            System.out.println("Pixel elevation angle is " + theta);
            System.out.println("theta1 = " + theta1);
            System.out.println("theta2 = " + theta2);
            System.out.println("gain1 = " + pattern[k]);
            System.out.println("gain2 = " + pattern[k+1]);
            System.out.println("gain = " + targetTileNewAntPat[x - x0]);
            */
        }
    }

    /**
     * The SPI is used to register this operator in the graph processing framework
     * via the SPI configuration file
     * {@code META-INF/services/org.esa.beam.framework.gpf.OperatorSpi}.
     * This class may also serve as a factory for new operator instances.
     *
     * @see OperatorSpi#createOperator()
     * @see OperatorSpi#createOperator(java.util.Map, java.util.Map)
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(ASARCalibrationOperator.class);
        }
    }
}
