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
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.dataop.dem.ElevationModelRegistry;
import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.resamp.Resampling;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.framework.dataop.maptransf.MapInfo;
import org.esa.beam.framework.dataop.maptransf.MapProjectionRegistry;
import org.esa.beam.framework.dataop.maptransf.IdentityTransformDescriptor;
import org.esa.beam.util.ProductUtils;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.util.MathUtils;
import org.esa.nest.util.GeoUtils;
import org.esa.nest.util.Constants;
import org.esa.nest.util.Settings;
import org.esa.nest.dataio.ReaderUtils;

import java.awt.*;
import java.util.*;
import java.io.IOException;
import java.io.File;
import java.text.SimpleDateFormat;

/**
 * Raw SAR images usually contain significant geometric distortions. One of the factors that cause the
 * distortions is the ground elevation of the targets. This operator corrects the topographic distortion
 * in the raw image caused by this factor. The operator implements the Range-Doppler (RD) geocoding method.
 *
 * The method consis of the following major steps:
 * (1) Get state vectors from the metadata;
 * (2) Compute satellite position and velocity for each azimuth time by interpolating the state vectors;
 * (3) Get corner latitudes and longitudes for the source image;
 * (4) Compute [LatMin, LatMax] and [LonMin, LonMax];
 * (5) Get the range and azimuth spacings for the source image;
 * (6) Compute DEM traversal sample intervals (delLat, delLon) based on source image pixel spacing;
 * (7) Compute target geocoded image dimension;
 * (8) Repeat the following steps for each sample in the target raster [LatMax:-delLat:LatMin]x[LonMin:delLon:LonMax]:
 * (8.1) Get local elevation h(i,j) for current sample given local latitude lat(i,j) and longitude lon(i,j);
 * (8.2) Convert (lat(i,j), lon(i,j), h(i,j)) to global Cartesian coordinates p(Px, Py, Pz);
 * (8.3) Compute zero Doppler time t(i,j) for point p(Px, Py, Pz) using Doppler frequency function;
 * (8.4) Compute satellite position s(i,j) and slant range r(i,j) = |s(i,j) - p| for zero Doppler time t(i,j);
 * (8.5) Compute bias-corrected zero Doppler time tc(i,j) = t(i,j) + r(i,j)*2/c, where c is the light speed;
 * (8.6) Update satellite position s(tc(i,j)) and slant range r(tc(i,j)) = |s(tc(i,j)) - p| for time tc(i,j);
 * (8.7) Compute azimuth image index Ia using zero Doppler time tc(i,j);
 * (8.8) Compute range image index Ir using slant range r(tc(i,j)) or ground range;
 * (8.9) Compute pixel value x(Ia,Ir) using interpolation and save it for current sample.
 *
 * Reference: Guide to ASAR Geocoding, Issue 1.0, 19.03.2008
 */

@OperatorMetadata(alias="Automated-Terrain-Correction", description="Orthorectification with SAR simulation")
public class AutomatedTerrainCorrectionOp extends Operator {

    @SourceProduct(alias="source")
    protected Product sourceProduct;
    @TargetProduct
    protected Product targetProduct;

    @Parameter(description = "The RMS threshold for eliminating invalid GCPs", interval = "(0, *)", defaultValue = "1.0",
                label="RMS Threshold")
    private float rmsThreshold = 1.0f;

    @Parameter(description = "The order of WARP polynomial function", valueSet = {"1", "2", "3"}, defaultValue = "1",
                label="Warp Polynomial Order")
    private int warpPolynomialOrder = 1;

    @Parameter(valueSet = {NEAREST_NEIGHBOUR, BILINEAR, CUBIC}, defaultValue = BILINEAR, label="DEM Resampling Method")
    private String demResamplingMethod = BILINEAR;

    @Parameter(valueSet = {NEAREST_NEIGHBOUR, BILINEAR, CUBIC}, defaultValue = BILINEAR, label="Image Resampling Method")
    private String imgResamplingMethod = BILINEAR;

    @Parameter(defaultValue="false", label="Save DEM as band")
    private boolean saveDEM = false;

    @Parameter(defaultValue="false", label="Save local incidence angle as band")
    private boolean saveLocalIncidenceAngle = false;

    @Parameter(defaultValue="false", label="Save projected local incidence angle as band")
    private boolean saveProjectedLocalIncidenceAngle = false;

    @Parameter(defaultValue="false", label="Apply radiometric calibration")
    private boolean applyRadiometricCalibration = false;

    private ProductNodeGroup<Pin> masterGCPGroup = null;
    private MetadataElement absRoot = null;
    private ElevationModel dem = null;
    private FileElevationModel fileElevationModel = null;
    private TiePointGrid latitude = null;
    private TiePointGrid longitude = null;
    private WarpOp.WarpData warpData = null;

    private boolean srgrFlag = false;
    private boolean multilookFlag = false;
    private boolean retroCalibrationFlag = false;
    private boolean wideSwathProductFlag = false;
    private boolean listedASARProductFlag = false;

    private String mission = null;
    private String swath = null;
    private String productType = null;
    private String[] mdsPolar = new String[2]; // polarizations for the two bands in the product
    private String newXCAFileName = null; // XCA file for radiometric calibration
    private String demName = null;

    private int sourceImageWidth = 0;
    private int sourceImageHeight = 0;
    private int targetImageWidth = 0;
    private int targetImageHeight = 0;

    private double avgSceneHeight = 0.0; // in m
    private double rangeSpreadingCompPower = 0.0;
    private double halfRangeSpreadingCompPower = 0.0;
    private double wavelength = 0.0; // in m
    private double rangeSpacing = 0.0;
    private double azimuthSpacing = 0.0;
    private double firstLineUTC = 0.0; // in days
    private double lastLineUTC = 0.0; // in days
    private double lineTimeInterval = 0.0; // in days
    private double nearEdgeSlantRange = 0.0; // in m
    private double demNoDataValue = 0.0; // no data value for DEM
    private double latMin = 0.0;
    private double latMax = 0.0;
    private double lonMin = 0.0;
    private double lonMax= 0.0;
    private double delLat = 0.0;
    private double delLon = 0.0;
    private double oldSatelliteHeight; // satellite to earth centre distance used in previous calibration, in m

    private double[][] sensorPosition = null; // sensor position for all range lines
    private double[][] sensorVelocity = null; // sensor velocity for all range lines
    private double[] timeArray = null;
    private double[] xPosArray = null;
    private double[] yPosArray = null;
    private double[] zPosArray = null;
    private double[] newCalibrationConstant = new double[2];
    private double[] oldRefElevationAngle = null; // reference elevation angle for given swath in old aux file, in degree
    private double[] newRefElevationAngle = null; // reference elevation angle for given swath in new aux file, in degree
    private double[] srgrConvParamsTime = null;
    private double[] earthRadius = null; // Earth radius for all range lines, in m

    private float[][] oldSlantRange = null; // old slant ranges for one range line, in m
    private float[][] oldAntennaPatternSingleSwath = null; // old antenna pattern gains for single swath product, in dB
    private float[][] oldAntennaPatternWideSwath = null; // old antenna pattern gains for single swath product, in dB
    private float[][] newAntennaPatternSingleSwath = null; // new antenna pattern gains for single swath product, in dB
    private float[][] newAntennaPatternWideSwath = null; // new antenna pattern gains for single swath product, in dB

    private AbstractMetadata.SRGRCoefficientList[] srgrConvParams = null;
    private AbstractMetadata.OrbitStateVector[] orbitStateVectors = null;
    private final HashMap<String, String[]> targetBandNameToSourceBandName = new HashMap<String, String[]>();

    static final String NEAREST_NEIGHBOUR = "Nearest Neighbour";
    static final String BILINEAR = "Bilinear Interpolation";
    static final String CUBIC = "Cubic Convolution";
    private static final double MeanEarthRadius = 6371008.7714; // in m (WGS84)
    private static final double refSlantRange = 800000.0; //  m
    private static final double NonValidZeroDopplerTime = -99999.0;
    private static final int numOfGains = 201; // number of antenna pattern gain values for a given swath and
                                               // polarization in the aux file
    private static final int INVALID_SUB_SWATH_INDEX = -1;
    
    private enum ResampleMethod { RESAMPLE_NEAREST_NEIGHBOUR, RESAMPLE_BILINEAR, RESAMPLE_CUBIC }
    private ResampleMethod imgResampling = null;

    private Map<String, ArrayList<Tile>> tileCache = new HashMap<String, ArrayList<Tile>>(2);

