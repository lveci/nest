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
import org.esa.beam.framework.dataop.maptransf.IdentityTransformDescriptor;
import org.esa.beam.util.ProductUtils;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.datamodel.Calibrator;
import org.esa.nest.datamodel.CalibrationFactory;
import org.esa.nest.util.MathUtils;
import org.esa.nest.util.GeoUtils;
import org.esa.nest.util.Constants;
import org.esa.nest.util.Settings;
import org.esa.nest.dataio.ReaderUtils;

import java.awt.*;
import java.util.*;
import java.io.File;
import java.io.IOException;

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

@OperatorMetadata(alias="SARSim-Terrain-Correction", category = "Geometry",
        description="Orthorectification with SAR simulation")
public class SARSimTerrainCorrectionOp extends Operator {

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

    @Parameter(description = "The pixel spacing", defaultValue = "", label="Pixel Spacing (m)")
    private String pixelSpacingStr = null;

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
    private WarpOp.WarpData warpData = null;
    private FileElevationModel fileElevationModel = null;

    private boolean srgrFlag = false;
    private boolean useExternalDEMFile = false;
    private boolean applyUserSelectedPixelSpacing = false;

    private String mission = null;
    private String[] mdsPolar = new String[2]; // polarizations for the two bands in the product
    private String demName = null;

    private int sourceImageWidth = 0;
    private int sourceImageHeight = 0;
    private int targetImageWidth = 0;
    private int targetImageHeight = 0;

    private double avgSceneHeight = 0.0; // in m
    private double wavelength = 0.0; // in m
    private double rangeSpacing = 0.0;
    private double azimuthSpacing = 0.0;
    private double firstLineUTC = 0.0; // in days
    private double lastLineUTC = 0.0; // in days
    private double lineTimeInterval = 0.0; // in days
    private double nearEdgeSlantRange = 0.0; // in m
    private float demNoDataValue = 0; // no data value for DEM
    private double latMin = 0.0;
    private double latMax = 0.0;
    private double lonMin = 0.0;
    private double lonMax= 0.0;
    private double delLat = 0.0;
    private double delLon = 0.0;
    private double pixelSpacing = 0.0;

    private double[][] sensorPosition = null; // sensor position for all range lines
    private double[][] sensorVelocity = null; // sensor velocity for all range lines
    private double[] timeArray = null;
    private double[] xPosArray = null;
    private double[] yPosArray = null;
    private double[] zPosArray = null;

    private AbstractMetadata.SRGRCoefficientList[] srgrConvParams = null;
    private AbstractMetadata.OrbitStateVector[] orbitStateVectors = null;
    private final HashMap<String, String[]> targetBandNameToSourceBandName = new HashMap<String, String[]>();

    static final String NEAREST_NEIGHBOUR = "Nearest Neighbour";
    static final String BILINEAR = "Bilinear Interpolation";
    static final String CUBIC = "Cubic Convolution";
    private static final double MeanEarthRadius = 6371008.7714; // in m (WGS84)
    private static final double NonValidZeroDopplerTime = -99999.0;
    private static final int INVALID_SUB_SWATH_INDEX = -1;
    
    private enum ResampleMethod { RESAMPLE_NEAREST_NEIGHBOUR, RESAMPLE_BILINEAR, RESAMPLE_CUBIC }
    private ResampleMethod imgResampling = null;

    private Map<String, ArrayList<Tile>> tileCache = new HashMap<String, ArrayList<Tile>>(2);

    boolean useAvgSceneHeight = false;
    Calibrator calibrator = null;

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

            if (pixelSpacingStr != null && !pixelSpacingStr.equals("")) {
                getUserSelectedPixelSpacing();
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
                calibrator = CalibrationFactory.createCalibrator(sourceProduct);
                calibrator.initialize(sourceProduct, targetProduct);
                getProductPolarization();
            }

