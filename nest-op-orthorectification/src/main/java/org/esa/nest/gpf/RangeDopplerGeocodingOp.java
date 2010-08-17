/*
 * Copyright (C) 2010 Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.nest.gpf;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.ProductProjectionBuilder;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.dem.ElevationModelRegistry;
import org.esa.beam.framework.dataop.maptransf.*;
import org.esa.beam.framework.dataop.resamp.ResamplingFactory;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.dataio.ReaderUtils;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.CalibrationFactory;
import org.esa.nest.datamodel.Calibrator;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.util.Constants;
import org.esa.nest.util.GeoUtils;
import org.esa.nest.util.MathUtils;

import java.awt.*;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

@OperatorMetadata(alias="Terrain-Correction", category = "Geometry", description="RD method for orthorectification")
public class RangeDopplerGeocodingOp extends Operator {

    public static final String PRODUCT_SUFFIX = "_TC";

    @SourceProduct(alias="source")
    Product sourceProduct;
    @TargetProduct
    Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            sourceProductId="source", label="Source Bands")
    private
    String[] sourceBandNames = null;

    @Parameter(valueSet = {"ACE", "GETASSE30", "SRTM 3Sec GeoTiff"}, description = "The digital elevation model.",
               defaultValue="SRTM 3Sec GeoTiff", label="Digital Elevation Model")
    private String demName = "SRTM 3Sec GeoTiff";

    @Parameter(label="External DEM")
    private File externalDEMFile = null;

    @Parameter(label="DEM No Data Value", defaultValue = "0")
    private double externalDEMNoDataValue = 0;

    @Parameter(valueSet = {ResamplingFactory.NEAREST_NEIGHBOUR_NAME,
            ResamplingFactory.BILINEAR_INTERPOLATION_NAME, ResamplingFactory.CUBIC_CONVOLUTION_NAME},
            defaultValue = ResamplingFactory.BILINEAR_INTERPOLATION_NAME, label="DEM Resampling Method")
    private String demResamplingMethod = ResamplingFactory.BILINEAR_INTERPOLATION_NAME;

    @Parameter(valueSet = {ResamplingFactory.NEAREST_NEIGHBOUR_NAME,
            ResamplingFactory.BILINEAR_INTERPOLATION_NAME, ResamplingFactory.CUBIC_CONVOLUTION_NAME},
            defaultValue = ResamplingFactory.BILINEAR_INTERPOLATION_NAME, label="Image Resampling Method")
    private String imgResamplingMethod = ResamplingFactory.BILINEAR_INTERPOLATION_NAME;

    @Parameter(description = "The pixel spacing in meters", defaultValue = "0", label="Pixel Spacing (m)")
    private double pixelSpacingInMeter = 0;

    @Parameter(description = "The pixel spacing in degrees", defaultValue = "0", label="Pixel Spacing (deg)")
    private double pixelSpacingInDegree = 0;

    @Parameter(description = "The projection name", defaultValue = IdentityTransformDescriptor.NAME)
    private String projectionName = IdentityTransformDescriptor.NAME;

    @Parameter(defaultValue="false", label="Save DEM as band")
    private boolean saveDEM = false;

    @Parameter(defaultValue="false", label="Save local incidence angle as band")
    private boolean saveLocalIncidenceAngle = false;

    @Parameter(defaultValue="false", label="Save projected local incidence angle as band")
    private boolean saveProjectedLocalIncidenceAngle = false;

    @Parameter(defaultValue="true", label="Save selected source band")
    private boolean saveSelectedSourceBand = true;

    @Parameter(defaultValue="false", label="Apply radiometric normalization")
    private boolean applyRadiometricNormalization = false;

    @Parameter(defaultValue="false", label="Save Sigma0 as a band")
    private boolean saveSigmaNought = false;

    @Parameter(defaultValue="false", label="Save Gamma0 as a band")
    private boolean saveGammaNought = false;

    @Parameter(defaultValue="false", label="Save Beta0 as a band")
    private boolean saveBetaNought = false;

    @Parameter(valueSet = {USE_INCIDENCE_ANGLE_FROM_ELLIPSOID, USE_INCIDENCE_ANGLE_FROM_DEM},
            defaultValue = USE_INCIDENCE_ANGLE_FROM_DEM, label="")
    private String incidenceAngleForSigma0 = USE_INCIDENCE_ANGLE_FROM_DEM;

    @Parameter(valueSet = {USE_INCIDENCE_ANGLE_FROM_ELLIPSOID, USE_INCIDENCE_ANGLE_FROM_DEM},
            defaultValue = USE_INCIDENCE_ANGLE_FROM_DEM, label="")
    private String incidenceAngleForGamma0 = USE_INCIDENCE_ANGLE_FROM_DEM;

    @Parameter(valueSet = {CalibrationOp.LATEST_AUX, CalibrationOp.PRODUCT_AUX, CalibrationOp.EXTERNAL_AUX},
            description = "The auxiliary file", defaultValue=CalibrationOp.LATEST_AUX, label="Auxiliary File")
    private String auxFile = CalibrationOp.LATEST_AUX;

    @Parameter(description = "The antenne elevation pattern gain auxiliary data file.", label="External Aux File")
    private File externalAuxFile = null;

    private MetadataElement absRoot = null;
    private ElevationModel dem = null;
    private FileElevationModel fileElevationModel = null;
    private GeoCoding targetGeoCoding = null;

    private boolean srgrFlag = false;
    private boolean saveIncidenceAngleFromEllipsoid = false;
    private boolean isElevationModelAvailable = false;
    private boolean usePreCalibrationOp = false;

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
    private float demNoDataValue = 0.0f; // no data value for DEM
    private final ImageGeoBoundary imageGeoBoundary = new ImageGeoBoundary();
    private double delLat = 0.0;
    private double delLon = 0.0;

    private double[][] sensorPosition = null; // sensor position for all range lines
    private double[][] sensorVelocity = null; // sensor velocity for all range lines
    private double[] timeArray = null;
    private double[] xPosArray = null;
    private double[] yPosArray = null;
    private double[] zPosArray = null;

    private AbstractMetadata.SRGRCoefficientList[] srgrConvParams = null;
    private AbstractMetadata.OrbitStateVector[] orbitStateVectors = null;
    private final HashMap<String, Band[]> targetBandNameToSourceBand = new HashMap<String, Band[]>();
    private final Map<String, Boolean> targetBandApplyRadiometricNormalizationFlag = new HashMap<String, Boolean>();
    private final Map<String, Boolean> targetBandApplyRetroCalibrationFlag = new HashMap<String, Boolean>();
    private TiePointGrid incidenceAngle = null;
    private TiePointGrid latitude = null;
    private TiePointGrid longitude = null;

    private static final double NonValidZeroDopplerTime = -99999.0;
    private static final double lightSpeedInMetersPerDay = Constants.lightSpeed * 86400.0;
    private static final int INVALID_SUB_SWATH_INDEX = -1;

    private enum ResampleMethod { RESAMPLE_NEAREST_NEIGHBOUR, RESAMPLE_BILINEAR, RESAMPLE_CUBIC }
    private ResampleMethod imgResampling = null;

    boolean useAvgSceneHeight = false;
    private Calibrator calibrator = null;
    private boolean orthoDataProduced = false;  // check if any ortho data is actually produced
    private boolean processingStarted = false;

    private boolean flipIndex = false; // temp fix for descending Radarsat2

    public static final String USE_INCIDENCE_ANGLE_FROM_DEM = "Use projected local incidence angle from DEM";
    public static final String USE_INCIDENCE_ANGLE_FROM_ELLIPSOID = "Use incidence angle from Ellipsoid";
    public static final double NonValidIncidenceAngle = -99999.0;

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
            if(OperatorUtils.isMapProjected(sourceProduct)) {
                throw new OperatorException("Source product is already map projected");
            }

            checkUserInput();

            getMetadata();

            getSourceImageDimension();

            getTiePointGrid();

            if (useAvgSceneHeight) {
                saveSigmaNought = false;
                saveBetaNought = false;
                saveGammaNought = false;
                saveDEM = false;
                saveLocalIncidenceAngle = false;
                saveProjectedLocalIncidenceAngle = false;
            }

            createTargetProduct();

            computeSensorPositionsAndVelocities();

            if (imgResamplingMethod.equals(ResamplingFactory.NEAREST_NEIGHBOUR_NAME)) {
                imgResampling = ResampleMethod.RESAMPLE_NEAREST_NEIGHBOUR;
            } else if (imgResamplingMethod.contains(ResamplingFactory.BILINEAR_INTERPOLATION_NAME)) {
                imgResampling = ResampleMethod.RESAMPLE_BILINEAR;
            } else if (imgResamplingMethod.contains(ResamplingFactory.CUBIC_CONVOLUTION_NAME)) {
                imgResampling = ResampleMethod.RESAMPLE_CUBIC;
            } else {
                throw new OperatorException("Unknown interpolation method");
            }

            if (saveSigmaNought) {
                calibrator = CalibrationFactory.createCalibrator(sourceProduct);
                calibrator.setAuxFileFlag(auxFile);
                calibrator.setExternalAuxFile(externalAuxFile);
                calibrator.initialize(sourceProduct, targetProduct, true, true);
            }

            updateTargetProductMetadata();

            if(externalDEMFile == null) {
                checkIfDEMInstalled(demName);
            }
        } catch(Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    @Override
    public void dispose() {
        if (dem != null) {
            dem.dispose();
        }
        if(fileElevationModel != null) {
            fileElevationModel.dispose();
        }

        if(!orthoDataProduced && processingStarted) {
            final String errMsg = getId() +" error: no valid output was produced. Please verify the DEM or FTP connection";
            System.out.println(errMsg);
            if(VisatApp.getApp() != null) {
                VisatApp.getApp().showErrorDialog(errMsg);
            }
        }
    }

    private void checkUserInput() {

        if (!saveSelectedSourceBand && !applyRadiometricNormalization) {
            throw new OperatorException("Please selecte output band for terrain corrected image");
        }

        if (!applyRadiometricNormalization) {
            saveSigmaNought = false;
            saveGammaNought = false;
            saveBetaNought = false;
        }

        if (saveBetaNought || saveGammaNought ||
            (saveSigmaNought && incidenceAngleForSigma0.contains(USE_INCIDENCE_ANGLE_FROM_ELLIPSOID))) {
            saveSigmaNought = true;
            saveProjectedLocalIncidenceAngle = true;
        }

        if ((saveGammaNought && incidenceAngleForGamma0.contains(USE_INCIDENCE_ANGLE_FROM_ELLIPSOID)) ||
            (saveSigmaNought && incidenceAngleForSigma0.contains(USE_INCIDENCE_ANGLE_FROM_ELLIPSOID))) {
            saveIncidenceAngleFromEllipsoid = true;
        }

        if (saveIncidenceAngleFromEllipsoid) {
            incidenceAngle = OperatorUtils.getIncidenceAngle(sourceProduct);
        }
    }

    private void getTiePointGrid() {
        latitude = OperatorUtils.getLatitude(sourceProduct);
        if (latitude == null) {
            throw new OperatorException("Product without latitude tie point grid");
        }

        longitude = OperatorUtils.getLongitude(sourceProduct);
        if (longitude == null) {
            throw new OperatorException("Product without longitude tie point grid");
        }
    }

    /**
     * Retrieve required data from Abstracted Metadata
     * @throws Exception if metadata not found
     */
    private void getMetadata() throws Exception {
        absRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct);

        final String mission = getMissionType(absRoot);

        srgrFlag = AbstractMetadata.getAttributeBoolean(absRoot, AbstractMetadata.srgr_flag);

        wavelength = getRadarFrequency(absRoot);

        rangeSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.range_spacing);
        if (rangeSpacing <= 0.0) {
            throw new OperatorException("Invalid input for range pixel spacing: " + rangeSpacing);
        }

        azimuthSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.azimuth_spacing);
        if (azimuthSpacing <= 0.0) {
            throw new OperatorException("Invalid input for azimuth pixel spacing: " + azimuthSpacing);
        }

        firstLineUTC = absRoot.getAttributeUTC(AbstractMetadata.first_line_time).getMJD(); // in days
        lastLineUTC = absRoot.getAttributeUTC(AbstractMetadata.last_line_time).getMJD(); // in days
        lineTimeInterval = absRoot.getAttributeDouble(AbstractMetadata.line_time_interval) / 86400.0; // s to day
        if (lastLineUTC == 0.0) {
            throw new OperatorException("Invalid input for Line Time Interval: " + lineTimeInterval);
        }

        orbitStateVectors = AbstractMetadata.getOrbitStateVectors(absRoot);
        if (orbitStateVectors == null || orbitStateVectors.length == 0) {
            throw new OperatorException("Invalid Obit State Vectors");
        }

        if (srgrFlag) {
            srgrConvParams = AbstractMetadata.getSRGRCoefficients(absRoot);
            if (srgrConvParams == null) {
                throw new OperatorException("Invalid SRGR Coefficients");
            }
        } else {
            nearEdgeSlantRange = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.slant_range_to_first_pixel);
        }

        // used for retro-calibration or when useAvgSceneHeight is true
        avgSceneHeight = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.avg_scene_height);

        MetadataAttribute attribute = absRoot.getAttribute("retro-calibration performed flag");
        if (attribute != null) {
            usePreCalibrationOp = true;
            if (!applyRadiometricNormalization) {
                throw new OperatorException("Apply radiometric normalization must be selected.");
            }
        } else {
            final boolean multilookFlag = AbstractMetadata.getAttributeBoolean(absRoot, AbstractMetadata.multilook_flag);
            if (applyRadiometricNormalization && mission.equals("ERS") && !multilookFlag) {
                throw new OperatorException("For radiometric normalization of ERS product, please first use\n" +
                        "  'Remove Antenna Pattern' operator to remove calibration factors applied and apply ADC,\n" +
                        "  then apply 'Range-Doppler Terrain Correction' operator; or use one of the following\n" +
                        "  user graphs: 'RemoveAntPat_Orthorectify' or 'RemoveAntPat_Multilook_Orthorectify'.");
            }
        }

        // temp fix for descending Radarsat2
        if (mission.equals("RS2")) {
            final String pass = absRoot.getAttributeString(AbstractMetadata.PASS);
            if (pass.contains("DESCENDING")) {
                flipIndex = true;
            }
        }
    }

    /**
     * Get the mission type.
     * @param absRoot the AbstractMetadata
     * @return the mission string
     */
    public static String getMissionType(final MetadataElement absRoot) {
        final String mission = absRoot.getAttributeString(AbstractMetadata.MISSION);
        final String sampleType = absRoot.getAttributeString(AbstractMetadata.SAMPLE_TYPE);
        if (mission.equals("ALOS")) {
            throw new OperatorException("ALOS PALSAR product is currently not supported");
        }

        if (mission.equals("TSX1") && !sampleType.equals("COMPLEX")) {
            throw new OperatorException("Only TerraSAR-X (SSC) products are currently supported");
        }

        if (mission.equals("RS1")) {
            throw new OperatorException("RadarSAT-1 product is currently not supported");
        }

        return mission;
    }

    /**
     * Get radar frequency from the abstracted metadata (in Hz).
     * @param absRoot the AbstractMetadata
     * @return wavelength
     * @throws Exception The exceptions.
     */
    public static double getRadarFrequency(final MetadataElement absRoot) throws Exception {
        final double radarFreq = AbstractMetadata.getAttributeDouble(absRoot,
                                                    AbstractMetadata.radar_frequency)*Constants.oneMillion; // Hz
        if (Double.compare(radarFreq, 0.0) <= 0) {
            throw new OperatorException("Invalid radar frequency: " + radarFreq);
        }
        return Constants.lightSpeed / radarFreq;
    }

    /**
     * Compute source image geodetic boundary (minimum/maximum latitude/longitude) from the its corner
     * latitude/longitude.
     * @param sourceProduct The input source product.
     * @param projectionName The projection name.
     * @param geoBoundary The object to pass back the max/min lat/lon.
     */
    public static void computeImageGeoBoundary(final Product sourceProduct,
                                               final String projectionName,
                                               final ImageGeoBoundary geoBoundary) {

        final int sourceW = sourceProduct.getSceneRasterWidth();
        final int sourceH = sourceProduct.getSceneRasterHeight();
        final MapProjection mapProjection = MapProjectionRegistry.getProjection(projectionName);
        final MapTransform mapTransform = mapProjection.getMapTransform();
        final Point2D[] envelope =
                ProductUtils.createMapEnvelope(sourceProduct, new Rectangle(sourceW, sourceH), mapTransform);
        final Point2D pMin = envelope[0];
        final Point2D pMax = envelope[1];

        geoBoundary.latMax = pMax.getY();
        geoBoundary.latMin = pMin.getY();
        geoBoundary.lonMax = pMax.getX();
        geoBoundary.lonMin = pMin.getX();
        
        /*
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

        geoBoundary.latMin = 90.0;
        geoBoundary.latMax = -90.0;
        for (double lat : lats) {
            if (lat < geoBoundary.latMin) {
                geoBoundary.latMin = lat;
            }
            if (lat > geoBoundary.latMax) {
                geoBoundary.latMax = lat;
            }
        }

        geoBoundary.lonMin = 180.0;
        geoBoundary.lonMax = -180.0;
        for (double lon : lons) {
            if (lon < geoBoundary.lonMin) {
                geoBoundary.lonMin = lon;
            }
            if (lon > geoBoundary.lonMax) {
                geoBoundary.lonMax = lon;
            }
        }
        */
    }

    /**
     * Compute DEM traversal step sizes (in degree) in latitude and longitude.
     */
    private void computeDEMTraversalSampleInterval() {

        /*
        double mapW = imageGeoBoundary.lonMax - imageGeoBoundary.lonMin;
        double mapH = imageGeoBoundary.latMax - imageGeoBoundary.latMin;

        delLat = Math.min(mapW / sourceImageWidth, mapH / sourceImageHeight);
        delLon = delLat;
        */

        /*
        double spacing = 0.0;
        if (pixelSpacingInMeter > 0.0) {
            spacing = pixelSpacingInMeter;
        } else {
            if (srgrFlag) {
                spacing = Math.min(rangeSpacing, azimuthSpacing);
            } else {
                spacing = Math.min(rangeSpacing/Math.sin(getIncidenceAngleAtCentreRangePixel(sourceProduct)), azimuthSpacing);
            }
        }
        */
        double spacing = pixelSpacingInMeter;
        double minAbsLat;
        if (imageGeoBoundary.latMin*imageGeoBoundary.latMax > 0) {
            minAbsLat = Math.min(Math.abs(imageGeoBoundary.latMin),
                    Math.abs(imageGeoBoundary.latMax)) * org.esa.beam.util.math.MathUtils.DTOR;
        } else {
            minAbsLat = 0.0;
        }
        delLat = spacing / Constants.MeanEarthRadius * org.esa.beam.util.math.MathUtils.RTOD;
        delLon = spacing / (Constants.MeanEarthRadius*Math.cos(minAbsLat)) * org.esa.beam.util.math.MathUtils.RTOD;
        delLat = Math.min(delLat, delLon);
        delLon = delLat;
    }

    /**
     * Get incidence angle at centre range pixel (in radian).
     * @param srcProduct The source product.
     * @throws OperatorException The exceptions.
     * @return The incidence angle.
     */
    private static double getIncidenceAngleAtCentreRangePixel(Product srcProduct) throws OperatorException {

        final int sourceImageWidth = srcProduct.getSceneRasterWidth();
        final int sourceImageHeight = srcProduct.getSceneRasterHeight();
        final int x = sourceImageWidth / 2;
        final int y = sourceImageHeight / 2;
        final TiePointGrid incidenceAngle = OperatorUtils.getIncidenceAngle(srcProduct);
        if(incidenceAngle == null) {
            throw new OperatorException("incidence_angle tie point grid not found in product");
        }
        return incidenceAngle.getPixelFloat((float)x, (float)y)*org.esa.beam.util.math.MathUtils.DTOR;
    }

    /**
     * Compute target image dimension.
     */
    private void computedTargetImageDimension() {
        targetImageWidth = (int)((imageGeoBoundary.lonMax - imageGeoBoundary.lonMin)/delLon) + 1;
        targetImageHeight = (int)((imageGeoBoundary.latMax - imageGeoBoundary.latMin)/delLat) + 1;
    }

    /**
     * Get elevation model.
     * @throws Exception The exceptions.
     */
    private synchronized void getElevationModel() throws Exception {

        if(isElevationModelAvailable) return;

        if(externalDEMFile != null && fileElevationModel == null) { // if external DEM file is specified by user

            fileElevationModel = new FileElevationModel(externalDEMFile,
                                                        ResamplingFactory.createResampling(demResamplingMethod),
                                                        (float)externalDEMNoDataValue);

            demNoDataValue = (float) externalDEMNoDataValue;
            demName = externalDEMFile.getName();

        } else {

            final ElevationModelRegistry elevationModelRegistry = ElevationModelRegistry.getInstance();
            final ElevationModelDescriptor demDescriptor = elevationModelRegistry.getDescriptor(demName);
            if (demDescriptor == null) {
                throw new OperatorException("The DEM '" + demName + "' is not supported.");
            }

            if (demDescriptor.isInstallingDem()) {
                throw new OperatorException("The DEM '" + demName + "' is currently being installed.");
            }

            dem = demDescriptor.createDem(ResamplingFactory.createResampling(demResamplingMethod));
            if(dem == null) {
                throw new OperatorException("The DEM '" + demName + "' has not been installed.");
            }

            demNoDataValue = dem.getDescriptor().getNoDataValue();
        }
        isElevationModelAvailable = true;
    }

    public static void checkIfDEMInstalled(final String demName) {

        final ElevationModelRegistry elevationModelRegistry = ElevationModelRegistry.getInstance();
        final ElevationModelDescriptor demDescriptor = elevationModelRegistry.getDescriptor(demName);
        if (demDescriptor == null) {
            throw new OperatorException("The DEM '" + demName + "' is not supported.");
        }

        if (!demDescriptor.isInstallingDem() && !demDescriptor.isDemInstalled()) {
            if(!demDescriptor.installDemFiles(VisatApp.getApp())) {
                throw new OperatorException("DEM "+ demName +" must be installed first");                          
            }
        }
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
     * @throws Exception The exception.
     */
    void createTargetProduct() throws Exception {

        final MapInfo mapInfo = ProductUtils.createSuitableMapInfo(
                                                sourceProduct,
                                                MapProjectionRegistry.getProjection(projectionName),
                                                0.0,
                                                sourceProduct.getBandAt(0).getNoDataValue());

        if (pixelSpacingInMeter > 0.0) {
            computeImageGeoBoundary(sourceProduct, projectionName, imageGeoBoundary);
            delLat = pixelSpacingInDegree;
            delLon = pixelSpacingInDegree;
            double pixelSizeX;
            double pixelSizeY;
            if (projectionName.equals("Geographic Lat/Lon")) {
                pixelSizeX = pixelSpacingInDegree;
                pixelSizeY = pixelSpacingInDegree;
            } else {
                pixelSizeX = pixelSpacingInMeter;
                pixelSizeY = pixelSpacingInMeter;
            }
            mapInfo.setPixelSizeX((float)pixelSizeX);
            mapInfo.setPixelSizeY((float)pixelSizeY);

            final Dimension outputRasterSize = ProductUtils.getOutputRasterSize(
                    sourceProduct, null, mapInfo.getMapProjection().getMapTransform(), pixelSizeX, pixelSizeY);
            mapInfo.setSceneWidth(outputRasterSize.width);
            mapInfo.setSceneHeight(outputRasterSize.height);
            mapInfo.setPixelX(0.5f*outputRasterSize.width);
            mapInfo.setPixelY(0.5f*outputRasterSize.height);
            mapInfo.setSceneSizeFitted(true);

        } else {
            delLat = mapInfo.getPixelSizeX();
            delLon = mapInfo.getPixelSizeY();
        }

        targetProduct = ProductProjectionBuilder.createProductProjection(
                                                sourceProduct,
                                                false,
                                                false,
                                                mapInfo,
                                                sourceProduct.getName() + PRODUCT_SUFFIX,
                                                "");

        targetImageWidth = targetProduct.getSceneRasterWidth();
        targetImageHeight = targetProduct.getSceneRasterHeight();

        for (Band band : targetProduct.getBands()) {
            targetProduct.removeBand(band);
        }
        
        addSelectedBands();

        targetGeoCoding = targetProduct.getGeoCoding();
    }

    /**
     * Add the user selected bands to target product.
     * @throws OperatorException The exceptions.
     */
    void addSelectedBands() throws OperatorException {

        final Band[] sourceBands = OperatorUtils.getSourceBands(sourceProduct, sourceBandNames);

        String targetBandName;
        for (int i = 0; i < sourceBands.length; i++) {

            final Band srcBand = sourceBands[i];
            final String unit = srcBand.getUnit();

            if (unit != null && unit.contains(Unit.PHASE)) {
                continue;

            } else if (unit != null && unit.contains(Unit.IMAGINARY)) {
                throw new OperatorException("Real and imaginary bands should be selected in pairs");

            } else if (unit != null && unit.contains(Unit.REAL)) {

                if (i == sourceBands.length - 1) {
                    throw new OperatorException("Real and imaginary bands should be selected in pairs");
                }
                final String nextUnit = sourceBands[i+1].getUnit();
                if (nextUnit == null || !nextUnit.contains(Unit.IMAGINARY)) {
                    throw new OperatorException("Real and imaginary bands should be selected in pairs");
                }
                final Band[] srcBands = new Band[2];
                srcBands[0] = srcBand;
                srcBands[1] = sourceBands[i+1];
                final String pol = OperatorUtils.getBandPolarization(srcBand.getName(), absRoot);

                if (saveSigmaNought) {
                    if (pol != null && !pol.isEmpty()) {
                        targetBandName = "Sigma0_" + pol.toUpperCase();
                    } else {
                        targetBandName = "Sigma0";
                    }
                    if (addTargetBand(targetBandName, Unit.INTENSITY, srcBand)) {
                        targetBandNameToSourceBand.put(targetBandName, srcBands);
                        targetBandApplyRadiometricNormalizationFlag.put(targetBandName, true);
                        if (usePreCalibrationOp) {
                            targetBandApplyRetroCalibrationFlag.put(targetBandName, false);
                        } else {
                            targetBandApplyRetroCalibrationFlag.put(targetBandName, true);
                        }
                    }
                }

                if (saveSelectedSourceBand) {
                    if (pol != null && !pol.isEmpty()) {
                        targetBandName = "Intensity_" + pol.toUpperCase();
                    } else {
                        targetBandName = "Intensity";
                    }
                    if (addTargetBand(targetBandName, Unit.INTENSITY, srcBand)) {
                        targetBandNameToSourceBand.put(targetBandName, srcBands);
                        targetBandApplyRadiometricNormalizationFlag.put(targetBandName, false);
                        targetBandApplyRetroCalibrationFlag.put(targetBandName, false);
                    }
                }
                ++i;

            } else {

                final Band[] srcBands = {srcBand};
                final String pol = OperatorUtils.getBandPolarization(srcBand.getName(), absRoot);
                if (saveSigmaNought) {
                    if (pol != null && !pol.isEmpty()) {
                        targetBandName = "Sigma0_" + pol.toUpperCase();
                    } else {
                        targetBandName = "Sigma0";
                    }
                    if (addTargetBand(targetBandName, Unit.INTENSITY, srcBand)) {
                        targetBandNameToSourceBand.put(targetBandName, srcBands);
                        targetBandApplyRadiometricNormalizationFlag.put(targetBandName, true);
                        if (usePreCalibrationOp) {
                            targetBandApplyRetroCalibrationFlag.put(targetBandName, false);
                        } else {
                            targetBandApplyRetroCalibrationFlag.put(targetBandName, true);
                        }
                    }
                }

                if (saveSelectedSourceBand) {
                    targetBandName = srcBand.getName();
                    if (addTargetBand(targetBandName, unit, srcBand)) {
                        targetBandNameToSourceBand.put(targetBandName, srcBands);
                        targetBandApplyRadiometricNormalizationFlag.put(targetBandName, false);
                        targetBandApplyRetroCalibrationFlag.put(targetBandName, false);
                    }
                }
            }
        }

        if(saveDEM) {
            addTargetBand("elevation", Unit.METERS, null);
        }

        if(saveLocalIncidenceAngle) {
            addTargetBand("incidenceAngle", Unit.DEGREES, null);
        }

        if(saveProjectedLocalIncidenceAngle) {
            addTargetBand("projectedIncidenceAngle", Unit.DEGREES, null);
        }

        if (saveIncidenceAngleFromEllipsoid) {
            addTargetBand("incidenceAngleFromEllipsoid", Unit.DEGREES, null);
        }

        if (saveSigmaNought && incidenceAngleForSigma0.contains(USE_INCIDENCE_ANGLE_FROM_ELLIPSOID)) {
            createSigmaNoughtVirtualBand(targetProduct, incidenceAngleForSigma0);
        }

        if (saveGammaNought) {
            createGammaNoughtVirtualBand(targetProduct, incidenceAngleForGamma0);
        }

        if (saveBetaNought) {
            createBetaNoughtVirtualBand(targetProduct);
        }
    }

    private boolean addTargetBand(String bandName, String bandUnit, Band sourceBand) {

        if(targetProduct.getBand(bandName) == null) {

            final Band targetBand = new Band(bandName,
                                             ProductData.TYPE_FLOAT32,
                                             targetImageWidth,
                                             targetImageHeight);

            targetBand.setUnit(bandUnit);
            if (sourceBand != null) {
                targetBand.setDescription(sourceBand.getDescription());
                targetBand.setNoDataValue(sourceBand.getNoDataValue());
            }
            targetBand.setNoDataValueUsed(true);
            targetProduct.addBand(targetBand);
            return true;
        }

        return false;
    }

    /**
     * Add geocoding to the target product.
     */
    protected void addGeoCoding() {

        final float[] latTiePoints = {(float)imageGeoBoundary.latMax, (float)imageGeoBoundary.latMax,
                                      (float)imageGeoBoundary.latMin, (float)imageGeoBoundary.latMin};
        final float[] lonTiePoints = {(float)imageGeoBoundary.lonMin, (float)imageGeoBoundary.lonMax,
                                      (float)imageGeoBoundary.lonMin, (float)imageGeoBoundary.lonMax};

        final int gridWidth = latitude.getRasterWidth();
        final int gridHeight = latitude.getRasterHeight();

        final float[] fineLatTiePoints = new float[gridWidth*gridHeight];
        ReaderUtils.createFineTiePointGrid(2, 2, gridWidth, gridHeight, latTiePoints, fineLatTiePoints);

        float subSamplingX = (float)targetImageWidth / (gridWidth - 1);
        float subSamplingY = (float)targetImageHeight / (gridHeight - 1);

        final TiePointGrid latGrid = new TiePointGrid(OperatorUtils.TPG_LATITUDE, gridWidth, gridHeight, 0.5f, 0.5f,
                subSamplingX, subSamplingY, fineLatTiePoints);
        latGrid.setUnit(Unit.DEGREES);

        final float[] fineLonTiePoints = new float[gridWidth*gridHeight];
        ReaderUtils.createFineTiePointGrid(2, 2, gridWidth, gridHeight, lonTiePoints, fineLonTiePoints);
        for (float lon : fineLonTiePoints) {
            if (lon >= 180) {
                lon -= 360;
            }
        }
        
        final TiePointGrid lonGrid = new TiePointGrid(OperatorUtils.TPG_LONGITUDE, gridWidth, gridHeight, 0.5f, 0.5f,
              subSamplingX, subSamplingY, fineLonTiePoints, TiePointGrid.DISCONT_AT_180);
        lonGrid.setUnit(Unit.DEGREES);

        final TiePointGeoCoding tpGeoCoding = new TiePointGeoCoding(latGrid, lonGrid, Datum.WGS_84);

        targetProduct.addTiePointGrid(latGrid);
        targetProduct.addTiePointGrid(lonGrid);
        targetProduct.setGeoCoding(tpGeoCoding);

        final Band[] srcBands = targetBandNameToSourceBand.get(targetProduct.getBandAt(0).getName());
    }

    /**
     * Update metadata in the target product.
     * @throws OperatorException The exception.
     */
    private void updateTargetProductMetadata() throws OperatorException, Exception {

        final MetadataElement absTgt = AbstractMetadata.getAbstractedMetadata(targetProduct);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.srgr_flag, 1);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.num_output_lines, targetImageHeight);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.num_samples_per_line, targetImageWidth);

        final GeoPos geoPosFirstNear = targetGeoCoding.getGeoPos(new PixelPos(0,0), null);
        final GeoPos geoPosFirstFar = targetGeoCoding.getGeoPos(new PixelPos(targetImageWidth-1, 0), null);
        final GeoPos geoPosLastNear = targetGeoCoding.getGeoPos(new PixelPos(0,targetImageHeight-1), null);
        final GeoPos geoPosLastFar = targetGeoCoding.getGeoPos(new PixelPos(targetImageWidth-1, targetImageHeight-1), null);

        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.first_near_lat, geoPosFirstNear.getLat());
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.first_far_lat, geoPosFirstFar.getLat());
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.last_near_lat, geoPosLastNear.getLat());
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.last_far_lat, geoPosLastFar.getLat());
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.first_near_long, geoPosFirstNear.getLon());
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.first_far_long, geoPosFirstFar.getLon());
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.last_near_long, geoPosLastNear.getLon());
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.last_far_long, geoPosLastFar.getLon());
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.TOT_SIZE, ReaderUtils.getTotalSize(targetProduct));
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.map_projection, IdentityTransformDescriptor.NAME);
        if (!useAvgSceneHeight) {
            AbstractMetadata.setAttribute(absTgt, AbstractMetadata.is_terrain_corrected, 1);
            if(externalDEMFile != null && fileElevationModel == null) { // if external DEM file is specified by user
                AbstractMetadata.setAttribute(absTgt, AbstractMetadata.DEM, externalDEMFile.getPath());
            } else {
                AbstractMetadata.setAttribute(absTgt, AbstractMetadata.DEM, demName);
            }
        }

        // map projection too
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.geo_ref_system, "WGS84");
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.lat_pixel_res, delLat);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.lon_pixel_res, delLon);

        if (pixelSpacingInMeter > 0.0 &&
            Double.compare(pixelSpacingInMeter, getPixelSpacing(sourceProduct)) != 0) {
            AbstractMetadata.setAttribute(absTgt, AbstractMetadata.range_spacing, pixelSpacingInMeter);
            AbstractMetadata.setAttribute(absTgt, AbstractMetadata.azimuth_spacing, pixelSpacingInMeter);
        }        
    }

    /**
     * Compute sensor position and velocity for each range line from the orbit state vectors.
     */
    private void computeSensorPositionsAndVelocities() {
        
        final int numVectorsUsed = Math.min(orbitStateVectors.length, 5);
        timeArray = new double[numVectorsUsed];
        xPosArray = new double[numVectorsUsed];
        yPosArray = new double[numVectorsUsed];
        zPosArray = new double[numVectorsUsed];
        sensorPosition = new double[sourceImageHeight][3]; // xPos, yPos, zPos
        sensorVelocity = new double[sourceImageHeight][3]; // xVel, yVel, zVel

        computeSensorPositionsAndVelocities(
                orbitStateVectors, timeArray, xPosArray, yPosArray, zPosArray,
                sensorPosition, sensorVelocity, firstLineUTC, lineTimeInterval, sourceImageHeight);
    }

    /**
     * Compute sensor position and velocity for each range line from the orbit state vectors using
     * cubic WARP polynomial.
     * @param orbitStateVectors The orbit state vectors.
     * @param timeArray Array holding zeros Doppler times for all state vectors.
     * @param xPosArray Array holding x coordinates for sensor positions in all state vectors.
     * @param yPosArray Array holding y coordinates for sensor positions in all state vectors.
     * @param zPosArray Array holding z coordinates for sensor positions in all state vectors.
     * @param sensorPosition Sensor positions for all range lines.
     * @param sensorVelocity Sensor velocities for all range lines.
     * @param firstLineUTC The zero Doppler time for the first range line.
     * @param lineTimeInterval The line time interval.
     * @param sourceImageHeight The source image height.
     */
    public static void computeSensorPositionsAndVelocities(AbstractMetadata.OrbitStateVector[] orbitStateVectors,
                                                           double[] timeArray, double[] xPosArray,
                                                           double[] yPosArray, double[] zPosArray,
                                                           double[][] sensorPosition, double[][] sensorVelocity,
                                                           double firstLineUTC, double lineTimeInterval,
                                                           int sourceImageHeight) {

        final int numVectors = orbitStateVectors.length;
        final int numVectorsUsed = timeArray.length;
        final int d = numVectors / numVectorsUsed;

        final double[] xVelArray = new double[numVectorsUsed];
        final double[] yVelArray = new double[numVectorsUsed];
        final double[] zVelArray = new double[numVectorsUsed];

        for (int i = 0; i < numVectorsUsed; i++) {
            timeArray[i] = orbitStateVectors[i*d].time_mjd;
            xPosArray[i] = orbitStateVectors[i*d].x_pos; // m
            yPosArray[i] = orbitStateVectors[i*d].y_pos; // m
            zPosArray[i] = orbitStateVectors[i*d].z_pos; // m
            xVelArray[i] = orbitStateVectors[i*d].x_vel; // m/s
            yVelArray[i] = orbitStateVectors[i*d].y_vel; // m/s
            zVelArray[i] = orbitStateVectors[i*d].z_vel; // m/s
        }

        // Lagrange polynomial interpolation
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

        processingStarted = true;
        try {
            if (!isElevationModelAvailable) {
                getElevationModel();
            }
        } catch(Exception e) {
            throw new OperatorException(e);
        }

        final int x0 = targetRectangle.x;
        final int y0 = targetRectangle.y;
        final int w  = targetRectangle.width;
        final int h  = targetRectangle.height;
        //System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

        try {
            float[][] localDEM = null; // DEM for current tile for computing slope angle
            if (saveLocalIncidenceAngle || saveProjectedLocalIncidenceAngle || saveSigmaNought) {
                localDEM = new float[h+2][w+2];
                final boolean valid = getLocalDEM(x0, y0, w, h, localDEM);
                if(!valid && !useAvgSceneHeight && !saveDEM)
                    return;
            }

//            final GeoPos geoPos = new GeoPos();
            GeoPos geoPos = null;
            final double[] earthPoint = new double[3];
            final double[] sensorPos = new double[3];
            final int srcMaxRange = sourceImageWidth - 1;
            final int srcMaxAzimuth = sourceImageHeight - 1;
            ProductData demBuffer = null;
            ProductData incidenceAngleBuffer = null;
            ProductData projectedIncidenceAngleBuffer = null;
            ProductData incidenceAngleFromEllipsoidBuffer = null;

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

                if (targetBand.getName().equals("incidenceAngleFromEllipsoid")) {
                    incidenceAngleFromEllipsoidBuffer = targetTiles.get(targetBand).getDataBuffer();
                    continue;
                }

                final Band[] srcBands = targetBandNameToSourceBand.get(targetBand.getName());

                final TileData td = new TileData();
                td.targetTile = targetTiles.get(targetBand);
                td.tileDataBuffer = td.targetTile.getDataBuffer();
                td.bandName = targetBand.getName();
                td.noDataValue = srcBands[0].getNoDataValue();
                td.applyRadiometricNormalization = targetBandApplyRadiometricNormalizationFlag.get(targetBand.getName());
                td.applyRetroCalibration = targetBandApplyRetroCalibrationFlag.get(targetBand.getName());
                td.bandPolar = OperatorUtils.getBandPolarization(srcBands[0].getName(), absRoot);
                trgTileList.add(td);
            }
            final TileData[] trgTiles = trgTileList.toArray(new TileData[trgTileList.size()]);

            final int maxY = y0 + h;
            final int maxX = x0 + w;
            for (int y = y0; y < maxY; y++) {
                final int yy = y-y0+1;

                for (int x = x0; x < maxX; x++) {

                    geoPos = targetGeoCoding.getGeoPos(new PixelPos(x,y), null);
                    final double lat = geoPos.lat;
                    double lon = geoPos.lon;
                    final int index = trgTiles[0].targetTile.getDataBufferIndex(x, y);
                    if (lon >= 180.0) {
                        lon -= 360.0;
                    }

                    double alt;
                    if (saveLocalIncidenceAngle || saveProjectedLocalIncidenceAngle || saveSigmaNought) { // localDEM is available
                        alt = (double)localDEM[yy][x-x0+1];
                    } else {
                        if (useAvgSceneHeight) {
                            alt = avgSceneHeight;
                        } else {
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

                    final double zeroDopplerTime = getEarthPointZeroDopplerTime(sourceImageHeight, firstLineUTC,
                            lineTimeInterval, wavelength, earthPoint, sensorPosition, sensorVelocity);

                    if (Double.compare(zeroDopplerTime, NonValidZeroDopplerTime) == 0) {
                        saveNoDataValueToTarget(index, trgTiles);
                        continue;
                    }

                    double slantRange = computeSlantRange(
                            zeroDopplerTime, timeArray, xPosArray, yPosArray, zPosArray, earthPoint, sensorPos);

                    final double zeroDopplerTimeWithoutBias = zeroDopplerTime + slantRange / lightSpeedInMetersPerDay;

                    final double azimuthIndex = (zeroDopplerTimeWithoutBias - firstLineUTC) / lineTimeInterval;

                    slantRange = computeSlantRange(
                            zeroDopplerTimeWithoutBias,  timeArray, xPosArray, yPosArray, zPosArray, earthPoint, sensorPos);

                    /*final*/ double rangeIndex = computeRangeIndex(srgrFlag, sourceImageWidth, firstLineUTC, lastLineUTC,
                            rangeSpacing, zeroDopplerTimeWithoutBias, slantRange, nearEdgeSlantRange, srgrConvParams);

                    // temp fix for descending Radarsat2
                    if (flipIndex) {
                        rangeIndex = srcMaxRange - rangeIndex;
                    }

                    if (!isValidCell(rangeIndex, azimuthIndex, lat, lon, srcMaxRange, srcMaxAzimuth, sensorPos)) {
                        saveNoDataValueToTarget(index, trgTiles);
                    } else {
                        double[] localIncidenceAngles = {NonValidIncidenceAngle, NonValidIncidenceAngle};
                        if (saveLocalIncidenceAngle || saveProjectedLocalIncidenceAngle || saveSigmaNought) {

                            final LocalGeometry localGeometry = new LocalGeometry();
                            setLocalGeometry(x, y, targetGeoCoding, earthPoint, sensorPos, localGeometry);

                            computeLocalIncidenceAngle(
                                    localGeometry, demNoDataValue, saveLocalIncidenceAngle, saveProjectedLocalIncidenceAngle,
                                    saveSigmaNought, x0, y0, x, y, localDEM, localIncidenceAngles); // in degrees

                            if (saveLocalIncidenceAngle && localIncidenceAngles[0] != NonValidIncidenceAngle) {
                                incidenceAngleBuffer.setElemDoubleAt(index, localIncidenceAngles[0]);
                            }

                            if (saveProjectedLocalIncidenceAngle && localIncidenceAngles[1] != NonValidIncidenceAngle) {
                                projectedIncidenceAngleBuffer.setElemDoubleAt(index, localIncidenceAngles[1]);
                            }
                        }

                        if (saveIncidenceAngleFromEllipsoid) {
                            incidenceAngleFromEllipsoidBuffer.setElemDoubleAt(
                                    index, (double)incidenceAngle.getPixelFloat((float)rangeIndex, (float)azimuthIndex));
                        }

                        double satelliteHeight = 0;
                        double sceneToEarthCentre = 0;
                        if (saveSigmaNought) {
                                satelliteHeight = Math.sqrt(
                                        sensorPos[0]*sensorPos[0] + sensorPos[1]*sensorPos[1] + sensorPos[2]*sensorPos[2]);

                                sceneToEarthCentre = Math.sqrt(
                                        earthPoint[0]*earthPoint[0] + earthPoint[1]*earthPoint[1] + earthPoint[2]*earthPoint[2]);
                        }

                        for(TileData tileData : trgTiles) {
                            final Unit.UnitType bandUnit = getBandUnit(tileData.bandName);
                            int[] subSwathIndex = {INVALID_SUB_SWATH_INDEX};
                            double v = getPixelValue(azimuthIndex, rangeIndex, tileData, bandUnit, subSwathIndex);

                            if (v != tileData.noDataValue && tileData.applyRadiometricNormalization) {
                                if (localIncidenceAngles[1] != NonValidIncidenceAngle) {
                                    v = calibrator.applyCalibration(
                                            v, rangeIndex, azimuthIndex, slantRange, satelliteHeight, sceneToEarthCentre,
                                            localIncidenceAngles[1], tileData.bandPolar, bandUnit, subSwathIndex); // use projected incidence angle
                                } else {
                                    v = tileData.noDataValue;
                                }
                            }
                            
                            tileData.tileDataBuffer.setElemDoubleAt(index, v);
                        }
                        orthoDataProduced = true;
                    }
                }
            }
            localDEM = null;
            
        } catch(Throwable e) {
            orthoDataProduced = true; //to prevent multiple error messages
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
     * @return true if all dem values are valid
     * @throws Exception from DEM
     */
    private boolean getLocalDEM(final int x0, final int y0,
                                final int tileWidth, final int tileHeight,
                                final float[][] localDEM) throws Exception {

        // Note: the localDEM covers current tile with 1 extra row above, 1 extra row below, 1 extra column to
        //       the left and 1 extra column to the right of the tile.            

        final int maxY = y0 + tileHeight + 1;
        final int maxX = x0 + tileWidth + 1;
        /*
        if(demName.equals("SRTM 3Sec GeoTiff")) {
            double maxLat = (imageGeoBoundary.latMax - maxY*delLat);
            double minLat = (imageGeoBoundary.latMax - y0*delLat);
            if((maxLat > 60 && minLat > 60) || (maxLat < -60 && minLat < -60)) {
                return false;
            }
        }
        */
        GeoPos geoPos = null;
        float alt;
        boolean valid = false;
        for (int y = y0 - 1; y < maxY; y++) {
            final int yy = y - y0 + 1;
            for (int x = x0 - 1; x < maxX; x++) {
                geoPos = targetGeoCoding.getGeoPos(new PixelPos(x,y), null);
                alt = getLocalElevation(geoPos);
                localDEM[yy][x - x0 + 1] = alt;
                if(alt != demNoDataValue)
                    valid = true;
            }
        }
        return valid;
    }

    /**
     * Get local elevation (in meter) for given latitude and longitude.
     * @param geoPos The latitude and longitude in degrees.
     * @return The elevation in meter.
     * @throws Exception from DEM
     */
    private float getLocalElevation(final GeoPos geoPos) throws Exception {

        if(externalDEMFile == null) {
            return dem.getElevation(geoPos);
        }
        return fileElevationModel.getElevation(geoPos);
    }

    private boolean isValidCell(final double rangeIndex, final double azimuthIndex,
                                final double lat, final double lon,
                                final int srcMaxRange, final int srcMaxAzimuth, final double[] sensorPos) {

        if (rangeIndex < 0.0 || rangeIndex >= srcMaxRange || azimuthIndex < 0.0 || azimuthIndex >= srcMaxAzimuth) {
            return  false;
        }

        final GeoPos sensorGeoPos = new GeoPos();
        GeoUtils.xyz2geo(sensorPos, sensorGeoPos, GeoUtils.EarthModel.WGS84);
        final double delLatMax = Math.abs(lat - sensorGeoPos.lat);
        double delLonMax;
        if (lon < 0 && sensorGeoPos.lon > 0) {
            delLonMax = Math.min(Math.abs(360 + lon - sensorGeoPos.lon), sensorGeoPos.lon - lon);
        } else if (lon > 0 && sensorGeoPos.lon < 0) {
            delLonMax = Math.min(Math.abs(360 + sensorGeoPos.lon - lon), lon - sensorGeoPos.lon);
        } else {
            delLonMax = Math.abs(lon - sensorGeoPos.lon);
        }

        final double delLat = Math.abs(lat - latitude.getPixelFloat((float)rangeIndex, (float)azimuthIndex));
        final double srcLon = longitude.getPixelFloat((float)rangeIndex, (float)azimuthIndex);
        double delLon;
        if (lon < 0 && srcLon > 0) {
            delLon = Math.min(Math.abs(360 + lon - srcLon), srcLon - lon);
        } else if (lon > 0 && srcLon < 0) {
            delLon = Math.min(Math.abs(360 + srcLon - lon), lon - srcLon);
        } else {
            delLon = Math.abs(lon - srcLon);
        }

        return (delLat + delLon <= delLatMax + delLonMax);
    }

    /**
     * Save noDataValue to target pixel with given index.
     * @param index The pixel index in target image.
     * @param trgTiles The target tiles.
     */
    private static void saveNoDataValueToTarget(final int index, final TileData[] trgTiles) {
        for(TileData tileData : trgTiles) {
            tileData.tileDataBuffer.setElemDoubleAt(index, tileData.noDataValue);
        }
    }

    /**
     * Compute zero Doppler time for given erath point.
     * @param sourceImageHeight The source image height.
     * @param firstLineUTC The zero Doppler time for the first range line.
     * @param lineTimeInterval The line time interval.
     * @param wavelength The ragar wavelength.
     * @param earthPoint The earth point in xyz cooordinate.
     * @param sensorPosition Array of sensor positions for all range lines.
     * @param sensorVelocity Array of sensor velocities for all range lines.
     * @return The zero Doppler time in days if it is found, -1 otherwise.
     * @throws OperatorException The operator exception.
     */
    public static double getEarthPointZeroDopplerTime(final int sourceImageHeight, final double firstLineUTC,
                                                      final double lineTimeInterval, final double wavelength,
                                                      final double[] earthPoint, final double[][] sensorPosition,
                                                      final double[][] sensorVelocity) throws OperatorException {

        // binary search is used in finding the zero doppler time
        int lowerBound = 0;
        int upperBound = sensorPosition.length - 1;
        double lowerBoundFreq = getDopplerFrequency(
                lowerBound, sourceImageHeight, earthPoint, sensorPosition, sensorVelocity, wavelength);
        double upperBoundFreq = getDopplerFrequency(
                upperBound, sourceImageHeight, earthPoint, sensorPosition, sensorVelocity, wavelength);

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
            midFreq = getDopplerFrequency(
                    mid, sourceImageHeight, earthPoint, sensorPosition, sensorVelocity, wavelength);
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
     * @param sourceImageHeight The source image height.
     * @param earthPoint The earth point in xyz coordinate.
     * @param sensorPosition Array of sensor positions for all range lines.
     * @param sensorVelocity Array of sensor velocities for all range lines.
     * @param wavelength The ragar wavelength.
     * @return The Doppler frequency in Hz.
     */
    private static double getDopplerFrequency(
            final int y, final int sourceImageHeight, final double[] earthPoint, final double[][] sensorPosition,
            final double[][] sensorVelocity, final double wavelength) {

        if (y < 0 || y > sourceImageHeight - 1) {
            throw new OperatorException("Invalid range line index: " + y);
        }
        
        final double xDiff = earthPoint[0] - sensorPosition[y][0];
        final double yDiff = earthPoint[1] - sensorPosition[y][1];
        final double zDiff = earthPoint[2] - sensorPosition[y][2];
        final double distance = Math.sqrt(xDiff*xDiff + yDiff*yDiff + zDiff*zDiff);

        return 2.0 * (sensorVelocity[y][0]*xDiff + sensorVelocity[y][1]*yDiff + sensorVelocity[y][2]*zDiff) / (distance*wavelength);
    }

    /**
     * Compute slant range distance for given earth point and given time.
     * @param time The given time in days.
     * @param timeArray Array holding zeros Doppler times for all state vectors.
     * @param xPosArray Array holding x coordinates for sensor positions in all state vectors.
     * @param yPosArray Array holding y coordinates for sensor positions in all state vectors.
     * @param zPosArray Array holding z coordinates for sensor positions in all state vectors.
     * @param earthPoint The earth point in xyz coordinate.
     * @param sensorPos The sensor position.
     * @return The slant range distance in meters.
     */
    public static double computeSlantRange(final double time, final double[] timeArray, final double[] xPosArray,
                                           final double[] yPosArray, final double[] zPosArray,
                                           final double[] earthPoint, final double[] sensorPos) {

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
    public static double computeRangeIndex(
            final boolean srgrFlag, final int sourceImageWidth, final double firstLineUTC, final double lastLineUTC,
            final double rangeSpacing, final double zeroDopplerTime, final double slantRange,
            final double nearEdgeSlantRange, final AbstractMetadata.SRGRCoefficientList[] srgrConvParams) {

        if (zeroDopplerTime < Math.min(firstLineUTC, lastLineUTC) ||
            zeroDopplerTime > Math.max(firstLineUTC, lastLineUTC)) {
            return -1.0;
        }

        if (srgrFlag) { // ground detected image

            double groundRange = 0.0;

            if (srgrConvParams.length == 1) {
                groundRange = computeGroundRange(sourceImageWidth, rangeSpacing, slantRange,
                                                 srgrConvParams[0].coefficients, srgrConvParams[0].ground_range_origin);
                if (groundRange < 0.0) {
                    return -1.0;
                } else {
                    return (groundRange - srgrConvParams[0].ground_range_origin) / rangeSpacing;
                }
            }
            
            int idx = 0;
            for (int i = 0; i < srgrConvParams.length && zeroDopplerTime >= srgrConvParams[i].timeMJD; i++) {
                idx = i;
            }

            final double[] srgrCoefficients = new double[srgrConvParams[idx].coefficients.length];
            if (idx == srgrConvParams.length - 1) {
                idx--;
            }

            final double mu = (zeroDopplerTime - srgrConvParams[idx].timeMJD) /
                              (srgrConvParams[idx+1].timeMJD - srgrConvParams[idx].timeMJD);
            for (int i = 0; i < srgrCoefficients.length; i++) {
                srgrCoefficients[i] = MathUtils.interpolationLinear(srgrConvParams[idx].coefficients[i],
                                                                    srgrConvParams[idx+1].coefficients[i], mu);
            }
            groundRange = computeGroundRange(sourceImageWidth, rangeSpacing, slantRange,
                                             srgrCoefficients, srgrConvParams[idx].ground_range_origin);
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
     * Compute ground range for given slant range.
     * @param sourceImageWidth The source image width.
     * @param rangeSpacing The range spacing.
     * @param slantRange The salnt range in meters.
     * @param srgrCoeff The SRGR coefficients for converting ground range to slant range.
     *                  Here it is assumed that the polinomial is given by
     *                  c0 + c1*x + c2*x^2 + ... + cn*x^n, where {c0, c1, ..., cn} are the SRGR coefficients.
     * @return The ground range in meters.
     */
    public static double computeGroundRange(final int sourceImageWidth, final double rangeSpacing,
                                            final double slantRange, final double[] srgrCoeff,
                                            final double ground_range_origin) {

        // binary search is used in finding the ground range for given slant range
        double lowerBound = ground_range_origin;
        double upperBound = ground_range_origin + sourceImageWidth*rangeSpacing;
        final double lowerBoundSlantRange = org.esa.nest.util.MathUtils.computePolynomialValue(lowerBound, srgrCoeff);
        final double upperBoundSlantRange = org.esa.nest.util.MathUtils.computePolynomialValue(upperBound, srgrCoeff);

        if (slantRange < lowerBoundSlantRange || slantRange > upperBoundSlantRange) {
            return -1.0;
        }

        // start binary search
        double midSlantRange;
        while(upperBound - lowerBound > 0.0) {

            final double mid = (lowerBound + upperBound)/2.0;
            midSlantRange = org.esa.nest.util.MathUtils.computePolynomialValue(mid, srgrCoeff);
            if (Math.abs(midSlantRange - slantRange) < 0.1) {
                return mid;
            } else if (midSlantRange < slantRange) {
                lowerBound = mid;
            } else if (midSlantRange > slantRange) {
                upperBound = mid;
            }
        }

        return -1.0;
    }

    /**
     * Get unit for the source band corresponding to the given target band.
     * @param bandName The target band name.
     * @return The source band unit.
     */
    private Unit.UnitType getBandUnit(String bandName) {
        final Band[] srcBands = targetBandNameToSourceBand.get(bandName);
        return Unit.getUnitType(srcBands[0]);
    }

    /**
     * Compute orthorectified pixel value for given pixel.
     * @param azimuthIndex The azimuth index for pixel in source image.
     * @param rangeIndex The range index for pixel in source image.
     * @param tileData The source tile information.
     * @param bandUnit The corresponding source band unit.
     * @param subSwathIndex The subSwath index.
     * @return The pixel value.
     */
    private double getPixelValue(final double azimuthIndex, final double rangeIndex,
                                 final TileData tileData, Unit.UnitType bandUnit, int[] subSwathIndex) {

        final Band[] srcBands = targetBandNameToSourceBand.get(tileData.bandName);
        final Band iSrcBand = srcBands[0];
        Tile sourceTile2 = null;

        if (imgResampling.equals(ResampleMethod.RESAMPLE_NEAREST_NEIGHBOUR)) {

            final Rectangle srcRect = new Rectangle((int)rangeIndex, (int)azimuthIndex, 1, 1);
            final Tile sourceTile = getSourceTile(iSrcBand, srcRect, ProgressMonitor.NULL);
            if (srcBands.length > 1) {
                sourceTile2 = getSourceTile(srcBands[1], srcRect, ProgressMonitor.NULL);
            }
            return getPixelValueUsingNearestNeighbourInterp(
                    azimuthIndex, rangeIndex, tileData, bandUnit, sourceTile, sourceTile2, subSwathIndex);

        } else if (imgResampling.equals(ResampleMethod.RESAMPLE_BILINEAR)) {

            final Rectangle srcRect = new Rectangle((int)rangeIndex, (int)azimuthIndex, 2, 2);
            final Tile sourceTile = getSourceTile(iSrcBand, srcRect, ProgressMonitor.NULL);
            if (srcBands.length > 1) {
                sourceTile2 = getSourceTile(srcBands[1], srcRect, ProgressMonitor.NULL);
            }
            return getPixelValueUsingBilinearInterp(azimuthIndex, rangeIndex,
                    tileData, bandUnit, sourceImageWidth, sourceImageHeight, sourceTile, sourceTile2, subSwathIndex);

        } else if (imgResampling.equals(ResampleMethod.RESAMPLE_CUBIC)) {

            final Rectangle srcRect = new Rectangle(Math.max(0, (int)rangeIndex - 1),
                                         Math.max(0, (int)azimuthIndex - 1), 4, 4);
            final Tile sourceTile = getSourceTile(iSrcBand, srcRect, ProgressMonitor.NULL);
            if (srcBands.length > 1) {
                sourceTile2 = getSourceTile(srcBands[1], srcRect, ProgressMonitor.NULL);
            }
            return getPixelValueUsingBicubicInterp(azimuthIndex, rangeIndex,
                    tileData, bandUnit, sourceImageWidth, sourceImageHeight, sourceTile, sourceTile2, subSwathIndex);
        } else {
            throw new OperatorException("Unknown interpolation method");
        }
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
        if (bandUnit == Unit.UnitType.REAL || bandUnit == Unit.UnitType.IMAGINARY) {

            final double vi = sourceTile.getDataBuffer().getElemDoubleAt(sourceTile.getDataBufferIndex(x0, y0));
            final double vq = sourceTile2.getDataBuffer().getElemDoubleAt(sourceTile2.getDataBufferIndex(x0, y0));
            if (vi == tileData.noDataValue || vq == tileData.noDataValue) {
                return tileData.noDataValue;
            }
            v = vi*vi + vq*vq;

        } else {

            v = sourceTile.getDataBuffer().getElemDoubleAt(sourceTile.getDataBufferIndex(x0, y0));
            if (v == tileData.noDataValue) {
                return tileData.noDataValue;
            }
        }

        if (tileData.applyRetroCalibration) {
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
        if (bandUnit == Unit.UnitType.REAL || bandUnit == Unit.UnitType.IMAGINARY) {

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

            v00 = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x0, y0));
            v01 = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x1, y0));
            v10 = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x0, y1));
            v11 = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x1, y1));

            if (v00 == tileData.noDataValue || v01 == tileData.noDataValue ||
                v10 == tileData.noDataValue || v11 == tileData.noDataValue) {
                return tileData.noDataValue;
            }
        }

        final int[] subSwathIndex00 = {0};
        final int[] subSwathIndex01 = {0};
        final int[] subSwathIndex10 = {0};
        final int[] subSwathIndex11 = {0};
        double v = 0;

        if (tileData.applyRetroCalibration) {

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
        if (bandUnit == Unit.UnitType.REAL || bandUnit == Unit.UnitType.IMAGINARY) {

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

            for (int i = 0; i < y.length; i++) {
                for (int j = 0; j < x.length; j++) {
                    v[i][j] = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x[j], y[i]));
                    if (v[i][j] == tileData.noDataValue) {
                        return tileData.noDataValue;
                    }
                }
            }
        }

        int[][][] ss = new int[4][4][1];
        if (tileData.applyRetroCalibration) {
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

    public static void setLocalGeometry(final int x, final int y, final GeoCoding targetGeoCoding,
                                        final double[] earthPoint, final double[] sensorPos,
                                        LocalGeometry localGeometry) {

        GeoPos rightPointGeoPos = targetGeoCoding.getGeoPos(new PixelPos(x + 1, y), null);
        GeoPos leftPointGeoPos  = targetGeoCoding.getGeoPos(new PixelPos(x - 1, y), null);
        GeoPos upPointGeoPos    = targetGeoCoding.getGeoPos(new PixelPos(x, y - 1), null);
        GeoPos downPointGeoPos  = targetGeoCoding.getGeoPos(new PixelPos(x, y + 1), null);

        localGeometry.leftPointLat  = leftPointGeoPos.lat;
        localGeometry.leftPointLon  = leftPointGeoPos.lon;
        localGeometry.rightPointLat = rightPointGeoPos.lat;
        localGeometry.rightPointLon = rightPointGeoPos.lon;
        localGeometry.upPointLat    = upPointGeoPos.lat;
        localGeometry.upPointLon    = upPointGeoPos.lon;
        localGeometry.downPointLat  = downPointGeoPos.lat;
        localGeometry.downPointLon  = downPointGeoPos.lon;
        localGeometry.centrePoint   = earthPoint;
        localGeometry.sensorPos     = sensorPos;
    }

    /**
     * Compute projected local incidence angle (in degree).
     * @param lg Object holding local geometry information.
     * @param saveLocalIncidenceAngle Boolean flag indicating saving local incidence angle.
     * @param saveProjectedLocalIncidenceAngle Boolean flag indicating saving projected local incidence angle.
     * @param saveSigmaNought Boolean flag indicating applying radiometric calibration.
     * @param x0 The x coordinate of the pixel at the upper left corner of current tile.
     * @param y0 The y coordinate of the pixel at the upper left corner of current tile.
     * @param x The x coordinate of the current pixel.
     * @param y The y coordinate of the current pixel.
     * @param localDEM The local DEM.
     * @param localIncidenceAngles The local incidence angle and projected local incidence angle.
     */
    public static void computeLocalIncidenceAngle(
            final LocalGeometry lg, final float demNoDataValue, final boolean saveLocalIncidenceAngle,
            final boolean saveProjectedLocalIncidenceAngle, final boolean saveSigmaNought, final int x0,
            final int y0, final int x, final int y, final float[][] localDEM, final double[] localIncidenceAngles) {

        // Note: For algorithm and notation of the following implementation, please see Andrea's email dated
        //       May 29, 2009 and Marcus' email dated June 3, 2009, or see Eq (14.10) and Eq (14.11) on page
        //       321 and 323 in "SAR Geocoding - Data and Systems".
        //       The Cartesian coordinate (x, y, z) is represented here by a length-3 array with element[0]
        //       representing x, element[1] representing y and element[2] representing z.

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (localDEM[y-y0+i][x-x0+j] == demNoDataValue) {
                    return;
                }
            }
        }

        final int yy = y - y0;
        final int xx = x - x0;
        final double rightPointHeight = (localDEM[yy][xx + 2] +
                                         localDEM[yy + 1][xx + 2] +
                                         localDEM[yy + 2][xx + 2]) / 3.0;

        final double leftPointHeight = (localDEM[yy][xx] +
                                         localDEM[yy + 1][xx] +
                                         localDEM[yy + 2][xx]) / 3.0;

        final double upPointHeight = (localDEM[yy][xx] +
                                        localDEM[yy][xx + 1] +
                                        localDEM[yy][xx + 2]) / 3.0;

        final double downPointHeight = (localDEM[yy + 2][xx] +
                                        localDEM[yy + 2][xx + 1] +
                                        localDEM[yy + 2][xx + 2]) / 3.0;

        final double[] rightPoint = new double[3];
        final double[] leftPoint = new double[3];
        final double[] upPoint = new double[3];
        final double[] downPoint = new double[3];

        GeoUtils.geo2xyz(lg.rightPointLat, lg.rightPointLon, rightPointHeight, rightPoint, GeoUtils.EarthModel.WGS84);
        GeoUtils.geo2xyz(lg.leftPointLat, lg.leftPointLon, leftPointHeight, leftPoint, GeoUtils.EarthModel.WGS84);
        GeoUtils.geo2xyz(lg.upPointLat, lg.upPointLon, upPointHeight, upPoint, GeoUtils.EarthModel.WGS84);
        GeoUtils.geo2xyz(lg.downPointLat, lg.downPointLon, downPointHeight, downPoint, GeoUtils.EarthModel.WGS84);

        final double[] a = {rightPoint[0] - leftPoint[0], rightPoint[1] - leftPoint[1], rightPoint[2] - leftPoint[2]};
        final double[] b = {downPoint[0] - upPoint[0], downPoint[1] - upPoint[1], downPoint[2] - upPoint[2]};
        final double[] c = {lg.centrePoint[0], lg.centrePoint[1], lg.centrePoint[2]};

        final double[] n = {a[1]*b[2] - a[2]*b[1],
                            a[2]*b[0] - a[0]*b[2],
                            a[0]*b[1] - a[1]*b[0]}; // ground plane normal
        normalizeVector(n);
        if (innerProduct(n, c) < 0) {
            n[0] = -n[0];
            n[1] = -n[1];
            n[2] = -n[2];
        }

        final double[] s = {lg.sensorPos[0] - lg.centrePoint[0],
                            lg.sensorPos[1] - lg.centrePoint[1],
                            lg.sensorPos[2] - lg.centrePoint[2]};
        normalizeVector(s);

        final double nsInnerProduct = innerProduct(n, s);

        if (saveLocalIncidenceAngle) { // local incidence angle
            localIncidenceAngles[0] = Math.acos(nsInnerProduct) * org.esa.beam.util.math.MathUtils.RTOD;
        }

        if (saveProjectedLocalIncidenceAngle || saveSigmaNought) { // projected local incidence angle
            final double[] m = {s[1]*c[2] - s[2]*c[1], s[2]*c[0] - s[0]*c[2], s[0]*c[1] - s[1]*c[0]}; // range plane normal
            normalizeVector(m);
            final double mnInnerProduct = innerProduct(m, n);
            final double[] n1 = {n[0] - m[0]*mnInnerProduct, n[1] - m[1]*mnInnerProduct, n[2] - m[2]*mnInnerProduct};
            normalizeVector(n1);
            localIncidenceAngles[1] = Math.acos(innerProduct(n1, s)) * org.esa.beam.util.math.MathUtils.RTOD;
        }
    }

    private static void normalizeVector(final double[] v) {
        final double norm = Math.sqrt(innerProduct(v, v));
        v[0] /= norm;
        v[1] /= norm;
        v[2] /= norm;
    }

    private static double innerProduct(final double[] a, final double[] b) {
        return a[0]*b[0] + a[1]*b[1] + a[2]*b[2];
    }

    /**
     * Compute pixel spacing (in m).
     * @param srcProduct The source product.
     * @return The pixel spacing.
     * @throws Exception The exception.
     */
    public static double getPixelSpacing(Product srcProduct) throws Exception {
        final MetadataElement abs = AbstractMetadata.getAbstractedMetadata(srcProduct);
        final double rangeSpacing = AbstractMetadata.getAttributeDouble(abs, AbstractMetadata.range_spacing);
        final double azimuthSpacing = AbstractMetadata.getAttributeDouble(abs, AbstractMetadata.azimuth_spacing);
        final boolean srgrFlag = AbstractMetadata.getAttributeBoolean(abs, AbstractMetadata.srgr_flag);
        if (srgrFlag) {
            return Math.min(rangeSpacing, azimuthSpacing);
        } else {
            return Math.min(rangeSpacing/Math.sin(getIncidenceAngleAtCentreRangePixel(srcProduct)), azimuthSpacing);
        }
    }

    /**
     * Compute pixel spacing in degrees.
     * @param pixelSpacingInMeter Pixel spacing in meters.
     * @return The pixel spacing in degrees.
     */
    public static double getPixelSpacingInDegree(double pixelSpacingInMeter) {
        return pixelSpacingInMeter / Constants.MeanEarthRadius * org.esa.beam.util.math.MathUtils.RTOD;
    }

    /**
     * Compute pixel spacing in meters.
     * @param pixelSpacingInDegree Pixel spacing in degrees.
     * @return The pixel spacing in meters.
     */
    public static double getPixelSpacingInMeter(double pixelSpacingInDegree) {
        return pixelSpacingInDegree * Constants.MeanEarthRadius * org.esa.beam.util.math.MathUtils.DTOR;
    }

    /**
     * Set flag for radiometric correction. This function is for unit test only.
     * @param flag The flag.
     */
    void setApplyRadiometricCalibration(boolean flag) {
        saveSelectedSourceBand = !flag;
        applyRadiometricNormalization = flag;
        saveSigmaNought = flag;
    }

    void setSourceBandNames(String[] names) {
        sourceBandNames = names;
    }

    public static class TileData {
        Tile targetTile = null;
        ProductData tileDataBuffer = null;
        String bandName = null;
        String bandPolar = "";
        double noDataValue = 0;
        boolean applyRadiometricNormalization = false;
        boolean applyRetroCalibration = false;
    }

    public static class LocalGeometry {
        public double leftPointLat;
        public double leftPointLon;
        public double rightPointLat;
        public double rightPointLon;
        public double upPointLat;
        public double upPointLon;
        public double downPointLat;
        public double downPointLon;
        public double[] sensorPos;
        public double[] centrePoint;

        public LocalGeometry() {
        }
        /*
        public LocalGeometry(final double lat, final double lon, final double delLat, final double delLon,
                             final double[] earthPoint, final double[] sensorPos) {
            this.leftPointLat = lat;
            this.leftPointLon = lon - delLon;
            this.rightPointLat = lat;
            this.rightPointLon = lon + delLon;
            this.upPointLat = lat + delLat;
            this.upPointLon = lon;
            this.downPointLat = lat - delLat;
            this.downPointLon = lon;
            this.centrePoint = earthPoint;
            this.sensorPos = sensorPos;
        }
        */
    }

    public static class ImageGeoBoundary {
        public double latMin = 0.0, latMax = 0.0;
        public double lonMin = 0.0, lonMax= 0.0;
    }


 //================================== Create Sigma0, Gamma0 and Beta0 virtual bands ====================================

    /**
     * Create Sigma0 image as a virtual band using incidence angle from ellipsoid.
     */
    /*
    private void createSigmaNoughtVirtualBand() {

        if (!incidenceAngleForSigma0.contains(USE_INCIDENCE_ANGLE_FROM_ELLIPSOID)) {
            return;
        }

        final Band[] bands = targetProduct.getBands();
        for(Band trgBand : bands) {

            final String trgBandName = trgBand.getName();
            if (trgBand.isSynthetic() || !trgBandName.contains("Sigma0")) {
                continue;
            }

            final String expression = trgBandName +
                         "==" + trgBand.getNoDataValue() + "?" + trgBand.getNoDataValue() +
                         ":" + trgBandName + " / sin(projectedIncidenceAngle * PI/180.0)" +
                         " * sin(incidenceAngleFromEllipsoid * PI/180)";

            String sigmaNoughtVirtualBandName = trgBandName + "_use_inci_angle_from_ellipsoid";

            final VirtualBand band = new VirtualBand(sigmaNoughtVirtualBandName,
                                                     ProductData.TYPE_FLOAT32,
                                                     targetProduct.getSceneRasterWidth(),
                                                     targetProduct.getSceneRasterHeight(),
                                                     expression);
            band.setSynthetic(true);
            band.setUnit(trgBand.getUnit());
            band.setDescription("Sigma0 image created using inci angle from ellipsoid");
            targetProduct.addBand(band);
        }
    }
    */
    public static void createSigmaNoughtVirtualBand(Product targetProduct, String incidenceAngleForSigma0) {

        if (!incidenceAngleForSigma0.contains(USE_INCIDENCE_ANGLE_FROM_ELLIPSOID)) {
            return;
        }

        final Band[] bands = targetProduct.getBands();
        for(Band trgBand : bands) {

            final String trgBandName = trgBand.getName();
            if (trgBand.isSynthetic() || !trgBandName.contains("Sigma0")) {
                continue;
            }

            final String expression = trgBandName +
                         "==" + trgBand.getNoDataValue() + "?" + trgBand.getNoDataValue() +
                         ":" + trgBandName + " / sin(projectedIncidenceAngle * PI/180.0)" +
                         " * sin(incidenceAngleFromEllipsoid * PI/180)";

            String sigmaNoughtVirtualBandName = trgBandName + "_use_inci_angle_from_ellipsoid";

            final VirtualBand band = new VirtualBand(sigmaNoughtVirtualBandName,
                                                     ProductData.TYPE_FLOAT32,
                                                     targetProduct.getSceneRasterWidth(),
                                                     targetProduct.getSceneRasterHeight(),
                                                     expression);
            band.setSynthetic(true);
            band.setUnit(trgBand.getUnit());
            band.setDescription("Sigma0 image created using inci angle from ellipsoid");
            targetProduct.addBand(band);
        }
    }

    /**
     * Create Gamma0 image as a virtual band.
     */
    /*
    private void createGammaNoughtVirtualBand() {

        final Band[] bands = targetProduct.getBands();
        for(Band trgBand : bands) {

            final String trgBandName = trgBand.getName();
            if (trgBand.isSynthetic() || !trgBandName.contains("Sigma0")) {
                continue;
            }

            final String incidenceAngle;
            if (incidenceAngleForGamma0.contains(USE_INCIDENCE_ANGLE_FROM_ELLIPSOID)) {
                incidenceAngle = "incidenceAngleFromEllipsoid";
            } else { // USE_INCIDENCE_ANGLE_FROM_DEM
                incidenceAngle = "projectedIncidenceAngle";
            }

            final String expression = trgBandName +
                         "==" + trgBand.getNoDataValue() + "?" + trgBand.getNoDataValue() +
                         ":" + trgBandName + " / sin(projectedIncidenceAngle * PI/180.0)" +
                         " * sin(" + incidenceAngle + " * PI/180)" + " / cos(" + incidenceAngle + " * PI/180)";

            String gammaNoughtVirtualBandName;
            String description;
            if (incidenceAngleForGamma0.contains(USE_INCIDENCE_ANGLE_FROM_ELLIPSOID)) {
                gammaNoughtVirtualBandName = "_use_inci_angle_from_ellipsoid";
                description = "Gamma0 image created using inci angle from ellipsoid";
            } else { // USE_INCIDENCE_ANGLE_FROM_DEM
                gammaNoughtVirtualBandName = "_use_projected_local_inci_angle_from_dem";
                description = "Gamma0 image created using projected local inci angle from dem";
            }

            if(trgBandName.contains("_HH")) {
                gammaNoughtVirtualBandName = "Gamma0_HH" + gammaNoughtVirtualBandName;
            } else if(trgBandName.contains("_VV")) {
                gammaNoughtVirtualBandName = "Gamma0_VV" + gammaNoughtVirtualBandName;
            } else if(trgBandName.contains("_HV")) {
                gammaNoughtVirtualBandName = "Gamma0_HV" + gammaNoughtVirtualBandName;
            } else if(trgBandName.contains("_VH")) {
                gammaNoughtVirtualBandName = "Gamma0_VH" + gammaNoughtVirtualBandName;
            } else {
                gammaNoughtVirtualBandName = "Gamma0" + gammaNoughtVirtualBandName;
            }

            final VirtualBand band = new VirtualBand(gammaNoughtVirtualBandName,
                                                     ProductData.TYPE_FLOAT32,
                                                     targetProduct.getSceneRasterWidth(),
                                                     targetProduct.getSceneRasterHeight(),
                                                     expression);
            band.setSynthetic(true);
            band.setUnit(trgBand.getUnit());
            band.setDescription(description);
            targetProduct.addBand(band);
        }
    }
    */
    public static void createGammaNoughtVirtualBand(Product targetProduct, String incidenceAngleForGamma0) {

        final Band[] bands = targetProduct.getBands();
        for(Band trgBand : bands) {

            final String trgBandName = trgBand.getName();
            if (trgBand.isSynthetic() || !trgBandName.contains("Sigma0")) {
                continue;
            }

            final String incidenceAngle;
            if (incidenceAngleForGamma0.contains(USE_INCIDENCE_ANGLE_FROM_ELLIPSOID)) {
                incidenceAngle = "incidenceAngleFromEllipsoid";
            } else { // USE_INCIDENCE_ANGLE_FROM_DEM
                incidenceAngle = "projectedIncidenceAngle";
            }

            final String expression = trgBandName +
                         "==" + trgBand.getNoDataValue() + "?" + trgBand.getNoDataValue() +
                         ":" + trgBandName + " / sin(projectedIncidenceAngle * PI/180.0)" +
                         " * sin(" + incidenceAngle + " * PI/180)" + " / cos(" + incidenceAngle + " * PI/180)";

            String gammaNoughtVirtualBandName;
            String description;
            if (incidenceAngleForGamma0.contains(USE_INCIDENCE_ANGLE_FROM_ELLIPSOID)) {
                gammaNoughtVirtualBandName = "_use_inci_angle_from_ellipsoid";
                description = "Gamma0 image created using inci angle from ellipsoid";
            } else { // USE_INCIDENCE_ANGLE_FROM_DEM
                gammaNoughtVirtualBandName = "_use_projected_local_inci_angle_from_dem";
                description = "Gamma0 image created using projected local inci angle from dem";
            }

            if(trgBandName.contains("_HH")) {
                gammaNoughtVirtualBandName = "Gamma0_HH" + gammaNoughtVirtualBandName;
            } else if(trgBandName.contains("_VV")) {
                gammaNoughtVirtualBandName = "Gamma0_VV" + gammaNoughtVirtualBandName;
            } else if(trgBandName.contains("_HV")) {
                gammaNoughtVirtualBandName = "Gamma0_HV" + gammaNoughtVirtualBandName;
            } else if(trgBandName.contains("_VH")) {
                gammaNoughtVirtualBandName = "Gamma0_VH" + gammaNoughtVirtualBandName;
            } else {
                gammaNoughtVirtualBandName = "Gamma0" + gammaNoughtVirtualBandName;
            }

            final VirtualBand band = new VirtualBand(gammaNoughtVirtualBandName,
                                                     ProductData.TYPE_FLOAT32,
                                                     targetProduct.getSceneRasterWidth(),
                                                     targetProduct.getSceneRasterHeight(),
                                                     expression);
            band.setSynthetic(true);
            band.setUnit(trgBand.getUnit());
            band.setDescription(description);
            targetProduct.addBand(band);
        }
    }

    /**
     * Create Beta0 image as a virtual band.
     */
    /*
    private void createBetaNoughtVirtualBand() {

        final Band[] bands = targetProduct.getBands();
        for(Band trgBand : bands) {

            final String trgBandName = trgBand.getName();
            if (trgBand.isSynthetic() || !trgBandName.contains("Sigma0")) {
                continue;
            }

            final String expression = trgBandName +
                         "==" + trgBand.getNoDataValue() + "?" + trgBand.getNoDataValue() +
                         ":" + trgBandName + " / sin(projectedIncidenceAngle * PI/180.0)";

            String betaNoughtVirtualBandName = "Beta0";
            final VirtualBand band = new VirtualBand(betaNoughtVirtualBandName,
                                                     ProductData.TYPE_FLOAT32,
                                                     targetProduct.getSceneRasterWidth(),
                                                     targetProduct.getSceneRasterHeight(),
                                                     expression);
            band.setSynthetic(true);
            band.setUnit(trgBand.getUnit());
            band.setDescription("Beta0 image");
            targetProduct.addBand(band);
        }
    }
    */
    public static void createBetaNoughtVirtualBand(Product targetProduct) {

        final Band[] bands = targetProduct.getBands();
        for(Band trgBand : bands) {

            final String trgBandName = trgBand.getName();
            if (trgBand.isSynthetic() || !trgBandName.contains("Sigma0")) {
                continue;
            }

            final String expression = trgBandName +
                         "==" + trgBand.getNoDataValue() + "?" + trgBand.getNoDataValue() +
                         ":" + trgBandName + " / sin(projectedIncidenceAngle * PI/180.0)";

            String betaNoughtVirtualBandName = "Beta0";
            final VirtualBand band = new VirtualBand(betaNoughtVirtualBandName,
                                                     ProductData.TYPE_FLOAT32,
                                                     targetProduct.getSceneRasterWidth(),
                                                     targetProduct.getSceneRasterHeight(),
                                                     expression);
            band.setSynthetic(true);
            band.setUnit(trgBand.getUnit());
            band.setDescription("Beta0 image");
            targetProduct.addBand(band);
        }
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
            super(RangeDopplerGeocodingOp.class);
            setOperatorUI(RangeDopplerGeocodingOpUI.class);
        }
    }
}
