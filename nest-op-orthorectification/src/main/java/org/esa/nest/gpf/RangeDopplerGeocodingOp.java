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
import org.esa.beam.util.ProductUtils;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.util.MathUtils;
import org.esa.nest.util.GeoUtils;
import org.esa.nest.util.Constants;
import org.esa.nest.gpf.OperatorUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.IOException;

import Jama.Matrix;

/**
 * Raw SAR images usually contain significant geometric distortions. One of the factors that cause the
 * distortions is the ground elevation of the targets. This operator corrects the topographic distortion
 * in the raw image caused by this factor. The operator implements the Range-Doppler (RD) geocoding method.
 *
 * The method consis of the following major steps:
 * (1) Get state vectors from the metadata;
 * (2) Compute satellite position and velocity for each azimuth time by interpolating the state vectors;
 * (3) Get coner latitudes and longitudes for the source image;
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
 * (8.8) Compute range image index Ir using slant range r(tc(i,j)) or groung range;
 * (8.9) Compute pixel value x(Ia,Ir) using interpolation and save it for current sample.
 *
 * Reference: Guide to ASAR Geocoding, Issue 1.0, 19.03.2008
 */

@OperatorMetadata(alias="Range-Doppler-Geocoding", description="RD method for orthorectification")
public final class RangeDopplerGeocodingOp extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            sourceProductId="source", label="Source Bands")
    String[] sourceBandNames = null;

    @Parameter(valueSet = {"ACE", "GETASSE30", "SRTM 3Sec GeoTiff"}, description = "The digital elevation model.",
               defaultValue="SRTM 3Sec GeoTiff", label="Digital Elevation Model")
    private String demName = "SRTM 3Sec GeoTiff";

    @Parameter(valueSet = {NEAREST_NEIGHBOUR, BILINEAR, CUBIC}, defaultValue = BILINEAR, label="DEM Resampling Method")
    private String demResamplingMethod = BILINEAR;

    @Parameter(valueSet = {NEAREST_NEIGHBOUR, BILINEAR, CUBIC}, defaultValue = BILINEAR, label="Image Resampling Method")
    private String imgResamplingMethod = BILINEAR;

    private Band sourceBand = null;  // i band in case of complex product
    private Band sourceBand2 = null; // q band in case of complex product
    private MetadataElement absRoot = null;
    private ElevationModel dem = null;
    private TiePointGrid slantRangeTime = null;
    private boolean srgrFlag = false;

    private int sourceImageWidth = 0;
    private int sourceImageHeight = 0;
    private int targetImageWidth = 0;
    private int targetImageHeight = 0;

    private double wavelength = 0.0; // in m
    private double rangeSpacing = 0.0;
    private double azimuthSpacing = 0.0;
    private double firstLineUTC = 0.0; // in days
    private double lineTimeInterval = 0.0; // in days
    private double demNoDataValue = 0.0; // no data value for DEM
    private double srcBandNoDataValue = 0.0; // no data value for source band
    private double firstNearLat = 0.0;
    private double firstFarLat = 0.0;
    private double lastNearLat = 0.0;
    private double lastFarLat = 0.0;
    private double firstNearLon = 0.0;
    private double firstFarLon = 0.0;
    private double lastNearLon = 0.0;
    private double lastFarLon = 0.0;
    private double latMin = 0.0;
    private double latMax = 0.0;
    private double lonMin = 0.0;
    private double lonMax= 0.0;
    private double delLat = 0.0;
    private double delLon = 0.0;

    private double[][] sensorPosition = null; // sensor position for all range lines
    private double[][] sensorVelocity = null; // sensor velocity for all range lines
    private double[] xPosWarpCoef = null; // warp polynomial coefficients for interpolating sensor xPos
    private double[] yPosWarpCoef = null; // warp polynomial coefficients for interpolating sensor yPos
    private double[] zPosWarpCoef = null; // warp polynomial coefficients for interpolating sensor zPos
    private double[] xVelWarpCoef = null; // warp polynomial coefficients for interpolating sensor xVel
    private double[] yVelWarpCoef = null; // warp polynomial coefficients for interpolating sensor yVel
    private double[] zVelWarpCoef = null; // warp polynomial coefficients for interpolating sensor zVel

    private AbstractMetadata.SRGRCoefficientList[] srgrConvParams = null;
    private AbstractMetadata.OrbitStateVector[] orbitStateVectors = null;
    private final HashMap<String, String[]> targetBandNameToSourceBandName = new HashMap<String, String[]>();

    private static final String NEAREST_NEIGHBOUR = "Nearest Neighbour";
    private static final String BILINEAR = "Bilinear Interpolation";
    private static final String CUBIC = "Cubic Convolution";
    private static final double MeanEarthRadius = 6371008.7714; // in m (WGS84)


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

            getSRGRFlag();

            getRadarFrequency();

            getRangeAzimuthSpacings();

            getFirstLineTime();

            getLineTimeInterval();

            getOrbitStateVectors();

            if (srgrFlag) {
                getSrgrCoeff();
            } else {
                slantRangeTime = OperatorUtils.getSlantRangeTime(sourceProduct);
            }

            getImageCornerLatLon();

            computeImageGeoBoundary();

            computeDEMTraversalSampleInterval();

            computedTargetImageDimension();

            getElevationModel();

            getSourceImageDimension();

            createTargetProduct();

            computeSensorPositionsAndVelocities();

        } catch(Exception e) {
            throw new OperatorException(e);
        }
    }

    /**
     * Get SRGR flag from the abstracted metadata.
     * @throws Exception The exceptions.
     */
    private void getSRGRFlag() throws Exception {
        srgrFlag = AbstractMetadata.getAttributeBoolean(absRoot, AbstractMetadata.srgr_flag);
        /*
        if (!srgrFlag) {
            throw new OperatorException("Slant range image currently cannot be handled.");
        }
        */
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
       /* for (int i = 0; i < orbitStateVectors.length; i++) {
            System.out.println("utcTime = " + orbitStateVectors[i].time);
            System.out.println("xPos = " + orbitStateVectors[i].x_pos);
            System.out.println("yPos = " + orbitStateVectors[i].y_pos);
            System.out.println("zPos = " + orbitStateVectors[i].z_pos);
            System.out.println("xVel = " + orbitStateVectors[i].x_vel);
            System.out.println("yVel = " + orbitStateVectors[i].y_vel);
            System.out.println("zVel = " + orbitStateVectors[i].z_vel);
        }       */
    }

    /**
     * Get SRGR conversion parameters.
     * @throws Exception The exceptions.
     */
    private void getSrgrCoeff() throws Exception {

        srgrConvParams = AbstractMetadata.getSRGRCoefficients(absRoot);
        /*
        for (int i = 0; i < srgrConvParams.length; i++) {
            System.out.println("time = " + srgrConvParams[i].time);
            System.out.println("ground_range_origin = " + srgrConvParams[i].ground_range_origin);
            for (int j = 0; j < srgrConvParams[i].coefficients.length; j++) {
                System.out.print("s[" + j + "] = " + srgrConvParams[i].coefficients[j] + ", ");
            }
        }
        */
    }

    /**
     * Get source image corner latitude and longitude (in degree).
     * @throws Exception The exceptions.
     */
    private void getImageCornerLatLon() throws Exception {

        // note longitude is in given in range [-180, 180]
        firstNearLat = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.first_near_lat);
        firstNearLon = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.first_near_long);
        firstFarLat  = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.first_far_lat);
        firstFarLon  = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.first_far_long);
        lastNearLat  = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.last_near_lat);
        lastNearLon  = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.last_near_long);
        lastFarLat   = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.last_far_lat);
        lastFarLon   = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.last_far_long);
    }

    /**
     * Compute source image geodetic boundary (minimum/maximum latitude/longitude) from the its corner
     * latitude/longitude.
     * @throws Exception The exceptions.
     */
    private void computeImageGeoBoundary() throws Exception {

        final double[] lats  = {firstNearLat, firstFarLat, lastNearLat, lastFarLat};
        final double[] lons  = {firstNearLon, firstFarLon, lastNearLon, lastFarLon};
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
     * Get elevation model.
     */
    private void getElevationModel() {

        final ElevationModelRegistry elevationModelRegistry = ElevationModelRegistry.getInstance();
        final ElevationModelDescriptor demDescriptor = elevationModelRegistry.getDescriptor(demName);
        if (demDescriptor == null) {
            throw new OperatorException("The DEM '" + demName + "' is not supported.");
        }

        if (demDescriptor.isInstallingDem()) {
            throw new OperatorException("The DEM '" + demName + "' is currently being installed.");
        }

        dem = demDescriptor.createDem();
        if(dem == null) {
            throw new OperatorException("The DEM '" + demName + "' has not been installed.");
        }

        if(demResamplingMethod.equals(NEAREST_NEIGHBOUR)) {
            dem.setResamplingMethod(Resampling.NEAREST_NEIGHBOUR);
        } else if(demResamplingMethod.equals(BILINEAR)) {
            dem.setResamplingMethod(Resampling.BILINEAR_INTERPOLATION);
        } else if(demResamplingMethod.equals(CUBIC)) {
            dem.setResamplingMethod(Resampling.CUBIC_CONVOLUTION);
        }

        demNoDataValue = dem.getDescriptor().getNoDataValue();
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

        updateTargetProductMetadata();

        //OperatorUtils.copyProductNodes(sourceProduct, targetProduct);

        // the tile width has to be the image width because otherwise sourceRaster.getDataBufferIndex(x, y)
        // returns incorrect index for the last tile on the right
        targetProduct.setPreferredTileSize(targetProduct.getSceneRasterWidth(), 20);
    }

    /**
     * Add the user selected bands to target product.
     * @throws OperatorException The exceptions.
     */
    private void addSelectedBands() throws OperatorException {

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
                throw new OperatorException("band " + srcBand.getName() + " requires a unit");
            }

            String targetUnit = "";

            if (unit.contains(Unit.PHASE)) {

                continue;

            } else if (unit.contains(Unit.IMAGINARY)) {

                throw new OperatorException("Real and imaginary bands should be selected in pairs");

            } else if (unit.contains(Unit.REAL)) {

                if (i == sourceBands.length - 1) {
                    throw new OperatorException("Real and imaginary bands should be selected in pairs");
                }
                final String nextUnit = sourceBands[i+1].getUnit();
                if (nextUnit == null || !nextUnit.contains(Unit.IMAGINARY)) {
                    throw new OperatorException("Real and imaginary bands should be selected in pairs");
                }
                final String[] srcBandNames = new String[2];
                srcBandNames[0] = srcBand.getName();
                srcBandNames[1] = sourceBands[i+1].getName();
                final String pol = OperatorUtils.getPolarizationFromBandName(srcBandNames[0]);
                if (pol != null) {
                    targetBandName = "Intensity_" + pol.toUpperCase();
                } else {
                    targetBandName = "Intensity";
                }
                ++i;
                if(targetProduct.getBand(targetBandName) == null) {
                    targetBandNameToSourceBandName.put(targetBandName, srcBandNames);
                    targetUnit = Unit.INTENSITY;
                }

            } else {

                final String[] srcBandNames = {srcBand.getName()};
                targetBandName = srcBand.getName();
                if(targetProduct.getBand(targetBandName) == null) {
                    targetBandNameToSourceBandName.put(targetBandName, srcBandNames);
                    targetUnit = unit;
                }
            }

            if(targetProduct.getBand(targetBandName) == null) {

                final Band targetBand = new Band(targetBandName,
                                                 srcBand.getDataType(),
                                                 targetImageWidth,
                                                 targetImageHeight);

                targetBand.setUnit(targetUnit);
                targetProduct.addBand(targetBand);
            }
        }
    }

    /**
     * Add geocoding to the target product.
     */
    private void addGeoCoding() {

        int gridWidth = 2;
        int gridHeight = 2;

        float subSamplingX = targetImageWidth;
        float subSamplingY = targetImageHeight;

        float[] latTiePoints = {(float)latMax, (float)latMax, (float)latMin, (float)latMin};
        float[] lonTiePoints = {(float)lonMin, (float)lonMax, (float)lonMin, (float)lonMax};

        TiePointGrid latGrid = new TiePointGrid(
                "latitude", gridWidth, gridHeight, 0.0f, 0.0f, subSamplingX, subSamplingY, latTiePoints);

        TiePointGrid lonGrid = new TiePointGrid(
                "longitude", gridWidth, gridHeight, 0.0f, 0.0f, subSamplingX, subSamplingY, lonTiePoints);

        targetProduct.addTiePointGrid(latGrid);
        targetProduct.addTiePointGrid(lonGrid);

        TiePointGeoCoding gc = new TiePointGeoCoding(latGrid, lonGrid);
        targetProduct.setGeoCoding(gc);
    }

    /**
     * Update metadata in the target product.
     * @throws Exception The exception.
     */
    private void updateTargetProductMetadata() throws OperatorException {

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        final MetadataElement absTgt = AbstractMetadata.getAbstractedMetadata(targetProduct);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.srgr_flag, 1);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.isMapProjected, 1);
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
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.is_geocoded, 1);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.DEM, demName);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.geo_ref_system, "WGS84");
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.lat_pixel_res, delLat);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.lon_pixel_res, delLon);
    }

    /**
     * Compute sensor position and velocity for each range line from the orbit state vectors using
     * cubic WARP polynomial.
     */
    private void computeSensorPositionsAndVelocities() {

        final int warpPolynomialOrder = 3;
        final int numVerctors = orbitStateVectors.length;

        final double[] timeArray = new double[numVerctors];
        final double[] xPosArray = new double[numVerctors];
        final double[] yPosArray = new double[numVerctors];
        final double[] zPosArray = new double[numVerctors];
        final double[] xVelArray = new double[numVerctors];
        final double[] yVelArray = new double[numVerctors];
        final double[] zVelArray = new double[numVerctors];

        for (int i = 0; i < numVerctors; i++) {
            timeArray[i] = orbitStateVectors[i].time.getMJD();
            xPosArray[i] = orbitStateVectors[i].x_pos / 100.0; // 10^-2 m to m
            yPosArray[i] = orbitStateVectors[i].y_pos / 100.0; // 10^-2 m to m
            zPosArray[i] = orbitStateVectors[i].z_pos / 100.0; // 10^-2 m to m
            xVelArray[i] = orbitStateVectors[i].x_vel / 100000.0; // 10^-5 m/s to m/s
            yVelArray[i] = orbitStateVectors[i].y_vel / 100000.0; // 10^-5 m/s to m/s
            zVelArray[i] = orbitStateVectors[i].z_vel / 100000.0; // 10^-5 m/s to m/s
        }

        xPosWarpCoef = computeWarpPolynomial(timeArray, xPosArray, warpPolynomialOrder);
        yPosWarpCoef = computeWarpPolynomial(timeArray, yPosArray, warpPolynomialOrder);
        zPosWarpCoef = computeWarpPolynomial(timeArray, zPosArray, warpPolynomialOrder);
        xVelWarpCoef = computeWarpPolynomial(timeArray, xVelArray, warpPolynomialOrder);
        yVelWarpCoef = computeWarpPolynomial(timeArray, yVelArray, warpPolynomialOrder);
        zVelWarpCoef = computeWarpPolynomial(timeArray, zVelArray, warpPolynomialOrder);

        sensorPosition = new double[sourceImageHeight][3]; // xPos, yPos, zPos
        sensorVelocity = new double[sourceImageHeight][3]; // xVel, yVel, zVel
        for (int i = 0; i < sourceImageHeight; i++) {
            final double time = firstLineUTC + i*lineTimeInterval; // zero Doppler time (in days) for each range line
            sensorPosition[i][0] = getInterpolatedData(xPosWarpCoef, time);
            sensorPosition[i][1] = getInterpolatedData(yPosWarpCoef, time);
            sensorPosition[i][2] = getInterpolatedData(zPosWarpCoef, time);
            sensorVelocity[i][0] = getInterpolatedData(xVelWarpCoef, time);
            sensorVelocity[i][1] = getInterpolatedData(yVelWarpCoef, time);
            sensorVelocity[i][2] = getInterpolatedData(zVelWarpCoef, time);
        }
    }

    /**
     * Compute warp polynomial coefficients.
     * @param timeArray The array of times for all orbit state vectors.
     * @param stateArray The array of data to be interpolated.
     * @param warpPolynomialOrder The order of the warp polynomial.
     * @return The array holding warp polynomial coefficients.
     */
    private static double[] computeWarpPolynomial(double[] timeArray, double[] stateArray, int warpPolynomialOrder) {

        final Matrix A = MathUtils.createVandermondeMatrix(timeArray, warpPolynomialOrder);
        final Matrix b = new Matrix(stateArray, stateArray.length);
        final Matrix x = A.solve(b);
        return x.getColumnPackedCopy();
    }

    /**
     * Get the interpolated data using warp polynomial for a given time.
     * @param warpCoef The warp polynomial coefficients.
     * @param time The given time in days.
     * @return The interpolated data.
     */
    private static double getInterpolatedData(double[] warpCoef, double time) {
        final double time2 = time*time;
        final double time3 = time*time2;
        return warpCoef[0] + warpCoef[1]*time + warpCoef[2]*time2 + warpCoef[3]*time3;
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

        /*
        * (8.1) Get local elevation h(i,j) for current sample given local latitude lat(i,j) and longitude lon(i,j);
        * (8.2) Convert (lat(i,j), lon(i,j), h(i,j)) to global Cartesian coordinates p(Px, Py, Pz);
        * (8.3) Compute zero Doppler time t(i,j) for point p(Px, Py, Pz) using Doppler frequency function;
        * (8.4) Compute satellite position s(i,j) and slant range r(i,j) = |s(i,j) - p| for zero Doppler time t(i,j);
        * (8.5) Compute bias-corrected zero Doppler time tc(i,j) = t(i,j) + r(i,j)*2/c, where c is the light speed;
        * (8.6) Update satellite position s(tc(i,j)) and slant range r(tc(i,j)) = |s(tc(i,j)) - p| for time tc(i,j);
        * (8.7) Compute azimuth image index Ia using zero Doppler time tc(i,j);
        * (8.8) Compute range image index Ir using slant range r(tc(i,j)) or groung range;
        * (8.9) Compute pixel value x(Ia,Ir) using interpolation and save it for current sample.
        */
        final Rectangle targetTileRectangle = targetTile.getRectangle();
        final int x0 = targetTileRectangle.x;
        final int y0 = targetTileRectangle.y;
        final int w  = targetTileRectangle.width;
        final int h  = targetTileRectangle.height;
        //System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

        final String[] srcBandNames = targetBandNameToSourceBandName.get(targetBand.getName());
        if (srcBandNames.length == 1) {
            sourceBand = sourceProduct.getBand(srcBandNames[0]);
        } else {
            sourceBand = sourceProduct.getBand(srcBandNames[0]);
            sourceBand2 = sourceProduct.getBand(srcBandNames[1]);
        }
        srcBandNoDataValue = sourceBand.getNoDataValue();

        try {
            final ProductData trgData = targetTile.getDataBuffer();
            final GeoPos geoPos = new GeoPos();
            final double[] earthPoint = new double[3];
            final int srcMaxRange = sourceImageWidth - 1;
            final int srcMaxAzimuth = sourceImageHeight - 1;

            for (int y = y0; y < y0 + h; y++) {
                final double lat = latMax - y*delLat;

                for (int x = x0; x < x0 + w; x++) {
                    final int index = targetTile.getDataBufferIndex(x, y);

                    final double lon = lonMin + x*delLon;
                    geoPos.setLocation((float)lat, (float)lon);
                    final double alt = getLocalElevation(geoPos);

                    GeoUtils.geo2xyz(lat, lon, alt, earthPoint);
                    final double zeroDopplerTime = getEarthPointZeroDopplerTime(earthPoint);
                    if (zeroDopplerTime < 0.0) {
                        trgData.setElemDoubleAt(index, srcBandNoDataValue);
                        continue;
                    }

                    double slantRange = computeSlantRangeDistance(earthPoint, zeroDopplerTime);
                    final double zeroDopplerTimeWithoutBias = zeroDopplerTime + slantRange / Constants.halfLightSpeed / 86400.0;
                    final double azimuthIndex = (zeroDopplerTimeWithoutBias - firstLineUTC) / lineTimeInterval;
                    slantRange = computeSlantRangeDistance(earthPoint, zeroDopplerTimeWithoutBias);

                    final double rangeIndex = computeRangeIndex(zeroDopplerTimeWithoutBias, slantRange);
                    if (rangeIndex < 0.0 || rangeIndex >= srcMaxRange ||
                        azimuthIndex < 0.0 || azimuthIndex >= srcMaxAzimuth) {
                            trgData.setElemDoubleAt(index, srcBandNoDataValue);
                    } else {
                        trgData.setElemDoubleAt(index, getPixelValue(azimuthIndex, rangeIndex));
                    }
                }
            }
        } catch(Exception e) {
            throw new OperatorException(e);
        }
    }

    /**
     * Get local elevation (in meter) for given latitude and longitude.
     * @param geoPos The latitude and longitude in degrees.
     * @return The elevation in meter.
     */
    private double getLocalElevation(GeoPos geoPos) {
        double alt;
        try {
            alt = dem.getElevation(geoPos);
        } catch (Exception e) {
            alt = demNoDataValue;
        }
        return alt == demNoDataValue ? 0.0 : alt;
    }

    /**
     * Compute zero Doppler time for given erath point.
     * @param earthPoint The earth point in xyz cooordinate.
     * @return The zero Doppler time in days if it is found, -1 otherwise.
     * @throws OperatorException The operator exception.
     */
    private double getEarthPointZeroDopplerTime(double[] earthPoint) throws OperatorException {

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
            return -1.0;
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
    private double getDopplerFrequency(int y, double[] earthPoint) {

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
     * @param earthPoint The earth point in xyz coordinate.
     * @param time The given time in days.
     * @return The slant range distance in meters.
     */
    private double computeSlantRangeDistance(double[] earthPoint, double time) {

        final double sensorXPos = getInterpolatedData(xPosWarpCoef, time);
        final double sensorYPos = getInterpolatedData(yPosWarpCoef, time);
        final double sensorZPos = getInterpolatedData(zPosWarpCoef, time);
        final double xDiff = sensorXPos - earthPoint[0];
        final double yDiff = sensorYPos - earthPoint[1];
        final double zDiff = sensorZPos - earthPoint[2];

        return Math.sqrt(xDiff*xDiff + yDiff*yDiff + zDiff*zDiff);
    }

    /**
     * Compute range index in source image for earth point with given zero Doppler time and slant range.
     * @param zeroDopplerTime The zero Doppler time in MJD.
     * @param slantRange The slant range in meters.
     * @return The range index.
     */
    private double computeRangeIndex(double zeroDopplerTime, double slantRange) {

        double rangeIndex = 0.0;

        if (srgrFlag) { // ground detected image

            int i;
            for (i = 0; i < srgrConvParams.length; i++) {
                if (zeroDopplerTime < srgrConvParams[i].time.getMJD()) {
                    break;
                }
            }
            rangeIndex = computeGroundRange(slantRange, srgrConvParams[i-1].coefficients) / rangeSpacing;

        } else { // slant range image

            final int azimuthIndex = (int)((zeroDopplerTime - firstLineUTC) / lineTimeInterval);
            final double r0 = slantRangeTime.getPixelDouble(0, azimuthIndex) / 1000000000.0 * Constants.halfLightSpeed;
            rangeIndex = (slantRange - r0) / rangeSpacing;
        }
        
        return rangeIndex;
    }

    /**
     * Compute ground range for given slant range.
     * @param slantRange The salnt range in meters.
     * @param srgrCoeff The SRGR coefficients for converting ground range to slant range.
     * @return The ground range in meters.
     */
    private static double computeGroundRange(double slantRange, double[] srgrCoeff) {

        // todo Can Newton's method be uaed in find zeros for the 4th order polynomial?
        final double s0 = srgrCoeff[0];
        final double s1 = srgrCoeff[1];
        final double s2 = srgrCoeff[2];
        final double s3 = srgrCoeff[3];
        final double s4 = srgrCoeff[4];
        double x = slantRange;
        double x2 = x*x;
        double y = s4*x2*x2 + s3*x2*x + s2*x2 + s1*x + s0 - slantRange;
        while (Math.abs(y) > 0.0001) {

            final double derivative = 4*s4*x2*x + 3*s3*x2 + 2*s2*x + s1;
            x -= y / derivative;
            x2 = x*x;
            y = s4*x2*x2 + s3*x2*x + s2*x2 + s1*x + s0 - slantRange;
        }
        return x;
    }

    /**
     * Compute orthorectified pixel value for given pixel.
     * @param azimuthIndex The azimuth index for pixel in source image.
     * @param rangeIndex The range index for pixel in source image.
     * @return The pixel value.
     * @throws IOException from readPixels
     */
    private double getPixelValue(double azimuthIndex, double rangeIndex) throws IOException {

        // todo here bilinear interpolation is used, need other interpolation method?
        if (imgResamplingMethod.contains(NEAREST_NEIGHBOUR)) {
            return getPixelValueUsingNearestNeighbourInterp(azimuthIndex, rangeIndex);
        } else if (imgResamplingMethod.contains(BILINEAR)) {
            return getPixelValueUsingBilinearInterp(azimuthIndex, rangeIndex);
        } else if (imgResamplingMethod.contains(CUBIC)) {
            return getPixelValueUsingBicubicInterp(azimuthIndex, rangeIndex);
        } else {
            throw new OperatorException("Unknown interpolation method");
        }
    }

    /**
     * Get source image pixel value using nearest neighbot interpolation.
     * @param azimuthIndex The azimuth index for pixel in source image.
     * @param rangeIndex The range index for pixel in source image.
     * @return The pixel value.
     */
    private double getPixelValueUsingNearestNeighbourInterp(double azimuthIndex, double rangeIndex) {

        final int x0 = (int)rangeIndex;
        final int y0 = (int)azimuthIndex;

        final Tile sourceRaster = getSourceTile(sourceBand, new Rectangle(x0, y0, 2, 2), ProgressMonitor.NULL);
        final ProductData srcData = sourceRaster.getDataBuffer();

        double v = 0.0;
        if (sourceBand.getUnit().contains(Unit.REAL)) {

            final Tile sourceRaster2 = getSourceTile(sourceBand2, new Rectangle(x0, y0, 2, 2), ProgressMonitor.NULL);
            final ProductData srcData2 = sourceRaster2.getDataBuffer();
            final double vi = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x0, y0));
            final double vq = srcData2.getElemDoubleAt(sourceRaster2.getDataBufferIndex(x0, y0));
            v = vi*vi + vq*vq;

        } else {

            v = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x0, y0));
        }

        return v;
    }

    /**
     * Get source image pixel value using bilinear interpolation.
     * @param azimuthIndex The azimuth index for pixel in source image.
     * @param rangeIndex The range index for pixel in source image.
     * @return The pixel value.
     */
    private double getPixelValueUsingBilinearInterp(double azimuthIndex, double rangeIndex) {

        final int x0 = (int)rangeIndex;
        final int y0 = (int)azimuthIndex;
        final int x1 = Math.min(x0 + 1, sourceImageWidth - 1);
        final int y1 = Math.min(y0 + 1, sourceImageHeight - 1);

        final Tile sourceRaster = getSourceTile(sourceBand, new Rectangle(x0, y0, 2, 2), ProgressMonitor.NULL);
        final ProductData srcData = sourceRaster.getDataBuffer();

        final double v00, v01, v10, v11;
        if (sourceBand.getUnit().contains(Unit.REAL)) {

            final Tile sourceRaster2 = getSourceTile(sourceBand2, new Rectangle(x0, y0, 2, 2), ProgressMonitor.NULL);
            final ProductData srcData2 = sourceRaster2.getDataBuffer();

            final double vi00 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x0, y0));
            final double vi01 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x1, y0));
            final double vi10 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x0, y1));
            final double vi11 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x1, y1));

            final double vq00 = srcData2.getElemDoubleAt(sourceRaster2.getDataBufferIndex(x0, y0));
            final double vq01 = srcData2.getElemDoubleAt(sourceRaster2.getDataBufferIndex(x1, y0));
            final double vq10 = srcData2.getElemDoubleAt(sourceRaster2.getDataBufferIndex(x0, y1));
            final double vq11 = srcData2.getElemDoubleAt(sourceRaster2.getDataBufferIndex(x1, y1));

            v00 = vi00*vi00 + vq00*vq00;
            v01 = vi01*vi01 + vq01*vq01;
            v10 = vi10*vi10 + vq10*vq10;
            v11 = vi11*vi11 + vq11*vq11;

        } else {

            v00 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x0, y0));
            v01 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x1, y0));
            v10 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x0, y1));
            v11 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x1, y1));
        }
        return MathUtils.interpolationBiLinear(v00, v01, v10, v11, rangeIndex - x0, azimuthIndex - y0);
    }

    /**
     * Get source image pixel value using bicubic interpolation.
     * @param azimuthIndex The azimuth index for pixel in source image.
     * @param rangeIndex The range index for pixel in source image.
     * @return The pixel value.
     */
    private double getPixelValueUsingBicubicInterp(double azimuthIndex, double rangeIndex) {

        final int [] x = new int[4];
        x[1] = (int)rangeIndex;
        x[0] = Math.max(0, x[1] - 1);
        x[2] = Math.min(x[1] + 1, sourceImageWidth - 1);
        x[3] = Math.min(x[1] + 2, sourceImageWidth - 1);

        final int [] y = new int[4];
        y[1] = (int)azimuthIndex;
        y[0] = Math.max(0, y[1] - 1);
        y[2] = Math.min(y[1] + 1, sourceImageHeight - 1);
        y[3] = Math.min(y[1] + 2, sourceImageHeight - 1);

        final Tile sourceRaster = getSourceTile(sourceBand, new Rectangle(x[0], y[0], 4, 4), ProgressMonitor.NULL);
        final ProductData srcData = sourceRaster.getDataBuffer();

        final double[][] v = new double[4][4];
        if (sourceBand.getUnit().contains(Unit.REAL)) {

            final Tile sourceRaster2 = getSourceTile(sourceBand2, new Rectangle(x[0], y[0], 4, 4), ProgressMonitor.NULL);
            final ProductData srcData2 = sourceRaster2.getDataBuffer();
            for (int i = 0; i < y.length; i++) {
                for (int j = 0; j < x.length; j++) {
                    final double vi = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x[j], y[i]));
                    final double vq = srcData2.getElemDoubleAt(sourceRaster2.getDataBufferIndex(x[j], y[i]));
                    v[i][j] = vi*vi + vq*vq;
                }
            }

        } else {

            for (int i = 0; i < y.length; i++) {
                for (int j = 0; j < x.length; j++) {
                    v[i][j] = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x[j], y[i]));
                }
            }
        }

        return MathUtils.interpolationBiCubic(v, rangeIndex - x[1], azimuthIndex - y[1]);
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
        }
    }
}