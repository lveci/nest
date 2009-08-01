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

import Jama.Matrix;
import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.util.Constants;
import org.esa.nest.util.GeoUtils;
import org.esa.nest.util.MathUtils;

import java.awt.*;
import java.util.ArrayList;

/**
 * Raw SAR images usually contain significant geometric distortions. One of the factors that cause the
 * distortions is the ground elevation of the targets. This operator corrects the topographic distortion
 * in the raw image caused by this factor. The operator implements the indirect(backward) orthorectification
 * method.
 *
 * The method consis of the following major steps:
 * (1) Get state vectors from the metadata;
 * (2) For each pixel (i,j) in the orthorectified image, get the latitude lat(i,j), longitude lon(i,j), and
 *     incidence angle alpha(i,j).
 * (3) Get local elevation h(i,j), which is computed by AddElevationBandOp from DEM given local latitude
 *     lat(i,j) and longitude lon(i,j);
 * (4) Convert (lat(i,j), lon(i,j), h(i,j)) to global Cartesian coordinates (Px, Py, Pz);
 * (5) Get initial zero Doppler time t0(i,j) for pixel (i,j);
 * (6) Compoute accurate zero Doppler time t(i,j) for point (Px, Py, Pz) using Doppler frequency function;
 * (7) Compute azimuth displacement detT(i,j) = (t0(i,j) - t(i,j)) / azimuth_sample_interval;
 * (8) Compute sensor position for t(i,j) and slant range distance R(i,j);
 * (9) Compute ground range displacement delR(i,j) using forward method;
 * (10) Compoute pixel value X(i,j) = X_raw(i - delT(i,j), j - delR(i,j)) using interpolation.
 */

@OperatorMetadata(alias="Backward-Terrain-Correction",
        description="Backward method for correcting topographic distortion caused by target elevation")
