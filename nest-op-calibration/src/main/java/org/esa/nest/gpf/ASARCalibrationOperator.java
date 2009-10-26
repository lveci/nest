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
import org.esa.nest.util.Constants;
import org.esa.nest.util.GeoUtils;
import org.esa.nest.util.Settings;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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

    @Parameter(description = "The antenne elevation pattern gain auxiliary data file.", label="External Aux File")
    private File externalAuxFile = null;

    @Parameter(description = "Output image scale", defaultValue = "false", label="Scale in dB")
    private boolean outputImageScaleInDb = false;

    @Parameter(description = "Create gamma0 virtual band", defaultValue = "false", label="Create gamma0 virtual band")
    private boolean createGammaBand = false;

    @Parameter(description = "Create beta0 virtual band", defaultValue = "false", label="Create beta0 virtual band")
    private boolean createBetaBand = false;

    protected MetadataElement absRoot = null;

    private String productType = null;
    private String sampleType = null;
    private String xcaFileName = null; // XCA file for radiometric calibration
    private String xcaFilePath = null; // absolute path for XCA file
    protected final String[] mdsPolar = new String[2]; // polarizations for the two bands in the product

    protected TiePointGrid incidenceAngle = null;
    protected TiePointGrid slantRangeTime = null;
    protected TiePointGrid latitude = null;
    protected TiePointGrid longitude = null;

    private boolean extAuxFileAvailableFlag = false;
    private boolean antElevCorrFlag = false;
    private boolean rangeSpreadCompFlag = false;

    private double firstLineUTC = 0.0; // in days
    private double lineTimeInterval = 0.0; // in days
    private double avgSceneHeight = 0.0; // in m
    private double rangeSpreadingCompPower; // power in range spreading loss compensation calculation
    private final double[] calibrationFactor = new double[2]; // calibration constants corresponding to 2 bands in product
    private double[] refElevationAngle = null; // reference elevation angles, in degree
    private double[] targetTileNewAntPat = null; // antenna pattern for a given tile, in linear scale
    private float[][] antennaPatternSingleSwath = null; // antenna pattern for two bands for single swath product, in dB
    private float[][] antennaPatternWideSwath = null; // antenna patterm for 5 sub swathes for wide swath product, in dB

    protected int numMPPRecords; // number of MPP ADSR records
    protected int[] lastLineIndex = null; // the index of the last line covered by each MPP ADSR record
    protected String swath;
    private AbstractMetadata.OrbitStateVector[] orbitStateVectors = null;

    private static final int numOfGains = 201; // number of antenna pattern gain values for a given swath and
                                               // polarization in the aux file
    private static final double refSlantRange = 800000.0; //  m
    protected static final double halfLightSpeedByRefSlantRange = Constants.halfLightSpeed / refSlantRange;
    protected static final double underFlowFloat = 1.0e-30;
    private final HashMap<String, String[]> targetBandNameToSourceBandName = new HashMap<String, String[]>(2);

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

            absRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct);

            getProductType();

            getSampleType();

            getProductSwath();

            getProductPolarization();

            getCalibrationFlags();

            numMPPRecords = getNumOfRecordsInMainProcParam(sourceProduct);  //???

            getTiePointGridData(sourceProduct);

            getXCAFile();

            getCalibrationFactor();

            if (!antElevCorrFlag) {

                getFirstLineTime();

                getLineTimeInterval();

                getAverageSceneHeight();

                getOrbitStateVectors();

                getAntennaPatternGain();
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

            //targetProduct.setPreferredTileSize(targetProduct.getSceneRasterWidth(), 5);

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
     * @throws Exception The exceptions.
     */
    private void getProductType() throws Exception {

        productType = sourceProduct.getProductType();
        if (!productType.equals("ASA_IMP_1P") && !productType.equals("ASA_IMM_1P") &&
            !productType.equals("ASA_APP_1P") && !productType.equals("ASA_APM_1P") &&
            !productType.equals("ASA_WSM_1P") && !productType.equals("ASA_IMG_1P") &&
            !productType.equals("ASA_APG_1P") && !productType.equals("ASA_IMS_1P") &&
            !productType.equals("ASA_APS_1P")) {

            throw new OperatorException(productType + " is not a valid product ID.");
        }
    }

    /**
     * Get sample type.
     * @throws Exception The exceptions.
     */
    private void getSampleType() throws Exception {
        sampleType = absRoot.getAttributeString(AbstractMetadata.SAMPLE_TYPE);
    }

    /**
     * Get product swath.
     * @throws Exception The exceptions.
     */
    private void getProductSwath() throws Exception {
        swath = absRoot.getAttributeString(AbstractMetadata.SWATH);
    }

    /**
     * Get product polarizations for each band in the product.
     * @throws Exception The exceptions.
     */
    private void getProductPolarization() throws Exception {

        String polarName = absRoot.getAttributeString(AbstractMetadata.mds1_tx_rx_polar);
        mdsPolar[0] = null;
        if (polarName.contains("HH") || polarName.contains("HV") || polarName.contains("VH") || polarName.contains("VV")) {
            mdsPolar[0] = polarName.toLowerCase();
        }

        mdsPolar[1] = null;
        polarName = absRoot.getAttributeString(AbstractMetadata.mds2_tx_rx_polar);
        if (polarName.contains("HH") || polarName.contains("HV") || polarName.contains("VH") || polarName.contains("VV")) {
            mdsPolar[1] = polarName.toLowerCase();
        }
    }

    /**
     * Get antenna elevation correction flag and range spreading compensation flag from Metadata.
     * @throws Exception The exceptions.
     */
    private void getCalibrationFlags() throws Exception {

        if (AbstractMetadata.getAttributeBoolean(absRoot, AbstractMetadata.abs_calibration_flag)) {
            throw new OperatorException("The product has already been calibrated.");
        }

        antElevCorrFlag = AbstractMetadata.getAttributeBoolean(absRoot, AbstractMetadata.ant_elev_corr_flag);
        rangeSpreadCompFlag = AbstractMetadata.getAttributeBoolean(absRoot, AbstractMetadata.range_spread_comp_flag);

        if (!antElevCorrFlag || !rangeSpreadCompFlag) {
            if (!productType.equals("ASA_IMS_1P") && !productType.equals("ASA_APS_1P")) {
                throw new OperatorException("Antenna pattern correction or range spreading compensation" +
                        " has not been applied to the ground detected source product.");
            }
        }
    }

    /**
     * Get number of records in Main Processing Params data set.
     * @param sourceProduct The source prodict.
     * @return The number of records.
     * @throws OperatorException The exceptions.
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
     * Get incidence angle and slant range time tie point grids.
     * @param sourceProduct the source
     */
    private void getTiePointGridData(Product sourceProduct) {
        slantRangeTime = OperatorUtils.getSlantRangeTime(sourceProduct);
        incidenceAngle = OperatorUtils.getIncidenceAngle(sourceProduct);
        latitude = OperatorUtils.getLatitude(sourceProduct);
        longitude = OperatorUtils.getLongitude(sourceProduct);
    }

    private void getXCAFile() throws Exception {

        if (extAuxFileAvailableFlag && externalAuxFile.exists()) {
            xcaFileName = externalAuxFile.getName();
            xcaFilePath = externalAuxFile.getAbsolutePath();
        } else {
            final Date startDate = sourceProduct.getStartTime().getAsDate();
            final Date endDate = sourceProduct.getEndTime().getAsDate();
            final File xcaFileDir = new File(Settings.instance().get("AuxData/envisatAuxDataPath"));
            xcaFileName = findXCAFile(xcaFileDir, startDate, endDate);
            xcaFilePath = xcaFileDir.toString() + File.separator + xcaFileName;
        }

        if (xcaFileName == null) {
            throw new OperatorException("No proper XCA file has been found");
        }
    }

    /**
     * Find the latest XVA file available.
     * @param xcaFileDir The complete path to the XCA file directory.
     * @param productStartDate The product start date.
     * @param productEndDate The product end data.
     * @return The name of the XCA file found.
     * @throws Exception The exceptions.
     */
    public static String findXCAFile(File xcaFileDir, Date productStartDate, Date productEndDate) throws Exception {

        final File[] list = xcaFileDir.listFiles();
        if(list == null) {
            return null;
        }

        final SimpleDateFormat dateformat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        Date latestCreationDate = dateformat.parse("19000101_000000");
        String xcaFileName = null;

        for(File f : list) {

            final String fileName = f.getName();
            if (fileName.length() < 61 || !fileName.substring(0,10).equals("ASA_XCA_AX")) {
                continue;
            }
            final Date creationDate = dateformat.parse(fileName.substring(14, 29));
            final Date validStartDate = dateformat.parse(fileName.substring(30, 45));
            final Date validStopDate = dateformat.parse(fileName.substring(46, 61));

            if (productStartDate.after(validStartDate) && productEndDate.before(validStopDate) &&
                latestCreationDate.before(creationDate)) {

                latestCreationDate = creationDate;
                xcaFileName = fileName;
            }
        }
        return xcaFileName;
    }

    /**
     * Get calibration factor.
     */
    private void getCalibrationFactor() {

        if (xcaFilePath != null) {
            getCalibrationFactorFromExternalAuxFile(xcaFilePath, swath, mdsPolar, productType, calibrationFactor);
        } else {
            getCalibrationFactorFromMetadata();
        }
    }

    /**
     * Get calibration factor from user specified auxiliary data.
     * @param auxFilePath The absolute path to the aux file.
     * @param swath The product swath.
     * @param mdsPolar The product polarizations.
     * @param productType The product type.
     * @param calibrationFactor The calibration factors.
     */
    public static void getCalibrationFactorFromExternalAuxFile(
            String auxFilePath, String swath, String[] mdsPolar, String productType, double[] calibrationFactor) {

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
                    if (swath.contains("IS1")) {
                        calibrationFactor[i] = factors[0];
                    } else if (swath.contains("IS2")) {
                        calibrationFactor[i] = factors[1];
                    } else if (swath.contains("IS3")) {
                        calibrationFactor[i] = factors[2];
                    } else if (swath.contains("IS4")) {
                        calibrationFactor[i] = factors[3];
                    } else if (swath.contains("IS5")) {
                        calibrationFactor[i] = factors[4];
                    } else if (swath.contains("IS6")) {
                        calibrationFactor[i] = factors[5];
                    } else if (swath.contains("IS7")) {
                        calibrationFactor[i] = factors[6];
                    } else {
                        throw new OperatorException("Invalid swath");
                    }
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
     * Get orbit state vectors from the abstracted metadata.
     * @throws Exception The exceptions.
     */
    private void getOrbitStateVectors() throws Exception {
        orbitStateVectors = AbstractMetadata.getOrbitStateVectors(absRoot);
    }

    /**
     * Get first line time from the abstracted metadata (in days).
     * @throws Exception The exceptions.
     */
    private void getFirstLineTime() throws Exception {
        firstLineUTC = absRoot.getAttributeUTC(AbstractMetadata.first_line_time).getMJD(); // in days
    }

    /**
     * Get line time interval from the abstracted metadata (in days).
     * @throws Exception The exceptions.
     */
    private void getLineTimeInterval() throws Exception {
        lineTimeInterval = absRoot.getAttributeDouble(AbstractMetadata.line_time_interval) / 86400.0; // s to day
    }

    /**
     * Get average scene height from abstracted metadata.
     * @throws Exception The exceptions.
     */
    private void getAverageSceneHeight() throws Exception {
        avgSceneHeight = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.avg_scene_height);
    }

    /**
     * Get antenna pattern from aux file for each band in the product.
     */
    private void getAntennaPatternGain() {

        if (!productType.equals("ASA_IMS_1P") && !productType.equals("ASA_APS_1P")) {
            throw new OperatorException("Found ground detected product without antenna pattern correction.");
        }

        if (swath.contains("WS")) {
            refElevationAngle = new double[5]; // reference elevation angles for 5 sub swathes
            antennaPatternWideSwath = new float[5][numOfGains]; // antenna pattern gain for 5 sub swathes
            getWideSwathAntennaPatternGainFromAuxData(
                    xcaFilePath, mdsPolar[0], numOfGains, refElevationAngle, antennaPatternWideSwath);
        } else {
            refElevationAngle = new double[1]; // reference elevation angle for 1 swath
            antennaPatternSingleSwath = new float[2][numOfGains];  // antenna pattern gain for 2 bands
            getSingleSwathAntennaPatternGainFromAuxData(
                    xcaFilePath, swath, mdsPolar, numOfGains, refElevationAngle, antennaPatternSingleSwath);
        }
    }

    /**
     * Get reference elevation angle and antenna pattern gain from auxiliary file for single swath product.
     * @param fileName The auxiliary data file name
     * @param swath The swath name.
     * @param pol The polarizations for 2 bands.
     * @param numOfGains The number of gains for given swath and polarization (201).
     * @param refElevAngle The reference elevation angle array.
     * @param antPatArray The antenna pattern array.
     * @throws OperatorException The IO exception.
     */
     public static void getSingleSwathAntennaPatternGainFromAuxData(
            String fileName, String swath, String[] pol, int numOfGains, double[] refElevAngle, float[][] antPatArray)
            throws OperatorException {

        final EnvisatAuxReader reader = new EnvisatAuxReader();
        try {
            reader.readProduct(fileName);

            String swathName;
            if (swath.contains("IS1")) {
                swathName = "is1";
            } else if (swath.contains("IS2")) {
                swathName = "is2";
            } else if (swath.contains("IS3")) {
                swathName = "is3_ss2";
            } else if (swath.contains("IS4")) {
                swathName = "is4_ss3";
            } else if (swath.contains("IS5")) {
                swathName = "is5_ss4";
            } else if (swath.contains("IS6")) {
                swathName = "is6_ss5";
            } else if (swath.contains("IS7")) {
                swathName = "is7";
            } else {
                throw new OperatorException("Invalid swath");
            }

            final String refElevAngleName = "elev_ang_" + swathName;
            final ProductData refElevAngleData = reader.getAuxData(refElevAngleName);
            refElevAngle[0] = (double) refElevAngleData.getElemFloat();

            final String patternName = "pattern_" + swathName;
            final ProductData patternData = reader.getAuxData(patternName);
            final float[] pattern = ((float[]) patternData.getElems());
            if (pattern.length != 804) {
                throw new OperatorException("Incorret array length for " + patternName);
            }

            for (int i = 0; i < 2 && pol[i] != null && pol[i].length() != 0; i++) {
                if (pol[i].contains("hh")) {
                    System.arraycopy(pattern, 0, antPatArray[i], 0, numOfGains);
                } else if (pol[i].contains("vv")) {
                    System.arraycopy(pattern, numOfGains, antPatArray[i], 0, numOfGains);
                } else if (pol[i].contains("hv")) {
                    System.arraycopy(pattern, 2 * numOfGains, antPatArray[i], 0, numOfGains);
                } else if (pol[i].contains("vh")) {
                    System.arraycopy(pattern, 3 * numOfGains, antPatArray[i], 0, numOfGains);
                }
            }

        } catch (IOException e) {
            throw new OperatorException(e);
        }
    }

    /**
     * Get reference elevation angle and antenna pattern gain from auxiliary file for wide swath product.
     * @param fileName The auxiliary data file name
     * @param pol The polarization.
     * @param numOfGains The number of gains for given swath and polarization (201).
     * @param refElevAngle The reference elevation angle array.
     * @param antPatArray The antenna pattern array.
     * @throws OperatorException The IO exception.
     */
    public static void getWideSwathAntennaPatternGainFromAuxData(
            String fileName, String pol, int numOfGains, double[] refElevAngle, float[][] antPatArray)
            throws OperatorException {

        final EnvisatAuxReader reader = new EnvisatAuxReader();
        try {
            reader.readProduct(fileName);

            String[] swathName = {"ss1", "is3_ss2", "is4_ss3", "is5_ss4", "is6_ss5"};

            for (int i = 0; i < swathName.length; i++) {

                // read elevation angles
                final String refElevAngleName = "elev_ang_" + swathName[i];
                final ProductData refElevAngleData = reader.getAuxData(refElevAngleName);
                refElevAngle[i] = (double) refElevAngleData.getElemFloat();

                // read antenna pattern gains
                final String patternName = "pattern_" + swathName[i];
                final ProductData patternData = reader.getAuxData(patternName);
                final float[] pattern = ((float[]) patternData.getElems());
                if (pattern.length != 804) {
                    throw new OperatorException("Incorret array length for " + patternName);
                }

                if (pol.contains("hh")) {
                    System.arraycopy(pattern, 0, antPatArray[i], 0, numOfGains);
                } else if (pol.contains("vv")) {
                    System.arraycopy(pattern, numOfGains, antPatArray[i], 0, numOfGains);
                } else if (pol.contains("hv")) {
                    System.arraycopy(pattern, 2 * numOfGains, antPatArray[i], 0, numOfGains);
                } else if (pol.contains("vh")) {
                    System.arraycopy(pattern, 3 * numOfGains, antPatArray[i], 0, numOfGains);
                }
            }
        } catch (IOException e) {
            throw new OperatorException(e);
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
                    ProductData.TYPE_FLOAT32,
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
                    ProductData.TYPE_FLOAT32,
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
                } else {
                    targetBandName = "Sigma0";
                }

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
                                           ProductData.TYPE_FLOAT32,
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

        final MetadataElement tgtAbsRoot = AbstractMetadata.getAbstractedMetadata(targetProduct);

        if (sampleType.contains("COMPLEX")) {
            AbstractMetadata.setAttribute(tgtAbsRoot, AbstractMetadata.SAMPLE_TYPE, "DETECTED");
        }

        if (!antElevCorrFlag) {
            AbstractMetadata.setAttribute(tgtAbsRoot, AbstractMetadata.ant_elev_corr_flag, 1);
        }

        if (!rangeSpreadCompFlag) {
            AbstractMetadata.setAttribute(tgtAbsRoot, AbstractMetadata.range_spread_comp_flag, 1);
        }

        AbstractMetadata.setAttribute(tgtAbsRoot, AbstractMetadata.abs_calibration_flag, 1);

        AbstractMetadata.setAttribute(tgtAbsRoot, AbstractMetadata.external_calibration_file, xcaFileName);
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
        //System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

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
            if (swath.contains("WS")) {
                computeWideSwathAntennaPatternForCurrentTile(x0, y0, w, h);
            } else {
                computeSingleSwathAntennaPatternForCurrentTile(x0, y0, w, h, prodBand);
            }
        }

        double sigma, dn, i, q, time;
        final double theCalibrationFactor = calibrationFactor[prodBand];

        int index;
        for (int y = y0; y < maxY; ++y) {

            incidenceAngle.getPixels(x0, y, w, 1,incidenceAnglesArray, pm, TiePointGrid.InterpMode.QUADRATIC);

            if (!rangeSpreadCompFlag) {
                slantRangeTime.getPixels(x0, y, w, 1,slantRangeTimeArray, pm, TiePointGrid.InterpMode.QUADRATIC);
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
                    sigma /= targetTileNewAntPat[xx] * targetTileNewAntPat[xx];
                }

                if (outputImageScaleInDb) { // convert calibration result to dB
                    if (sigma < underFlowFloat) {
                        sigma = -underFlowFloat;
                    } else {
                        sigma = 10.0 * Math.log10(sigma);
                    }
                }

                trgData.setElemDoubleAt(targetTile.getDataBufferIndex(x, y), sigma);
            }
        }
    }

    /**
     * Compute antenna pattern for the middle row of the given tile for single swath product.
     * Here it is assumed that the elevation angles for pixels in the same column are the same.
     * @param x0 The x coordinate of the upper left point in the current tile.
     * @param y0 The y coordinate of the upper left point in the current tile.
     * @param w The width of the current tile.
     * @param h The height of the current tile.
     * @param band The band index.
     */
    private void computeSingleSwathAntennaPatternForCurrentTile(int x0, int y0, int w, int h, int band) {

        final int y = y0 + h / 2;
        final double zeroDopplerTime = firstLineUTC + y*lineTimeInterval;
        double satelitteHeight = computeSatelliteHeight(zeroDopplerTime, orbitStateVectors);

        targetTileNewAntPat = new double[w];
        for (int x = x0; x < x0 + w; x++) {
            final double slantRange = computeSlantRange(x, y); // in m
            final double earthRadius = computeEarthRadius(latitude.getPixelFloat(x,y), longitude.getPixelFloat(x,y)); // in m
            final double theta = computeElevationAngle(slantRange, satelitteHeight, avgSceneHeight + earthRadius); // in degree
            targetTileNewAntPat[x - x0] = computeAntPatGain(theta, refElevationAngle[0], antennaPatternSingleSwath[band]);
        }
    }

    /**
     * Compute antenna pattern for the middle row of the given tile for wide swath product.
     * Here it is assumed that the elevation angles for pixels in the same column are the same.
     * @param x0 The x coordinate of the upper left point in the current tile.
     * @param y0 The y coordinate of the upper left point in the current tile.
     * @param w The width of the current tile.
     * @param h The height of the current tile.
     */
    private void computeWideSwathAntennaPatternForCurrentTile(int x0, int y0, int w, int h) {

        final int y = y0 + h / 2;
        final double zeroDopplerTime = firstLineUTC + y*lineTimeInterval;
        double satelitteHeight = computeSatelliteHeight(zeroDopplerTime, orbitStateVectors);

        targetTileNewAntPat = new double[w];
        for (int x = x0; x < x0 + w; x++) {
            final double slantRange = computeSlantRange(x, y); // in m
            final double earthRadius = computeEarthRadius(latitude.getPixelFloat(x,y), longitude.getPixelFloat(x,y)); // in m
            final double theta = computeElevationAngle(slantRange, satelitteHeight, avgSceneHeight + earthRadius); // in degree

            final int subSwathIndex = findSubSwath(theta, refElevationAngle);
            targetTileNewAntPat[x - x0] = computeAntPatGain(
                    theta, refElevationAngle[subSwathIndex], antennaPatternWideSwath[subSwathIndex]);
        }
    }

    /**
     * Find the sub swath index for given elevation angle.
     * @param theta The elevation angle.
     * @param refElevationAngle The reference elevation array.
     * @return The sub swath index.
     */
    public static int findSubSwath(double theta, double[] refElevationAngle) {
        // The method below finds the nearest reference elevation angle to the given elevation angle theta.
        // The method is equivalent to the one proposed by Romain in his email dated April 28, 2009, in which
        // middle point of the overlapped area of two adjacent sub swathes is used as boundary of sub swath.
        int idx = -1;
        double min = 360.0;
        for (int i = 0 ; i < refElevationAngle.length; i++) {
            double d = Math.abs(theta - refElevationAngle[i]);
            if (d < min) {
                min = d;
                idx = i;
            }
        }
        return idx;
    }

    /**
     * Compute antenna pattern gains for the given elevation angle using linear interpolation.
     *
     * @param elevAngle The elevation angle (in degree) of a given pixel.
     * @param refElevationAngle The reference elevation angle (in degree).
     * @param antPatArray The antenna pattern array.
     * @return The antenna pattern gain (in linear scale).
     */
    public static double computeAntPatGain(double elevAngle, double refElevationAngle, float[] antPatArray) {

        final double delta = 0.05;
        int k0 = (int) ((elevAngle - refElevationAngle + 5.0) / delta);
        if (k0 < 0) {
            k0 = 0;
        } else if (k0 >= antPatArray.length - 1) {
            k0 = antPatArray.length - 2;
        }
        final double theta0 = refElevationAngle - 5.0 + k0*delta;
        final double theta1 = theta0 + delta;
        final double gain0 = Math.pow(10, (double) antPatArray[k0] / 10.0); // convert dB to linear scale
        final double gain1 = Math.pow(10, (double) antPatArray[k0+1] / 10.0);
        final double mu = (elevAngle - theta0) / (theta1 - theta0);

        return org.esa.nest.util.MathUtils.interpolationLinear(gain0, gain1, mu);
    }

    //============================================================================================================
    /**
     * Compute slant range for given pixel.
     * @param x The x coordinate of the pixel in the source image.
     * @param y The y coordinate of the pixel in the source image.
     * @return The slant range (in meters).
     */
    private double computeSlantRange(int x, int y) {
        final double time = slantRangeTime.getPixelFloat((float)x, (float)y) / 1000000000.0; //convert ns to s
        return time * Constants.halfLightSpeed; // in m
    }

    /**
     * Compute distance from satelitte to the Earth centre (in meters).
     * @param zeroDopplerTime The zero Doppler time (in days).
     * @param orbitStateVectors The orbit state vectors.
     * @return The distance.
     */
    public static double computeSatelliteHeight(double zeroDopplerTime, AbstractMetadata.OrbitStateVector[] orbitStateVectors) {

        // todo should use the 3rd state vector as suggested by the doc?
        int idx = 0;
        for (int i = 0; i < orbitStateVectors.length && zeroDopplerTime >= orbitStateVectors[i].time_mjd; i++) {
            idx = i;
        }
        final double xPos = orbitStateVectors[idx].x_pos;
        final double yPos = orbitStateVectors[idx].y_pos;
        final double zPos = orbitStateVectors[idx].z_pos;
        return Math.sqrt(xPos*xPos + yPos*yPos + zPos*zPos);
    }

    /**
     * Compute Earth radius (in meters) for given pixel in source image.
     * @param lat The latitude of a given pixel in source image.
     * @param lon The longitude of a given pixel in source image.
     * @return The Earth radius.
     */
    public static double computeEarthRadius(float lat, float lon) {
        final double[] xyz = new double[3];
        GeoUtils.geo2xyz(lat, lon, 0.0, xyz, GeoUtils.EarthModel.WGS84);
        return Math.sqrt(xyz[0]*xyz[0] + xyz[1]*xyz[1] + xyz[2]*xyz[2]);
    }

    /**
     * Compute elevation angle (in degree).
     * @param slantRange The slant range (in meters).
     * @param satelliteHeight The distance from satelitte to the Earth centre (in meters).
     * @param sceneToEarthCentre The distance from the backscatter element to the Earth centre (in meters).
     * @return The elevation angle.
     */
    public static double computeElevationAngle(double slantRange, double satelliteHeight, double sceneToEarthCentre) {

        return Math.acos((slantRange*slantRange + satelliteHeight*satelliteHeight -
               (sceneToEarthCentre)*(sceneToEarthCentre))/(2*slantRange*satelliteHeight))*MathUtils.RTOD;
    }

    /**
    * Set the XCA file name.
    * This function is used by unit test only.
    * @param xcaFileName The XCA file name.
    */
    public void setExternalAntennaPatternFile(String xcaFileName) {

        String path = Settings.instance().get("AuxData/envisatAuxDataPath") + File.separator + xcaFileName;
        externalAuxFile = new File(path);
        if (!externalAuxFile.exists()) {
            throw new OperatorException("External antenna pattern file for unit test does not exist");
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