    boolean useAvgSceneHeight = false;

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
            absRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct);

            if(OperatorUtils.isMapProjected(sourceProduct)) {
                throw new OperatorException("Source product is already map projected");
            }

            getMissionType();

            getSRGRFlag();

            getRadarFrequency();

            getRangeAzimuthSpacings();

            getFirstLastLineTimes();

            getLineTimeInterval();

            getOrbitStateVectors();

            if (srgrFlag) {
                getSrgrCoeff();
            } else {
                getNearEdgeSlantRange();
            }

            getAverageSceneHeight(); // used for retro-calibration or when useAvgSceneHeight is true

            computeImageGeoBoundary();

            computeDEMTraversalSampleInterval();

            computedTargetImageDimension();

            if (useAvgSceneHeight) {
                applyRadiometricCalibration = false;
                retroCalibrationFlag = false;
                saveDEM = false;
                saveLocalIncidenceAngle = false;
                saveProjectedLocalIncidenceAngle = false;
            } else {
                getElevationModel();
            }

            getSourceImageDimension();

            createTargetProduct();

            computeWARPFunction();

            computeSensorPositionsAndVelocities();

            if (imgResamplingMethod.equals(NEAREST_NEIGHBOUR)) {
                imgResampling = ResampleMethod.RESAMPLE_NEAREST_NEIGHBOUR;
            } else if (imgResamplingMethod.contains(BILINEAR)) {
                imgResampling = ResampleMethod.RESAMPLE_BILINEAR;
            } else if (imgResamplingMethod.contains(CUBIC)) {
                imgResampling = ResampleMethod.RESAMPLE_CUBIC;
            } else {
                throw new OperatorException("Unknown interpolation method");
            }

            if (applyRadiometricCalibration) {
                prepareForRadiometricCalibration();
            }

            updateTargetProductMetadata();

        } catch(Exception e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    /**
     * Get all information needed for retro-calibration.
     * @throws Exception The exceptions.
     */
    private void prepareForRadiometricCalibration() throws Exception {

        getMultilookFlag();

        getProductSwath();

        getProductType();

        getProductPolarization();

        setRetroCalibrationFlag();

        setRangeSpreadingLossCompPower();

        if (retroCalibrationFlag) {

            getTiePointGrid(sourceProduct);

            getOldAntennaPattern();

            computeOldSatelliteHeight();

            computeOldSlantRange();

            computeEarthRadius();
        }

        if (listedASARProductFlag) {
            getNewAntennaPattern();
        } else {
            getCalibrationConstant();
        }
    }

    /**
     * Get the mission type.
     * @throws Exception The exceptions.
     */
    private void getMissionType() throws Exception {
        mission = absRoot.getAttributeString(AbstractMetadata.MISSION);

        if (mission.contains("TSX1")) {
            throw new OperatorException("TerraSar-X product is not supported yet");
        }

        if (mission.contains("ALOS")) {
            throw new OperatorException("ALOS PALSAR product is not supported yet");
        }
    }

    /**
     * Get multilook flag from the abstracted metadata.
     * @throws Exception The exceptions.
     */
    private void getMultilookFlag() throws Exception {
        multilookFlag = AbstractMetadata.getAttributeBoolean(absRoot, AbstractMetadata.multilook_flag);
    }

    /**
     * Get product swath.
     * @throws Exception The exceptions.
     */
    private void getProductSwath() throws Exception {
        swath = absRoot.getAttributeString(AbstractMetadata.SWATH);
        wideSwathProductFlag = swath.contains("WS");
    }

    /**
     * Get Product ID from MPH.
     * @throws Exception The exceptions.
     */
    private void getProductType() throws Exception {
        productType = absRoot.getAttributeString(AbstractMetadata.PRODUCT_TYPE);
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
     * Set retro-calibration flag.
     */
    private void setRetroCalibrationFlag() {

        if (productType.contains("ASA_IMP_1P") || productType.contains("ASA_IMM_1P") ||
            productType.contains("ASA_APP_1P") || productType.contains("ASA_APM_1P") ||
            productType.contains("ASA_WSM_1P") || productType.contains("ASA_IMG_1P") ||
            productType.contains("ASA_APG_1P") || productType.contains("ASA_IMS_1P") ||
            productType.contains("ASA_APS_1P")) {

            if (srgrFlag) {
                if (multilookFlag) {
                    retroCalibrationFlag = false;
                    System.out.println("Only constant and incidence angle corrections will be performed for radiometric calibration");
                } else {
                    retroCalibrationFlag = true;
                }
            }
            listedASARProductFlag = true;

        } else if (mission.contains("ENVISAT") || (mission.contains("ERS") && productType.contains("SAR"))) {

            retroCalibrationFlag = false;
            listedASARProductFlag = false;

        } else {
            throw new OperatorException("Radiometric calibration cannot be applied to the selected product");
        }
    }

    /**
     * Set power coefficient used in range spreading loss compensation computation.
     */
    private void setRangeSpreadingLossCompPower() {
        rangeSpreadingCompPower = 3.0;
        if (productType.contains("ASA_APS_1P")) {
            rangeSpreadingCompPower = 4.0;
        }
        halfRangeSpreadingCompPower = rangeSpreadingCompPower / 2.0;
    }

    /**
     * Get incidence angle and slant range time tie point grids.
     * @param sourceProduct the source
     */
    private void getTiePointGrid(Product sourceProduct) {
        latitude = OperatorUtils.getLatitude(sourceProduct);
        longitude = OperatorUtils.getLongitude(sourceProduct);
    }

    /**
     * Get average scene height from abstracted metadata.
     * @throws Exception The exceptions.
     */
    private void getAverageSceneHeight() throws Exception {
        avgSceneHeight = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.avg_scene_height);
    }

    /**
     * Compute satellite to Earth centre distance (in m) using the middle orbit state vector.
     */
    private void computeOldSatelliteHeight() {
        final int mid = orbitStateVectors.length / 2;
        oldSatelliteHeight = ASARCalibrationOperator.computeSatelliteHeight(
                orbitStateVectors[mid].time.getMJD(), orbitStateVectors);
    }

    /**
     * Compute slant range for a range line using SRGR coefficient and the equation below
     * slant range = S0 + S1(GR-GR0) + S2 (GR-GR0)^2 + S3(GR-GR0)^3 + S4(GR-GR0)^4
     */
    private void computeOldSlantRange() {
        srgrConvParamsTime = new double[srgrConvParams.length];
        oldSlantRange = new float[srgrConvParams.length][sourceImageWidth];
        for (int i = 0; i < srgrConvParams.length; i++) {
            srgrConvParamsTime[i] = srgrConvParams[i].time.getMJD();
            for (int x = 0; x < sourceImageWidth; x++) {
                oldSlantRange[i][x] = (float)computePolinomialValue(
                        x*rangeSpacing + srgrConvParams[i].ground_range_origin, srgrConvParams[i].coefficients);
            }
        }
    }

    /**
     * Compute earth radius for all range lines (in m).
     */
    private void computeEarthRadius() {
        earthRadius = new double[targetImageHeight];
        for (int i = 0; i < targetImageHeight; i++) {
            earthRadius[i] = ASARCalibrationOperator.computeEarthRadius((float)(latMin + i*delLat), 0.0f);
        }
    }

    /**
     * Get old antenna pattern 
     */
    private void getOldAntennaPattern() {
        
        final String xcaFileName = absRoot.getAttributeString(AbstractMetadata.external_calibration_file);
        final String xcaFilePath = Settings.instance().get("AuxData/envisatAuxDataPath") + File.separator + xcaFileName;
        
        if (wideSwathProductFlag) {

            oldRefElevationAngle = new double[5]; // reference elevation angles for 5 sub swathes
            oldAntennaPatternWideSwath = new float[5][numOfGains]; // antenna pattern gain for 5 sub swathes
            ASARCalibrationOperator.getWideSwathAntennaPatternGainFromAuxData(
                    xcaFilePath, mdsPolar[0], numOfGains, oldRefElevationAngle, oldAntennaPatternWideSwath);

        } else {

            oldRefElevationAngle = new double[1]; // reference elevation angle for 1 swath
            oldAntennaPatternSingleSwath = new float[2][numOfGains]; // antenna pattern gain for 2 bands
            ASARCalibrationOperator.getSingleSwathAntennaPatternGainFromAuxData(
                    xcaFilePath, swath, mdsPolar, numOfGains, oldRefElevationAngle, oldAntennaPatternSingleSwath);
        }
    }

    /**
     * Get the new antenna pattern gain from the latest XCA file available.
     * @throws Exception The exceptions.
     */
    private void getNewAntennaPattern() throws Exception {

        final Date startDate = sourceProduct.getStartTime().getAsDate();
        final Date endDate = sourceProduct.getEndTime().getAsDate();
        final File xcaFileDir = new File(Settings.instance().get("AuxData/envisatAuxDataPath"));
        newXCAFileName = ASARCalibrationOperator.findXCAFile(xcaFileDir, startDate, endDate);

        String xcaFilePath;
        if (newXCAFileName != null) {
            xcaFilePath = xcaFileDir.toString() + File.separator + newXCAFileName;
        } else {
            throw new OperatorException("No proper XCA file has been found");
        }

        if (wideSwathProductFlag) {

            newRefElevationAngle = new double[5]; // reference elevation angles for 5 sub swathes
            newAntennaPatternWideSwath = new float[5][numOfGains]; // antenna pattern gain for 5 sub swathes
            ASARCalibrationOperator.getWideSwathAntennaPatternGainFromAuxData(
                    xcaFilePath, mdsPolar[0], numOfGains, newRefElevationAngle, newAntennaPatternWideSwath);

        } else {

            newRefElevationAngle = new double[1]; // reference elevation angle for 1 swath
            newAntennaPatternSingleSwath = new float[2][numOfGains];  // antenna pattern gain for 2 bands
            ASARCalibrationOperator.getSingleSwathAntennaPatternGainFromAuxData(
                    xcaFilePath,  swath, mdsPolar, numOfGains, newRefElevationAngle, newAntennaPatternSingleSwath);
        }

        ASARCalibrationOperator.getCalibrationFactorFromExternalAuxFile(
                xcaFilePath, swath, mdsPolar, productType, newCalibrationConstant);
    }

    /**
     * Get calibration constant from the metadata.
     * @throws Exception The exceptions.
     */
    private void getCalibrationConstant() throws Exception {
        newCalibrationConstant[0] = absRoot.getAttributeDouble(AbstractMetadata.calibration_factor, 1.0);
    }

    /**
     * Get SRGR flag from the abstracted metadata.
     * @throws Exception The exceptions.
     */
    private void getSRGRFlag() throws Exception {
        srgrFlag = AbstractMetadata.getAttributeBoolean(absRoot, AbstractMetadata.srgr_flag);
    }

    /**
     * Get radar frequency from the abstracted metadata (in Hz).
     * @throws Exception The exceptions.
     */
    private void getRadarFrequency() throws Exception {
        final double radarFreq = AbstractMetadata.getAttributeDouble(absRoot,
                                                    AbstractMetadata.radar_frequency)*Constants.oneMillion; // Hz
        wavelength = Constants.lightSpeed / radarFreq;
    }

    /**
     * Get range and azimuth spacings from the abstracted metadata.
     * @throws Exception The exceptions.
     */
    private void getRangeAzimuthSpacings() throws Exception {
        rangeSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.range_spacing);
        azimuthSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.azimuth_spacing);
    }

    /**
     * Get orbit state vectors from the abstracted metadata.
     * @throws Exception The exceptions.
     */
    private void getOrbitStateVectors() throws Exception {
        orbitStateVectors = AbstractMetadata.getOrbitStateVectors(absRoot);
    }

    /**
     * Get SRGR conversion parameters.
     * @throws Exception The exceptions.
     */
    private void getSrgrCoeff() throws Exception {
        srgrConvParams = AbstractMetadata.getSRGRCoefficients(absRoot);
    }

    /**
     * Get near edge slant range (in m).
     * @throws Exception The exceptions.
     */
    private void getNearEdgeSlantRange() throws Exception {
        nearEdgeSlantRange = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.slant_range_to_first_pixel);
    }

    /**
     * Compute source image geodetic boundary (minimum/maximum latitude/longitude) from the its corner
     * latitude/longitude.
     * @throws Exception The exceptions.
     */
    private void computeImageGeoBoundary() throws Exception {
        
        final GeoCoding geoCoding = sourceProduct.getGeoCoding();
        if(geoCoding == null) {
            throw new OperatorException("Product does not contain a geocoding");
        }
        final GeoPos geoPosFirstNear = geoCoding.getGeoPos(new PixelPos(0,0), null);
        final GeoPos geoPosFirstFar = geoCoding.getGeoPos(new PixelPos(sourceProduct.getSceneRasterWidth(),0), null);
        final GeoPos geoPosLastNear = geoCoding.getGeoPos(new PixelPos(0,sourceProduct.getSceneRasterHeight()), null);
        final GeoPos geoPosLastFar = geoCoding.getGeoPos(new PixelPos(sourceProduct.getSceneRasterWidth(),
                                                                      sourceProduct.getSceneRasterHeight()), null);
        
        final double[] lats  = {geoPosFirstNear.getLat(), geoPosFirstFar.getLat(), geoPosLastNear.getLat(), geoPosLastFar.getLat()};
        final double[] lons  = {geoPosFirstNear.getLon(), geoPosFirstFar.getLon(), geoPosLastNear.getLon(), geoPosLastFar.getLon()};
        latMin = 90.0;
        latMax = -90.0;
        for (double lat : lats) {
            if (lat < latMin) {
                latMin = lat;
            }
            if (lat > latMax) {
                latMax = lat;
            }
        }

        lonMin = 180.0;
        lonMax = -180.0;
        for (double lon : lons) {
            if (lon < lonMin) {
                lonMin = lon;
            }
            if (lon > lonMax) {
                lonMax = lon;
            }
        }
    }

    /**
     * Compute DEM traversal step sizes (in degree) in latitude and longitude.
     */
    private void computeDEMTraversalSampleInterval() {

        final double minSpacing = Math.min(rangeSpacing, azimuthSpacing);
        double minAbsLat;
        if (latMin*latMax > 0) {
            minAbsLat = Math.min(Math.abs(latMin), Math.abs(latMax)) * org.esa.beam.util.math.MathUtils.DTOR;
        } else {
            minAbsLat = 0.0;
        }
        delLat = minSpacing / MeanEarthRadius * org.esa.beam.util.math.MathUtils.RTOD;
        delLon = minSpacing / (MeanEarthRadius*Math.cos(minAbsLat)) * org.esa.beam.util.math.MathUtils.RTOD;
        delLat = Math.min(delLat, delLon);
        delLon = delLat;
    }

    /**
     * Compute target image dimension.
     */
    private void computedTargetImageDimension() {
        targetImageWidth = (int)((lonMax - lonMin)/delLon) + 1;
        targetImageHeight = (int)((latMax - latMin)/delLat) + 1;
    }

    /**
     * Get first line time from the abstracted metadata (in days).
     * @throws Exception The exceptions.
     */
    private void getFirstLastLineTimes() throws Exception {
        firstLineUTC = absRoot.getAttributeUTC(AbstractMetadata.first_line_time).getMJD(); // in days
        lastLineUTC = absRoot.getAttributeUTC(AbstractMetadata.last_line_time).getMJD(); // in days
    }

    /**
     * Get line time interval from the abstracted metadata (in days).
     * @throws Exception The exceptions.
     */
    private void getLineTimeInterval() throws Exception {
        lineTimeInterval = absRoot.getAttributeDouble(AbstractMetadata.line_time_interval) / 86400.0; // s to day
    }

    /**
     * Get elevation model.
     * @throws Exception The exceptions.
     */
    private void getElevationModel() throws Exception {

        demName = absRoot.getAttributeString(AbstractMetadata.DEM);
        final ElevationModelRegistry elevationModelRegistry = ElevationModelRegistry.getInstance();
        final ElevationModelDescriptor demDescriptor = elevationModelRegistry.getDescriptor(demName);
        if (demDescriptor == null) {
            throw new OperatorException("The DEM '" + demName + "' is not supported.");
        }

        if (demDescriptor.isInstallingDem()) {
            throw new OperatorException("The DEM '" + demName + "' is currently being installed.");
        }

        dem = demDescriptor.createDem(getResamplingMethod());
        if(dem == null) {
            throw new OperatorException("The DEM '" + demName + "' has not been installed.");
        }

        demNoDataValue = dem.getDescriptor().getNoDataValue();
    }

    private Resampling getResamplingMethod() {
        Resampling resamplingMethod = Resampling.BILINEAR_INTERPOLATION;
        if(demResamplingMethod.equals(NEAREST_NEIGHBOUR)) {
            resamplingMethod = Resampling.NEAREST_NEIGHBOUR;
        } else if(demResamplingMethod.equals(BILINEAR)) {
            resamplingMethod = Resampling.BILINEAR_INTERPOLATION;
        } else if(demResamplingMethod.equals(CUBIC)) {
            resamplingMethod = Resampling.CUBIC_CONVOLUTION;
        }
        return resamplingMethod;
    }

    /**
     * Get source image width and height.
     */
    private void getSourceImageDimension() {
        sourceImageWidth = sourceProduct.getSceneRasterWidth();
        sourceImageHeight = sourceProduct.getSceneRasterHeight();
    }

    /**
     * Create target product.
     * @throws OperatorException The exception.
     */
    private void createTargetProduct() throws OperatorException {
        
        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    targetImageWidth,
                                    targetImageHeight);

        addSelectedBands();

        addGeoCoding();

        addLayoverShadowBitmasks(targetProduct);

        // the tile width has to be the image width because otherwise sourceRaster.getDataBufferIndex(x, y)
        // returns incorrect index for the last tile on the right
        targetProduct.setPreferredTileSize(targetProduct.getSceneRasterWidth(), 20);
    }

    private static void addLayoverShadowBitmasks(Product product) {
        for(Band band : product.getBands()) {
            final String expression = band.getName() + " < 0";
            final BitmaskDef nrv = new BitmaskDef(band.getName()+"_non_reliable_values",
                    "Non reliable values where DN is negative", expression, Color.RED, 0.5f);
            product.addBitmaskDef(nrv);
        }
    }

    /**
     * Add the user selected bands to target product.
     * @throws OperatorException The exceptions.
     */
    private void addSelectedBands() throws OperatorException {

        final Band[] sourceBands = sourceProduct.getBands();
        if (sourceBands.length == 1) {
            throw new OperatorException("Source product should have more than one band");
        }

        String targetBandName;
        for (int i = 1; i < sourceBands.length; i++) { // skip master band (i=0, simulated image)

            final Band srcBand = sourceBands[i];
            final String unit = srcBand.getUnit();
            if(unit == null) {
                throw new OperatorException("band " + srcBand.getName() + " requires a unit");
            }

            if (unit.contains(Unit.PHASE) || unit.contains(Unit.REAL) || unit.contains(Unit.IMAGINARY)) {
                throw new OperatorException("Only amplitude or intensity band should be used for orthorectification");
            }

            targetBandName = srcBand.getName();
            final String[] srcBandNames = {srcBand.getName()};
            targetBandNameToSourceBandName.put(targetBandName, srcBandNames);

            final Band targetBand = new Band(targetBandName,
                                             ProductData.TYPE_FLOAT32,
                                             targetImageWidth,
                                             targetImageHeight);

            targetBand.setUnit(unit);
            targetBand.setDescription(srcBand.getDescription());
            targetBand.setNoDataValue(srcBand.getNoDataValue());
            targetBand.setNoDataValueUsed(true);
            targetProduct.addBand(targetBand);
        }

        if(saveDEM) {
            final Band demBand = new Band("elevation",
                                             ProductData.TYPE_FLOAT32,
                                             targetImageWidth,
                                             targetImageHeight);
            demBand.setUnit(Unit.METERS);
            targetProduct.addBand(demBand);
        }

        if(saveLocalIncidenceAngle) {
            final Band incidenceAngleBand = new Band("incidenceAngle",
                                                     ProductData.TYPE_FLOAT32,
                                                     targetImageWidth,
                                                     targetImageHeight);
            incidenceAngleBand.setUnit(Unit.DEGREES);
            targetProduct.addBand(incidenceAngleBand);
        }

        if(saveProjectedLocalIncidenceAngle) {
            final Band projectedIncidenceAngleBand = new Band("projectedIncidenceAngle",
                                                     ProductData.TYPE_FLOAT32,
                                                     targetImageWidth,
                                                     targetImageHeight);
            projectedIncidenceAngleBand.setUnit(Unit.DEGREES);
            targetProduct.addBand(projectedIncidenceAngleBand);
        }
    }

    /**
     * Add geocoding to the target product.
     */
    private void addGeoCoding() {

        final float[] latTiePoints = {(float)latMax, (float)latMax, (float)latMin, (float)latMin};
        final float[] lonTiePoints = {(float)lonMin, (float)lonMax, (float)lonMin, (float)lonMax};

        final int gridWidth = 10;
        final int gridHeight = 10;

        final float[] fineLatTiePoints = new float[gridWidth*gridHeight];
        ReaderUtils.createFineTiePointGrid(2, 2, gridWidth, gridHeight, latTiePoints, fineLatTiePoints);

        float subSamplingX = (float)targetImageWidth / (gridWidth - 1);
        float subSamplingY = (float)targetImageHeight / (gridHeight - 1);

        final TiePointGrid latGrid = new TiePointGrid("latitude", gridWidth, gridHeight, 0.5f, 0.5f,
                subSamplingX, subSamplingY, fineLatTiePoints);
        latGrid.setUnit(Unit.DEGREES);

        final float[] fineLonTiePoints = new float[gridWidth*gridHeight];
        ReaderUtils.createFineTiePointGrid(2, 2, gridWidth, gridHeight, lonTiePoints, fineLonTiePoints);

        final TiePointGrid lonGrid = new TiePointGrid("longitude", gridWidth, gridHeight, 0.5f, 0.5f,
                subSamplingX, subSamplingY, fineLonTiePoints, TiePointGrid.DISCONT_AT_180);
        lonGrid.setUnit(Unit.DEGREES);

        final TiePointGeoCoding tpGeoCoding = new TiePointGeoCoding(latGrid, lonGrid, Datum.WGS_84);

        targetProduct.addTiePointGrid(latGrid);
        targetProduct.addTiePointGrid(lonGrid);
        targetProduct.setGeoCoding(tpGeoCoding);

        final String[] srcBandNames = targetBandNameToSourceBandName.get(targetProduct.getBandAt(0).getName());

        ReaderUtils.createMapGeocoding(targetProduct, sourceProduct.getBand(srcBandNames[0]).getNoDataValue());
    }

    /**
     * Update metadata in the target product.
     * @throws OperatorException The exception.
     */
    private void updateTargetProductMetadata() throws OperatorException {

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        final MetadataElement absTgt = AbstractMetadata.getAbstractedMetadata(targetProduct);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.srgr_flag, 1);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.num_output_lines, targetImageHeight);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.num_samples_per_line, targetImageWidth);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.first_near_lat, latMax);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.first_far_lat, latMax);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.last_near_lat, latMin);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.last_far_lat, latMin);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.first_near_long, lonMin);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.first_far_long, lonMax);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.last_near_long, lonMin);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.last_far_long, lonMax);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.TOT_SIZE,
                (int)(targetProduct.getRawStorageSize() / (1024.0f * 1024.0f)));
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.map_projection, IdentityTransformDescriptor.NAME);
        if (!useAvgSceneHeight) {
            AbstractMetadata.setAttribute(absTgt, AbstractMetadata.is_terrain_corrected, 1);
            AbstractMetadata.setAttribute(absTgt, AbstractMetadata.DEM, demName);
        }

        // map projection too
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.geo_ref_system, "WGS84");
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.lat_pixel_res, delLat);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.lon_pixel_res, delLon);

        if (applyRadiometricCalibration) {
            if (listedASARProductFlag && !multilookFlag) {
                AbstractMetadata.setAttribute(absTgt, AbstractMetadata.SAMPLE_TYPE, "DETECTED");
                AbstractMetadata.setAttribute(absTgt, AbstractMetadata.ant_elev_corr_flag, 1);
                AbstractMetadata.setAttribute(absTgt, AbstractMetadata.range_spread_comp_flag, 1);
                AbstractMetadata.setAttribute(absTgt, AbstractMetadata.external_calibration_file, newXCAFileName);
            }
            AbstractMetadata.setAttribute(absTgt, AbstractMetadata.abs_calibration_flag, 1);
            AbstractMetadata.setAttribute(absTgt, AbstractMetadata.calibration_factor, newCalibrationConstant[0]);
        }
    }

    private void computeWARPFunction() {

        final Band masterBand = sourceProduct.getBandAt(0);
        masterGCPGroup = sourceProduct.getGcpGroup(masterBand);

        final Band slaveBand = sourceProduct.getBandAt(1);
        final ProductNodeGroup<Pin> slaveGCPGroup = sourceProduct.getGcpGroup(slaveBand);
        if(slaveGCPGroup.getNodeCount() < 3) {
            throw new OperatorException(slaveGCPGroup.getNodeCount() +
                    " GCPs survived. Try using more GCPs or a larger window");
        }

        warpData = new WarpOp.WarpData(slaveGCPGroup);
        WarpOp.computeWARPPolynomial(warpData, warpPolynomialOrder, masterGCPGroup); // compute initial warp polynomial

        if (warpData.rmsMean > rmsThreshold && WarpOp.eliminateGCPsBasedOnRMS(warpData, (float)warpData.rmsMean)) {
            WarpOp.computeWARPPolynomial(warpData, warpPolynomialOrder, masterGCPGroup); // compute 2nd warp polynomial
        }

        if (warpData.rmsMean > rmsThreshold && WarpOp.eliminateGCPsBasedOnRMS(warpData, (float)warpData.rmsMean)) {
            WarpOp.computeWARPPolynomial(warpData, warpPolynomialOrder, masterGCPGroup); // compute 3rd warp polynomial
        }

        WarpOp.eliminateGCPsBasedOnRMS(warpData, rmsThreshold);
        WarpOp.computeWARPPolynomial(warpData, warpPolynomialOrder, masterGCPGroup); // compute final warp polynomial
    }

    /**
     * Compute sensor position and velocity for each range line from the orbit state vectors using
     * cubic WARP polynomial.
     */
    private void computeSensorPositionsAndVelocities() {

        final int numVectors = orbitStateVectors.length;
        final int numVectorsUsed = Math.min(numVectors, 5);
        final int d = numVectors / numVectorsUsed;

        timeArray = new double[numVectorsUsed];
        xPosArray = new double[numVectorsUsed];
        yPosArray = new double[numVectorsUsed];
        zPosArray = new double[numVectorsUsed];
        final double[] xVelArray = new double[numVectorsUsed];
        final double[] yVelArray = new double[numVectorsUsed];
        final double[] zVelArray = new double[numVectorsUsed];

        for (int i = 0; i < numVectorsUsed; i++) {
            timeArray[i] = orbitStateVectors[i*d].time.getMJD();
            xPosArray[i] = orbitStateVectors[i*d].x_pos; // m
            yPosArray[i] = orbitStateVectors[i*d].y_pos; // m
            zPosArray[i] = orbitStateVectors[i*d].z_pos; // m
            xVelArray[i] = orbitStateVectors[i*d].x_vel; // m/s
            yVelArray[i] = orbitStateVectors[i*d].y_vel; // m/s
            zVelArray[i] = orbitStateVectors[i*d].z_vel; // m/s
        }

        // Lagrange polynomial interpolation
        sensorPosition = new double[sourceImageHeight][3]; // xPos, yPos, zPos
        sensorVelocity = new double[sourceImageHeight][3]; // xVel, yVel, zVel
        for (int i = 0; i < sourceImageHeight; i++) {
            final double time = firstLineUTC + i*lineTimeInterval; // zero Doppler time (in days) for each range line
            sensorPosition[i][0] = MathUtils.lagrangeInterpolatingPolynomial(timeArray, xPosArray, time);
            sensorPosition[i][1] = MathUtils.lagrangeInterpolatingPolynomial(timeArray, yPosArray, time);
            sensorPosition[i][2] = MathUtils.lagrangeInterpolatingPolynomial(timeArray, zPosArray, time);
            sensorVelocity[i][0] = MathUtils.lagrangeInterpolatingPolynomial(timeArray, xVelArray, time);
            sensorVelocity[i][1] = MathUtils.lagrangeInterpolatingPolynomial(timeArray, yVelArray, time);
            sensorVelocity[i][2] = MathUtils.lagrangeInterpolatingPolynomial(timeArray, zVelArray, time);
        }
    }

    /**
     * Called by the framework in order to compute the stack of tiles for the given target bands.
     * <p>The default implementation throws a runtime exception with the message "not implemented".</p>
     *
     * @param targetTiles     The current tiles to be computed for each target band.
     * @param targetRectangle The area in pixel coordinates to be computed (same for all rasters in <code>targetRasters</code>).
     * @param pm              A progress monitor which should be used to determine computation cancelation requests.
     * @throws OperatorException if an error occurs during computation of the target rasters.
     */
    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws OperatorException {

        final int x0 = targetRectangle.x;
        final int y0 = targetRectangle.y;
        final int w  = targetRectangle.width;
        final int h  = targetRectangle.height;
        //System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

        final GeoPos geoPos = new GeoPos();
        final double[] earthPoint = new double[3];
        final double[] sensorPos = new double[3];
        final int srcMaxRange = sourceImageWidth - 1;
        final int srcMaxAzimuth = sourceImageHeight - 1;
        ProductData demBuffer = null;
        ProductData incidenceAngleBuffer = null;
        ProductData projectedIncidenceAngleBuffer = null;
        final double halfLightSpeedInMetersPerDay = Constants.halfLightSpeed * 86400.0;

        final ArrayList<TileData> trgTileList = new ArrayList<TileData>();
        final Set<Band> keySet = targetTiles.keySet();
        for(Band targetBand : keySet) {

            if(targetBand.getName().equals("elevation")) {
                demBuffer = targetTiles.get(targetBand).getDataBuffer();
                continue;
            }

            if(targetBand.getName().equals("incidenceAngle")) {
                incidenceAngleBuffer = targetTiles.get(targetBand).getDataBuffer();
                continue;
            }

            if(targetBand.getName().equals("projectedIncidenceAngle")) {
                projectedIncidenceAngleBuffer = targetTiles.get(targetBand).getDataBuffer();
                continue;
            }

            final String[] srcBandNames = targetBandNameToSourceBandName.get(targetBand.getName());

            final TileData td = new TileData();
            td.targetTile = targetTiles.get(targetBand);
            td.tileDataBuffer = td.targetTile.getDataBuffer();
            td.bandName = targetBand.getName();
            td.noDataValue = sourceProduct.getBand(srcBandNames[0]).getNoDataValue();

            final String pol = OperatorUtils.getPolarizationFromBandName(srcBandNames[0]);
            td.bandPolar = 0;
            if (pol != null && mdsPolar[1] != null && pol.contains(mdsPolar[1])) {
                td.bandPolar = 1;
            }
            trgTileList.add(td);
        }
        final TileData[] trgTiles = trgTileList.toArray(new TileData[trgTileList.size()]);

        float[][] localDEM = null; // DEM for current tile for computing slope angle
        if (saveLocalIncidenceAngle || saveProjectedLocalIncidenceAngle || applyRadiometricCalibration) {
            localDEM = new float[h+2][w+2];
            getLocalDEM(x0, y0, w, h, localDEM);
        }

        try {
            for (int y = y0; y < y0 + h; y++) {
                final double lat = latMax - y*delLat;

                for (int x = x0; x < x0 + w; x++) {
                    final int index = trgTiles[0].targetTile.getDataBufferIndex(x, y);

                    final double lon = lonMin + x*delLon;

                    double alt;
                    if (saveLocalIncidenceAngle || saveProjectedLocalIncidenceAngle || applyRadiometricCalibration) { // localDEM is available
                        alt = (double)localDEM[y-y0+1][x-x0+1];
                    } else {
                        if (useAvgSceneHeight) {
                            alt = avgSceneHeight;
                        } else {
                            geoPos.setLocation((float)lat, (float)lon);
                            alt = getLocalElevation(geoPos);
                        }
                    }

                    if(saveDEM) {
                        demBuffer.setElemDoubleAt(index, alt);
                    }

                    if (!useAvgSceneHeight && alt == demNoDataValue) {
                        saveNoDataValueToTarget(index, trgTiles);
                        continue;
                    }

                    GeoUtils.geo2xyz(lat, lon, alt, earthPoint, GeoUtils.EarthModel.WGS84);
                    final double zeroDopplerTime = getEarthPointZeroDopplerTime(earthPoint);
                    if (Double.compare(zeroDopplerTime, NonValidZeroDopplerTime) == 0) {
                        saveNoDataValueToTarget(index, trgTiles);
                        continue;
                    }

                    double slantRange = computeSlantRange(zeroDopplerTime, earthPoint, sensorPos);
                    final double zeroDopplerTimeWithoutBias = zeroDopplerTime + slantRange / halfLightSpeedInMetersPerDay;
                    final double azimuthIndex = (zeroDopplerTimeWithoutBias - firstLineUTC) / lineTimeInterval;
                    slantRange = computeSlantRange(zeroDopplerTimeWithoutBias, earthPoint, sensorPos);

                    double[] localIncidenceAngles = {0.0, 0.0};
                    if (saveLocalIncidenceAngle || saveProjectedLocalIncidenceAngle || applyRadiometricCalibration) {

                        computeLocalIncidenceAngle(
                                sensorPos, earthPoint, lat, lon, x0, y0, x, y, localDEM, localIncidenceAngles); // in degrees

                        if (saveLocalIncidenceAngle) {
                            incidenceAngleBuffer.setElemDoubleAt(index, localIncidenceAngles[0]);
                        }

                        if (saveProjectedLocalIncidenceAngle) {
                            projectedIncidenceAngleBuffer.setElemDoubleAt(index, localIncidenceAngles[1]);
                        }
                    }

                    final double rangeIndex = computeRangeIndex(zeroDopplerTimeWithoutBias, slantRange);
                    if (rangeIndex < 0.0 || rangeIndex >= srcMaxRange ||
                            azimuthIndex < 0.0 || azimuthIndex >= srcMaxAzimuth) {

                        saveNoDataValueToTarget(index, trgTiles);

                    } else {

                        final PixelPos pixelPos = new PixelPos(0.0f,0.0f);
                        WarpOp.getWarpedCoords(
                                warpData.warp, warpPolynomialOrder, (float)rangeIndex, (float)azimuthIndex, pixelPos);

                        if (pixelPos.x < 0.0 || pixelPos.x >= srcMaxRange ||
                            pixelPos.y < 0.0 || pixelPos.y >= srcMaxAzimuth) {
                            saveNoDataValueToTarget(index, trgTiles);

                        } else {

                            for(TileData tileData : trgTiles) {

                                Unit.UnitType bandUnit = getBandUnit(tileData.bandName);
                                int[] subSwathIndex = {INVALID_SUB_SWATH_INDEX};
                                double v = getPixelValue(
                                        pixelPos.y, pixelPos.x, tileData.bandName, tileData.bandPolar, bandUnit, subSwathIndex);

                                if (applyRadiometricCalibration) {

                                    v = applyCalibration(slantRange, sensorPos, earthPoint, localIncidenceAngles[1],
                                            tileData.bandPolar, v, bandUnit, subSwathIndex); // use projected incidence angle
                                }

                                tileData.tileDataBuffer.setElemDoubleAt(index, v);
                            }
                        }
                    }
                }
            }
        } catch(Exception e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    /**
     * Read DEM for current tile.
     * @param x0 The x coordinate of the pixel at the upper left corner of current tile.
     * @param y0 The y coordinate of the pixel at the upper left corner of current tile.
     * @param tileHeight The tile height.
     * @param tileWidth The tile width.
     * @param localDEM The DEM for the tile.
     */
    private void getLocalDEM(
            final int x0, final int y0, final int tileWidth, final int tileHeight, final float[][] localDEM) {

        // Note: the localDEM covers current tile with 1 extra row above, 1 extra row below, 1 extra column to
        //       the left and 1 extra column to the right of the tile.
        final GeoPos geoPos = new GeoPos();
        final int maxY = y0 + tileHeight + 1;
        final int maxX = x0 + tileWidth + 1;
        for (int y = y0 - 1; y < maxY; y++) {
            final float lat = (float)(latMax - y*delLat);
            final int yy = y - y0 + 1;
            for (int x = x0 - 1; x < maxX; x++) {
                geoPos.setLocation(lat, (float)(lonMin + x*delLon));
                localDEM[yy][x - x0 + 1] = (float)getLocalElevation(geoPos);
            }
        }
    }

    /**
     * Get local elevation (in meter) for given latitude and longitude.
     * @param geoPos The latitude and longitude in degrees.
     * @return The elevation in meter.
     */
    private double getLocalElevation(final GeoPos geoPos) {
        double alt;
        try {
            alt = dem.getElevation(geoPos);
        } catch (Exception e) {
            alt = demNoDataValue;
        }

        return alt;
    }

    /**
     * Save noDataValue to target pixel with given index.
     * @param index The pixel index in target image.
     * @param trgTiles The target tiles.
     */
    private static void saveNoDataValueToTarget(final int index, TileData[] trgTiles) {
        for(TileData tileData : trgTiles) {
            tileData.tileDataBuffer.setElemDoubleAt(index, tileData.noDataValue);
        }
    }

    /**
     * Compute zero Doppler time for given erath point.
     * @param earthPoint The earth point in xyz cooordinate.
     * @return The zero Doppler time in days if it is found, -1 otherwise.
     * @throws OperatorException The operator exception.
     */
    private double getEarthPointZeroDopplerTime(final double[] earthPoint) throws OperatorException {

        // binary search is used in finding the zero doppler time
        int lowerBound = 0;
        int upperBound = sensorPosition.length - 1;
        double lowerBoundFreq = getDopplerFrequency(lowerBound, earthPoint);
        double upperBoundFreq = getDopplerFrequency(upperBound, earthPoint);

        if (Double.compare(lowerBoundFreq, 0.0) == 0) {
            return firstLineUTC + lowerBound*lineTimeInterval;
        } else if (Double.compare(upperBoundFreq, 0.0) == 0) {
            return firstLineUTC + upperBound*lineTimeInterval;
        } else if (lowerBoundFreq*upperBoundFreq > 0.0) {
            return NonValidZeroDopplerTime;
        }

        // start binary search
        double midFreq;
        while(upperBound - lowerBound > 1) {

            final int mid = (int)((lowerBound + upperBound)/2.0);
            midFreq = getDopplerFrequency(mid, earthPoint);
            if (Double.compare(midFreq, 0.0) == 0) {
                return firstLineUTC + mid*lineTimeInterval;
            } else if (midFreq*lowerBoundFreq > 0.0) {
                lowerBound = mid;
                lowerBoundFreq = midFreq;
            } else if (midFreq*upperBoundFreq > 0.0) {
                upperBound = mid;
                upperBoundFreq = midFreq;
            }
        }

        final double y0 = lowerBound - lowerBoundFreq*(upperBound - lowerBound)/(upperBoundFreq - lowerBoundFreq);
        return firstLineUTC + y0*lineTimeInterval;
    }

    /**
     * Compute Doppler frequency for given earthPoint and sensor position.
     * @param y The index for given range line.
     * @param earthPoint The earth point in xyz coordinate.
     * @return The Doppler frequency in Hz.
     */
    private double getDopplerFrequency(final int y, final double[] earthPoint) {

        if (y < 0 || y > sourceImageHeight - 1) {
            throw new OperatorException("Invalid range line index: " + y);
        }
        
        final double xVel = sensorVelocity[y][0];
        final double yVel = sensorVelocity[y][1];
        final double zVel = sensorVelocity[y][2];
        final double xDiff = earthPoint[0] - sensorPosition[y][0];
        final double yDiff = earthPoint[1] - sensorPosition[y][1];
        final double zDiff = earthPoint[2] - sensorPosition[y][2];
        final double distance = Math.sqrt(xDiff*xDiff + yDiff*yDiff + zDiff*zDiff);

        return 2.0 * (xVel*xDiff + yVel*yDiff + zVel*zDiff) / (distance*wavelength);
    }

    /**
     * Compute slant range distance for given earth point and given time.
     * @param time The given time in days.
     * @param earthPoint The earth point in xyz coordinate.
     * @param sensorPos The sensor position.
     * @return The slant range distance in meters.
     */
    private double computeSlantRange(final double time, final double[] earthPoint, double[] sensorPos) {

        sensorPos[0] = MathUtils.lagrangeInterpolatingPolynomial(timeArray, xPosArray, time);
        sensorPos[1] = MathUtils.lagrangeInterpolatingPolynomial(timeArray, yPosArray, time);
        sensorPos[2] = MathUtils.lagrangeInterpolatingPolynomial(timeArray, zPosArray, time);

        final double xDiff = sensorPos[0] - earthPoint[0];
        final double yDiff = sensorPos[1] - earthPoint[1];
        final double zDiff = sensorPos[2] - earthPoint[2];

        return Math.sqrt(xDiff*xDiff + yDiff*yDiff + zDiff*zDiff);
    }

    /**
     * Compute range index in source image for earth point with given zero Doppler time and slant range.
     * @param zeroDopplerTime The zero Doppler time in MJD.
     * @param slantRange The slant range in meters.
     * @return The range index.
     */
    private double computeRangeIndex(final double zeroDopplerTime, final double slantRange) {

        if (zeroDopplerTime < firstLineUTC || zeroDopplerTime > lastLineUTC) {
            return -1.0;
        }

        if (srgrFlag) { // ground detected image

            double groundRange = 0.0;

            if (srgrConvParams.length == 1) {
                groundRange = RangeDopplerGeocodingOp.computeGroundRange(sourceImageWidth, rangeSpacing, slantRange, srgrConvParams[0].coefficients);
                if (groundRange < 0.0) {
                    return -1.0;
                } else {
                    return (groundRange - srgrConvParams[0].ground_range_origin) / rangeSpacing;
                }
            }
            
            int idx = 0;
            for (int i = 0; i < srgrConvParams.length && zeroDopplerTime >= srgrConvParams[i].time.getMJD(); i++) {
                idx = i;
            }

            double[] srgrCoefficients = new double[srgrConvParams[idx].coefficients.length];
            if (idx == srgrConvParams.length - 1) {
                idx--;
            }

            final double mu = (zeroDopplerTime - srgrConvParams[idx].time.getMJD()) /
                              (srgrConvParams[idx+1].time.getMJD() - srgrConvParams[idx].time.getMJD());
            for (int i = 0; i < srgrCoefficients.length; i++) {
                srgrCoefficients[i] = MathUtils.interpolationLinear(srgrConvParams[idx].coefficients[i],
                                                                    srgrConvParams[idx+1].coefficients[i], mu);
            }
            groundRange = RangeDopplerGeocodingOp.computeGroundRange(sourceImageWidth, rangeSpacing, slantRange, srgrCoefficients);
            if (groundRange < 0.0) {
                return -1.0;
            } else {
                return (groundRange - srgrConvParams[idx].ground_range_origin) / rangeSpacing;
            }

        } else { // slant range image

            return (slantRange - nearEdgeSlantRange) / rangeSpacing;
        }
    }

    /**
     * Compute polynomial value.
     * @param x The variable.
     * @param srgrCoeff The polynomial coefficients.
     * @return The function value.
     */
    private static double computePolinomialValue(final double x, final double[] srgrCoeff) {
        double v = 0.0;
        for (int i = srgrCoeff.length-1; i > 0; i--) {
            v = (v + srgrCoeff[i])*x;
        }
        return v + srgrCoeff[0];
    }

    /**
     * Get unit for the source band corresponding to the given target band.
     * @param bandName The target band name.
     * @return The source band unit.
     */
    private Unit.UnitType getBandUnit(String bandName) {
        final String[] srcBandNames = targetBandNameToSourceBandName.get(bandName);
        return Unit.getUnitType(sourceProduct.getBand(srcBandNames[0]));
    }

    /**
     * Compute orthorectified pixel value for given pixel.
     * @param azimuthIndex The azimuth index for pixel in source image.
     * @param rangeIndex The range index for pixel in source image.
     * @param bandName The name of the target band.
     * @param bandPolar The source band polarization index.
     * @param bandUnit The corresponding source band unit.
     * @return The pixel value.
     * @throws IOException from readPixels
     */
    private double getPixelValue(final double azimuthIndex, final double rangeIndex,
                                 final String bandName, final int bandPolar, Unit.UnitType bandUnit, int[] subSwathIndex)
            throws IOException {

        final String[] srcBandNames = targetBandNameToSourceBandName.get(bandName);
        final String iBandName = srcBandNames[0];
        String qBandName = null;
        if (srcBandNames.length > 1) {
            qBandName = srcBandNames[1];
        }

        if (imgResampling.equals(ResampleMethod.RESAMPLE_NEAREST_NEIGHBOUR)) {

            final Tile sourceTile = getSrcTile(iBandName, (int)rangeIndex, (int)azimuthIndex, 1, 1);
            final Tile sourceTile2 = getSrcTile(qBandName, (int)rangeIndex, (int)azimuthIndex, 1, 1);
            return getPixelValueUsingNearestNeighbourInterp(
                    azimuthIndex, rangeIndex, bandPolar, bandUnit, sourceTile, sourceTile2, subSwathIndex);

        } else if (imgResampling.equals(ResampleMethod.RESAMPLE_BILINEAR)) {

            final Tile sourceTile = getSrcTile(iBandName, (int)rangeIndex, (int)azimuthIndex, 2, 2);
            final Tile sourceTile2 = getSrcTile(qBandName, (int)rangeIndex, (int)azimuthIndex, 2, 2);
            return getPixelValueUsingBilinearInterp(azimuthIndex, rangeIndex,
                    bandPolar, bandUnit, sourceImageWidth, sourceImageHeight, sourceTile, sourceTile2, subSwathIndex);

        } else if (imgResampling.equals(ResampleMethod.RESAMPLE_CUBIC)) {

            final Tile sourceTile = getSrcTile(iBandName, Math.max(0, (int)rangeIndex - 1),
                                                Math.max(0, (int)azimuthIndex - 1), 4, 4);
            final Tile sourceTile2 = getSrcTile(qBandName, Math.max(0, (int)rangeIndex - 1),
                                                Math.max(0, (int)azimuthIndex - 1), 4, 4);
            return getPixelValueUsingBicubicInterp(azimuthIndex, rangeIndex,
                    bandPolar, bandUnit, sourceImageWidth, sourceImageHeight, sourceTile, sourceTile2, subSwathIndex);
        } else {
            throw new OperatorException("Unknown interpolation method");
        }
    }

    private Tile getSrcTile(String bandName, int minX, int minY, int width, int height) {
        if(bandName == null)
            return null;

        final Band sourceBand = sourceProduct.getBand(bandName);
        final Rectangle srcRect = new Rectangle(minX, minY, width, height);
        return getSourceTile(sourceBand, srcRect, ProgressMonitor.NULL);
    }

    /**
     * Get source image pixel value using nearest neighbot interpolation.
     * @param azimuthIndex The azimuth index for pixel in source image.
     * @param rangeIndex The range index for pixel in source image.
     * @param bandPolar The source band polarization index.
     * @param bandUnit The source band unit.
     * @param sourceTile  i
     * @param sourceTile2 q
     * @param subSwathIndex The sub swath index for current pixel for wide swath product case.
     * @return The pixel value.
     */
    private double getPixelValueUsingNearestNeighbourInterp(final double azimuthIndex, final double rangeIndex,
            final int bandPolar, final Unit.UnitType bandUnit, final Tile sourceTile, final Tile sourceTile2,
            int[] subSwathIndex) {

        final int x0 = (int)rangeIndex;
        final int y0 = (int)azimuthIndex;

        double v = 0.0;
        if (bandUnit == Unit.UnitType.AMPLITUDE || bandUnit == Unit.UnitType.INTENSITY || bandUnit == Unit.UnitType.INTENSITY_DB) {

            v = sourceTile.getDataBuffer().getElemDoubleAt(sourceTile.getDataBufferIndex(x0, y0));

        } else if (bandUnit == Unit.UnitType.REAL || bandUnit == Unit.UnitType.IMAGINARY) {

            final double vi = sourceTile.getDataBuffer().getElemDoubleAt(sourceTile.getDataBufferIndex(x0, y0));
            final double vq = sourceTile2.getDataBuffer().getElemDoubleAt(sourceTile2.getDataBufferIndex(x0, y0));
            v = vi*vi + vq*vq;

        } else {
            throw new OperatorException("Uknown band unit");
        }

        if (retroCalibrationFlag) {
            v = applyRetroCalibration(x0, y0, v, bandPolar, bandUnit, subSwathIndex);
        }

        return v;
    }

    /**
     * Get source image pixel value using bilinear interpolation.
     * @param azimuthIndex The azimuth index for pixel in source image.
     * @param rangeIndex The range index for pixel in source image.
     * @param bandPolar The polarization of the source band.
     * @param bandUnit The source band unit.
     * @param sceneRasterWidth the product width
     * @param sceneRasterHeight the product height
     * @param sourceTile  i
     * @param sourceTile2 q
     * @param subSwathIndex The sub swath index for current pixel for wide swath product case.
     * @return The pixel value.
     */
    private double getPixelValueUsingBilinearInterp(final double azimuthIndex, final double rangeIndex,
                                                    final int bandPolar, final Unit.UnitType bandUnit,
                                                    final int sceneRasterWidth, final int sceneRasterHeight,
                                                    final Tile sourceTile, final Tile sourceTile2, int[] subSwathIndex) {

        final int x0 = (int)rangeIndex;
        final int y0 = (int)azimuthIndex;
        final int x1 = Math.min(x0 + 1, sceneRasterWidth - 1);
        final int y1 = Math.min(y0 + 1, sceneRasterHeight - 1);
        final double dx = rangeIndex - x0;
        final double dy = azimuthIndex - y0;

        final ProductData srcData = sourceTile.getDataBuffer();

        double v00, v01, v10, v11;
        if (bandUnit == Unit.UnitType.AMPLITUDE || bandUnit == Unit.UnitType.INTENSITY || bandUnit == Unit.UnitType.INTENSITY_DB) {

            v00 = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x0, y0));
            v01 = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x1, y0));
            v10 = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x0, y1));
            v11 = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x1, y1));

        } else if (bandUnit == Unit.UnitType.REAL || bandUnit == Unit.UnitType.IMAGINARY) {

            final ProductData srcData2 = sourceTile2.getDataBuffer();

            final double vi00 = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x0, y0));
            final double vi01 = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x1, y0));
            final double vi10 = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x0, y1));
            final double vi11 = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x1, y1));

            final double vq00 = srcData2.getElemDoubleAt(sourceTile2.getDataBufferIndex(x0, y0));
            final double vq01 = srcData2.getElemDoubleAt(sourceTile2.getDataBufferIndex(x1, y0));
            final double vq10 = srcData2.getElemDoubleAt(sourceTile2.getDataBufferIndex(x0, y1));
            final double vq11 = srcData2.getElemDoubleAt(sourceTile2.getDataBufferIndex(x1, y1));

            v00 = vi00*vi00 + vq00*vq00;
            v01 = vi01*vi01 + vq01*vq01;
            v10 = vi10*vi10 + vq10*vq10;
            v11 = vi11*vi11 + vq11*vq11;

        } else {
            throw new OperatorException("Uknown band unit");
        }

        int[] subSwathIndex00 = {0};
        int[] subSwathIndex01 = {0};
        int[] subSwathIndex10 = {0};
        int[] subSwathIndex11 = {0};
        double v = 0;

        if (retroCalibrationFlag) {

            v00 = applyRetroCalibration(x0, y0, v00, bandPolar, bandUnit, subSwathIndex00);
            v01 = applyRetroCalibration(x1, y0, v01, bandPolar, bandUnit, subSwathIndex01);
            v10 = applyRetroCalibration(x0, y1, v10, bandPolar, bandUnit, subSwathIndex10);
            v11 = applyRetroCalibration(x1, y1, v11, bandPolar, bandUnit, subSwathIndex11);

            if (dx <= 0.5 && dy <= 0.5) {
                subSwathIndex[0] = subSwathIndex00[0];
                v = v00;
            } else if (dx > 0.5 && dy <= 0.5) {
                subSwathIndex[0] = subSwathIndex01[0];
                v = v01;
            } else if (dx <= 0.5 && dy > 0.5) {
                subSwathIndex[0] = subSwathIndex10[0];
                v = v10;
            } else if (dx > 0.5 && dy > 0.5) {
                subSwathIndex[0] = subSwathIndex11[0];
                v = v11;
            }
        }

        if (subSwathIndex00[0] == subSwathIndex01[0] &&
            subSwathIndex00[0] == subSwathIndex10[0] &&
            subSwathIndex00[0] == subSwathIndex11[0]) {
            return MathUtils.interpolationBiLinear(v00, v01, v10, v11, dx, dy);
        } else {
            return v;
        }
    }

    /**
     * Get source image pixel value using bicubic interpolation.
     * @param azimuthIndex The azimuth index for pixel in source image.
     * @param rangeIndex The range index for pixel in source image.
     * @param bandPolar The polarization of the source band.
     * @param bandUnit The source band unit.
     * @param sceneRasterWidth the product width
     * @param sceneRasterHeight the product height
     * @param sourceTile  i
     * @param sourceTile2 q
     * @param subSwathIndex The sub swath index for current pixel for wide swath product case.
     * @return The pixel value.
     */
    private double getPixelValueUsingBicubicInterp(final double azimuthIndex, final double rangeIndex,
                                                   final int bandPolar, final Unit.UnitType bandUnit,
                                                   final int sceneRasterWidth, final int sceneRasterHeight,
                                                   final Tile sourceTile, final Tile sourceTile2, int[] subSwathIndex) {

        final int [] x = new int[4];
        x[1] = (int)rangeIndex;
        x[0] = Math.max(0, x[1] - 1);
        x[2] = Math.min(x[1] + 1, sceneRasterWidth - 1);
        x[3] = Math.min(x[1] + 2, sceneRasterWidth - 1);

        final int [] y = new int[4];
        y[1] = (int)azimuthIndex;
        y[0] = Math.max(0, y[1] - 1);
        y[2] = Math.min(y[1] + 1, sceneRasterHeight - 1);
        y[3] = Math.min(y[1] + 2, sceneRasterHeight - 1);

        final ProductData srcData = sourceTile.getDataBuffer();

        final double[][] v = new double[4][4];
        if (bandUnit == Unit.UnitType.AMPLITUDE || bandUnit == Unit.UnitType.INTENSITY || bandUnit == Unit.UnitType.INTENSITY_DB) {

            for (int i = 0; i < y.length; i++) {
                for (int j = 0; j < x.length; j++) {
                    v[i][j] = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x[j], y[i]));
                }
            }

        } else if (bandUnit == Unit.UnitType.REAL || bandUnit == Unit.UnitType.IMAGINARY) {

            final ProductData srcData2 = sourceTile2.getDataBuffer();
            for (int i = 0; i < y.length; i++) {
                for (int j = 0; j < x.length; j++) {
                    final double vi = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x[j], y[i]));
                    final double vq = srcData2.getElemDoubleAt(sourceTile2.getDataBufferIndex(x[j], y[i]));
                    v[i][j] = vi*vi + vq*vq;
                }
            }

        } else {
            throw new OperatorException("Uknown band unit");
        }

        int[][][] ss = new int[4][4][1];
        if (retroCalibrationFlag) {
            for (int i = 0; i < y.length; i++) {
                for (int j = 0; j < x.length; j++) {
                    v[i][j] = applyRetroCalibration(x[j], y[i], v[i][j], bandPolar, bandUnit, ss[i][j]);
                }
            }
        }

        final double dx = rangeIndex - x[1];
        final double dy = azimuthIndex - y[1];
        double vv = 0;
        if (dx <= 0.5 && dy <= 0.5) {
            subSwathIndex[0] = ss[1][1][0];
            vv = v[1][1];
        } else if (dx > 0.5 && dy <= 0.5) {
            subSwathIndex[0] = ss[1][2][0];
            vv = v[1][2];
        } else if (dx <= 0.5 && dy > 0.5) {
            subSwathIndex[0] = ss[2][1][0];
            vv = v[2][1];
        } else if (dx > 0.5 && dy > 0.5) {
            subSwathIndex[0] = ss[2][2][0];
            vv = v[2][2];
        }

        if (ss[1][1][0] == ss[1][2][0] && ss[1][1][0] == ss[2][1][0] && ss[1][1][0] == ss[2][2][0]) {
            return MathUtils.interpolationBiCubic(v, rangeIndex - x[1], azimuthIndex - y[1]);
        } else {
            return vv;
        }
    }

    /**
     * Remove the antenna pattern compensation and range spreading loss applied to the pixel.
     * @param x The x coordinate of the pixel in the source image.
     * @param y The y coordinate of the pixel in the source image.
     * @param v The pixel value.
     * @param bandPolar The polarization of the source band.
     * @param bandUnit The source band unit.
     * @param subSwathIndex The sub swath index for current pixel for wide swath product case.
     * @return The pixel value with antenna pattern compensation and range spreading loss correction removed.
     */
    private double applyRetroCalibration(int x, int y, double v, int bandPolar, final Unit.UnitType bandUnit, int[] subSwathIndex) {

        final double zeroDopplerTime = firstLineUTC + y*lineTimeInterval;

        final double slantRange = getOldSlantRange(x, zeroDopplerTime);

        //final double earthRadius = ASARCalibrationOperator.computeEarthRadius(latitude.getPixelFloat(x, y),
        //                                                                      longitude.getPixelFloat(x, y));

        int i = (int)((latitude.getPixelFloat(x, y) - latMin)/delLat + 0.5);

        final double elevationAngle = ASARCalibrationOperator.computeElevationAngle(
                                            slantRange, oldSatelliteHeight, avgSceneHeight + earthRadius[i]);

        double gain = 0.0;
        if (wideSwathProductFlag) {
            gain = getAntennaPatternGain(elevationAngle, bandPolar, oldRefElevationAngle, oldAntennaPatternWideSwath, true, subSwathIndex);
        } else {
            gain = ASARCalibrationOperator.computeAntPatGain(elevationAngle, oldRefElevationAngle[0], oldAntennaPatternSingleSwath[bandPolar]);
        }

        if (bandUnit == Unit.UnitType.AMPLITUDE) {
            return v*gain*Math.pow(refSlantRange / slantRange, halfRangeSpreadingCompPower); // amplitude
        } else if (bandUnit == Unit.UnitType.INTENSITY || bandUnit == Unit.UnitType.REAL || bandUnit == Unit.UnitType.IMAGINARY) {
            return v*gain*gain*Math.pow(refSlantRange / slantRange, rangeSpreadingCompPower); // intensity
        } else if (bandUnit == Unit.UnitType.INTENSITY_DB) {
            return 10.0*Math.log10(Math.pow(10, v/10.0)*gain*gain*Math.pow(refSlantRange/slantRange, rangeSpreadingCompPower));
        } else {
            throw new OperatorException("Uknown band unit");
        }
    }

    /**
     * Get old slant range for given pixel.
     * @param x The x coordinate of the pixel in the source image.
     * @param zeroDopplerTime The zero doppler time for the given pixel.
     * @return The slant range (in meters).
     */
    private double getOldSlantRange(int x, double zeroDopplerTime) {
        int idx = 0;
        for (int i = 0; i < srgrConvParams.length && zeroDopplerTime >= srgrConvParamsTime[i]; i++) {
            idx = i;
        }
        return oldSlantRange[idx][x];
    }

    /**
     * Get antenna pattern gain value for given elevation angle.
     * @param elevationAngle The elevation angle (in degree).
     * @param bandPolar The source band polarization index.
     * @param refElevationAngle The reference elevation angles for different swathes or sub swathes.
     * @param antennaPattern The antenna pattern array. For single swath product, it contains two 201-length arrays
     *                       corresponding to the two bands of different polarizations. For wide swath product, it
     *                       contains five 201-length arrays with each for a sub swath.
     * @param compSubSwathIdx The boolean flag indicating if sub swath index should be computed.
     * @param subSwathIndex The sub swath index for current pixel for wide swath product case.
     * @return The antenna pattern gain value.
     */
    private static double getAntennaPatternGain(double elevationAngle, int bandPolar, double[] refElevationAngle,
                                         float[][] antennaPattern, boolean compSubSwathIdx, int[] subSwathIndex) {

        if (refElevationAngle.length == 1) { // single swath

            return ASARCalibrationOperator.computeAntPatGain(elevationAngle, refElevationAngle[0], antennaPattern[bandPolar]);

        } else { // wide swath

            if (compSubSwathIdx || subSwathIndex[0] == INVALID_SUB_SWATH_INDEX) {
                subSwathIndex[0] = ASARCalibrationOperator.findSubSwath(elevationAngle, refElevationAngle);
            }

            return ASARCalibrationOperator.computeAntPatGain(
                    elevationAngle, refElevationAngle[subSwathIndex[0]], antennaPattern[subSwathIndex[0]]);
        }
    }

    /**
     * Apply calibrations to the given point. The following calibrations are included: calibration constant,
     * antenna pattern compensation, range spreading loss correction and incidence angle correction.
     * @param slantRange The slant range (in m).
     * @param sensorPos The satellite position.
     * @param earthPoint The backscattering element position.
     * @param localIncidenceAngle The local incidence angle (in degrees).
     * @param bandPolar The source band polarization index.
     * @param v The pixel value.
     * @param bandUnit The source band unit.
     * @param subSwathIndex The sub swath index for current pixel for wide swath product case.
     * @return The calibrated pixel value.
     */
    private double applyCalibration(final double slantRange, final double[] sensorPos, final double[] earthPoint,
                                    final double localIncidenceAngle, final int bandPolar, final double v,
                                    final Unit.UnitType bandUnit, int[] subSwathIndex) {

        double sigma = 0.0;
        if (bandUnit == Unit.UnitType.AMPLITUDE) {
            sigma = v*v;
        } else if (bandUnit == Unit.UnitType.INTENSITY || bandUnit == Unit.UnitType.REAL || bandUnit == Unit.UnitType.IMAGINARY) {
            sigma = v;
        } else if (bandUnit == Unit.UnitType.INTENSITY_DB) {
            sigma = Math.pow(10, v/10.0); // convert dB to linear scale
        } else {
            throw new OperatorException("Uknown band unit");
        }

        if (!listedASARProductFlag || multilookFlag) { // calibration constant and incidence angle corrections only
            return sigma / newCalibrationConstant[bandPolar] *
                   Math.sin(Math.abs(localIncidenceAngle)*org.esa.beam.util.math.MathUtils.DTOR);
        }

        final double satelliteHeight = Math.sqrt(
                sensorPos[0]*sensorPos[0] + sensorPos[1]*sensorPos[1] + sensorPos[2]*sensorPos[2]);

        final double sceneToEarthCentre = Math.sqrt(
                earthPoint[0]*earthPoint[0] + earthPoint[1]*earthPoint[1] + earthPoint[2]*earthPoint[2]);

        final double elevationAngle = ASARCalibrationOperator.computeElevationAngle(
                slantRange, satelliteHeight, sceneToEarthCentre); // in degrees

        double gain;
        if (wideSwathProductFlag) {
            gain = getAntennaPatternGain(
                    elevationAngle, bandPolar, newRefElevationAngle, newAntennaPatternWideSwath, false, subSwathIndex);
        } else {
            //gain = getAntennaPatternGain(
            //        elevationAngle, bandPolar, newRefElevationAngle, newAntennaPatternSingleSwath, false, subSwathIndex);
            gain = ASARCalibrationOperator.computeAntPatGain(elevationAngle, newRefElevationAngle[0], newAntennaPatternSingleSwath[bandPolar]);
        }

        return sigma / newCalibrationConstant[bandPolar] / (gain*gain) *
               Math.pow(slantRange/refSlantRange, rangeSpreadingCompPower) *
               Math.sin(Math.abs(localIncidenceAngle)*org.esa.beam.util.math.MathUtils.DTOR);
    }

    /**
     * Compute projected local incidence angle (in degree).
     * @param sensorPos The satellite position.
     * @param centrePoint The backscattering element position.
     * @param lat Latitude of the given point.
     * @param lon Longitude of the given point.
     * @param x0 The x coordinate of the pixel at the upper left corner of current tile.
     * @param y0 The y coordinate of the pixel at the upper left corner of current tile.
     * @param x The x coordinate of the current pixel.
     * @param y The y coordinate of the current pixel.
     * @param localDEM The local DEM.
     * @param localIncidenceAngles The local incidence angle and projected local incidence angle.
     */
    private void computeLocalIncidenceAngle(final double[] sensorPos, final double[] centrePoint,
                                            final double lat, final double lon, final int x0, final int y0,
                                            final int x, final int y, final float[][] localDEM,
                                            double[] localIncidenceAngles) {

        // Note: For algorithm and notation of the following implementation, please see Andrea's email dated
        //       May 29, 2009 and Marcus' email dated June 3, 2009, or see Eq (14.10) and Eq (14.11) on page
        //       321 and 323 in "SAR Geocoding - Data and Systems".
        //       The Cartesian coordinate (x, y, z) is represented here by a length-3 array with element[0]
        //       representing x, element[1] representing y and element[2] representing z.

        final double rightPointHeight = (localDEM[y - y0][x - x0 + 2] +
                                         localDEM[y - y0 + 1][x - x0 + 2] +
                                         localDEM[y - y0 + 2][x - x0 + 2]) / 3.0;

        final double leftPointHeight = (localDEM[y - y0][x - x0] +
                                         localDEM[y - y0 + 1][x - x0] +
                                         localDEM[y - y0 + 2][x - x0]) / 3.0;

        final double upPointHeight = (localDEM[y - y0][x - x0] +
                                        localDEM[y - y0][x - x0 + 1] +
                                        localDEM[y - y0][x - x0 + 2]) / 3.0;

        final double downPointHeight = (localDEM[y - y0 + 2][x - x0] +
                                        localDEM[y - y0 + 2][x - x0 + 1] +
                                        localDEM[y - y0 + 2][x - x0 + 2]) / 3.0;

        final double[] rightPoint = new double[3];
        final double[] leftPoint = new double[3];
        final double[] upPoint = new double[3];
        final double[] downPoint = new double[3];
        GeoUtils.geo2xyz(lat, lon + delLon, rightPointHeight, rightPoint, GeoUtils.EarthModel.WGS84);
        GeoUtils.geo2xyz(lat, lon - delLon, leftPointHeight, leftPoint, GeoUtils.EarthModel.WGS84);
        GeoUtils.geo2xyz(lat + delLat, lon, upPointHeight, upPoint, GeoUtils.EarthModel.WGS84);
        GeoUtils.geo2xyz(lat - delLat, lon, downPointHeight, downPoint, GeoUtils.EarthModel.WGS84);

        final double[] a = {rightPoint[0] - leftPoint[0], rightPoint[1] - leftPoint[1], rightPoint[2] - leftPoint[2]};
        final double[] b = {downPoint[0] - upPoint[0], downPoint[1] - upPoint[1], downPoint[2] - upPoint[2]};
        final double[] c = {centrePoint[0], centrePoint[1], centrePoint[2]};

        double[] n = {a[1]*b[2] - a[2]*b[1], a[2]*b[0] - a[0]*b[2], a[0]*b[1] - a[1]*b[0]}; // ground plane normal
        normalizeVector(n);
        if (innerProduct(n, c) < 0) {
            n[0] = -n[0];
            n[1] = -n[1];
            n[2] = -n[2];
        }

        double[] s = {sensorPos[0] - centrePoint[0], sensorPos[1] - centrePoint[1], sensorPos[2] - centrePoint[2]};
        normalizeVector(s);

        final double nsInnerProduct = innerProduct(n, s);

        if (saveLocalIncidenceAngle) { // local incidence angle
            localIncidenceAngles[0] = Math.acos(nsInnerProduct) * org.esa.beam.util.math.MathUtils.RTOD;
        }

        if (saveProjectedLocalIncidenceAngle || applyRadiometricCalibration) { // projected local incidence angle
            double[] m = {s[1]*c[2] - s[2]*c[1], s[2]*c[0] - s[0]*c[2], s[0]*c[1] - s[1]*c[0]}; // range plane normal
            normalizeVector(m);
            final double mnInnerProduct = innerProduct(m, n);
            double[] n1 = {n[0] - m[0]*mnInnerProduct, n[1] - m[1]*mnInnerProduct, n[2] - m[2]*mnInnerProduct};
            normalizeVector(n1);
            localIncidenceAngles[1] = Math.acos(innerProduct(n1, s)) * org.esa.beam.util.math.MathUtils.RTOD;
        }
    }

    private static void normalizeVector(double[] v) {
        final double norm = Math.sqrt(innerProduct(v, v));
        v[0] = v[0] / norm;
        v[1] = v[1] / norm;
        v[2] = v[2] / norm;
    }

    private static double innerProduct(final double[] a, final double[] b) {
        return a[0]*b[0] + a[1]*b[1] + a[2]*b[2];
    }

    private static class TileData {
        Tile targetTile = null;
        ProductData tileDataBuffer = null;
        String bandName = null;
        int bandPolar = 0;
        double noDataValue = 0;
    }

    /**
     * The SPI is used to register this operator in the graph processing framework
     * via the SPI configuration file
     * {@code META-INF/services/org.esa.beam.framework.gpf.OperatorSpi}.
     * This class may also serve as a factory for new operator instances.
     * @see org.esa.beam.framework.gpf.OperatorSpi#createOperator()
     * @see org.esa.beam.framework.gpf.OperatorSpi#createOperator(java.util.Map, java.util.Map)
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(AutomatedTerrainCorrectionOp.class);
        }
    }
}