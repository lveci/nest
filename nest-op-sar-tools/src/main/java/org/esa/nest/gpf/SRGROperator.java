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

import javax.media.jai.JAI;
import java.awt.*;

import Jama.Matrix;
/**
 * Calibration for ASAR data products.
 *
 * @todo handle ERS product
 * @todo handle case that output image has different dimension than the input image
 * @todo use bilinear, nearest neighbor, cubic and sinc interpolation methods
 * @todo output warp coefficients of all tiles to NEST metadata
 * @todo compute xyz from lat/lon using library
 */
/**
 * Slant Range to Ground Range Conversion.
 */

/**
 * The sample operator implementation for an algorithm
 * that can compute bands independently of each other.
 */
@OperatorMetadata(alias="SRGR")
public class SRGROperator extends Operator {

    @SourceProduct
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The order of WARP polynomial function", interval = "[1, *)", defaultValue = "4")
    private int warpPolynomialOrder;

    @Parameter(description = "The number of range points used in computing WARP polynomial",
               interval = "(1, *)", defaultValue = "100")
    private int numRangePoints;

    @Parameter(valueSet = {NEAREST_NEIGHBOR, BILINEAR, BICUBIC, SINC}, defaultValue = BILINEAR)
    private String interpolationMethod;

    private MetadataElement mppAds;
    private TiePointGrid incidenceAngle;
    private GeoCoding geoCoding;
    private boolean srgrFlag;
    private double slantRangeSpacing; // in m
    private double groundRangeSpacing; // in m
    private double[] slantRangeDistanceArray; // slant range distance from each selected range point to the 1st one
    private double[] groundRangeDistanceArray; // ground range distance from each selected range point to the first one
    private double[] warpPolynomialCoef; // coefficients for warp polynomial

    private int sourceImageWidth;
    private int sourceImageHeight;
    private int tileIdx;

    private static final String NEAREST_NEIGHBOR = "Nearest-neighbor interpolation";
    private static final String BILINEAR = "Bilinear interpolation";
    private static final String BICUBIC = "Bicubic interpolation";
    private static final String SINC = "Sinc interpolation";

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

        if (numRangePoints < warpPolynomialOrder + 1) {
            throw new OperatorException("numRangePoints must be greater than warpPolynomialOrder");
        }

        getMainProcParamADS();

        getSRGRFlag();

        if (srgrFlag) {
            throw new OperatorException("Slant range to ground range conversion has already been applied");
        }

        getSlantRangeSpacing();

        sourceImageWidth = sourceProduct.getSceneRasterWidth();
        sourceImageHeight = sourceProduct.getSceneRasterHeight();
        geoCoding = sourceProduct.getGeoCoding();

        computeSlantRangeDistanceArray();

        getIncidenceAngle();

        computeGroundRangeSpacing();

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

        Rectangle targetTileRectangle = targetTile.getRectangle();
        int x0 = targetTileRectangle.x;
        int y0 = targetTileRectangle.y;
        int w = targetTileRectangle.width;
        int h = targetTileRectangle.height;
        System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

        // compute WARP polynomial coefficients
        if (tileIdx != y0) {
            tileIdx = y0;
            computeWarpPolynomial(y0);
        }

