/*
 * $Id: MergeBandsOp.java,v 1.6 2008-10-16 17:49:15 lveci Exp $
 *
 * Copyright (C) 2007 by Brockmann Consult (info@brockmann-consult.de)
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
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.IndexCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.operators.common.MergeOp;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.framework.gpf.annotations.SourceProducts;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.StringUtils;

import java.awt.image.RenderedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@OperatorMetadata(alias = "Merge-Bands",
        description = "Merges an arbitrary number of source bands into the target product.")
public class MergeBandsOp extends Operator {

    @SourceProducts
    private Product[] sourceProducts;
    @TargetProduct
    private Product targetProduct;

    @Parameter(defaultValue = "mergedProduct", description = "The name of the target product.")
    private String productName;

    @Parameter(defaultValue = "none")
    private String[] selectedBandNames;
    private transient Map<Band, Band> bandMap;

    @Override
    public void initialize() throws OperatorException {

        bandMap = new HashMap<Band, Band>(5);

        Product srcProduct = sourceProducts[0];
        final int sceneRasterWidth = srcProduct.getSceneRasterWidth();
        final int sceneRasterHeight = srcProduct.getSceneRasterHeight();

        for(Product prod : sourceProducts) {
            if(prod.getSceneRasterWidth() != sceneRasterWidth ||
               prod.getSceneRasterHeight() != sceneRasterHeight)
                throw new OperatorException("Source bands need to be coregistered");
        }

        targetProduct = new Product(productName, srcProduct.getProductType(),
                sceneRasterWidth, sceneRasterHeight);

        ProductUtils.copyMetadata(srcProduct, targetProduct);
        copyGeoCoding(srcProduct, targetProduct);

        if(selectedBandNames == null || selectedBandNames.length == 0) {
            for(Product srcProd : sourceProducts) {
                for(Band srcBand : srcProd.getBands()) {
                    copyBandWithFeatures(srcProd, targetProduct, srcBand.getName());
                }
            }
        } else {
          for(String name : selectedBandNames) {
                if(name.contains("::")) {
                    int index = name.indexOf("::");
                    String bandName = name.substring(0, index);
                    String productName = name.substring(index+2, name.length());
                    Product srcProd = findSourceProduct(productName);
                    if(srcProd != null)
                        copyBandWithFeatures(srcProd, targetProduct, bandName);
                } else {
                    copyBandWithFeatures(srcProduct, targetProduct, name);
                }
            }
        }

        targetProduct.setPreferredTileSize(sceneRasterWidth, 128);
    }

    private Product findSourceProduct(String name) {
        for(Product prod : sourceProducts) {
            if(prod.getName().equals(name))
                return prod;
        }
        return null;
    }

    /*
     * Copies the tie point data, geocoding and the start and stop time.
     */
    private static void copyGeoCoding(Product sourceProduct,
                                      Product destinationProduct) {
        // copy all tie point grids to output product
        ProductUtils.copyTiePointGrids(sourceProduct, destinationProduct);
        // copy geo-coding to the output product
        ProductUtils.copyGeoCoding(sourceProduct, destinationProduct);
        destinationProduct.setStartTime(sourceProduct.getStartTime());
        destinationProduct.setEndTime(sourceProduct.getEndTime());
    }

    private Band copyBandWithFeatures(Product srcProduct, Product outputProduct, String bandName) {

        String newBandName = renameDuplicateBand(outputProduct, bandName, 1);

        Band destBand = ProductUtils.copyBand(bandName, srcProduct, newBandName, outputProduct);
        Band srcBand = srcProduct.getBand(bandName);
        bandMap.put(destBand, srcBand);
        //destBand.setSourceImage(srcBand.getSourceImage());

        if (srcBand.getFlagCoding() != null) {
            FlagCoding srcFlagCoding = srcBand.getFlagCoding();
            if (!outputProduct.getFlagCodingGroup().contains(srcFlagCoding.getName())) {
                ProductUtils.copyFlagCoding(srcFlagCoding, outputProduct);
            }
            destBand.setSampleCoding(outputProduct.getFlagCodingGroup().get(srcFlagCoding.getName()));
        }
        if (srcBand.getIndexCoding() != null) {
            IndexCoding srcIndexCoding = srcBand.getIndexCoding();
            if (!outputProduct.getIndexCodingGroup().contains(srcIndexCoding.getName())) {
                ProductUtils.copyIndexCoding(srcIndexCoding, outputProduct);
            }
            destBand.setSampleCoding(outputProduct.getIndexCodingGroup().get(srcIndexCoding.getName()));
        }
        return destBand;
    }

    private String renameDuplicateBand(Product outputProduct, String bandName, int count) {
        if(outputProduct.getBand(bandName) != null) {        
            if(bandName.endsWith(""+count))
                bandName = bandName.substring(0, bandName.lastIndexOf(""+count));
            ++count;
            bandName += count;
            bandName = renameDuplicateBand(outputProduct, bandName, count);
        }
        return bandName;
    }

    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        targetTile.setRawSamples(getSourceTile(bandMap.get(band), targetTile.getRectangle(), pm).getRawSamples());
    }


    public static class Spi extends OperatorSpi {
        public Spi() {
            super(MergeBandsOp.class);
            setOperatorUI(MergeBandsOpUI.class);
        }
    }
}