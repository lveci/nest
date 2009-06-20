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
import org.esa.beam.framework.dataop.dem.ElevationModelRegistry;
import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.dataop.resamp.Resampling;
import org.esa.beam.util.ProductUtils;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.util.MathUtils;
import org.esa.nest.util.Constants;
import org.esa.nest.util.GeoUtils;
import org.esa.nest.gpf.OperatorUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.File;

import Jama.Matrix;
import Jama.SingularValueDecomposition;

/**
 * Raw SAR images usually contain significant geometric distortions. One of the factors that cause the
 * distortions is the ground elevation of the targets. This operator corrects the topographic distortion
 * in the raw image caused by this factor. The operator implements the direct (forward) orthorectification
 * method.
 *
 * The method consis of the following major steps:
 * (1) For each pixel (i,j) in the orthorectified image, get the local incidence angle alpha(i,j) and
 *     slant range distance R(i,j);
 * (2) Get local elevation h(i,j), which is computed by AddElevationBandOp from DEM given local latitude
 *     lat(i,j) and longitude lon(i,j);
 * (3) Compute ground range displacement delta(i,j) with the equation given in ADD using alpha(i,j), R(i,j)
 *     and h(i,j);
 * (4) Compoute pixel value X(i,j) = X_raw(i,j - delt(i,j)) using interpolation.
 */

@OperatorMetadata(alias="SAR-Simulation",
        description="Rigorous SAR Simulation")
