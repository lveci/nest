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
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.nest.util.MathUtils;

import java.awt.*;
import java.util.ArrayList;

/**
 * Fill hole pixels in source product with linear interpolations in both x and y directions.
 */

@OperatorMetadata(alias="Fill-Hole", category = "SAR Tools", description="Fill holes in given product")
public final class FillHoleOp extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            sourceProductId="source", label="Source Bands")
    String[] sourceBandNames;

    @Parameter(label="No Data Value", defaultValue = "0.0")
    private double NoDataValue = 0.0;

    private int sourceImageWidth;
    private int sourceImageHeight;

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
            getSourceImageDimension();

            createTargetProduct();

        } catch(Exception e) {
            throw new OperatorException(e);
        }
    }

    /**
     * Get source image dimension.
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
                                    sourceImageWidth,
                                    sourceImageHeight);

        addSelectedBands();

        OperatorUtils.copyProductNodes(sourceProduct, targetProduct);
    }

    /**
     * Add user select bands to the target product.
     * @throws OperatorException The exception.
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

        for (String sourceBandName : sourceBandNames) {
            final Band sourceBand = sourceProduct.getBand(sourceBandName);
            if (sourceBand == null) {
                throw new OperatorException("Source band not found: " + sourceBandName);
            }

            final Band targetBand = new Band(sourceBand.getName(),
                                             ProductData.TYPE_FLOAT32,
                                             sourceImageWidth,
                                             sourceImageHeight);

            targetBand.setUnit(sourceBand.getUnit());
            targetProduct.addBand(targetBand);
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

        final Rectangle targetTileRectangle = targetTile.getRectangle();
        final int x0 = targetTileRectangle.x;
        final int y0 = targetTileRectangle.y;
        final int w  = targetTileRectangle.width;
        final int h  = targetTileRectangle.height;
        //System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

        Band sourceBand = sourceProduct.getBand(targetBand.getName());
        Tile sourceTile = getSourceTile(sourceBand, targetTileRectangle, pm);
        final ProductData srcData = sourceTile.getDataBuffer();
        final ProductData trgData = targetTile.getDataBuffer();

        final int maxY = y0 + h;
        final int maxX = x0 + w;
        double v;
        for (int y = y0; y < maxY; ++y) {
            for (int x = x0; x < maxX; ++x) {
                v = srcData.getElemDoubleAt(sourceTile.getDataBufferIndex(x, y));
                if (v == NoDataValue) {
                    v = getPixelValueByInterpolation(x, y, srcData, sourceTile);
                }
                trgData.setElemDoubleAt(targetTile.getDataBufferIndex(x, y), v);
            }
        }
    }

    /**
     * Compute pixel value using linear interpolations in both x and y direction.
     * @param x The X coordinate of the given pixel.
     * @param y The Y coordinate of the given pixel.
     * @param srcData The source data.
     * @param srcTile The source tile.
     * @return The interpolated pixel value.
     */
    private double getPixelValueByInterpolation(
            final int x, final int y, final ProductData srcData, final Tile srcTile) {

        PixelPos pixelUp = new PixelPos(x, y);
        PixelPos pixelDown = new PixelPos(x, y);
        PixelPos pixelLeft = new PixelPos(x, y);
        PixelPos pixelRight = new PixelPos(x, y);

        double vUp = getNearestNonHolePixelPosition(x, y, srcData, srcTile, pixelUp, "up");
        double vDown = getNearestNonHolePixelPosition(x, y, srcData, srcTile, pixelDown, "down");
        double vLeft = getNearestNonHolePixelPosition(x, y, srcData, srcTile, pixelLeft, "left");
        double vRight = getNearestNonHolePixelPosition(x, y, srcData, srcTile, pixelRight, "right");

        double v1 = NoDataValue;
        if (vUp != NoDataValue && vDown != NoDataValue) {
            double mu = (y - pixelUp.y) / (pixelDown.y - pixelUp.y);
            v1 = MathUtils.interpolationLinear(vUp, vDown, mu);
        }

        double v2 = NoDataValue;
        if (vLeft != NoDataValue && vRight != NoDataValue) {
            double mu = (x - pixelLeft.x) / (pixelRight.x - pixelLeft.x);
            v2 = MathUtils.interpolationLinear(vLeft, vRight, mu);
        }

        if (v1 != NoDataValue && v2 != NoDataValue) {
            return(v1 + v2)/2.0;
        } else if (v1 != NoDataValue) {
            return v1;
        } else if (v2 != NoDataValue) {
            return v2;
        } else {
            return NoDataValue;
        }
    }

    /**
     * Get the position and value for the nearest non-hole pixel in a given direction.
     * @param x The X coordinate of the given pixel.
     * @param y The Y coordinate of the given pixel.
     * @param srcData The source data.
     * @param srcTile The source tile.
     * @param pixel The pixel position.
     * @param direction The direction string which can be "up", "down", "left" and "right".
     * @return The pixel value.
     */
    private double getNearestNonHolePixelPosition(
            final int x, final int y, final ProductData srcData, final Tile srcTile,
            final PixelPos pixel, final String direction) {

        final Rectangle srcTileRectangle = srcTile.getRectangle();
        final int x0 = srcTileRectangle.x;
        final int y0 = srcTileRectangle.y;
        final int w  = srcTileRectangle.width;
        final int h  = srcTileRectangle.height;

        double v = 0.0;
        if (direction.contains("up")) {

            for (int yy = y; yy >= y0; yy--) {
                v = srcData.getElemDoubleAt(srcTile.getDataBufferIndex(x, yy));
                if (v != NoDataValue) {
                    pixel.y = yy;
                    return v;
                }
            }

        } else if (direction.contains("down")) {

            for (int yy = y; yy < y0 + h; yy++) {
                v = srcData.getElemDoubleAt(srcTile.getDataBufferIndex(x, yy));
                if (v != NoDataValue) {
                    pixel.y = yy;
                    return v;
                }
            }

        } else if (direction.contains("left")) {

            for (int xx = x; xx >= x0; xx--) {
                v = srcData.getElemDoubleAt(srcTile.getDataBufferIndex(xx, y));
                if (v != NoDataValue) {
                    pixel.x = xx;
                    return v;
                }
            }

        } else if (direction.contains("right")) {

            for (int xx = x; xx < x0 + w; xx++) {
                v = srcData.getElemDoubleAt(srcTile.getDataBufferIndex(xx, y));
                if (v != NoDataValue) {
                    pixel.x = xx;
                    return v;
                }
            }

        } else {
            throw new OperatorException("Invalid direction");
        }

        return NoDataValue;
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
            super(FillHoleOp.class);
        }
    }
}