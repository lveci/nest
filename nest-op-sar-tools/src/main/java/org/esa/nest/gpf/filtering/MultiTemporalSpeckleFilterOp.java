/*
 * Copyright (C) 2002-2007 by Array System Computing Inc.
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
package org.esa.nest.gpf.filtering;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.nest.gpf.OperatorUtils;
import org.esa.nest.datamodel.Unit;

import java.awt.*;
import java.util.ArrayList;
import java.util.Map;

/**
 * Applies Multitemporal Speckle Filtering to multitemporal images.
 *
 * For a sequence of n registered multitemporal PRI images, with intensity at position (x, y) in image k
 * denoted by Ik(x, y), the goal of temporal filtering is to combine them linearly such that the n output
 * images Jk(x, y) meeting the following two conditions:
 *
 * 1. Jk is unbiased (i.e. E[Jk] = E[Ik], where E[] denotes expected value, so that the filtering does not
 *    distort the sigma0 values).
 *
 * 2. Jk has minimum variance, so that speckle is minimized. 
 *
 * The following equation has been implemented:
 *
 *    Jk(x, y) = E[Ik]*(I1(x, y)/E[I1] + ... + In(x, y)/E[In])/n
 *
 * where E[I] is the local mean value of pixels in a user selected window centered at (x, y) in image I.
 * The window size can be 3x3, 5x5, 7x7, 9x9 or 11x11.
 * 
 * The operator has the following two preprocessing steps:
 *
 * 1. The first step is calibration in which ?0 is derived from the digital number at each pixel. This
 *    ensures that values of from different times and in different parts of the image are comparable.
 *
 * 2. The second is registration of the images in the multitemporal sequence.
 *
 * Here it is assumed that preprocessing has been performed before applying this operator. The input to
 * the operator is assumed to be a product with multiple calibrated and co-registrated bands.
 *
 * Reference:
 * [1] S. Quegan, T. L. Toan, J. J. Yu, F. Ribbes and N. Floury, “Multitemporal ERS SAR Analysis Applied to
 * Forest Mapping”, IEEE Transactions on Geoscience and Remote Sensing, vol. 38, no. 2, March 2000.
 */

@OperatorMetadata(alias="Multi-Temporal-Speckle-Filter",
                  category = "SAR Tools",
                  description = "Speckle Reduction using Multitemporal Filtering")