            updateTargetProductMetadata();

        } catch(Exception e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    /**
     * Get user selected pixel spacing (in m).
     */
    private void getUserSelectedPixelSpacing() {

        pixelSpacing = Double.parseDouble(pixelSpacingStr);
        if (pixelSpacing <= 0.0) {
            throw new OperatorException("Invalid value for pixel spacing: " + pixelSpacingStr);
        }
        applyUserSelectedPixelSpacing = true;
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
            if(!absRoot.getAttributeString(AbstractMetadata.SAMPLE_TYPE).contains("COMPLEX")) {
                throw new OperatorException("Only level 1.1 ALOS PALSAR product is supported");
            }
        }
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
     * Get average scene height from abstracted metadata.
     * @throws Exception The exceptions.
     */
    private void getAverageSceneHeight() throws Exception {
        avgSceneHeight = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.avg_scene_height);
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
        final GeoPos geoPosFirstFar = geoCoding.getGeoPos(new PixelPos(sourceProduct.getSceneRasterWidth()-1,0), null);
        final GeoPos geoPosLastNear = geoCoding.getGeoPos(new PixelPos(0,sourceProduct.getSceneRasterHeight()-1), null);
        final GeoPos geoPosLastFar = geoCoding.getGeoPos(new PixelPos(sourceProduct.getSceneRasterWidth()-1,
                                                                      sourceProduct.getSceneRasterHeight()-1), null);
        
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

        double spacing = 0.0;
        if (applyUserSelectedPixelSpacing) {
            spacing = pixelSpacing;
        } else {
            spacing = Math.min(rangeSpacing, azimuthSpacing);
        }

        double minAbsLat;
        if (latMin*latMax > 0) {
            minAbsLat = Math.min(Math.abs(latMin), Math.abs(latMax)) * org.esa.beam.util.math.MathUtils.DTOR;
        } else {
            minAbsLat = 0.0;
        }
        delLat = spacing / MeanEarthRadius * org.esa.beam.util.math.MathUtils.RTOD;
        delLon = spacing / (MeanEarthRadius*Math.cos(minAbsLat)) * org.esa.beam.util.math.MathUtils.RTOD;
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
        if (firstLineUTC >= lastLineUTC) {
            throw new OperatorException("First line time should be smaller than the last line time");
        }
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

        if (demDescriptor != null) {

            if (demDescriptor.isInstallingDem()) {
                throw new OperatorException("The DEM '" + demName + "' is currently being installed.");
            }

            dem = demDescriptor.createDem(getResamplingMethod());
            if(dem == null) {
                throw new OperatorException("The DEM '" + demName + "' has not been installed.");
            }

            demNoDataValue = dem.getDescriptor().getNoDataValue();

        } else { // then demName is user selected DEM file name
            
            File externalDemFile = new File(demName);
            fileElevationModel = new FileElevationModel(externalDemFile, getResamplingMethod());
            demNoDataValue = fileElevationModel.getNoDataValue();
            demName = externalDemFile.getName();
            useExternalDEMFile = true;
        }
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

        ProductUtils.copyMetadata(sourceProduct, targetProduct);

        addLayoverShadowBitmasks(targetProduct);
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

            if (applyRadiometricCalibration) {
                targetBandName = "Sigma0";
            } else {
                targetBandName = srcBand.getName();
            }

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

        ReaderUtils.createMapGeocoding(targetProduct, IdentityTransformDescriptor.NAME,
                sourceProduct.getBand(srcBandNames[0]).getNoDataValue());
    }

    /**
     * Update metadata in the target product.
     * @throws OperatorException The exception.
     */
    private void updateTargetProductMetadata() throws OperatorException {

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

        final int numVectorsUsed = Math.min(orbitStateVectors.length, 5);
        timeArray = new double[numVectorsUsed];
        xPosArray = new double[numVectorsUsed];
        yPosArray = new double[numVectorsUsed];
        zPosArray = new double[numVectorsUsed];
        sensorPosition = new double[sourceImageHeight][3]; // xPos, yPos, zPos
        sensorVelocity = new double[sourceImageHeight][3]; // xVel, yVel, zVel

        RangeDopplerGeocodingOp.computeSensorPositionsAndVelocities(orbitStateVectors, timeArray, xPosArray, yPosArray, zPosArray,
                sensorPosition, sensorVelocity, firstLineUTC, lineTimeInterval, sourceImageHeight);
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

                    double slantRange = RangeDopplerGeocodingOp.computeSlantRange(
                            zeroDopplerTime, timeArray, xPosArray, yPosArray, zPosArray, earthPoint, sensorPos);

                    final double zeroDopplerTimeWithoutBias = zeroDopplerTime + slantRange / halfLightSpeedInMetersPerDay;

                    final double azimuthIndex = (zeroDopplerTimeWithoutBias - firstLineUTC) / lineTimeInterval;

                    slantRange = RangeDopplerGeocodingOp.computeSlantRange(
                            zeroDopplerTimeWithoutBias, timeArray, xPosArray, yPosArray, zPosArray, earthPoint, sensorPos);

                    double[] localIncidenceAngles = {0.0, 0.0};
                    if (saveLocalIncidenceAngle || saveProjectedLocalIncidenceAngle || applyRadiometricCalibration) {

                        final RangeDopplerGeocodingOp.LocalGeometry localGeometry =
                                new RangeDopplerGeocodingOp.LocalGeometry(lat, lon, delLat, delLon, earthPoint, sensorPos);

                        RangeDopplerGeocodingOp.computeLocalIncidenceAngle(
                                localGeometry, saveLocalIncidenceAngle, saveProjectedLocalIncidenceAngle,
                                applyRadiometricCalibration, x0, y0, x, y, localDEM, localIncidenceAngles); // in degrees

                        if (saveLocalIncidenceAngle) {
                            incidenceAngleBuffer.setElemDoubleAt(index, localIncidenceAngles[0]);
                        }

                        if (saveProjectedLocalIncidenceAngle) {
                            projectedIncidenceAngleBuffer.setElemDoubleAt(index, localIncidenceAngles[1]);
                        }
                    }

                    final double rangeIndex = RangeDopplerGeocodingOp.computeRangeIndex(
                            srgrFlag, sourceImageWidth, firstLineUTC, lastLineUTC, rangeSpacing,
                            zeroDopplerTimeWithoutBias, slantRange, nearEdgeSlantRange, srgrConvParams);

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
                                double v = getPixelValue(pixelPos.y, pixelPos.x, tileData, bandUnit, subSwathIndex);

                                if (applyRadiometricCalibration) {

                                    final double satelliteHeight = Math.sqrt(
                                            sensorPos[0]*sensorPos[0] + sensorPos[1]*sensorPos[1] + sensorPos[2]*sensorPos[2]);

                                    final double sceneToEarthCentre = Math.sqrt(
                                            earthPoint[0]*earthPoint[0] + earthPoint[1]*earthPoint[1] + earthPoint[2]*earthPoint[2]);

                                    v = calibrator.applyCalibration(
                                            v, slantRange, satelliteHeight, sceneToEarthCentre, localIncidenceAngles[1],
                                            tileData.bandPolar, bandUnit, subSwathIndex); // use projected incidence angle
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
                localDEM[yy][x - x0 + 1] = getLocalElevation(geoPos);
            }
        }
    }

    /**
     * Get local elevation (in meter) for given latitude and longitude.
     * @param geoPos The latitude and longitude in degrees.
     * @return The elevation in meter.
     */
    private float getLocalElevation(final GeoPos geoPos) {
        try {
            if (!useExternalDEMFile) {
                return dem.getElevation(geoPos);
            }
            return fileElevationModel.getElevation(geoPos);
        } catch (Exception e) {
            //
        }
        return demNoDataValue;
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
     * @param tileData The source tile information.
     * @param bandUnit The corresponding source band unit.
     * @param subSwathIndex The subswath index.
     * @return The pixel value.
     * @throws IOException from readPixels
     */
    private double getPixelValue(final double azimuthIndex, final double rangeIndex,
                                 final TileData tileData, Unit.UnitType bandUnit, int[] subSwathIndex)
            throws IOException {

        final String[] srcBandNames = targetBandNameToSourceBandName.get(tileData.bandName);
        final String iBandName = srcBandNames[0];
        String qBandName = null;
        if (srcBandNames.length > 1) {
            qBandName = srcBandNames[1];
        }

        if (imgResampling.equals(ResampleMethod.RESAMPLE_NEAREST_NEIGHBOUR)) {

            final Tile sourceTile = getSrcTile(iBandName, (int)rangeIndex, (int)azimuthIndex, 1, 1);
            final Tile sourceTile2 = getSrcTile(qBandName, (int)rangeIndex, (int)azimuthIndex, 1, 1);
            return getPixelValueUsingNearestNeighbourInterp(
                    azimuthIndex, rangeIndex, tileData, bandUnit, sourceTile, sourceTile2, subSwathIndex);

        } else if (imgResampling.equals(ResampleMethod.RESAMPLE_BILINEAR)) {

            final Tile sourceTile = getSrcTile(iBandName, (int)rangeIndex, (int)azimuthIndex, 2, 2);
            final Tile sourceTile2 = getSrcTile(qBandName, (int)rangeIndex, (int)azimuthIndex, 2, 2);
            return getPixelValueUsingBilinearInterp(azimuthIndex, rangeIndex,
                    tileData, bandUnit, sourceImageWidth, sourceImageHeight, sourceTile, sourceTile2, subSwathIndex);

        } else if (imgResampling.equals(ResampleMethod.RESAMPLE_CUBIC)) {

            final Tile sourceTile = getSrcTile(iBandName, Math.max(0, (int)rangeIndex - 1),
                                                Math.max(0, (int)azimuthIndex - 1), 4, 4);
            final Tile sourceTile2 = getSrcTile(qBandName, Math.max(0, (int)rangeIndex - 1),
                                                Math.max(0, (int)azimuthIndex - 1), 4, 4);
            return getPixelValueUsingBicubicInterp(azimuthIndex, rangeIndex,
                    tileData, bandUnit, sourceImageWidth, sourceImageHeight, sourceTile, sourceTile2, subSwathIndex);
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
     * @param tileData The source tile information.
     * @param bandUnit The source band unit.
     * @param sourceTile  i
     * @param sourceTile2 q
     * @param subSwathIndex The sub swath index for current pixel for wide swath product case.
     * @return The pixel value.
     */
    private double getPixelValueUsingNearestNeighbourInterp(final double azimuthIndex, final double rangeIndex,
            final TileData tileData, final Unit.UnitType bandUnit, final Tile sourceTile, final Tile sourceTile2,
            int[] subSwathIndex) {

        final int x0 = (int)rangeIndex;
        final int y0 = (int)azimuthIndex;

        double v = 0.0;
        if (bandUnit == Unit.UnitType.AMPLITUDE || bandUnit == Unit.UnitType.INTENSITY || bandUnit == Unit.UnitType.INTENSITY_DB) {

            v = sourceTile.getDataBuffer().getElemDoubleAt(sourceTile.getDataBufferIndex(x0, y0));
            if (v == tileData.noDataValue) {
                return tileData.noDataValue;
            }

        } else if (bandUnit == Unit.UnitType.REAL || bandUnit == Unit.UnitType.IMAGINARY) {

            final double vi = sourceTile.getDataBuffer().getElemDoubleAt(sourceTile.getDataBufferIndex(x0, y0));
            final double vq = sourceTile2.getDataBuffer().getElemDoubleAt(sourceTile2.getDataBufferIndex(x0, y0));
            if (vi == tileData.noDataValue || vq == tileData.noDataValue) {
                return tileData.noDataValue;
            }
            v = vi*vi + vq*vq;

        } else {
            throw new OperatorException("Uknown band unit");
        }

        if (applyRadiometricCalibration) {
            v = calibrator.applyRetroCalibration(x0, y0, v, tileData.bandPolar, bandUnit, subSwathIndex);
        }

        return v;
    }

    /**
     * Get source image pixel value using bilinear interpolation.
     * @param azimuthIndex The azimuth index for pixel in source image.
     * @param rangeIndex The range index for pixel in source image.
     * @param tileData The source tile information.
     * @param bandUnit The source band unit.
     * @param sceneRasterWidth the product width
     * @param sceneRasterHeight the product height
     * @param sourceTile  i
     * @param sourceTile2 q
     * @param subSwathIndex The sub swath index for current pixel for wide swath product case.
     * @return The pixel value.
     */
    private double getPixelValueUsingBilinearInterp(final double azimuthIndex, final double rangeIndex,
                                                    final TileData tileData, final Unit.UnitType bandUnit,
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

            if (v00 == tileData.noDataValue || v01 == tileData.noDataValue ||
                v10 == tileData.noDataValue || v11 == tileData.noDataValue) {
                return tileData.noDataValue;
            }


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

            if (vi00 == tileData.noDataValue || vi01 == tileData.noDataValue ||
                vi10 == tileData.noDataValue || vi11 == tileData.noDataValue ||
                vq00 == tileData.noDataValue || vq01 == tileData.noDataValue ||
                vq10 == tileData.noDataValue || vq11 == tileData.noDataValue) {
                return tileData.noDataValue;
            }

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

        if (applyRadiometricCalibration) {

            v00 = calibrator.applyRetroCalibration(x0, y0, v00, tileData.bandPolar, bandUnit, subSwathIndex00);
            v01 = calibrator.applyRetroCalibration(x1, y0, v01, tileData.bandPolar, bandUnit, subSwathIndex01);
            v10 = calibrator.applyRetroCalibration(x0, y1, v10, tileData.bandPolar, bandUnit, subSwathIndex10);
            v11 = calibrator.applyRetroCalibration(x1, y1, v11, tileData.bandPolar, bandUnit, subSwathIndex11);

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
     * @param tileData The source tile information.
     * @param bandUnit The source band unit.
     * @param sceneRasterWidth the product width
     * @param sceneRasterHeight the product height
     * @param sourceTile  i
     * @param sourceTile2 q
     * @param subSwathIndex The sub swath index for current pixel for wide swath product case.
     * @return The pixel value.
     */
    private double getPixelValueUsingBicubicInterp(final double azimuthIndex, final double rangeIndex,
                                                   final TileData tileData, final Unit.UnitType bandUnit,
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
                    if (v[i][j] == tileData.noDataValue) {
                        return tileData.noDataValue;
                    }
                }
            }

        } else if (bandUnit == Unit.UnitType.REAL || bandUnit == Unit.UnitType.IMAGINARY) {

            final ProductData srcData2 = sourceTile2.getDataBuffer();
            for (int i = 0; i < y.length; i++) {
                for (int j = 0; j < x.length; j++) {
                    final double vi = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x[j], y[i]));
                    final double vq = srcData2.getElemDoubleAt(sourceTile2.getDataBufferIndex(x[j], y[i]));
                    if (vi == tileData.noDataValue || vq == tileData.noDataValue) {
                        return tileData.noDataValue;
                    }
                    v[i][j] = vi*vi + vq*vq;
                }
            }

        } else {
            throw new OperatorException("Uknown band unit");
        }

        int[][][] ss = new int[4][4][1];
        if (applyRadiometricCalibration) {
            for (int i = 0; i < y.length; i++) {
                for (int j = 0; j < x.length; j++) {
                    v[i][j] = calibrator.applyRetroCalibration(x[j], y[i], v[i][j], tileData.bandPolar, bandUnit, ss[i][j]);
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
            super(SARSimTerrainCorrectionOp.class);
        }
    }
}