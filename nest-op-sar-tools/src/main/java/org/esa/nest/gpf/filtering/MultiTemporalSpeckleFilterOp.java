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

import java.awt.*;
import java.util.ArrayList;

/**
 * Applies Multitemporal Speckle Filtering to multitemporal images.
 *
 * For a sequence of n registered multitemporal PRI images, with intensity at position (x, y) in image k
 * denoted by Ik(x, y), the goal of temporal filtering is to combine them linearly such that the n output
 * images Jk(x, y) meeting the following two conditions:
 *
 * 1. Jk is unbiased (i.e. E[Jk] = E[Ik], where E[] denotes expected value, so that the filtering does not
 *    distort the ?0 values).
 *
 * 2. Jk has minimum variance, so that speckle is minimized. 
 *
 * The following equation has been implemented:
 *
 *    Jk(x, y) = E[Ik]*(I1(x, y)/E[I1] + ... + In(x, y)/E[In])/n
 *
 * Since the n output images differ by a factor, only one image is output, i.e.
 *
 *    J(x, y) = (E[I1] + ... + E[In])/n * (I1(x, y)/E[I1] + ... + In(x, y)/E[In])/n
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

    private boolean bandMeanComputed = false;
    private double avgSigmma0 = 0.0;
    private double[] bandMeanValues = null;
    private double[] bandNoDataValues = null;

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

        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    sourceProduct.getSceneRasterWidth(),
                                    sourceProduct.getSceneRasterHeight());

        OperatorUtils.copyProductNodes(sourceProduct, targetProduct);

        addSelectedBands();

        // The tile width has to be the image width, otherwise the index calculation in the last tile is noe correct.
        targetProduct.setPreferredTileSize(targetProduct.getSceneRasterWidth(), 50);

        bandMeanValues = new double[sourceBandNames.length];
        bandNoDataValues = new double[sourceBandNames.length];
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

        final Band targetBand = new Band("filtered_band",
                                         ProductData.TYPE_FLOAT32,
                                         sourceProduct.getSceneRasterWidth(),
                                         sourceProduct.getSceneRasterHeight());

        targetBand.setUnit(sourceProduct.getBand(sourceBandNames[0]).getUnit());
        targetProduct.addBand(targetBand);
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

        final int numSourceBands = sourceBandNames.length;
        final ProductData srcData[] = new ProductData[numSourceBands];

        for (int i = 0; i < numSourceBands; i++) {
            final String srcBandName = sourceBandNames[i];
            final Band srcBand = sourceProduct.getBand(srcBandName);
            final Tile srcTile = getSourceTile(srcBand, targetTileRectangle, pm);
            srcData[i] = srcTile.getDataBuffer();

            if (!bandMeanComputed) {
                bandMeanValues[i] = srcBand.getStx().getMean();
                bandNoDataValues[i] = srcBand.getNoDataValue();
                avgSigmma0 += bandMeanValues[i] / numSourceBands;
            }
        }

        if (!bandMeanComputed) {
            bandMeanComputed = true;
        }

        final ProductData trgData = targetTile.getDataBuffer();
        double srcDataValue = 0.0;
        double tgtDataValue = 0.0;
        for(int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                final int index = targetTile.getDataBufferIndex(x, y);
                tgtDataValue = 0.0;
                int n = 0;
                for (int i = 0; i < numSourceBands; i++) {
                    srcDataValue = srcData[i].getElemDoubleAt(index);
                    if (srcDataValue != bandNoDataValues[i]) {
                        tgtDataValue += srcDataValue / bandMeanValues[i];
                        n++;
                    }
                }
                tgtDataValue *= avgSigmma0 / n;
                trgData.setElemDoubleAt(index, tgtDataValue);
            }
        }
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

