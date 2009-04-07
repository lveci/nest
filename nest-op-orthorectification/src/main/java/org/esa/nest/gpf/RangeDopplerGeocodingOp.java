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

    @Parameter(valueSet = {NEAREST_NEIGHBOUR, BILINEAR, CUBIC}, defaultValue = BILINEAR, label="Resampling Method")
    private String resamplingMethod = BILINEAR;

    private Band sourceBand = null;
    private MetadataElement absRoot = null;
    private ElevationModel dem = null;
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

    private SRGRConvParameters[] srgrConvParams;
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

            getSrgrCoeff();

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
        srgrConvParams = new SRGRConvParameters[14];
        /*
        for (int i = 0; i < srgrConvParams.length; i++) {
            SRGRConvParameters params = new SRGRConvParameters();
            params.zeroDopplerTime = 0;
            params.groundRangeOrigin = 0;
            params.srgeCoeff = new double[5];
            params.srgeCoeff[0] = 0;
            params.srgeCoeff[1] = 0;
            params.srgeCoeff[2] = 0;
            params.srgeCoeff[3] = 0;
            params.srgeCoeff[4] = 0;
            srgrConvParams[i] = params;
        }
        */
        srgrConvParams[0] = new SRGRConvParameters();
        srgrConvParams[0].zeroDopplerTime = ProductData.UTC.parse("19-JAN-2008 09:34:46.546519").getMJD();
        srgrConvParams[0].groundRangeOrigin = 0;
        srgrConvParams[0].srgeCoeff = new double[5];
        srgrConvParams[0].srgeCoeff[0] = 819107.94;
        srgrConvParams[0].srgeCoeff[1] = 0.27902585;
        srgrConvParams[0].srgeCoeff[2] = 6.5015223e-7;
        srgrConvParams[0].srgeCoeff[3] = -2.9536695e-13;
        srgrConvParams[0].srgeCoeff[4] = 3.545273e-20;

        srgrConvParams[1] = new SRGRConvParameters();
        srgrConvParams[1].zeroDopplerTime = ProductData.UTC.parse("19-JAN-2008 09:34:52.664346").getMJD();
        srgrConvParams[1].groundRangeOrigin = 0;
        srgrConvParams[1].srgeCoeff = new double[5];
        srgrConvParams[1].srgeCoeff[0] = 819021.3;
        srgrConvParams[1].srgeCoeff[1] = 0.27899656;
        srgrConvParams[1].srgeCoeff[2] = 6.5022937e-7;
        srgrConvParams[1].srgeCoeff[3] = -2.954239e-13;
        srgrConvParams[1].srgeCoeff[4] = 3.5463617e-20;

        srgrConvParams[2] = new SRGRConvParameters();
        srgrConvParams[2].zeroDopplerTime = ProductData.UTC.parse("19-JAN-2008 09:34:58.782173").getMJD();
        srgrConvParams[2].groundRangeOrigin = 0;
        srgrConvParams[2].srgeCoeff = new double[5];
        srgrConvParams[2].srgeCoeff[0] = 818935.06;
        srgrConvParams[2].srgeCoeff[1] = 0.27896845;
        srgrConvParams[2].srgeCoeff[2] = 6.503057e-7;
        srgrConvParams[2].srgeCoeff[3] = -2.9548098e-13;
        srgrConvParams[2].srgeCoeff[4] = 3.547482e-20;

        srgrConvParams[3] = new SRGRConvParameters();
        srgrConvParams[3].zeroDopplerTime = ProductData.UTC.parse("19-JAN-2008 09:35:04.900000").getMJD();
        srgrConvParams[3].groundRangeOrigin = 0;
        srgrConvParams[3].srgeCoeff = new double[5];
        srgrConvParams[3].srgeCoeff[0] = 818848.75;
        srgrConvParams[3].srgeCoeff[1] = 0.27893913;
        srgrConvParams[3].srgeCoeff[2] = 6.5038273e-7;
        srgrConvParams[3].srgeCoeff[3] = -2.9553774e-13;
        srgrConvParams[3].srgeCoeff[4] = 3.5485644e-20;

        srgrConvParams[4] = new SRGRConvParameters();
        srgrConvParams[4].zeroDopplerTime = ProductData.UTC.parse("19-JAN-2008 09:35:11.017827").getMJD();
        srgrConvParams[4].groundRangeOrigin = 0;
        srgrConvParams[4].srgeCoeff = new double[5];
        srgrConvParams[4].srgeCoeff[0] = 818762.5;
        srgrConvParams[4].srgeCoeff[1] = 0.27890918;
        srgrConvParams[4].srgeCoeff[2] = 6.5046e-7;
        srgrConvParams[4].srgeCoeff[3] = -2.955943e-13;
        srgrConvParams[4].srgeCoeff[4] = 3.549626e-20;

        srgrConvParams[5] = new SRGRConvParameters();
        srgrConvParams[5].zeroDopplerTime = ProductData.UTC.parse("19-JAN-2008 09:35:17.135654").getMJD();
        srgrConvParams[5].groundRangeOrigin = 0;
        srgrConvParams[5].srgeCoeff = new double[5];
        srgrConvParams[5].srgeCoeff[0] = 818676.75;
        srgrConvParams[5].srgeCoeff[1] = 0.27888086;
        srgrConvParams[5].srgeCoeff[2] = 6.505361E-7;
        srgrConvParams[5].srgeCoeff[3] = -2.9565098E-13	;
        srgrConvParams[5].srgeCoeff[4] = 3.5507293E-20;

        srgrConvParams[6] = new SRGRConvParameters();
        srgrConvParams[6].zeroDopplerTime = ProductData.UTC.parse("19-JAN-2008 09:35:23.253481").getMJD();
        srgrConvParams[6].groundRangeOrigin = 0;
        srgrConvParams[6].srgeCoeff = new double[5];
        srgrConvParams[6].srgeCoeff[0] = 818591.25;
        srgrConvParams[6].srgeCoeff[1] = 0.27885258;
        srgrConvParams[6].srgeCoeff[2] = 6.506121E-7;
        srgrConvParams[6].srgeCoeff[3] = -2.9570752E-13;
        srgrConvParams[6].srgeCoeff[4] = 3.551828E-20;

        srgrConvParams[7] = new SRGRConvParameters();
        srgrConvParams[7].zeroDopplerTime = ProductData.UTC.parse("19-JAN-2008 09:35:29.371308").getMJD();
        srgrConvParams[7].groundRangeOrigin = 0;
        srgrConvParams[7].srgeCoeff = new double[5];
        srgrConvParams[7].srgeCoeff[0] = 818505.6;
        srgrConvParams[7].srgeCoeff[1] = 0.27882284;
        srgrConvParams[7].srgeCoeff[2] = 6.5068883E-7;
        srgrConvParams[7].srgeCoeff[3] = -2.9576368E-13;
        srgrConvParams[7].srgeCoeff[4] = 3.5528826E-20;

        srgrConvParams[8] = new SRGRConvParameters();
        srgrConvParams[8].zeroDopplerTime = ProductData.UTC.parse("19-JAN-2008 09:35:35.489135").getMJD();
        srgrConvParams[8].groundRangeOrigin = 0;
        srgrConvParams[8].srgeCoeff = new double[5];
        srgrConvParams[8].srgeCoeff[0] = 818420.56;
        srgrConvParams[8].srgeCoeff[1] = 0.27879432;
        srgrConvParams[8].srgeCoeff[2] = 6.507646E-7;
        srgrConvParams[8].srgeCoeff[3] = -2.9581987E-13;
        srgrConvParams[8].srgeCoeff[4] = 3.5539653E-20;

        srgrConvParams[9] = new SRGRConvParameters();
        srgrConvParams[9].zeroDopplerTime = ProductData.UTC.parse("19-JAN-2008 09:35:41.606962").getMJD();
        srgrConvParams[9].groundRangeOrigin = 0;
        srgrConvParams[9].srgeCoeff = new double[5];
        srgrConvParams[9].srgeCoeff[0] = 818335.8;
        srgrConvParams[9].srgeCoeff[1] = 0.27876624;
        srgrConvParams[9].srgeCoeff[2] = 6.5084E-7;
        srgrConvParams[9].srgeCoeff[3] = -2.9587595E-13;
        srgrConvParams[9].srgeCoeff[4] = 3.5550546E-20;

        srgrConvParams[10] = new SRGRConvParameters();
        srgrConvParams[10].zeroDopplerTime = ProductData.UTC.parse("19-JAN-2008 09:35:47.724789").getMJD();
        srgrConvParams[10].groundRangeOrigin = 0;
        srgrConvParams[10].srgeCoeff = new double[5];
        srgrConvParams[10].srgeCoeff[0] = 818251.4;
        srgrConvParams[10].srgeCoeff[1] = 0.27873793;
        srgrConvParams[10].srgeCoeff[2] = 6.509153E-7;
        srgrConvParams[10].srgeCoeff[3] = -2.959318E-13;
        srgrConvParams[10].srgeCoeff[4] = 3.5561312E-20;

        srgrConvParams[11] = new SRGRConvParameters();
        srgrConvParams[11].zeroDopplerTime = ProductData.UTC.parse("19-JAN-2008 09:35:53.842620").getMJD();
        srgrConvParams[11].groundRangeOrigin = 0;
        srgrConvParams[11].srgeCoeff = new double[5];
        srgrConvParams[11].srgeCoeff[0] = 818166.9;
        srgrConvParams[11].srgeCoeff[1] = 0.27870905;
        srgrConvParams[11].srgeCoeff[2] = 6.509909E-7;
        srgrConvParams[11].srgeCoeff[3] = -2.9598744E-13;
        srgrConvParams[11].srgeCoeff[4] = 3.5571888E-20;

        srgrConvParams[12] = new SRGRConvParameters();
        srgrConvParams[12].zeroDopplerTime = ProductData.UTC.parse("19-JAN-2008 09:35:59.960447").getMJD();
        srgrConvParams[12].groundRangeOrigin = 0;
        srgrConvParams[12].srgeCoeff = new double[5];
        srgrConvParams[12].srgeCoeff[0] = 818083.0;
        srgrConvParams[12].srgeCoeff[1] = 0.27868077;
        srgrConvParams[12].srgeCoeff[2] = 6.5106576E-7;
        srgrConvParams[12].srgeCoeff[3] = -2.960428E-13;
        srgrConvParams[12].srgeCoeff[4] = 3.5582525E-20;

        srgrConvParams[13] = new SRGRConvParameters();
        srgrConvParams[13].zeroDopplerTime = ProductData.UTC.parse("19-JAN-2008 09:36:06.078274").getMJD();
        srgrConvParams[13].groundRangeOrigin = 0;
        srgrConvParams[13].srgeCoeff = new double[5];
        srgrConvParams[13].srgeCoeff[0] = 817999.6;
        srgrConvParams[13].srgeCoeff[1] = 0.27865288;
        srgrConvParams[13].srgeCoeff[2] = 6.5114017E-7;
        srgrConvParams[13].srgeCoeff[3] = -2.9609805E-13;
        srgrConvParams[13].srgeCoeff[4] = 3.55932E-20;
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
        final double meanLat = 0.5*(latMin + latMax)*org.esa.beam.util.math.MathUtils.DTOR;
        delLat = minSpacing / MeanEarthRadius * org.esa.beam.util.math.MathUtils.RTOD;
        delLon = minSpacing / (MeanEarthRadius*Math.cos(meanLat)) * org.esa.beam.util.math.MathUtils.RTOD;
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

        if(resamplingMethod.equals(NEAREST_NEIGHBOUR))
            dem.setResamplingMethod(Resampling.NEAREST_NEIGHBOUR);
        else if(resamplingMethod.equals(BILINEAR))
            dem.setResamplingMethod(Resampling.BILINEAR_INTERPOLATION);
        else if(resamplingMethod.equals(CUBIC))
            dem.setResamplingMethod(Resampling.CUBIC_CONVOLUTION);

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
     * @throws Exception The exception.
     */
    private void createTargetProduct() throws Exception {
        
        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    targetImageWidth,
                                    targetImageHeight);

        addSelectedBands();

        // todo create new tie points for target product (lat, lon)
        // todo update metadata for target product

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
                                                 ProductData.TYPE_FLOAT64,
                                                 targetImageWidth,
                                                 targetImageHeight);

                targetBand.setUnit(targetUnit);
                targetProduct.addBand(targetBand);
            }
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
        System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

        try {
            sourceBand = sourceProduct.getBand(targetBand.getName());
            srcBandNoDataValue = sourceBand.getNoDataValue();

            final ProductData trgData = targetTile.getDataBuffer();
            final GeoPos geoPos = new GeoPos();
            final double[] earthPoint = new double[3];

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
                    if (rangeIndex < 0.0 || rangeIndex >= sourceImageWidth - 1 ||
                        azimuthIndex < 0.0 || azimuthIndex >= sourceImageHeight - 1) {
                            trgData.setElemDoubleAt(index, srcBandNoDataValue);
                    } else
                        trgData.setElemDoubleAt(index, getPixelValue(azimuthIndex, rangeIndex));
                }
            }
        } catch(Exception e) {
            throw new OperatorException(e);
        }
    }

    /**
     * Get local elevation 9in meter) for given latitude and longitude.
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

        //todo For slant range image, the index is computed differently.
        int i;
        for (i = 0; i < srgrConvParams.length; i++) {
            if (zeroDopplerTime < srgrConvParams[i].zeroDopplerTime) {
                break;
            }
        }
        return computeGroundRange(slantRange, srgrConvParams[i-1].srgeCoeff) / rangeSpacing;
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
        while (Math.abs(y) > 0.001) {

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

        // todo For complex image, intensity image is generated first.

        /*
        final Rectangle sourceTileRectangle = getSourceTileRectangle(x0, y0, w, h);
        final Tile sourceRaster = getSourceTile(sourceBand, sourceTileRectangle, pm);
        final ProductData srcData = sourceRaster.getDataBuffer();
        */
        final int x0 = (int)rangeIndex;
        final double muX = rangeIndex - x0;

        final int y0 = (int)azimuthIndex;
        final double muY = azimuthIndex - y0;

        // todo check if the following call will triger previous operator
        double[] pixels = new double[4];
        sourceBand.readPixels(x0, y0, 2, 2, pixels);

        return MathUtils.interpolationBiLinear(pixels[0]*pixels[0], pixels[1]*pixels[1],
                                               pixels[2]*pixels[2], pixels[3]*pixels[3],
                                               muX, muY);
    }

    private static class SRGRConvParameters {
        private double zeroDopplerTime;   // in MJD
        private double groundRangeOrigin; // in m
        private double[] srgeCoeff;
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