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
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.math.MathUtils;
import org.esa.nest.datamodel.AbstractMetadata;

import java.awt.*;
import java.util.ArrayList;

import Jama.Matrix;
/**
 * Calibration for ASAR data products.
 *
 * @todo handle Incidence angle should be obtained from the abstracted metadata, mission type should not be used
 * @todo should use user selected interpolation methods
 * @todo add virtual band in target product (see createVirtualIntensityBand() in WSSDeBuestOp.java)
 * @todo compute xyz from lat/lon using library
 */
/**
 * Slant Range to Ground Range Conversion.
 */

@OperatorMetadata(alias="SRGR", description="Converts Slant Range to Ground Range")
public class SRGROp extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            sourceProductId="source", label="Source Bands")
    String[] sourceBandNames;

    @Parameter(description = "The order of WARP polynomial function", interval = "[1, *)", defaultValue = "4",
                label="Warp Polynomial Order")
    private int warpPolynomialOrder;

    @Parameter(description = "The number of range points used in computing WARP polynomial",
               interval = "(1, *)", defaultValue = "100", label="Number of Range Points")
    private int numRangePoints;

//    @Parameter(valueSet = {NEAREST_NEIGHBOR, LINEAR, CUBIC, SINC}, defaultValue = LINEAR, label="Interpolation Method")
//    private String interpolationMethod;

    private MetadataElement absRoot;
    private GeoCoding geoCoding;
    private String missionType;
    private boolean srgrFlag;
    private double slantRangeSpacing; // in m
    private double groundRangeSpacing; // in m
    private double nearRangeIncidenceAngle; // in degree
    private double[] slantRangeDistanceArray; // slant range distance from each selected range point to the 1st one
    private double[] groundRangeDistanceArray; // ground range distance from each selected range point to the first one
    private double[] warpPolynomialCoef; // coefficients for warp polynomial

    private int sourceImageWidth;
    private int sourceImageHeight;
    private int targetImageWidth;
    private int tileIdx;

    private static final String NEAREST_NEIGHBOR = "Nearest-neighbor interpolation";
    private static final String LINEAR = "Linear interpolation";
    private static final String CUBIC = "Cubic interpolation";
    private static final String SINC = "Sinc interpolation";

    private static double a = 6378137; // m
    private static double earthFlatCoef = 298.257223563;
    private static double e = 2 / earthFlatCoef - 1 / (earthFlatCoef * earthFlatCoef);

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

        if (numRangePoints < warpPolynomialOrder + 2) {
            throw new OperatorException("numRangePoints must be greater than warpPolynomialOrder");
        }

        absRoot = sourceProduct.getMetadataRoot().getElement("Abstracted Metadata");
        if (absRoot == null) {
            throw new OperatorException("Abstracted Metadata not found");
        }

        getSRGRFlag();

        if (srgrFlag) {
            throw new OperatorException("Slant range to ground range conversion has already been applied");
        }

        getSlantRangeSpacing();

        sourceImageWidth = sourceProduct.getSceneRasterWidth();
        sourceImageHeight = sourceProduct.getSceneRasterHeight();
        geoCoding = sourceProduct.getGeoCoding();

        computeSlantRangeDistanceArray();
        getNearRangeIncidenceAngle();

        groundRangeSpacing = slantRangeSpacing / Math.sin(nearRangeIncidenceAngle*MathUtils.DTOR);

        createTargetProduct();

        tileIdx = -1;
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
        final int w = targetTileRectangle.width;
        final int h = targetTileRectangle.height;
        //System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

        // compute WARP polynomial coefficients
        if (tileIdx != y0) {
            tileIdx = y0;
            computeWarpPolynomial(y0);
        }

        // compute ground range image pixel values
        final Band sourceBand = sourceProduct.getBand(targetBand.getName());
        final Rectangle sourceTileRectangle = new Rectangle(x0, y0, sourceImageWidth, h);
        final Tile sourceRaster = getSourceTile(sourceBand, sourceTileRectangle, pm);

        final ProductData trgData = targetTile.getDataBuffer();
        final ProductData srcData = sourceRaster.getDataBuffer();

        for (int x = x0; x < x0 + w; x++) {

            final double srpp = getSlantRangePixelPosition(x);

            int x1, x2;
            if (srpp < x0 + sourceImageWidth - 1) {
                x1 = (int)srpp;
                x2 = x1 + 1;
            } else {
                x1 = x0 + sourceImageWidth - 2;
                x2 = x0 + sourceImageWidth - 1;
            }
            final double a = x2 - srpp;
            final double b = srpp - x1;

            for (int y = y0; y < y0 + h; y++) {

                final double v1 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x1, y));
                final double v2 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(x2, y));
                final double v = a*v1 + b*v2;

                trgData.setElemDoubleAt(targetTile.getDataBufferIndex(x, y), v);
            }
        }
    }

    /**
     * Get slant range pixel position given pixel index in the ground range image.
     *
     * @param x The pixel index in the ground range image.
     * @return The pixel index in the slant range image
     */
    private double getSlantRangePixelPosition(int x) {

        if (x == 0) {
            return 0.0;
        }

        final double dg = groundRangeSpacing * x;
        double ds = 0.0;
        for (int j = 0; j < warpPolynomialOrder + 1; j++) {
            ds += Math.pow(dg, (double)j) * warpPolynomialCoef[j];
        }
        return ds / slantRangeSpacing;
    }

    /**
     * Get srgr_flag from metadata.
     */
    private void getSRGRFlag() {

        final MetadataAttribute srgrFlagAttr = absRoot.getAttribute(AbstractMetadata.srgr_flag);
        if (srgrFlagAttr == null) {
            throw new OperatorException(AbstractMetadata.srgr_flag + " not found");
        }
        srgrFlag = srgrFlagAttr.getData().getElemBoolean();
    }

    /**
     * Get slant range spacing from metadata.
     */
    private void getSlantRangeSpacing() {
        
        final MetadataAttribute rangeSpacingAttr = absRoot.getAttribute(AbstractMetadata.range_spacing);
        if (rangeSpacingAttr == null) {
            throw new OperatorException(AbstractMetadata.range_spacing + " not found");
        }

        slantRangeSpacing = (double)rangeSpacingAttr.getData().getElemFloat();
        if(slantRangeSpacing == 0) {
            throw new OperatorException(AbstractMetadata.range_spacing + " is zero");
        }
    }

    /**
     * Compute slant range distance from each selected range point to the 1st one.
     */
    private void computeSlantRangeDistanceArray() {

        slantRangeDistanceArray = new double[numRangePoints - 1];
        final int pixelsBetweenPoints = sourceImageWidth / numRangePoints;
        final double slantDistanceBetweenPoints = slantRangeSpacing * pixelsBetweenPoints;
        for (int i = 0; i < numRangePoints - 1; i++) {
            slantRangeDistanceArray[i] = slantDistanceBetweenPoints * (i+1);
        }
    }

    /**
     * Get near range incidence angle (in degree).
     */
    private void getNearRangeIncidenceAngle() {

        final TiePointGrid incidenceAngle = getIncidenceAngle();

        final double alphaFirst = incidenceAngle.getPixelFloat(0.5f, 0.5f);
        final double alphaLast = incidenceAngle.getPixelFloat(sourceImageWidth - 0.5f, 0.5f);
        nearRangeIncidenceAngle = Math.min(alphaFirst, alphaLast);

        /*
        final MetadataAttribute passAttr = absRoot.getAttribute(AbstractMetadata.PASS);
        if (passAttr == null) {
            throw new OperatorException(AbstractMetadata.PASS + " not found");
        }
        final String pass = passAttr.getData().getElemString();

        if (pass.contains("DESCENDING")) {
            nearRangeIncidenceAngle = incidenceAngle.getPixelFloat(sourceImageWidth - 0.5f, 0.5f);
        } else {
            nearRangeIncidenceAngle = incidenceAngle.getPixelFloat(0.5f, 0.5f);
        }
        */
    }

    /**
     * Get incidence angle tie point grid.
     *
     * @return srcTPG The incidence angle tie point grid.
     */
    private TiePointGrid getIncidenceAngle() {

        for (int i = 0; i < sourceProduct.getNumTiePointGrids(); i++) {
            final TiePointGrid srcTPG = sourceProduct.getTiePointGridAt(i);
            if (srcTPG.getName().equals("incident_angle")) {
                return srcTPG;
            }
        }
        return null;
    }

    /**
     * Get the mission type.
     */
    private void getMissionType() {

        final MetadataAttribute missionTypeAttr = absRoot.getAttribute(AbstractMetadata.MISSION);
        if (missionTypeAttr == null) {
            throw new OperatorException(AbstractMetadata.MISSION + " not found");
        }

        missionType = missionTypeAttr.getData().getElemString();
        //System.out.println("Mission is " + missionType);
    }
    
    /**
     * Create target product.
     */
    private void createTargetProduct() {

        computeTargetImageWidth();

        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    targetImageWidth,
                                    sourceImageHeight);

        addSelectedBands();

        targetProduct.setPreferredTileSize(targetImageWidth, 256);

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyFlagCodings(sourceProduct, targetProduct);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        updateTargetProductMetadata();
    }

    private void computeTargetImageWidth() {

        final double[] xyz = new double[3];
        GeoPos geoPos = geoCoding.getGeoPos(new PixelPos(0, 0), null);
        geo2xyz(geoPos, xyz);
        double xP0 = xyz[0];
        double yP0 = xyz[1];
        double zP0 = xyz[2];

        double totalDistance = 0.0;
        for (int i = 1; i < sourceImageWidth; i++) {

            geoPos = geoCoding.getGeoPos(new PixelPos(i, 0), null);
            geo2xyz(geoPos, xyz);
            totalDistance += Math.sqrt(Math.pow(xP0 - xyz[0], 2) +
                                       Math.pow(yP0 - xyz[1], 2) +
                                       Math.pow(zP0 - xyz[2], 2));

            xP0 = xyz[0];
            yP0 = xyz[1];
            zP0 = xyz[2];
        }

        targetImageWidth = (int)(totalDistance / groundRangeSpacing);
    }

    /**
     * Update metadata in the target product.
     */
    private void updateTargetProductMetadata() {

        final MetadataElement abs = targetProduct.getMetadataRoot().getElement("Abstracted Metadata");
        if (abs == null) {
            throw new OperatorException("Abstracted Metadata not found");
        }
        abs.getAttribute(AbstractMetadata.srgr_flag).getData().setElemBoolean(true);

        final MetadataAttribute rangeSpacingAttr = abs.getAttribute(AbstractMetadata.range_spacing);
        if (rangeSpacingAttr == null) {
            throw new OperatorException(AbstractMetadata.range_spacing + " not found");
        }
        rangeSpacingAttr.getData().setElemFloat((float)(groundRangeSpacing));
    }

    private void addSelectedBands() {

        if (sourceBandNames == null || sourceBandNames.length == 0) {
            final Band[] bands = sourceProduct.getBands();
            ArrayList<String> bandNameList = new ArrayList<String>(sourceProduct.getNumBands());
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

        for(Band srcBand : sourceBands) {
            if (srcBand.getUnit() != null && srcBand.getUnit().contains("phase")) {
                continue;
            }
            final Band targetBand = new Band(srcBand.getName(),
                                       ProductData.TYPE_FLOAT32,
                                       targetImageWidth,
                                       sourceImageHeight);
            targetProduct.addBand(targetBand);
        }
    }

    /**
     * Compute WARP polynomial coefficients.
     *
     * @param y0 The y coordinate of the upper left pixel in the current tile.
     */
    private void computeWarpPolynomial(int y0) {

        computeGroundRangeDistanceArray(y0);
        final Matrix A = createVandermondeMatrix(groundRangeDistanceArray);
        final Matrix b = new Matrix(slantRangeDistanceArray, numRangePoints - 1);
        final Matrix x = A.solve(b);
        warpPolynomialCoef = x.getColumnPackedCopy();

        /*
        for (double c : warpPolynomialCoef) {
            System.out.print(c + ", ");
        }
        System.out.println();
        */
    }

    /**
     * Compute ground range distance from each selected range point to the 1st one (in m).
     *
     * @param y0 The y coordinate of the upper left pixel in the current tile.
     */
    private void computeGroundRangeDistanceArray(int y0) {

        groundRangeDistanceArray = new double[numRangePoints - 1];
        final double[] xyz = new double[3];
        final int pixelsBetweenPoints = sourceImageWidth / numRangePoints;

        // geomatic geo = new geomatic();
        // MxVector3 xyz1 = new MxVector3();
        // xyz1 = geo.GeoCordToXYZ( xyz1, t1*geo.R2D, -t3*geo.R2D+180, 0);

        GeoPos geoPos = geoCoding.getGeoPos(new PixelPos(0, y0), null);
        geo2xyz(geoPos, xyz);
        double xP0 = xyz[0];
        double yP0 = xyz[1];
        double zP0 = xyz[2];

        for (int i = 0; i < numRangePoints - 1; i++) {

            geoPos = geoCoding.getGeoPos(new PixelPos(pixelsBetweenPoints*(i+1), y0), null);
            geo2xyz(geoPos, xyz);
            final double pointToPointDistance = Math.sqrt(Math.pow(xP0 - xyz[0], 2) +
                                                    Math.pow(yP0 - xyz[1], 2) +
                                                    Math.pow(zP0 - xyz[2], 2));

            if (i == 0) {
                groundRangeDistanceArray[i] = pointToPointDistance;
            } else {
                groundRangeDistanceArray[i] = groundRangeDistanceArray[i-1] + pointToPointDistance;
            }

            xP0 = xyz[0];
            yP0 = xyz[1];
            zP0 = xyz[2];
        }
    }

    /**
     * Compute XYZ coordinates for a given pixel from its geo position.
     *
     * @param geoPos The geo position of a given pixel.
     * @param xyz The xyz coordinates of the given pixel.
     */
    private static void geo2xyz(GeoPos geoPos, double xyz[]) {

        final double lat = ((double)geoPos.lat) * MathUtils.DTOR;
        final double lon = ((double)geoPos.lon) * MathUtils.DTOR;

        final double sinLat = Math.sin(lat);
        final double cosLat = Math.cos(lat);
        final double N = a / Math.sqrt(1 - Math.pow(e*sinLat, 2));

        xyz[0] = N * cosLat * Math.cos(lon); // in m
        xyz[1] = N * cosLat * Math.sin(lon); // in m
        xyz[2] = (1 - e * e) * N * sinLat;   // in m
    }

    /**
     * Get Vandermonde matrix constructed from a given distance array.
     *
     * @param d The given range distance array.
     * @return the matrix
     */
    private Matrix createVandermondeMatrix(double[] d) {

        final int n = d.length;
        final double[][] array = new double[n][warpPolynomialOrder + 1];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < warpPolynomialOrder + 1; j++) {
                array[i][j] = Math.pow(d[i], (double)j);
            }
        }

        return new Matrix(array);
    }

    /**
     * Set the number of range points used for creating warp function.
     * This function is used for unit test only.
     *
     * @param numPoints The number of range points.
     */
    public void setNumOfRangePoints(int numPoints) {
        numRangePoints = numPoints;
    }

    public void setSourceBandName(String name) {
        sourceBandNames = new String[1];
        sourceBandNames[0] = name;
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
            super(SRGROp.class);
        }
    }
}