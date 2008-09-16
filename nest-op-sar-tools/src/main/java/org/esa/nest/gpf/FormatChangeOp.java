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
import org.esa.beam.util.ProductUtils;

import javax.media.jai.JAI;

/**
 * Format-Change
 */

@OperatorMetadata(alias="Convert-Datatype", description="Convert product data type")
public class FormatChangeOp extends Operator {

    @SourceProduct
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(valueSet = { ProductData.TYPESTRING_INT8,
                            ProductData.TYPESTRING_INT16,
                            ProductData.TYPESTRING_INT32,
                            ProductData.TYPESTRING_UINT8,
                            ProductData.TYPESTRING_UINT16,
                            ProductData.TYPESTRING_UINT32,
                            ProductData.TYPESTRING_FLOAT32,
                            ProductData.TYPESTRING_FLOAT64
            }, defaultValue = ProductData.TYPESTRING_FLOAT32
            , label="Target Data Type")
    private String targetDataType;
    private int dataType;

    @Parameter(valueSet = { "None"//,
                            //"Linear (slope and intercept)",
                            //"Linear (between min max)",
                            //"Logarithmic"
            }, defaultValue = "None", label="Scaling")
    private String scaling;

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

        targetProduct.setPreferredTileSize(JAI.getDefaultTileSize());

        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyFlagCodings(sourceProduct, targetProduct);

        dataType = ProductData.getType(targetDataType);
        for(Band band : sourceProduct.getBands()) {
            targetProduct.addBand(band.getName(), dataType);
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

        Band sourceBand = sourceProduct.getBand(targetBand.getName());
        Tile srcTile = getSourceTile(sourceBand, targetTile.getRectangle(), pm);

        try {
            ProductData srcData = srcTile.getRawSamples();
            ProductData dstData = targetTile.getRawSamples();

            int numElem = dstData.getNumElems();
            if(dataType == ProductData.TYPE_FLOAT32) {
                for(int i=0; i < numElem; ++i) {
                    dstData.setElemFloatAt(i, srcData.getElemFloatAt(i));
                }
            }
            else if(dataType == ProductData.TYPE_FLOAT64) {
                for(int i=0; i < numElem; ++i) {
                    dstData.setElemDoubleAt(i, srcData.getElemDoubleAt(i));
                }
            }
            else if(dataType == ProductData.TYPE_INT8 || dataType == ProductData.TYPE_INT16 ||
                    dataType == ProductData.TYPE_INT32) {
                for(int i=0; i < numElem; ++i) {
                    dstData.setElemIntAt(i, srcData.getElemIntAt(i));
                }
            }
            else if(dataType == ProductData.TYPE_UINT8 || dataType == ProductData.TYPE_UINT16 ||
                    dataType == ProductData.TYPE_UINT32) {
                for(int i=0; i < numElem; ++i) {
                    dstData.setElemUIntAt(i, srcData.getElemUIntAt(i));
                }
            }

            targetTile.setRawSamples(dstData);
        } catch(Exception e) {
            System.out.println(e.toString());
        }
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
            super(FormatChangeOp.class);
        }
    }
}