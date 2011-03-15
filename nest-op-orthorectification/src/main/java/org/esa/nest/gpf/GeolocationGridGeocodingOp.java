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
import org.esa.beam.framework.dataop.maptransf.IdentityTransformDescriptor;
import org.esa.beam.framework.dataop.maptransf.MapInfo;
import org.esa.beam.framework.dataop.maptransf.MapProjectionRegistry;
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
import org.esa.nest.dataio.ReaderUtils;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.util.Constants;
import org.esa.nest.util.MathUtils;

import java.awt.*;
import java.io.IOException;
import java.util.HashMap;

/**
 * Raw SAR images usually contain significant geometric distortions. One of the factors that cause the
 * distortions is the ground elevation of the targets. This operator corrects the topographic distortion
 * in the raw image caused by this factor. The operator implements the Geolocation-Grid (GG) geocoding method.
 *
 * The method consis of the following major steps:
 * (1) Get coner latitudes and longitudes for the source image;
 * (2) Compute [LatMin, LatMax] and [LonMin, LonMax];
 * (3) Get the range and azimuth spacings for the source image;
 * (4) Compute DEM traversal sample intervals (delLat, delLon) based on source image pixel spacing;
 * (5) Compute target geocoded image dimension;
 * (6) Get latitude, longitude and slant range time tie points from geolocation LADS;
 * (7) Repeat the following steps for each point in the target raster [LatMax:-delLat:LatMin]x[LonMin:delLon:LonMax]:
 * (7.1) Get local latitude lat(i,j) and longitude lon(i,j) for current point;
 * (7.2) Determine the 4 cells in the source image that are immidiately adjacent and enclose the point;
 * (7.3) Compute slant range r(i,j) for the point using biquadratic interpolation;
 * (7.4) Compute azimuth time t(i,j) for the point using biquadratic interpolation;
 * (7.5) Compute bias-corrected zero Doppler time tc(i,j) = t(i,j) + r(i,j)*2/c, where c is the light speed;
 * (7.6) Compute azimuth image index Ia using zero Doppler time tc(i,j);
 * (7.8) Compute range image index Ir using slant range r(i,j) or groung range;
 * (7.9) Compute pixel value x(Ia,Ir) using interpolation and save it for current sample.
 *
 * Reference: Guide to ASAR Geocoding, Issue 1.0, 19.03.2008
 */

@OperatorMetadata(alias="Ellipsoid-Correction-GG",
        category = "Geometry",
        description="GG method for orthorectification")
public final class GeolocationGridGeocodingOp extends Operator {

