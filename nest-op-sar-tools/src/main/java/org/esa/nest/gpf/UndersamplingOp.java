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
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.dataio.ProductSubsetBuilder;
import org.esa.beam.framework.dataio.ProductSubsetDef;
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

import java.awt.*;
import java.io.IOException;
import java.io.File;
import java.util.ArrayList;

/**
 * Undersample
 */

@OperatorMetadata(alias="Undersample")
public class UndersamplingOp extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            sourceProductId="source", label="Source Bands")
    String[] sourceBandNames;

    @Parameter(valueSet = {SUB_SAMPLING, MULTI_LOOKING, KERNEL}, defaultValue = SUB_SAMPLING, label="Under-Sampling Methods")
    private String method;

    @Parameter(description = "The row dimension of the output image", defaultValue = "1000", label="Output Image Rows")
    private int numRows;
    @Parameter(description = "The col dimension of the output image", defaultValue = "1000", label="Output Image Columns")
    private int numCols;

    @Parameter(description = "The row ratio of the output/input images", defaultValue = "0.5", label="Row Ratio")
    private float rowRatio;
    @Parameter(description = "The col dimension of the output image", defaultValue = "0.5", label="Column Ratio")
    private float colRatio;

    @Parameter(defaultValue = "2", label=" Sub-Sampling in X")
    private int subSamplingX;
    @Parameter(defaultValue = "2", label=" Sub-Sampling in Y")
    private int subSamplingY;

    @Parameter(description = "The filter row size", defaultValue = "3", label="Filter Row Size")
    private int filterRowSize;
    @Parameter(description = "The filter col size", defaultValue = "3", label="Filter Col Size")
    private int filterColSize;

    @Parameter(description = "The kernel file", label="Kernel File")
    private File kernelFile;

    private ProductReader subsetReader;

    private static final String SUB_SAMPLING = "Sub-sampling";
    private static final String MULTI_LOOKING = "Multi-looking";
    private static final String KERNEL = "2D-kernel";

    
    @Override
    public void initialize() throws OperatorException {

        if (method.contains(SUB_SAMPLING)) {
            initializeForSubSampling();
        } else if (method.contains(MULTI_LOOKING)) {
            initializeForMultiLooking();
        } else if (method.contains(KERNEL)) {
            initializeForKernelFiltering();
        } else {
            throw new OperatorException("Unknown undersampling method: " + method);
        }
    }

    private void initializeForSubSampling() {

        if (sourceBandNames == null || sourceBandNames.length == 0) {
            Band[] bands = sourceProduct.getBands();
            ArrayList<String> bandNameList = new ArrayList<String>(sourceProduct.getNumBands());
            for (Band band : bands) {
                bandNameList.add(band.getName());
            }
            sourceBandNames = bandNameList.toArray(new String[bandNameList.size()]);
        }

        subsetReader = new ProductSubsetBuilder();
        ProductSubsetDef subsetDef = new ProductSubsetDef();

        subsetDef.addNodeNames(sourceProduct.getTiePointGridNames());
        subsetDef.addNodeNames(sourceBandNames);
        subsetDef.setSubSampling(subSamplingX, subSamplingY);
        subsetDef.setIgnoreMetadata(false);

        try {
            targetProduct = subsetReader.readProductNodes(sourceProduct, subsetDef);
        } catch (Throwable t) {
            throw new OperatorException(t);
        }
    }

    private void initializeForMultiLooking() {

    }

    private void initializeForKernelFiltering() {

        if (kernelFile == null) {
            throw new OperatorException("Please provide kernel file");
        }
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        if (method.contains(SUB_SAMPLING)) {
            computeTileUsingSubSampling(targetBand, targetTile, pm);
        } else if (method.contains(MULTI_LOOKING)) {
            computeTileUsingMultiLooking(targetBand, targetTile, pm);
        } else if (method.contains(KERNEL)) {
            computeTileUsingKernelFiltering(targetBand, targetTile, pm);
        } else {
            throw new OperatorException("Unknown undersampling method: " + method);
        }
    }

    private void computeTileUsingSubSampling(Band targetBand, Tile targetTile, ProgressMonitor pm) {

        ProductData destBuffer = targetTile.getRawSamples();
        Rectangle rectangle = targetTile.getRectangle();
        try {
            subsetReader.readBandRasterData(targetBand,
                                            rectangle.x,
                                            rectangle.y,
                                            rectangle.width,
                                            rectangle.height,
                                            destBuffer, pm);
            targetTile.setRawSamples(destBuffer);
        } catch (IOException e) {
            throw new OperatorException(e);
        }
    }

    private void computeTileUsingMultiLooking(Band targetBand, Tile targetTile, ProgressMonitor pm) {

    }

    private void computeTileUsingKernelFiltering(Band targetBand, Tile targetTile, ProgressMonitor pm) {

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
            super(UndersamplingOp.class);
        }
    }
}