public class MultiTemporalSpeckleFilterOp extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct = null;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band", 
            sourceProductId="source", label="Source Bands")
    String[] sourceBandNames;

    @Parameter(valueSet = {WINDOW_SIZE_3x3, WINDOW_SIZE_5x5, WINDOW_SIZE_7x7, WINDOW_SIZE_9x9, WINDOW_SIZE_11x11},
               defaultValue = WINDOW_SIZE_3x3, label="Window Size")
    private String windowSize = WINDOW_SIZE_3x3;

    private int halfWindowWidth = 0;
    private int halfWindowHeight = 0;
    private int sourceImageWidth = 0;
    private int sourceImageHeight = 0;
    private double[] bandNoDataValues = null;

    public static final String WINDOW_SIZE_3x3 = "3x3";
    public static final String WINDOW_SIZE_5x5 = "5x5";
    public static final String WINDOW_SIZE_7x7 = "7x7";
    public static final String WINDOW_SIZE_9x9 = "9x9";
    public static final String WINDOW_SIZE_11x11 = "11x11";

    /**
     * Default constructor. The graph processing framework
     * requires that an operator has a default constructor.
     */
    public MultiTemporalSpeckleFilterOp() {
    }

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

        sourceImageWidth = sourceProduct.getSceneRasterWidth();
        sourceImageHeight = sourceProduct.getSceneRasterHeight();

        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    sourceProduct.getSceneRasterWidth(),
                                    sourceProduct.getSceneRasterHeight());

        OperatorUtils.copyProductNodes(sourceProduct, targetProduct);

        addSelectedBands();

        // The tile width has to be the image width, otherwise the index calculation in the last tile is not correct.
        targetProduct.setPreferredTileSize(targetProduct.getSceneRasterWidth(), 50);

        int windowWidth = 0;
        int windowHeight = 0;
        if (windowSize.equals(WINDOW_SIZE_3x3)) {
            windowWidth = 3;
            windowHeight = 3;
        } else if (windowSize.equals(WINDOW_SIZE_5x5)) {
            windowWidth = 5;
            windowHeight = 5;
        } else if (windowSize.equals(WINDOW_SIZE_7x7)) {
            windowWidth = 7;
            windowHeight = 7;
        } else if (windowSize.equals(WINDOW_SIZE_9x9)) {
            windowWidth = 9;
            windowHeight = 9;
        } else if (windowSize.equals(WINDOW_SIZE_11x11)) {
            windowWidth = 11;
            windowHeight = 11;
        } else {
            throw new OperatorException("Unknown filter size: " + windowSize);
        }

        halfWindowWidth = windowWidth/2;
        halfWindowHeight = windowHeight/2;
    }

    /**
     * Add user selected bands to target product.
     */
    private void addSelectedBands() {

        if (sourceBandNames == null || sourceBandNames.length == 0) {
            final Band[] bands = sourceProduct.getBands();
            final ArrayList<String> bandNameList = new ArrayList<String>(sourceProduct.getNumBands());
            for (Band band : bands) {
                bandNameList.add(band.getName());
            }
            sourceBandNames = bandNameList.toArray(new String[bandNameList.size()]);
        }

        if (sourceBandNames.length <= 1) {
            throw new OperatorException("Multitemporal filtering cannot be applied with one source band. Select more bands.");
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

            if (unit.contains(Unit.PHASE) || unit.contains(Unit.IMAGINARY) || unit.contains(Unit.REAL)) {
                throw new OperatorException("Please select amplitude or intensity bands.");
            } else {
                final Band targetBand = new Band(srcBand.getName(),
                                                 ProductData.TYPE_FLOAT32,
                                                 sourceProduct.getSceneRasterWidth(),
                                                 sourceProduct.getSceneRasterHeight());

                targetBand.setUnit(unit);
                targetProduct.addBand(targetBand);
            }
        }

        bandNoDataValues = new double[targetProduct.getNumBands()];
    }

    /**
     * Called by the framework in order to compute the stack of tiles for the given target bands.
     * <p>The default implementation throws a runtime exception with the message "not implemented".</p>
     *
     * @param targetTiles     The current tiles to be computed for each target band.
     * @param targetRectangle The area in pixel coordinates to be computed (same for all rasters in <code>targetRasters</code>).
     * @param pm              A progress monitor which should be used to determine computation cancelation requests.
     * @throws OperatorException if an error occurs during computation of the target rasters.
     */
    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws OperatorException {

        final int x0 = targetRectangle.x;
        final int y0 = targetRectangle.y;
        final int w  = targetRectangle.width;
        final int h  = targetRectangle.height;
        //System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);
        
        final Band[] targetBands = targetProduct.getBands();
        final int numBands = targetBands.length;
        final ProductData[] targetData = new ProductData[numBands];
        for (int i = 0; i < numBands; i++) {
            Tile targetTile = targetTiles.get(targetBands[i]);
            targetData[i] = targetTile.getDataBuffer();
        }

        Rectangle sourceRectangle = getSourceRectangle(x0, y0, w, h);

        final Tile[] sourceTile = new Tile[numBands];
        final ProductData[] sourceData = new ProductData[numBands];
        for (int i = 0; i < numBands; i++) {
            final Band srcBand = sourceProduct.getBand(targetBands[i].getName());
            sourceTile[i] = getSourceTile(srcBand, sourceRectangle, pm);
            sourceData[i] = sourceTile[i].getDataBuffer();
            bandNoDataValues[i] = srcBand.getNoDataValue();
        }

        double[] localMeans = new double[numBands];
        double srcDataValue = 0.0;
        for(int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {

                final int sourceIndex = sourceTile[0].getDataBufferIndex(x, y);

                double sum = 0.0;
                int n = 0;
                for (int i = 0; i < numBands; i++) {
                    srcDataValue = sourceData[i].getElemDoubleAt(sourceIndex);
                    if (srcDataValue == bandNoDataValues[i]) {
                        localMeans[i] = bandNoDataValues[i];
                        continue;
                    }

                    localMeans[i] = computeLocalMean(x, y, sourceTile[i], sourceData[i], bandNoDataValues[i]);

                    if (localMeans[i] != 0.0) {
                        sum += sourceData[i].getElemDoubleAt(sourceIndex) / localMeans[i];
                    }
                    n++;
                }
                if (n > 0) {
                    sum /= n;
                }

                final int targetIndex = targetTiles.get(targetBands[0]).getDataBufferIndex(x, y);
                for (int i = 0; i < numBands; i++) {
                    if (localMeans[i] != bandNoDataValues[i]) {
                        targetData[i].setElemDoubleAt(targetIndex, sum * localMeans[i]);
                    } else {
                        targetData[i].setElemDoubleAt(targetIndex, bandNoDataValues[i]);
                    }
                }
            }
        }
    }

    /**
     * Get source tile rectangle.
     * @param tx0 X coordinate for the upper left corner pixel in the target tile.
     * @param ty0 Y coordinate for the upper left corner pixel in the target tile.
     * @param tw The target tile width.
     * @param th The target tile height.
     * @return The source tile rectangle.
     */
    private Rectangle getSourceRectangle(final int tx0, final int ty0, final int tw, final int th) {
        final int x0 = Math.max(0, tx0 - halfWindowWidth);
        final int y0 = Math.max(0, ty0 - halfWindowHeight);
        final int xMax = Math.min(tx0 + tw - 1 + halfWindowWidth, sourceImageWidth);
        final int yMax = Math.min(ty0 + th - 1 + halfWindowHeight, sourceImageHeight);
        final int w = xMax - x0 + 1;
        final int h = yMax - y0 + 1;
        return new Rectangle(x0, y0, w, h);
    }

    /**
     * Compute mean value for pixels in a window with given center.
     * @param xc X coordinate of the center pixel.
     * @param yc Y coordinate of the center pixel.
     * @param srcTile Source tile.
     * @param srcData Source data.
     * @param noDataValue The noDataValue for source band.
     * @return The mean value.
     */
    private double computeLocalMean(int xc, int yc, Tile srcTile, ProductData srcData, double noDataValue) {
        final int x0 = Math.max(0, xc - halfWindowWidth);
        final int y0 = Math.max(0, yc - halfWindowHeight);
        final int xMax = Math.min(xc + halfWindowWidth, sourceImageWidth-1);
        final int yMax = Math.min(yc + halfWindowHeight, sourceImageHeight-1);

        double mean = 0.0;
        double value = 0.0;
        int n = 0;
        for (int y = y0; y < yMax; y++) {
            for (int x = x0; x < xMax; x++) {
                final int index = srcTile.getDataBufferIndex(x, y);
                value = srcData.getElemDoubleAt(index);
                if (value != noDataValue) {
                    mean += value;
                    n++;
                }
            }
        }
        return mean/n;
    }


    /**
     * The SPI is used to register this operator in the graph processing framework
     * via the SPI configuration file
     * {@code META-INF/services/org.esa.beam.framework.gpf.OperatorSpi}.
     * This class may also serve as a factory for new operator instances.
     * @see OperatorSpi#createOperator()
     * @see OperatorSpi#createOperator(java.util.Map, java.util.Map)
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(MultiTemporalSpeckleFilterOp.class);
        }
    }
}

