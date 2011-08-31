/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.beam.gpf.operators.standard;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.ProductUtils;

import javax.media.jai.JAI;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.renderable.ParameterBlock;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

@OperatorMetadata(alias = "WriteRGB",
                  description = "Creates an RGB image from three source bands.",
                  internal = false)
public class WriteRGBOp extends Operator {

    @Parameter(description = "The zero-based index of the red band.", defaultValue = "0")
    private int red;
    @Parameter(description = "The zero-based index of the green band.", defaultValue = "1")
    private int green;
    @Parameter(description = "The zero-based index of the blue band.", defaultValue = "2")
    private int blue;
    @Parameter(defaultValue = "png")
    private String formatName;
    @Parameter(description = "The file to which the image is written.")
    private File file;

    @SourceProduct(alias = "input")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    private transient RasterDataNode[] rgbChannelNodes;
    private transient Map<Band, Band> bandMap;
    private transient Map<Band, ProductData> dataMap;

    private boolean processed = false;

    @Override
    public void initialize() throws OperatorException {
        bandMap = new HashMap<Band, Band>(3);
        dataMap = new HashMap<Band, ProductData>(3);
        rgbChannelNodes = new RasterDataNode[3];

        final int height = sourceProduct.getSceneRasterHeight();
        final int width = sourceProduct.getSceneRasterWidth();

        targetProduct = new Product("RGB", "RGB", width, height);
        prepareTargetBand(0, sourceProduct.getBandAt(red), "red", width, height);
        prepareTargetBand(1, sourceProduct.getBandAt(green), "green", width, height);
        prepareTargetBand(2, sourceProduct.getBandAt(blue), "blue", width, height);
    }

    /** get the selected bands
     * @param sourceProduct the input product
     * @param sourceBandNames the select band names
     * @return band list
     * @throws OperatorException if source band not found
     */
    private static Band[] getSourceBands(final Product sourceProduct, String[] sourceBandNames) throws OperatorException {

        if (sourceBandNames == null || sourceBandNames.length == 0) {
            final Band[] bands = sourceProduct.getBands();
            final ArrayList<String> bandNameList = new ArrayList<String>(sourceProduct.getNumBands());
            for (Band band : bands) {
                if(!(band instanceof VirtualBand))
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
        return sourceBands;
    }

    private void prepareTargetBand(int rgbIndex, Band sourceBand, String bandName, int width, int height) {
        Band targetBand = new Band(bandName, sourceBand.getDataType(), width, height);
        targetProduct.addBand(targetBand);
        bandMap.put(targetBand, sourceBand);

        ProductData data = targetBand.createCompatibleRasterData();
        dataMap.put(targetBand, data);

        targetBand.setRasterData(data);
        rgbChannelNodes[rgbIndex] = targetBand;
    }

    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle rectangle = targetTile.getRectangle();

        Band sourceBand = bandMap.get(band);
        Tile sourceTile = getSourceTile(sourceBand, rectangle);

        ProductData rgbData = dataMap.get(band);
        System.arraycopy(sourceTile.getRawSamples().getElems(), 0, rgbData.getElems(), rectangle.x + rectangle.y * rectangle.width, rectangle.width * rectangle.height);
        processed = true;
    }

    @Override
    public void dispose() {
        try {
            if(processed)
                writeImage();
        } catch (IOException e) {
            e.printStackTrace();
        }
        super.dispose();
    }

    private void writeImage() throws IOException {
        ImageInfo imageInfo = ProductUtils.createImageInfo(rgbChannelNodes, true, ProgressMonitor.NULL);
        BufferedImage outputImage = ProductUtils.createRgbImage(rgbChannelNodes, imageInfo, ProgressMonitor.NULL);
        ParameterBlock storeParams = new ParameterBlock();
        storeParams.addSource(outputImage);
        storeParams.add(file.getAbsolutePath());
        storeParams.add(formatName);
        JAI.create("filestore", storeParams);
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(WriteRGBOp.class);
        }
    }
}
