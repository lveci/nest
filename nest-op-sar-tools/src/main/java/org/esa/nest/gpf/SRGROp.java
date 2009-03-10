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
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.util.GeoUtils;
import org.esa.nest.util.MathUtils;

import java.awt.*;
import java.util.ArrayList;

import Jama.Matrix;
/**
 * Calibration for ASAR data products.
 *
 * @todo should use user selected interpolation methods
 * @todo add virtual band in target product (see createVirtualIntensityBand() in WSSDeBuestOp.java)
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
    private int warpPolynomialOrder = 4;

//    @Parameter(description = "The number of range points used in computing WARP polynomial",
//               interval = "(1, *)", defaultValue = "100", label="Number of Range Points")
    private int numRangePoints = 100;

    @Parameter(valueSet = {NEAREST_NEIGHBOR, LINEAR, CUBIC, CUBIC2}, defaultValue = LINEAR, label="Interpolation Method")
    private String interpolationMethod = LINEAR;

    private GeoCoding geoCoding;
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
    private int targetImageHeight;

    private static final String NEAREST_NEIGHBOR = "Nearest-neighbor interpolation";
    private static final String LINEAR = "Linear interpolation";
    private static final String CUBIC = "Cubic interpolation";
    private static final String CUBIC2 = "Cubic2 interpolation";

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
            if (numRangePoints < warpPolynomialOrder + 2) {
                throw new OperatorException("numRangePoints must be greater than warpPolynomialOrder");
            }

            final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct);

            srgrFlag = AbstractMetadata.getAttributeBoolean(absRoot, AbstractMetadata.srgr_flag);

            if (srgrFlag) {
                throw new OperatorException("Slant range to ground range conversion has already been applied");
            }

            slantRangeSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.range_spacing);

            sourceImageWidth = sourceProduct.getSceneRasterWidth();
            sourceImageHeight = sourceProduct.getSceneRasterHeight();
            geoCoding = sourceProduct.getGeoCoding();

            computeSlantRangeDistanceArray();

            getNearRangeIncidenceAngle();

            groundRangeSpacing = slantRangeSpacing / Math.sin(nearRangeIncidenceAngle*Math.PI/180.0);

            computeWarpPolynomial(sourceImageHeight / 2);

            createTargetProduct();

        } catch(Exception e) {
            throw new OperatorException("SRGR:"+e.getMessage());
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
      try {
        final Rectangle targetTileRectangle = targetTile.getRectangle();
        final int x0 = targetTileRectangle.x;
        final int y0 = targetTileRectangle.y;
        final int w = targetTileRectangle.width;
        final int h = targetTileRectangle.height;
        //System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

        // compute ground range image pixel values
        final Band sourceBand = sourceProduct.getBand(targetBand.getName());
        final Rectangle sourceTileRectangle = new Rectangle(x0, y0, sourceImageWidth, h);
        final Tile sourceRaster = getSourceTile(sourceBand, sourceTileRectangle, pm);

        final ProductData trgData = targetTile.getDataBuffer();
        final ProductData srcData = sourceRaster.getDataBuffer();

        int p0 = 0, p1 = 0, p2 = 0, p3 = 0;
        double v0 = 0.0, v1 = 0.0, v2 = 0.0, v3 = 0.0, v = 0.0;
        double mu = 0.0;

        for (int x = x0; x < x0 + w; x++) {
            final double p = getSlantRangePixelPosition((double)x);
            if (interpolationMethod.equals(NEAREST_NEIGHBOR)) {
                p0 = Math.min((int)(p + 0.5), sourceImageWidth - 1);
            } else if (interpolationMethod.equals(LINEAR))  {
                p0 = Math.min((int)p, sourceImageWidth - 2);
                p1 = p0 + 1;
                mu = p - p0;
            } else if (interpolationMethod.equals(CUBIC) || interpolationMethod.equals(CUBIC2))  {
                p1 = Math.min((int)p, sourceImageWidth - 1);
                p0 = Math.max(p1 - 1, 0);
                p2 = Math.min(p1 + 1, sourceImageWidth - 1);
                p3 = Math.min(p1 + 2, sourceImageWidth - 1);
                mu = Math.min(p - p1, 1.0);
            }

            for (int y = y0; y < y0 + h; y++) {
                if (interpolationMethod.equals(NEAREST_NEIGHBOR)) {
                    v = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(p0, y));
                } else if (interpolationMethod.equals(LINEAR))  {
                    v0 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(p0, y));
                    v1 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(p1, y));
                    v = MathUtils.interpolationLinear(v0, v1, mu);
                } else if (interpolationMethod.equals(CUBIC))  {
                    v0 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(p0, y));
                    v1 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(p1, y));
                    v2 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(p2, y));
                    v3 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(p3, y));
                    v = MathUtils.interpolationCubic(v0, v1, v2, v3, mu);
                } else if (interpolationMethod.equals(CUBIC2))  {
                    v0 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(p0, y));
                    v1 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(p1, y));
                    v2 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(p2, y));
                    v3 = srcData.getElemDoubleAt(sourceRaster.getDataBufferIndex(p3, y));
                    v = MathUtils.interpolationCubic2(v0, v1, v2, v3, mu);
                }
                v = Math.max(v, 0.0);
                trgData.setElemDoubleAt(targetTile.getDataBufferIndex(x, y), v);
            }
        }
      } catch(Exception e) {
        throw new OperatorException("SRGR:"+e.getMessage());
      }
    }

    /**
     * Get slant range pixel position given pixel index in the ground range image.
     *
     * @param x The pixel index in the ground range image.
     * @return The pixel index in the slant range image
     */
    private double getSlantRangePixelPosition(double x) {

        if (Double.compare(x, 0.0) == 0) {
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

        final TiePointGrid incidenceAngle = OperatorUtils.getIncidenceAngle(sourceProduct);

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
     * Create target product.
     */
    private void createTargetProduct() {

        computeTargetImageDimension();

        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    targetImageWidth,
                                    targetImageHeight);

        addSelectedBands();

        targetProduct.setPreferredTileSize(targetImageWidth, 256);

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        //ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyFlagCodings(sourceProduct, targetProduct);
        //ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        addGeoCoding();

        updateTargetProductMetadata();
    }

    private void computeTargetImageDimension() {

        final double[] xyz = new double[3];
        GeoPos geoPos = geoCoding.getGeoPos(new PixelPos(0, 0), null);
        GeoUtils.geo2xyz(geoPos, xyz);
        double xP0 = xyz[0];
        double yP0 = xyz[1];
        double zP0 = xyz[2];

        double totalDistance = 0.0;
        for (int i = 1; i < sourceImageWidth; i++) {

            geoPos = geoCoding.getGeoPos(new PixelPos(i, 0), null);
            GeoUtils.geo2xyz(geoPos, xyz);
            totalDistance += Math.sqrt(Math.pow(xP0 - xyz[0], 2) +
                                       Math.pow(yP0 - xyz[1], 2) +
                                       Math.pow(zP0 - xyz[2], 2));

            xP0 = xyz[0];
            yP0 = xyz[1];
            zP0 = xyz[2];
        }

        targetImageWidth = (int)(totalDistance / groundRangeSpacing);
        targetImageHeight = sourceImageHeight;
    }

    /**
     * Update metadata in the target product.
     */
    private void updateTargetProductMetadata() {

        final MetadataElement absTgt = AbstractMetadata.getAbstractedMetadata(targetProduct);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.srgr_flag, 1);
        AbstractMetadata.setAttribute(absTgt, AbstractMetadata.range_spacing, groundRangeSpacing);
    }

    private void addSelectedBands() {

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

        for(Band srcBand : sourceBands) {
            if (srcBand.getUnit() != null && srcBand.getUnit().contains("phase")) {
                continue;
            }
            final Band targetBand = new Band(srcBand.getName(),
                                       ProductData.TYPE_FLOAT32, //srcBand.getDataType(),
                                       targetImageWidth,
                                       targetImageHeight);
            targetBand.setUnit(srcBand.getUnit());
            targetProduct.addBand(targetBand);
        }
    }

    private void addGeoCoding() {

        TiePointGrid lat = OperatorUtils.getLatitude(sourceProduct);
        TiePointGrid lon = OperatorUtils.getLongitude(sourceProduct);
        TiePointGrid incidenceAngle = OperatorUtils.getIncidenceAngle(sourceProduct);
        TiePointGrid slantRgTime = OperatorUtils.getSlantRangeTime(sourceProduct);
        if (lat == null || lon == null || incidenceAngle == null || slantRgTime == null) { // for unit test
            ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
            ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
            return;
        }

        int gridWidth = 11;
        int gridHeight = 11;
        float subSamplingX = targetImageWidth / (gridWidth - 1.0f);
        float subSamplingY = targetImageHeight / (gridHeight - 1.0f);
        PixelPos[] newTiePointPos = new PixelPos[gridWidth*gridHeight];

        int k = 0;
        for (int j = 0; j < gridHeight; j++) {
            float y = Math.min(j*subSamplingY, targetImageHeight - 1);
            for (int i = 0; i < gridWidth; i++) {
                float tx = Math.min(i*subSamplingX, targetImageWidth - 1);
                float x = (float)getSlantRangePixelPosition((double)tx);
                newTiePointPos[k] = new PixelPos();
                newTiePointPos[k].x = x;
                newTiePointPos[k].y = y;
                k++;
            }
        }

        OperatorUtils.createNewTiePointGridsAndGeoCoding(
                sourceProduct,
                targetProduct,
                gridWidth,
                gridHeight,
                subSamplingX,
                subSamplingY,
                newTiePointPos);
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
        GeoUtils.geo2xyz(geoPos, xyz);
        double xP0 = xyz[0];
        double yP0 = xyz[1];
        double zP0 = xyz[2];

        for (int i = 0; i < numRangePoints - 1; i++) {

            geoPos = geoCoding.getGeoPos(new PixelPos(pixelsBetweenPoints*(i+1), y0), null);
            GeoUtils.geo2xyz(geoPos, xyz);
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

    public double[] getWarpPolynomialCoef() {
        return warpPolynomialCoef;
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