    public static final String PRODUCT_SUFFIX = "_EC";

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            rasterDataNodeType = Band.class, label="Source Bands")
    private
    String[] sourceBandNames = null;

    @Parameter(valueSet = {ResamplingFactory.NEAREST_NEIGHBOUR_NAME,
            ResamplingFactory.BILINEAR_INTERPOLATION_NAME, ResamplingFactory.CUBIC_CONVOLUTION_NAME},
            defaultValue = ResamplingFactory.BILINEAR_INTERPOLATION_NAME, label="Image Resampling Method")
    private String imgResamplingMethod = ResamplingFactory.BILINEAR_INTERPOLATION_NAME;

    @Parameter(description = "The projection name", defaultValue = IdentityTransformDescriptor.NAME)
    private String projectionName = IdentityTransformDescriptor.NAME;
    
    private Band sourceBand = null;
    private Band sourceBand2 = null;
    private MetadataElement absRoot = null;
    private boolean srgrFlag = false;

    private int sourceImageWidth = 0;
    private int sourceImageHeight = 0;
    private int targetImageWidth = 0;
    private int targetImageHeight = 0;

    private TiePointGrid slantRangeTime = null;
    private GeoCoding targetGeoCoding = null;

    private double rangeSpacing = 0.0;
    private double firstLineUTC = 0.0; // in days
    private double lineTimeInterval = 0.0; // in days
    private final RangeDopplerGeocodingOp.ImageGeoBoundary imageGeoBoundary = new RangeDopplerGeocodingOp.ImageGeoBoundary();
    private double delLat = 0.0;
    private double delLon = 0.0;

    private AbstractMetadata.SRGRCoefficientList[] srgrConvParams = null;
    private final HashMap<String, String[]> targetBandNameToSourceBandName = new HashMap<String, String[]>();

    private enum ResampleMethod { RESAMPLE_NEAREST_NEIGHBOUR, RESAMPLE_BILINEAR, RESAMPLE_CUBIC }
    private ResampleMethod imgResampling = null;

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

            getMetadata();

            getSourceImageDimension();

            createTargetProduct();

            getTiePointGrids();

            if (imgResamplingMethod.equals(ResamplingFactory.NEAREST_NEIGHBOUR_NAME)) {
                imgResampling = ResampleMethod.RESAMPLE_NEAREST_NEIGHBOUR;
            } else if (imgResamplingMethod.contains(ResamplingFactory.BILINEAR_INTERPOLATION_NAME)) {
                imgResampling = ResampleMethod.RESAMPLE_BILINEAR;
            } else if (imgResamplingMethod.contains(ResamplingFactory.CUBIC_CONVOLUTION_NAME)) {
                imgResampling = ResampleMethod.RESAMPLE_CUBIC;
            } else {
                throw new OperatorException("Unknown interpolation method");
            }

        } catch(Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    /**
     * Retrieve required data from Abstracted Metadata
     * @throws Exception if metadata not found
     */
    private void getMetadata() throws Exception {
        absRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct);

        RangeDopplerGeocodingOp.getMissionType(absRoot);

        srgrFlag = AbstractMetadata.getAttributeBoolean(absRoot, AbstractMetadata.srgr_flag);

        rangeSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.range_spacing);

        firstLineUTC = absRoot.getAttributeUTC(AbstractMetadata.first_line_time).getMJD(); // in days

        lineTimeInterval = absRoot.getAttributeDouble(AbstractMetadata.line_time_interval) / 86400.0; // s to day

        if (srgrFlag) {
            srgrConvParams = AbstractMetadata.getSRGRCoefficients(absRoot);
        }
    }

    /**
     * Compute DEM traversal step sizes (in degree) in latitude and longitude.
     */
    private void computeDEMTraversalSampleInterval() {

        double mapW = imageGeoBoundary.lonMax - imageGeoBoundary.lonMin;
        double mapH = imageGeoBoundary.latMax - imageGeoBoundary.latMin;

        delLat = Math.min(mapW / sourceImageWidth, mapH / sourceImageHeight);
        delLon = delLat;
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
    private void createTargetProduct() throws OperatorException, IOException {

        final MapInfo mapInfo = ProductUtils.createSuitableMapInfo(
                                                sourceProduct,
                                                MapProjectionRegistry.getProjection(projectionName),
                                                0.0,
                                                sourceProduct.getBandAt(0).getNoDataValue());

        delLat = mapInfo.getPixelSizeX();
        delLon = mapInfo.getPixelSizeY();

        targetProduct = ProductProjectionBuilder.createProductProjection(sourceProduct, false, false, mapInfo,
                                                                  sourceProduct.getName() + PRODUCT_SUFFIX, "");

        targetImageWidth = targetProduct.getSceneRasterWidth();
        targetImageHeight = targetProduct.getSceneRasterHeight();

        for (Band band : targetProduct.getBands()) {
            targetProduct.removeBand(band);
        }

        addSelectedBands();

        targetGeoCoding = targetProduct.getGeoCoding();

        updateTargetProductMetadata();
    }

    /**
     * Add the user selected bands to target product.
     * @throws OperatorException The exceptions.
     */
    private void addSelectedBands() throws OperatorException {

        final Band[] sourceBands = OperatorUtils.getSourceBands(sourceProduct, sourceBandNames);

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
                final String pol = OperatorUtils.getBandPolarization(srcBandNames[0], absRoot);
                if (pol != null && !pol.isEmpty()) {
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
                                                 ProductData.TYPE_FLOAT32,
                                                 targetImageWidth,
                                                 targetImageHeight);

                targetBand.setUnit(targetUnit);
                targetProduct.addBand(targetBand);
            }
        }
    }

    /**
     * Update metadata in the target product.
     * @throws OperatorException The exception.
     */
    private void updateTargetProductMetadata() throws OperatorException {

        final MetadataElement absTgt = AbstractMetadata.getAbstractedMetadata(targetProduct);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.srgr_flag, 1);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.map_projection, projectionName);
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
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.is_terrain_corrected, 0);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.geo_ref_system, "WGS84");
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.lat_pixel_res, delLat);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.lon_pixel_res, delLon);
    }

    /**
     * Get latitude, longitude and slant range time tie point grids.
     */
    private void getTiePointGrids() {
        slantRangeTime = OperatorUtils.getSlantRangeTime(sourceProduct);
        if (slantRangeTime == null) {
            throw new OperatorException("Product without slant range time tie point grid");
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

        /*
         * (7.1) Get local latitude lat(i,j) and longitude lon(i,j) for current point;
         * (7.2) Determine the 4 cells in the source image that are immidiately adjacent and enclose the point;
         * (7.3) Compute slant range r(i,j) for the point using biquadratic interpolation;
         * (7.4) Compute azimuth time t(i,j) for the point using biquadratic interpolation;
         * (7.5) Compute bias-corrected zero Doppler time tc(i,j) = t(i,j) + r(i,j)*2/c, where c is the light speed;
         * (7.6) Compute azimuth image index Ia using zero Doppler time tc(i,j);
         * (7.8) Compute range image index Ir using slant range r(i,j) or groung range;
         * (7.9) Compute pixel value x(Ia,Ir) using interpolation and save it for current sample.
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
        final double srcBandNoDataValue = sourceBand.getNoDataValue();

        try {
            final ProductData trgData = targetTile.getDataBuffer();
            final int srcMaxRange = sourceImageWidth - 1;
            final int srcMaxAzimuth = sourceImageHeight - 1;
            GeoPos geoPos = null;
            for (int y = y0; y < y0 + h; y++) {
                for (int x = x0; x < x0 + w; x++) {

                    final int index = targetTile.getDataBufferIndex(x, y);
                    geoPos = targetGeoCoding.getGeoPos(new PixelPos(x,y), null);
                    final double lat = geoPos.lat;
                    double lon = geoPos.lon;
                    if (lon >= 180.0) {
                        lon -= 360.0;
                    }
                    final PixelPos pixPos = computePixelPosition(lat, lon);
                    if (Float.isNaN(pixPos.x) || Float.isNaN(pixPos.y)) {
                        trgData.setElemDoubleAt(index, srcBandNoDataValue);
                        continue;
                    }

                    final double slantRange = computeSlantRange(pixPos);
                    final double zeroDopplerTime = computeZeroDopplerTime(pixPos);
                    final double zeroDopplerTimeWithoutBias = zeroDopplerTime + slantRange / Constants.halfLightSpeed / 86400.0;
                    final double azimuthIndex = (zeroDopplerTimeWithoutBias - firstLineUTC) / lineTimeInterval;
                    final double rangeIndex = computeRangeIndex(zeroDopplerTimeWithoutBias, slantRange);
                    if (rangeIndex < 0.0 || rangeIndex >= srcMaxRange ||
                        azimuthIndex < 0.0 || azimuthIndex >= srcMaxAzimuth) {
                            trgData.setElemDoubleAt(index, srcBandNoDataValue);
                    } else {
                        trgData.setElemDoubleAt(index, getPixelValue(azimuthIndex, rangeIndex));
                    }
                }
            }
        } catch(Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        } finally {
            pm.done();
        }
    }

    /**
     * Compute pixel position in source image for given latitude and longitude.
     * @param lat The latitude in degrees.
     * @param lon The longitude in degrees.
     * @return The pixel position.
     */
    private PixelPos computePixelPosition(double lat, double lon) {
        // todo the following method is not accurate, should use point-in-polygon test
        final GeoPos geoPos = new GeoPos((float)lat, (float)lon);
        return sourceBand.getGeoCoding().getPixelPos(geoPos, null);
    }

    /**
     * Compute slant range for a given pixel using biquadratic interpolation.
     * @param pixPos The pixel position.
     * @return The slant range in meters.
     */
    private double computeSlantRange(PixelPos pixPos) {
        return slantRangeTime.getPixelDouble(pixPos.x, pixPos.y, TiePointGrid.InterpMode.BIQUADRATIC) /
                1000000000.0 * Constants.halfLightSpeed;
    }

    /**
     * Compute zero Doppler time for a given pixel using biquadratic interpolation.
     * @param pixPos The pixel position.
     * @return The zero Doppler time in days.
     */
    private double computeZeroDopplerTime(PixelPos pixPos) {
        // todo Guide requires using biquadratic interpolation, is it necessary?
        final int j0 = (int)pixPos.y;
        final double t0 = firstLineUTC + j0*lineTimeInterval;
        final double t1 = firstLineUTC + (j0 + 1)*lineTimeInterval;
        return t0 + (pixPos.y - j0)*(t1 - t0);
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

            int idx = 0;
            for (int i = 0; i < srgrConvParams.length && zeroDopplerTime >= srgrConvParams[i].timeMJD; i++) {
                idx = i;
            }
            final double groundRange = RangeDopplerGeocodingOp.computeGroundRange(
                    sourceImageWidth, rangeSpacing, slantRange, srgrConvParams[idx].coefficients,
                    srgrConvParams[idx].ground_range_origin);

            if (groundRange < 0.0) {
                return -1.0;
            } else {
                rangeIndex = (groundRange - srgrConvParams[idx].ground_range_origin) / rangeSpacing;
            }

        } else { // slant range image

            final int azimuthIndex = (int)((zeroDopplerTime - firstLineUTC) / lineTimeInterval);
            final double r0 = slantRangeTime.getPixelDouble(0, azimuthIndex) / 1000000000.0 * Constants.halfLightSpeed;
            rangeIndex = (slantRange - r0) / rangeSpacing;
        }

        return rangeIndex;
    }

    /**
     * Compute orthorectified pixel value for given pixel.
     * @param azimuthIndex The azimuth index for pixel in source image.
     * @param rangeIndex The range index for pixel in source image.
     * @return The pixel value.
     */

    private double getPixelValue(final double azimuthIndex, final double rangeIndex) {

        Unit.UnitType bandUnit = Unit.getUnitType(sourceBand);
        if (imgResampling.equals(ResampleMethod.RESAMPLE_NEAREST_NEIGHBOUR)) {

            final Tile sourceTile = getSrcTile(sourceBand, (int)rangeIndex, (int)azimuthIndex, 1, 1);
            final Tile sourceTile2 = getSrcTile(sourceBand2, (int)rangeIndex, (int)azimuthIndex, 1, 1);
            return getPixelValueUsingNearestNeighbourInterp(
                    azimuthIndex, rangeIndex, bandUnit, sourceTile, sourceTile2);

        } else if (imgResampling.equals(ResampleMethod.RESAMPLE_BILINEAR)) {

            final Tile sourceTile = getSrcTile(sourceBand, (int)rangeIndex, (int)azimuthIndex, 2, 2);
            final Tile sourceTile2 = getSrcTile(sourceBand2, (int)rangeIndex, (int)azimuthIndex, 2, 2);
            return getPixelValueUsingBilinearInterp(azimuthIndex, rangeIndex,
                    bandUnit, sourceImageWidth, sourceImageHeight, sourceTile, sourceTile2);

        } else if (imgResampling.equals(ResampleMethod.RESAMPLE_CUBIC)) {

            final Tile sourceTile = getSrcTile(sourceBand, Math.max(0, (int)rangeIndex - 1),
                                                Math.max(0, (int)azimuthIndex - 1), 4, 4);
            final Tile sourceTile2 = getSrcTile(sourceBand2, Math.max(0, (int)rangeIndex - 1),
                                                Math.max(0, (int)azimuthIndex - 1), 4, 4);
            return getPixelValueUsingBicubicInterp(azimuthIndex, rangeIndex,
                    bandUnit, sourceImageWidth, sourceImageHeight, sourceTile, sourceTile2);
        } else {
            throw new OperatorException("Unknown interpolation method");
        }
    }

    private Tile getSrcTile(Band sourceBand, int minX, int minY, int width, int height) {
        if(sourceBand == null)
            return null;

        final Rectangle srcRect = new Rectangle(minX, minY, width, height);
        return getSourceTile(sourceBand, srcRect);
    }
    
    /**
     * Get source image pixel value using nearest neighbot interpolation.
     * @param azimuthIndex The azimuth index for pixel in source image.
     * @param rangeIndex The range index for pixel in source image.
     * @param bandUnit The source band unit.
     * @param sourceTile  i
     * @param sourceTile2 q
     * @return The pixel value.
     */
    private static double getPixelValueUsingNearestNeighbourInterp(final double azimuthIndex, final double rangeIndex,
            final Unit.UnitType bandUnit, final Tile sourceTile, final Tile sourceTile2) {

        final int x0 = (int)rangeIndex;
        final int y0 = (int)azimuthIndex;

        double v = 0.0;
        if (bandUnit == Unit.UnitType.REAL || bandUnit == Unit.UnitType.IMAGINARY) {

            final double vi = sourceTile.getDataBuffer().getElemDoubleAt(sourceTile.getDataBufferIndex(x0, y0));
            final double vq = sourceTile2.getDataBuffer().getElemDoubleAt(sourceTile2.getDataBufferIndex(x0, y0));
            v = vi*vi + vq*vq;

        } else {
            v = sourceTile.getDataBuffer().getElemDoubleAt(sourceTile.getDataBufferIndex(x0, y0));
        }

        return v;
    }

    /**
     * Get source image pixel value using bilinear interpolation.
     * @param azimuthIndex The azimuth index for pixel in source image.
     * @param rangeIndex The range index for pixel in source image.
     * @param bandUnit The source band unit.
     * @param sceneRasterWidth the product width
     * @param sceneRasterHeight the product height
     * @param sourceTile  i
     * @param sourceTile2 q
     * @return The pixel value.
     */
    private static double getPixelValueUsingBilinearInterp(final double azimuthIndex, final double rangeIndex,
                                                    final Unit.UnitType bandUnit,
                                                    final int sceneRasterWidth, final int sceneRasterHeight,
                                                    final Tile sourceTile, final Tile sourceTile2) {

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

            v00 = vi00*vi00 + vq00*vq00;
            v01 = vi01*vi01 + vq01*vq01;
            v10 = vi10*vi10 + vq10*vq10;
            v11 = vi11*vi11 + vq11*vq11;

        } else {

            v00 = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x0, y0));
            v01 = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x1, y0));
            v10 = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x0, y1));
            v11 = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x1, y1));
        }

        return MathUtils.interpolationBiLinear(v00, v01, v10, v11, dx, dy);
    }

    /**
     * Get source image pixel value using bicubic interpolation.
     * @param azimuthIndex The azimuth index for pixel in source image.
     * @param rangeIndex The range index for pixel in source image.
     * @param bandUnit The source band unit.
     * @param sceneRasterWidth the product width
     * @param sceneRasterHeight the product height
     * @param sourceTile  i
     * @param sourceTile2 q
     * @return The pixel value.
     */
    private static double getPixelValueUsingBicubicInterp(final double azimuthIndex, final double rangeIndex,
                                                   final Unit.UnitType bandUnit,
                                                   final int sceneRasterWidth, final int sceneRasterHeight,
                                                   final Tile sourceTile, final Tile sourceTile2) {

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
                    v[i][j] = vi*vi + vq*vq;
                }
            }

        } else {

            for (int i = 0; i < y.length; i++) {
                for (int j = 0; j < x.length; j++) {
                    v[i][j] = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x[j], y[i]));
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
            super(GeolocationGridGeocodingOp.class);
            setOperatorUI(GeolocationGridGeocodingOpUI.class);            
        }
    }
}