public final class SARSimulationOp extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            sourceProductId="source", label="Source Bands")
    String[] sourceBandNames;

    @Parameter(valueSet = {"ACE", "GETASSE30", "SRTM 3Sec GeoTiff"}, description = "The digital elevation model.",
               defaultValue="SRTM 3Sec GeoTiff", label="Digital Elevation Model")
    private String demName = "SRTM 3Sec GeoTiff";

    @Parameter(valueSet = {NEAREST_NEIGHBOUR, BILINEAR, CUBIC}, defaultValue = BILINEAR, label="DEM Resampling Method")
    private String demResamplingMethod = BILINEAR;

    @Parameter(label="External DEM")
    private File externalDemFile = null;

    private MetadataElement absRoot = null;
    private ElevationModel dem = null;
    private FileElevationModel fileElevationModel = null;
    private TiePointGrid latitude = null;
    private TiePointGrid longitude = null;

    private int sourceImageWidth = 0;
    private int sourceImageHeight = 0;
    private int ny0 = 0; // line index for starting traverse
    private boolean srgrFlag = false;
    private boolean ny0Updated = false;

    private double wavelength = 0.0; // in m
    private double rangeSpacing = 0.0;
    private double firstLineUTC = 0.0; // in days
    private double lastLineUTC = 0.0; // in days
    private double lineTimeInterval = 0.0; // in days
    private double nearEdgeSlantRange = 0.0; // in m
    private double demNoDataValue = 0.0; // no data value for DEM
    private double[][] sensorPosition = null; // sensor position for all range lines
    private double[][] sensorVelocity = null; // sensor velocity for all range lines
    private double[] timeArray = null;
    private double[] xPosArray = null;
    private double[] yPosArray = null;
    private double[] zPosArray = null;

    private AbstractMetadata.OrbitStateVector[] orbitStateVectors = null;
    private AbstractMetadata.SRGRCoefficientList[] srgrConvParams = null;

    private static final double NonValidZeroDopplerTime = -99999.0;
    static final String NEAREST_NEIGHBOUR = "Nearest Neighbour";
    static final String BILINEAR = "Bilinear Interpolation";
    static final String CUBIC = "Cubic Convolution";

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

            getFirstLastLineTimes();

            getLineTimeInterval();

            getNearEdgeSlantRange();

            getOrbitStateVectors();

            getSrgrCoeff();

            getElevationModel();

            getTiePointGrid();

            getSourceImageDimension();

            computeSensorPositionsAndVelocities();
            //computeStateVectorModelingCoefficients();

            createTargetProduct();

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
    }

    /**
     * Get radar frequency from the abstracted metadata (in Hz).
     * @throws Exception The exceptions.
     */
    private void getRadarFrequency() throws Exception {
        final double radarFreq = AbstractMetadata.getAttributeDouble(absRoot,
                                                    AbstractMetadata.radar_frequency)* Constants.oneMillion; // Hz
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
     * Get near edge slant range (in m).
     * @throws Exception The exceptions.
     */
    private void getNearEdgeSlantRange() throws Exception {
        nearEdgeSlantRange = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.slant_range_to_first_pixel);
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
     * Get source image width and height.
     */
    private void getSourceImageDimension() {
        sourceImageWidth = sourceProduct.getSceneRasterWidth();
        sourceImageHeight = sourceProduct.getSceneRasterHeight();
    }

    /**
     * Get elevation model.
     * @throws Exception The exceptions.
     */
    private void getElevationModel() throws Exception {

        if(externalDemFile != null && fileElevationModel == null) { // if external DEM file is specified by user

            fileElevationModel = new FileElevationModel(externalDemFile, getResamplingMethod());
            demNoDataValue = fileElevationModel.getNoDataValue();
            demName = externalDemFile.getName();

        } else {

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
    }

    /**
     * Get resampling method.
     * @return The resampling method.
     */
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
     * Get incidence angle and slant range time tie point grids.
     */
    private void getTiePointGrid() {
        latitude = OperatorUtils.getLatitude(sourceProduct);
        longitude = OperatorUtils.getLongitude(sourceProduct);
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
     * Create target product.
     * @throws Exception The exception.
     */
    private void createTargetProduct() throws Exception {

        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    sourceImageWidth,
                                    sourceImageHeight);

        final Band targetBand = new Band("amplitude_sim",
                                   ProductData.TYPE_FLOAT32,
                                   sourceImageWidth,
                                   sourceImageHeight);

        targetBand.setUnit(Unit.AMPLITUDE);
        targetProduct.addBand(targetBand);

        OperatorUtils.copyProductNodes(sourceProduct, targetProduct);

        // the tile width has to be the image width because otherwise sourceRaster.getDataBufferIndex(x, y)
        // returns incorrect index for the last tile on the right
        targetProduct.setPreferredTileSize(targetProduct.getSceneRasterWidth(), 50);
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
        final ProductData trgData = targetTile.getDataBuffer();
        //System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);
        final double halfLightSpeedInMetersPerDay = Constants.halfLightSpeed * 86400.0;

        int ymin = y0;
        int nh = h;
        if (ny0Updated) {
            ymin = ny0;
            nh += y0 - ny0;
            ny0Updated = false;
        }

        float[][] localDEM = new float[nh+2][w+2];
        getLocalDEM(x0, ymin, w, nh, localDEM);

        final double[] earthPoint = new double[3];
        final double[] sensorPos = new double[3];
        for (int y = ymin; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                final double alt = localDEM[y-ymin+1][x-x0+1];
                if (alt == demNoDataValue) {
                    continue;
                }
                GeoUtils.geo2xyz(latitude.getPixelFloat(x, y), longitude.getPixelFloat(x, y), alt, earthPoint, GeoUtils.EarthModel.WGS84);
                final double zeroDopplerTime = getEarthPointZeroDopplerTime(earthPoint);
                double slantRange = computeSlantRange(zeroDopplerTime, earthPoint, sensorPos);
                final double zeroDopplerTimeWithoutBias = zeroDopplerTime + slantRange / halfLightSpeedInMetersPerDay;
                final int azimuthIndex = (int)((zeroDopplerTimeWithoutBias - firstLineUTC) / lineTimeInterval + 0.5);
                slantRange = computeSlantRange(zeroDopplerTimeWithoutBias, earthPoint, sensorPos);
                final int rangeIndex = (int)(computeRangeIndex(zeroDopplerTimeWithoutBias, slantRange) + 0.5);
                final double localIncidenceAngle = computeLocalIncidenceAngle(sensorPos, earthPoint, x0, ymin, x, y, localDEM);
                final double v = computeBackscatteredPower(localIncidenceAngle);
                final int index = targetTile.getDataBufferIndex(rangeIndex, azimuthIndex);
                if (rangeIndex >= x0 && rangeIndex < x0+w && azimuthIndex >= y0 && azimuthIndex < y0+h) {
                    trgData.setElemDoubleAt(index, v + trgData.getElemDoubleAt(index));
                } else {
                    if (azimuthIndex >= y0+h) {
                        if (!ny0Updated) {
                            ny0 = y;
                            ny0Updated = true;
                        } else {
                            ny0 = Math.min(ny0, y);
                        }
                    }
                }
            }
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
            final int yy = y - y0 + 1;
            for (int x = x0 - 1; x < maxX; x++) {
                geoPos.setLocation(latitude.getPixelFloat(x, y), longitude.getPixelFloat(x, y));
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
            if(externalDemFile == null) {
                alt = dem.getElevation(geoPos);
            } else {
                alt = fileElevationModel.getElevation(geoPos);
            }
        } catch (Exception e) {
            alt = demNoDataValue;
        }

        return alt;
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
     * Compute projected local incidence angle (in degree).
     * @param sensorPos The satellite position.
     * @param centrePoint The backscattering element position.
     * @param x0 The x coordinate of the pixel at the upper left corner of current tile.
     * @param y0 The y coordinate of the pixel at the upper left corner of current tile.
     * @param x The x coordinate of the current pixel.
     * @param y The y coordinate of the current pixel.
     * @param localDEM The local DEM.
     * @return The local incidence angle.
     */
    private double computeLocalIncidenceAngle(final double[] sensorPos, final double[] centrePoint,
                                              final int x0, final int y0,
                                              final int x, final int y, final float[][] localDEM) {

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
        GeoUtils.geo2xyz(latitude.getPixelFloat(x+1, y), longitude.getPixelFloat(x+1, y), rightPointHeight, rightPoint, GeoUtils.EarthModel.WGS84);
        GeoUtils.geo2xyz(latitude.getPixelFloat(x-1, y), longitude.getPixelFloat(x-1, y), leftPointHeight, leftPoint, GeoUtils.EarthModel.WGS84);
        GeoUtils.geo2xyz(latitude.getPixelFloat(x, y-1), longitude.getPixelFloat(x, y-1), upPointHeight, upPoint, GeoUtils.EarthModel.WGS84);
        GeoUtils.geo2xyz(latitude.getPixelFloat(x, y+1), longitude.getPixelFloat(x, y+1), downPointHeight, downPoint, GeoUtils.EarthModel.WGS84);

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

        return Math.acos(innerProduct(n, s)) * org.esa.beam.util.math.MathUtils.RTOD;
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

    /**
     * Compute backscattered power for a given local incidence angle.
     * @param localIncidenceAngle The local incidence angle (in degree).
     * @return The backscattered power.
     */
    private double computeBackscatteredPower(final double localIncidenceAngle) {
        final double alpha = localIncidenceAngle*org.esa.beam.util.math.MathUtils.DTOR;
        return (0.0118*Math.cos(alpha) / Math.pow(Math.sin(alpha) + 0.111*Math.cos(alpha), 3));
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
            super(SARSimulationOp.class);
        }
    }
}