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

@OperatorMetadata(alias="Geolocation-Grid-Geocoding", description="GG method for orthorectification")
public final class GeolocationGridGeocodingOp extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            sourceProductId="source", label="Source Bands")
    String[] sourceBandNames = null;

    //@Parameter(valueSet = {"ACE", "GETASSE30", "SRTM 3Sec GeoTiff"}, description = "The digital elevation model.",
    //           defaultValue="SRTM 3Sec GeoTiff", label="Digital Elevation Model")
    //private String demName = "SRTM 3Sec GeoTiff";

    private Band sourceBand = null;
    private Band sourceBand2 = null;
    private MetadataElement absRoot = null;
    private boolean srgrFlag = false;

    private int sourceImageWidth = 0;
    private int sourceImageHeight = 0;
    private int targetImageWidth = 0;
    private int targetImageHeight = 0;

    private TiePointGrid slantRangeTime = null;
    private TiePointGrid latitude = null;
    private TiePointGrid longitude = null;

    private double rangeSpacing = 0.0;
    private double azimuthSpacing = 0.0;
    private double firstLineUTC = 0.0; // in days
    private double lineTimeInterval = 0.0; // in days
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

    private AbstractMetadata.SRGRCoefficientList[] srgrConvParams = null;
    private final HashMap<String, String[]> targetBandNameToSourceBandName = new HashMap<String, String[]>();
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

            getRangeAzimuthSpacings();

            getFirstLineTime();

            getLineTimeInterval();

            if (srgrFlag) {
                getSrgrCoeff();
            }

            getImageCornerLatLon();

            computeImageGeoBoundary();

            computeDEMTraversalSampleInterval();

            computedTargetImageDimension();

            getSourceImageDimension();

            createTargetProduct();

            getTiePointGrids();

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
     * Get range and azimuth spacings from the abstracted metadata.
     * @throws Exception The exceptions.
     */
    private void getRangeAzimuthSpacings() throws Exception {
        rangeSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.range_spacing);
        azimuthSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.azimuth_spacing);
    }

    /**
     * Get SRGR conversion parameters.
     * @throws Exception The exceptions.
     */
    private void getSrgrCoeff() throws Exception {
        srgrConvParams = AbstractMetadata.getSRGRCoefficients(absRoot);
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
        final double minAbsLat = Math.min(Math.abs(latMin), Math.abs(latMax)) * org.esa.beam.util.math.MathUtils.DTOR;
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
                                                 ProductData.TYPE_FLOAT32,
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
     * @throws OperatorException The exception.
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
        //AbstractMetadata.setAttribute(absTgt, AbstractMetadata.DEM, demName);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.geo_ref_system, "WGS84");
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.lat_pixel_res, delLat);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.lon_pixel_res, delLon);
    }

    /**
     * Get latitude, longitude and slant range time tie point grids.
     */
    private void getTiePointGrids() {
        slantRangeTime = OperatorUtils.getSlantRangeTime(sourceProduct);
        latitude = OperatorUtils.getLatitude(sourceProduct);
        longitude = OperatorUtils.getLongitude(sourceProduct);
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
        srcBandNoDataValue = sourceBand.getNoDataValue();

        try {
            final ProductData trgData = targetTile.getDataBuffer();
            final int srcMaxRange = sourceImageWidth - 1;
            final int srcMaxAzimuth = sourceImageHeight - 1;

            for (int y = y0; y < y0 + h; y++) {
                final double lat = latMax - y*delLat;

                for (int x = x0; x < x0 + w; x++) {

                    final int index = targetTile.getDataBufferIndex(x, y);
                    final double lon = lonMin + x*delLon;
                    final PixelPos pixPos = computePixelPosition(lat, lon);
                    if (pixPos.x < 0 || pixPos.y < 0) {
                        trgData.setElemDoubleAt(index, srcBandNoDataValue);
                        continue;
                    }
                    /*
                    int i0 = (int)pixPos.x;
                    int i1 = i0 + 1;
                    int j0 = (int)pixPos.y;
                    int j1 = j0 + 1;
                    float lat00 = latitude.getPixelFloat(i0, j0);
                    float lat01 = latitude.getPixelFloat(i1, j0);
                    float lat10 = latitude.getPixelFloat(i0, j1);
                    float lat11 = latitude.getPixelFloat(i1, j1);
                    float lon00 = longitude.getPixelFloat(i0, j0);
                    float lon01 = longitude.getPixelFloat(i1, j0);
                    float lon10 = longitude.getPixelFloat(i0, j1);
                    float lon11 = longitude.getPixelFloat(i1, j1);
                    System.out.println("lat = " + lat + ", lon = " + lon);
                    System.out.println("lat00 = " + lat00 + ", lon00 = " + lon00);
                    System.out.println("lat01 = " + lat01 + ", lon01 = " + lon01);
                    System.out.println("lat10 = " + lat10 + ", lon10 = " + lon10);
                    System.out.println("lat11 = " + lat11 + ", lon11 = " + lon11);
                    */
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
        } catch(Exception e) {
            throw new OperatorException(e);
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
        // todo should use quadratic interpolation
        final int i0 = (int)pixPos.x;
        final int j0 = (int)pixPos.y;
        final double r00 = slantRangeTime.getPixelDouble(i0, j0) / 1000000000.0 * Constants.halfLightSpeed;
        final double r01 = slantRangeTime.getPixelDouble(i0+1, j0) / 1000000000.0 * Constants.halfLightSpeed;
        final double r10 = slantRangeTime.getPixelDouble(i0, j0+1) / 1000000000.0 * Constants.halfLightSpeed;
        final double r11 = slantRangeTime.getPixelDouble(i0+1, j0+1) / 1000000000.0 * Constants.halfLightSpeed;
        return MathUtils.interpolationBiLinear(r00, r01, r10, r11, pixPos.x - i0, pixPos.y - j0);
    }

    /**
     * Compute zero Doppler time for a given pixel using biquadratic interpolation.
     * @param pixPos The pixel position.
     * @return The zero Doppler time in days.
     */
    private double computeZeroDopplerTime(PixelPos pixPos) {
        // todo should use quadratic interpolation
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

        // todo should use the same function in RD method
        final int x0 = (int)rangeIndex;
        final int y0 = (int)azimuthIndex;

        final Tile sourceRaster = getSourceTile(sourceBand, new Rectangle(x0, y0, 2, 2), ProgressMonitor.NULL);
        final ProductData srcData = sourceRaster.getDataBuffer();

        final double v00, v01, v10, v11;

        if (sourceBand.getUnit().contains(Unit.REAL)) {

            final Tile sourceRaster2 = getSourceTile(sourceBand2, new Rectangle(x0, y0, 2, 2), ProgressMonitor.NULL);
            final ProductData srcData2 = sourceRaster2.getDataBuffer();

            final double vi00 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x0, y0));
            final double vi01 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x0+1, y0));
            final double vi10 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x0, y0+1));
            final double vi11 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x0+1, y0+1));

            final double vq00 = srcData2.getElemDoubleAt(sourceRaster2.getDataBufferIndex(x0, y0));
            final double vq01 = srcData2.getElemDoubleAt(sourceRaster2.getDataBufferIndex(x0+1, y0));
            final double vq10 = srcData2.getElemDoubleAt(sourceRaster2.getDataBufferIndex(x0, y0+1));
            final double vq11 = srcData2.getElemDoubleAt(sourceRaster2.getDataBufferIndex(x0+1, y0+1));

            v00 = vi00*vi00 + vq00*vq00;
            v01 = vi01*vi01 + vq01*vq01;
            v10 = vi10*vi10 + vq10*vq10;
            v11 = vi11*vi11 + vq11*vq11;

        } else {

            v00 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x0, y0));
            v01 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x0+1, y0));
            v10 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x0, y0+1));
            v11 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x0+1, y0+1));
        }

        return MathUtils.interpolationBiLinear(v00, v01, v10, v11, rangeIndex - x0, azimuthIndex - y0);
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
        }
    }
}