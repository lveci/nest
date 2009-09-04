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
import org.esa.beam.util.ProductUtils;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.gpf.OperatorUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Applies Multitemporal Speckle Filtering to multitemporal images.
 * The input to the operator is assumed to be one product with multiple calibrated and co-registrated bands.
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
                avgSigmma0 += bandMeanValues[i] / numSourceBands;
            }
        }

        if (!bandMeanComputed) {
            bandMeanComputed = true;
        }

        final ProductData trgData = targetTile.getDataBuffer();
        for(int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                final int index = targetTile.getDataBufferIndex(x, y);
                double v = 0.0;
                for (int i = 0; i < numSourceBands; i++) {
                    v += srcData[i].getElemDoubleAt(index) / bandMeanValues[i];
                }
                v *= avgSigmma0 / numSourceBands;
                trgData.setElemDoubleAt(index, v);
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