        // compute ground range image pixel values
        Band sourceBand = sourceProduct.getBand(targetBand.getName());
        Tile sourceRaster = getSourceTile(sourceBand, targetTileRectangle, pm);

        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {

                double srpp = getSlantRangePixelPosition(x);

                // get pixel value by interpolation
                int x1, x2;
                if (srpp < x0 + w - 1) {
                    x1 = (int)srpp;
                    x2 = x1 + 1;
                } else {
                    x1 = x0 + w - 2;
                    x2 = x0 + w - 1;
                }
                double v1 = sourceRaster.getSampleDouble(x1, y);
                double v2 = sourceRaster.getSampleDouble(x2, y);
                double v = (x2 - srpp)*v1 + (srpp - x1)*v2;

                targetTile.setSample(x, y, v);
            }
        }
    }

    /**
     * Get slant range pixel position given pixel index in the ground range image.
     *
     * @param x The pixel index in the ground range image.
     * @return The pixel index in the slant range image
     */
    double getSlantRangePixelPosition(int x) {

        double dg = groundRangeSpacing * x;
        double ds = 0.0;
        for (int j = 0; j < warpPolynomialOrder + 1; j++) {
            ds += Math.pow(dg, (double)j) * warpPolynomialCoef[j];
        }
        return ds / slantRangeSpacing;
    }

    /**
     * Get Main Processing Parameters ADSR from metadata.
     */
    void getMainProcParamADS() {

        mppAds = sourceProduct.getMetadataRoot().getElement("MAIN_PROCESSING_PARAMS_ADS");
        if (mppAds == null) {
            throw new OperatorException("MAIN_PROCESSING_PARAMS_ADS not found");
        }
    }

    /**
     * Get srgr_flag from metadata.
     */
    void getSRGRFlag() {

        MetadataAttribute srgrFlagAttr = mppAds.getAttribute("srgr_flag");
        if (srgrFlagAttr == null) {
            throw new OperatorException("srgr_flag not found");
        }
        srgrFlag = srgrFlagAttr.getData().getElemBoolean();
    }

    /**
     * Get slant range spacing from metadata.
     */
    void getSlantRangeSpacing() {
        
        MetadataAttribute rangeSpacingAttr = mppAds.getAttribute("range_spacing");
        if (rangeSpacingAttr == null) {
            throw new OperatorException("range_spacing not found");
        }

        slantRangeSpacing = (double)rangeSpacingAttr.getData().getElemFloat();
    }

    /**
     * Compute slant range distance from each selected range point to the 1st one.
     */
    void computeSlantRangeDistanceArray() {

        slantRangeDistanceArray = new double[numRangePoints - 1];
        int pixelsBetweenPoints = sourceImageWidth / numRangePoints;
        double slantDistanceBetweenPoints = slantRangeSpacing * pixelsBetweenPoints;
        for (int i = 0; i < numRangePoints - 1; i++) {
            slantRangeDistanceArray[i] = slantDistanceBetweenPoints * (i+1);
        }
    }

    /**
     * Get incidence angle tie point grid.
     */
    void getIncidenceAngle() {

        for (int i = 0; i < sourceProduct.getNumTiePointGrids(); i++) {
            TiePointGrid srcTPG = sourceProduct.getTiePointGridAt(i);
            if (srcTPG.getName().equals("incident_angle")) {
                incidenceAngle = srcTPG;
                break;
            }
        }
    }

    /**
     * Compute ground range spacing.
     */
    void computeGroundRangeSpacing() {

        // get near range incidence angle
        double alpha = incidenceAngle.getPixelFloat(sourceImageWidth - 0.5f, 0.5f) * MathUtils.DTOR;
        groundRangeSpacing = slantRangeSpacing / Math.sin(alpha);
    }

    /**
     * Create target product.
     */
    void createTargetProduct() {

        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    sourceProduct.getSceneRasterWidth(),
                                    sourceProduct.getSceneRasterHeight());

        for(Band band : sourceProduct.getBands()) {
            if (!band.isSynthetic()) {
                targetProduct.addBand(band.getName(), ProductData.TYPE_FLOAT32);
            }
        }

        targetProduct.setPreferredTileSize(sourceProduct.getSceneRasterWidth(), 256);

        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyFlagCodings(sourceProduct, targetProduct);

        // create NEST metadata
    }

    /**
     * Compute WARP polynomial coefficients.
     *
     * @param y0 The y coordinate of the upper left pixel in the current tile.
     */
    void computeWarpPolynomial(int y0) {

        computeGroundRangeDistanceArray(y0);
        Matrix A = createVandermondeMatrix(groundRangeDistanceArray);
        Matrix b = new Matrix(slantRangeDistanceArray, numRangePoints - 1);
        Matrix x = A.solve(b);
        warpPolynomialCoef = x.getColumnPackedCopy();

        for (double c : warpPolynomialCoef) {
            System.out.print(c + ", ");
        }
        System.out.println();
    }

    /**
     * Compute ground range distance from each selected range point to the 1st one.
     *
     * @param y0 The y coordinate of the upper left pixel in the current tile.
     */
    void computeGroundRangeDistanceArray(int y0) {

        double[] pointToPointDistance = new double[numRangePoints - 1];
        double[] xyz = new double[3];
        int pixelsBetweenPoints = sourceImageWidth / numRangePoints;

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
            pointToPointDistance[i] = (float)Math.sqrt(Math.pow(xP0 - xyz[0], 2) +
                                                       Math.pow(yP0 - xyz[1], 2) +
                                                       Math.pow(zP0 - xyz[2], 2));
            xP0 = xyz[0];
            yP0 = xyz[1];
            zP0 = xyz[2];
        }

        groundRangeDistanceArray = new double[numRangePoints - 1];
        groundRangeDistanceArray[0] = pointToPointDistance[0];
        for (int i = 1; i < numRangePoints - 1; i++) {
            groundRangeDistanceArray[i] = groundRangeDistanceArray[i-1] + pointToPointDistance[i];
        }
    }

    /**
     * Compute XYZ coordinates for a given pixel from its geo position.
     *
     * @param geoPos The geo position of a given pixel.
     * @param xyz The xyz coordinates of the given pixel.
     */
    void geo2xyz(GeoPos geoPos, double xyz[]) {

        double lat = (double)geoPos.lat * MathUtils.DTOR;
        double lon = (double)geoPos.lon * MathUtils.DTOR;
        double a = 6378137; // m
        double earthFlatCoef = 298.257223563;
        double e = 2 / earthFlatCoef - 1 / (earthFlatCoef * earthFlatCoef);
        double N = a / Math.sqrt(1 - Math.pow(e*Math.sin(lat), 2));

        xyz[0] = N * Math.cos(lat) * Math.cos(lon);
        xyz[1] = N * Math.cos(lat) * Math.sin(lon);
        xyz[2] = (1 - e * e) * N * Math.sin(lat);
    }

    /**
     * Get Vandermonde matrix constructed from a given distance array.
     *
     * @param d The given range distance array.
     */
    Matrix createVandermondeMatrix(double[] d) {

        int n = d.length;
        double[][] array = new double[n][warpPolynomialOrder + 1];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < warpPolynomialOrder + 1; j++) {
                array[i][j] = Math.pow(d[i], (double)j);
            }
        }

        return new Matrix(array);
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
            super(SRGROperator.class);
        }
    }
}