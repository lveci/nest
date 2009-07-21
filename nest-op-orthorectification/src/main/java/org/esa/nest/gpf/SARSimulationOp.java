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
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.dem.ElevationModelRegistry;
import org.esa.beam.framework.dataop.resamp.Resampling;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.ProductUtils;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.util.Constants;
import org.esa.nest.util.GeoUtils;
import org.esa.nest.util.MathUtils;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;

/**
 * This operator generates simulated SAR image using DEM, the Geocoding and orbit state vectors from a given
 * SAR image, and mathematical modeling of SAR imaging geometry. The simulated SAR image will have the same
 * dimension and resolution as the original SAR image.
 *
 * The simulation algorithm first create a DEM image from the original SAR image. The DEM image has the same
 * dimension as the original SAR image. The value of each pixel in the DEM image is the elevation of the same
 * pixel in the original SAR image. Then, for each cell in the DEM image, its corresponding pixel position
 * (row/column indices) in the simulated SAR image is computed based on the SAR model. Finally, the backscattered
 * power ? for the pixel is computed using backscattering model.
 *
 * Detailed procedure is as the follows:
 * 1. Get the following parameters from the metadata of the SAR image product:
 * (1.1) radar wave length
 * (1.2) range spacing
 * (1.3) first_line_time
 * (1.4) line_time_interval
 * (1.5) slant range to 1st pixel
 * (1.6) orbit state vectors
 * (1.7) slant range to ground range conversion coefficients
 *
 * 2. Compute satellite position and velocity for each azimuth time by interpolating the state vectors;
 *
 * 3. Repeat the following steps for each cell in the DEM image:
 * (3.1) Get latitude, longitude and elevation for the cell;
 * (3.2) Convert (latitude, longitude, elevation) to Cartesian coordinate P(X, Y, Z);
 * (3.3) Compute zero Doppler time t for point P(x, y, z) using Doppler frequency function;
 * (3.3) Compute SAR sensor position S(X, Y, Z) at time t;
 * (3.4) Compute slant range r = |S - P|;
 * (3.5) Compute bias-corrected zero Doppler time tc = t + r*2/c, where c is the light speed;
 * (3.6) Update satellite position S(tc) and slant range r(tc) = |S(tc) – P| for the bias-corrected zero Doppler time tc;
 * (3.7) Compute azimuth index Ia in the source image using zero Doppler time tc;
 * (3.8) Compute range index Ir in the source image using slant range r(tc);
 * (3.9) Compute local incidence angle;
 * (3.10)Compute backscattered power and save it as value for pixel ((int)Ia, (int)Ir);
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

    private double rangeSpacing = 0.0;
    private double firstLineUTC = 0.0; // in days
    private double lastLineUTC = 0.0; // in days
    private double lineTimeInterval = 0.0; // in days
    private double nearEdgeSlantRange = 0.0; // in m
    private double wavelength = 0.0; // in m
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

            getOrbitStateVectors();

            if (srgrFlag) {
                getSrgrCoeff();
            } else {
                getNearEdgeSlantRange();
            }

            getElevationModel();

            getTiePointGrid();

            getSourceImageDimension();

            computeSensorPositionsAndVelocities();

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
    private void getFirstLastLineTimes() {
        firstLineUTC = absRoot.getAttributeUTC(AbstractMetadata.first_line_time).getMJD(); // in days
        lastLineUTC = absRoot.getAttributeUTC(AbstractMetadata.last_line_time).getMJD(); // in days
    }

    /**
     * Get line time interval from the abstracted metadata (in days).
     * @throws Exception The exceptions.
     */
    private void getLineTimeInterval() {
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
     * Create target product.
     * @throws Exception The exception.
     */
    private void createTargetProduct() {

        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    sourceImageWidth,
                                    sourceImageHeight);

        addSelectedBands();

        OperatorUtils.copyProductNodes(sourceProduct, targetProduct);

        final MetadataElement absTgt = AbstractMetadata.getAbstractedMetadata(targetProduct);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.DEM, demName);
        
        // the tile width has to be the image width because otherwise sourceRaster.getDataBufferIndex(x, y)
        // returns incorrect index for the last tile on the right
        targetProduct.setPreferredTileSize(targetProduct.getSceneRasterWidth(), 50);
    }

    private void addSelectedBands() {

        // add master band first
        Band targetBand = new Band("intensity_mst",
                                   ProductData.TYPE_FLOAT32,
                                   sourceImageWidth,
                                   sourceImageHeight);

        targetBand.setUnit(Unit.INTENSITY);
        targetProduct.addBand(targetBand);

        // add selected slave bands
        boolean bandSlected = false;
        if (sourceBandNames == null || sourceBandNames.length == 0) {
            final Band[] bands = sourceProduct.getBands();
            final ArrayList<String> bandNameList = new ArrayList<String>(sourceProduct.getNumBands());
            for (Band band : bands) {
                bandNameList.add(band.getName());
            }
            sourceBandNames = bandNameList.toArray(new String[bandNameList.size()]);
            bandSlected = false;
        } else {
            bandSlected = true;
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
            final String unit = srcBand.getUnit();
            if(unit == null) {
                throw new OperatorException("band " + srcBand.getName() + " requires a unit");
            }

            if (bandSlected && (unit.contains(Unit.IMAGINARY) || unit.contains(Unit.REAL) || unit.contains(Unit.PHASE))) {
                throw new OperatorException("Please select amplitude or intensity band for co-registration");
            }

            targetBand = ProductUtils.copyBand(srcBand.getName(), sourceProduct, targetProduct);
            targetBand.setSourceImage(srcBand.getSourceImage());
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

        double[][] localDEM = new double[nh+2][w+2];
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

                final double zeroDopplerTime = RangeDopplerGeocodingOp.getEarthPointZeroDopplerTime(sourceImageHeight,
                        firstLineUTC, lineTimeInterval, wavelength, earthPoint, sensorPosition, sensorVelocity);

                double slantRange = RangeDopplerGeocodingOp.computeSlantRange(
                        zeroDopplerTime,  timeArray, xPosArray, yPosArray, zPosArray, earthPoint, sensorPos);

                final double zeroDopplerTimeWithoutBias = zeroDopplerTime + slantRange / halfLightSpeedInMetersPerDay;

                final int azimuthIndex = (int)((zeroDopplerTimeWithoutBias - firstLineUTC) / lineTimeInterval + 0.5);

                slantRange = RangeDopplerGeocodingOp.computeSlantRange(
                        zeroDopplerTimeWithoutBias,  timeArray, xPosArray, yPosArray, zPosArray, earthPoint, sensorPos);

                final int rangeIndex = (int)(RangeDopplerGeocodingOp.computeRangeIndex(
                        srgrFlag, sourceImageWidth, firstLineUTC, lastLineUTC, rangeSpacing, zeroDopplerTimeWithoutBias,
                        slantRange, nearEdgeSlantRange, srgrConvParams) + 0.5);


                final RangeDopplerGeocodingOp.LocalGeometry localGeometry = new RangeDopplerGeocodingOp.LocalGeometry();
                setLocalGeometry(x, y, earthPoint, sensorPos, localGeometry);

                double[] localIncidenceAngles = {0.0, 0.0};
                RangeDopplerGeocodingOp.computeLocalIncidenceAngle(
                        localGeometry, true, false, false, x0, ymin, x, y, localDEM, localIncidenceAngles); // in degrees

                //final double localIncidenceAngle = computeLocalIncidenceAngle(sensorPos, earthPoint, x0, ymin, x, y, localDEM);

                final double v = computeBackscatteredPower(localIncidenceAngles[0]);

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
            final int x0, final int y0, final int tileWidth, final int tileHeight, final double[][] localDEM) {

        // Note: the localDEM covers current tile with 1 extra row above, 1 extra row below, 1 extra column to
        //       the left and 1 extra column to the right of the tile.
        final GeoPos geoPos = new GeoPos();
        final int maxY = y0 + tileHeight + 1;
        final int maxX = x0 + tileWidth + 1;
        for (int y = y0 - 1; y < maxY; y++) {
            final int yy = y - y0 + 1;
            for (int x = x0 - 1; x < maxX; x++) {
                geoPos.setLocation(latitude.getPixelFloat(x, y), longitude.getPixelFloat(x, y));
                localDEM[yy][x - x0 + 1] = getLocalElevation(geoPos);
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

    private void setLocalGeometry(final int x, final int y, final double[] earthPoint, final double[] sensorPos,
                                  RangeDopplerGeocodingOp.LocalGeometry localGeometry) {
        localGeometry.leftPointLat  = latitude.getPixelFloat(x-1, y);
        localGeometry.leftPointLon  = longitude.getPixelFloat(x-1, y);
        localGeometry.rightPointLat = latitude.getPixelFloat(x+1, y);
        localGeometry.rightPointLon = longitude.getPixelFloat(x+1, y);
        localGeometry.upPointLat    = latitude.getPixelFloat(x, y-1);
        localGeometry.upPointLon    = longitude.getPixelFloat(x, y-1);
        localGeometry.downPointLat  = latitude.getPixelFloat(x, y+1);
        localGeometry.downPointLon  = longitude.getPixelFloat(x, y+1);
        localGeometry.centrePoint   = earthPoint;
        localGeometry.sensorPos     = sensorPos;
    }

    /**
     * Compute backscattered power for a given local incidence angle.
     * @param localIncidenceAngle The local incidence angle (in degree).
     * @return The backscattered power.
     */
    private static double computeBackscatteredPower(final double localIncidenceAngle) {
        final double alpha = localIncidenceAngle*org.esa.beam.util.math.MathUtils.DTOR;
        final double cosAlpha = Math.cos(alpha);
        return (0.0118*cosAlpha / Math.pow(Math.sin(alpha) + 0.111*cosAlpha, 3));
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