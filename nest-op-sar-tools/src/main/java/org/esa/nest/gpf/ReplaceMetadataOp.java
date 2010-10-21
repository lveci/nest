/*
 * Copyright (C) 2010 Array Systems Computing Inc. http://www.array.ca
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
package org.esa.nest.gpf;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProducts;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.ProductUtils;
import org.esa.nest.datamodel.AbstractMetadata;

/**
 * Replaces the Metadata with that of another product
 */
@OperatorMetadata(alias="ReplaceMetadataOp", category = "Utilities",
                  description = "Replace the metadata of the first product with that of the second")
public class ReplaceMetadataOp extends Operator {

    @SourceProducts
    private Product[] sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(defaultValue="Replace the metadata of the first product with that of the second", label=" ")
    String note;

    /**
	     * Default constructor. The graph processing framework
	     * requires that an operator has a default constructor.
	 */
    public ReplaceMetadataOp() {
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

        try {
            if(sourceProduct.length != 2) {
                throw new OperatorException("ReplaceMetadataOp requires two source products.");
            }
            final Product masterProduct = sourceProduct[0];
            final Product slaveProduct = sourceProduct[1];

            // create target product
            targetProduct = new Product(masterProduct.getName(),
                                        slaveProduct.getProductType(),
                                        masterProduct.getSceneRasterWidth(),
                                        masterProduct.getSceneRasterHeight());

            // Add target bands
            final Band[] bands = masterProduct.getBands();
            for (Band srcBand : bands) {
    
                final Band targetBand = ProductUtils.copyBand(srcBand.getName(), masterProduct, targetProduct);
                targetBand.setSourceImage(srcBand.getSourceImage());
            }

            // copy or create product nodes for metadata, tiepoint grids, geocoding, start/end times, etc.
            ProductUtils.copyMetadata(slaveProduct, targetProduct);
            ProductUtils.copyTiePointGrids(slaveProduct, targetProduct);
            ProductUtils.copyFlagCodings(slaveProduct, targetProduct);
            ProductUtils.copyGeoCoding(slaveProduct, targetProduct);
            targetProduct.setStartTime(slaveProduct.getStartTime());
            targetProduct.setEndTime(slaveProduct.getEndTime());
            targetProduct.setDescription(slaveProduct.getDescription());

            final MetadataElement absRootMst = AbstractMetadata.getAbstractedMetadata(masterProduct);
            final int isPolsarPro = absRootMst.getAttributeInt(AbstractMetadata.polsarProData, 0);
            if(isPolsarPro > 0) {
                resetPolarizations(AbstractMetadata.getAbstractedMetadata(targetProduct));
            }

        } catch(Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    private static void resetPolarizations(final MetadataElement absRoot) {
        absRoot.setAttributeString(AbstractMetadata.mds1_tx_rx_polar, " ");
        absRoot.setAttributeString(AbstractMetadata.mds2_tx_rx_polar, " ");
        absRoot.setAttributeString(AbstractMetadata.mds3_tx_rx_polar, " ");
        absRoot.setAttributeString(AbstractMetadata.mds4_tx_rx_polar, " ");
        absRoot.setAttributeInt(AbstractMetadata.polsarProData, 1);
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
            super(ReplaceMetadataOp.class);
        }
    }
}