public final class BackwardTerrainCorrectionOp extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            sourceProductId="source", label="Source Bands")
    String[] sourceBandNames = null;

    private Band sourceBand = null;
    private Band elevationBand = null;
    private MetadataElement absRoot = null;

    private TiePointGrid incidenceAngle = null;
    private TiePointGrid latitude = null;
    private TiePointGrid longitude = null;

    private int sourceImageWidth = 0;
    private int sourceImageHeight = 0;

    private double lineTimeInterval = 0; // in days
    private double firstLineUTC = 0; // in days
    private double wavelength = 0; // in m
    private double rangeSpacing = 0;
    private double noDataValue = 0; // NoDataValue for elevation band

    private AbstractMetadata.OrbitStateVector[] orbitStateVectors = null;
    private double[][] sensorPosition = null; // sensor position for all range lines
    private double[][] sensorVelocity = null; // sensor velocity for all range lines
    private double[] xPosWarpCoef = null; // warp polynomial coefficients for interpolating sensor xPos
    private double[] yPosWarpCoef = null; // warp polynomial coefficients for interpolating sensor yPos
    private double[] zPosWarpCoef = null; // warp polynomial coefficients for interpolating sensor zPos
    private double[] xVelWarpCoef = null; // warp polynomial coefficients for interpolating sensor xVel
    private double[] yVelWarpCoef = null; // warp polynomial coefficients for interpolating sensor yVel
    private double[] zVelWarpCoef = null; // warp polynomial coefficients for interpolating sensor zVel

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

            getRangeSpacing();

            getOrbitStateVectors();

            getFirstLineTime();

            getLineTimeInterval();

            getTiePointGrids();

            getElevationBand();

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
        boolean srgrFlag = AbstractMetadata.getAttributeBoolean(absRoot, AbstractMetadata.srgr_flag);
        if (!srgrFlag) {
            throw new OperatorException("Source product should be ground detected image");
        }
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
     * Get range spacing from the abstracted metadata.
     * @throws Exception The exceptions.
     */
    private void getRangeSpacing() throws Exception {
        rangeSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.range_spacing);
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
     * Get latitude, longitude and incidence angle tie point grids.
     */
    private void getTiePointGrids() {
        incidenceAngle = OperatorUtils.getIncidenceAngle(sourceProduct);
        latitude = OperatorUtils.getLatitude(sourceProduct);
        longitude = OperatorUtils.getLongitude(sourceProduct);
    }

    /**
     * Get elevation band.
     */
    private void getElevationBand() {
        final Band[] srcBands = sourceProduct.getBands();
        for (Band band : srcBands) {
            if (band.getUnit().equals(Unit.METERS)) {
                elevationBand = band;
                break;
            }
        }

        if (elevationBand == null) {
            throw new OperatorException(
                    "Source product does not have elevation band, please run Create Elevation Band Operator first");
        }
        noDataValue = elevationBand.getNoDataValue();
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
    private void createTargetProduct() throws Exception {

        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    sourceImageWidth,
                                    sourceImageHeight);

        addSelectedBands();

        OperatorUtils.copyProductNodes(sourceProduct, targetProduct);

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

        for (Band srcBand : sourceBands) {

            if (srcBand.getUnit().equals(Unit.METERS)) { // skip elevation band
                continue;
            }

            final Band targetBand = new Band(srcBand.getName(),
                                       ProductData.TYPE_FLOAT32,
                                       sourceImageWidth,
                                       sourceImageHeight);

            targetBand.setUnit(srcBand.getUnit());
            targetProduct.addBand(targetBand);
        }
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
            timeArray[i] = orbitStateVectors[i].time_mjd;
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

        final Rectangle targetTileRectangle = targetTile.getRectangle();
        final int x0 = targetTileRectangle.x;
        final int y0 = targetTileRectangle.y;
        final int w  = targetTileRectangle.width;
        final int h  = targetTileRectangle.height;
        //System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

        sourceBand = sourceProduct.getBand(targetBand.getName());
        final Rectangle sourceTileRectangle = getSourceTileRectangle(x0, y0, w, h);
        final Tile sourceRaster = getSourceTile(sourceBand, sourceTileRectangle, pm);
        final Tile elevationRaster = getSourceTile(elevationBand, targetTileRectangle, pm);
        final ProductData srcData = sourceRaster.getDataBuffer();
        final ProductData elevData = elevationRaster.getDataBuffer();
        final ProductData trgData = targetTile.getDataBuffer();

        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                final int index = targetTile.getDataBufferIndex(x, y);
                final double alt = elevData.getElemDoubleAt(index); // target elevation in m
                final double v = computeOrthoRectifiedPixelValue(x, y, alt, srcData, sourceRaster);
                trgData.setElemDoubleAt(index, v);
            }
        }
    }

    /**
     * Get source tile rectangle.
     * @param x0 The x coordinate for pixel at upper left corner of target tile.
     * @param y0 The y coordinate for pixel at upper left corner of target tile.
     * @param w The target tile width.
     * @param h Thetarget tile height.
     * @return The source tile rectangle.
     */
    private Rectangle getSourceTileRectangle(int x0, int y0, int w, int h) {

        int sx0 = x0;
        int sy0 = y0;
        int sw = w;
        int sh = h;

        if (x0 >= w/2) {
            sx0 -= w/2;
            sw += w/2;
        }

        if (y0 >= h/2) {
            sy0 -= h/2;
            sh += h/2;
        }

        if (x0 + w + w/2 <= sourceImageWidth) {
            sw += w/2;
        }

        if (y0 + h + h/2 <= sourceImageHeight) {
            sh += h/2;
        }

        return new Rectangle(sx0, sy0, sw, sh);
    }

    /**
     * Compute orthorectified pixel value for given pixel.
     * @param x The x coordinate for given pixel.
     * @param y The y coordinate for given pixel.
     * @param alt The elevation in meters for given pixel.
     * @param srcData The source data for current tile.
     * @param sourceRaster The source raster for current tile.
     * @return The pixel value.
     */
    private double computeOrthoRectifiedPixelValue(
            int x, int y, double alt, ProductData srcData, Tile sourceRaster) {

        final int index = sourceRaster.getDataBufferIndex(x, y);
        if (Double.compare(alt, noDataValue) == 0) {
            return srcData.getElemDoubleAt(index);
        }

        // compute azimuth displacement
        final double[] earthPoint = getEarthPoint(x, y, alt);
        final double initialZeroDopplerTime = getCurrentLineUTC((double)y); // zero Doppler time for current range line
        final double zeroDopplerTime = getEarthPointZeroDopplerTime(y, earthPoint);
        final double delAz = (initialZeroDopplerTime - zeroDopplerTime) / lineTimeInterval;

        // compute range displacement
        final double slantRange = computeSlantRangeDistance(earthPoint, zeroDopplerTime); // slant range in m
        final double alpha = incidenceAngle.getPixelFloat((float)x, (float)y) * org.esa.beam.util.math.MathUtils.DTOR; // incidence angle in radian
        final double delRg = ForwardTerrainCorrectionOp.getRangeDisplacement(slantRange, alpha, alt, rangeSpacing);

        // get pixel value
        return getPixelValue(x, y, index, delAz, delRg, srcData, sourceRaster);
    }

    /**
     * Get earth point for given image pixel.
     * @param x The x coordinate for the given pixel.
     * @param y The y coordinate for the given pixel.
     * @param alt The altitude in meters for the given pixel in tile.
     * @return The earth point in xyz coordinate.
     */
    private double[] getEarthPoint(int x, int y, double alt) {

        final double[] earthPoint = new double[3];
        final double lat = latitude.getPixelFloat((float)x, (float)y);
        final double lon = longitude.getPixelFloat((float)x, (float)y);
        GeoUtils.geo2xyz(lat, lon, alt, earthPoint, GeoUtils.EarthModel.WGS84);

        return earthPoint;
    }

    /**
     * Get zero Doppler time for given range line.
     * @param y The index for a given range line.
     * @return The zero Doppler time in days.
     */
    private double getCurrentLineUTC(double y) {
        return (firstLineUTC + y*lineTimeInterval);
    }

    /**
     * Compute zero Doppler time for given erath point.
     * @param y The index for given range line.
     * @param earthPoint The earth point in xyz cooordinate.
     * @return The zero Doppler time in days.
     * @throws OperatorException The operator exception.
     */
    private double getEarthPointZeroDopplerTime(int y, double[] earthPoint) throws OperatorException {

        double f0 = 0.0, f1 = 0.0, f2 = 0.0;
        int y1 = y, y2 = y;

        f1 = getDopplerFrequency(y1, earthPoint);
        if (f1 > 0) {

            while (f2 >= 0.0) {
                y2++;
                if (y2 > sourceImageHeight - 1) {
                    return getCurrentLineUTC(y1);
                }
                f2 = getDopplerFrequency(y2, earthPoint);
            }

        } else if (f1 < 0) {

            while (f2 <= 0.0) {
                y2--;
                if (y2 < 0) {
                    return getCurrentLineUTC(y1);
                }
                f2 = getDopplerFrequency(y2, earthPoint);
            }

        } else {
            return getCurrentLineUTC(y1);
        }

        final double y0 = y1 + f1*(y2 - y1)/(f1 - f2);

        double zeroDopplerTimeWithBias = getCurrentLineUTC(y0);

        double slantRange = computeSlantRangeDistance(earthPoint, zeroDopplerTimeWithBias); // slant range in m

        double zeroDopplerTimeWithoutBias = zeroDopplerTimeWithBias + slantRange / Constants.halfLightSpeed / 86400.0;

        return zeroDopplerTimeWithoutBias;
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
     * Compute orthorectified pixel value for given pixel.
     * @param x The x coordinate for given pixel.
     * @param y The y coordinate for given pixel.
     * @param index The data index for given pixel in current tile.
     * @param delAz The azimuth displacement for given pixel.
     * @param delRg The range displacement for given pixel.
     * @param srcData The source data for current tile.
     * @param sourceRaster The source raster for current tile.
     * @return The pixel value.
     */
    private double getPixelValue(
            int x, int y, int index, double delAz, double delRg, ProductData srcData, Tile sourceRaster) {

        final double xip = x - delRg; // imaged pixel position in range
        final double yip = y - delAz; // imaged pixel position in azimuth
        if (xip < 0.0 || xip >= sourceImageWidth - 1 || yip < 0.0 || yip >= sourceImageHeight - 1) {
            return srcData.getElemDoubleAt(index);
        }

        final int x0 = (int)xip;
        final int x1 = x0 + 1;
        final double muX = xip - x0;

        final int y0 = (int)yip;
        final int y1 = y0 + 1;
        final double muY = yip - y0;

        final double v00 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x0, y0));
        final double v01 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x1, y0));
        final double v10 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x0, y1));
        final double v11 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x1, y1));
        return MathUtils.interpolationBiLinear(v00, v01, v10, v11, muX, muY);        
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
            super(BackwardTerrainCorrectionOp.class);
        }
    }
}