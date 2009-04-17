
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
import org.esa.beam.framework.dataio.ProductSubsetDef;
import org.esa.beam.framework.dataop.maptransf.MapInfo;
import org.esa.beam.framework.dataop.maptransf.MapProjectionRegistry;
import org.esa.beam.framework.dataop.maptransf.IdentityTransformDescriptor;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.math.MathUtils;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.gpf.OperatorUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.IOException;

/**

 */

@OperatorMetadata(alias="MapProjection", description="Applies a map projection")
public final class MapProjectionOp extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;
    private Product projectedProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            sourceProductId="source", label="Source Bands")
    String[] sourceBandNames;


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
            projectedProduct = createSubsampledProduct(sourceProduct, sourceBandNames);

            targetProduct = new Product(sourceProduct.getName(),
                                        sourceProduct.getProductType(),
                                        projectedProduct.getSceneRasterWidth(),
                                        projectedProduct.getSceneRasterHeight());
            ProductUtils.copyMetadata(sourceProduct, targetProduct);
            targetProduct.setStartTime(sourceProduct.getStartTime());
            targetProduct.setEndTime(sourceProduct.getEndTime());

            for (Band sourceBand : projectedProduct.getBands()) {
                final Band targetBand = new Band(sourceBand.getName(),
                                                sourceBand.getDataType(),
                                                sourceBand.getRasterWidth(),
                                                sourceBand.getRasterHeight());
                ProductUtils.copyRasterDataNodeProperties(sourceBand, targetBand);
                targetProduct.addBand(targetBand);
            }
            
        } catch(Exception e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    private static boolean isMapProjected(Product product) {
        if(product.getGeoCoding() instanceof MapGeoCoding)
            return true;
        final MetadataElement absRoot = product.getMetadataRoot().getElement("Abstracted Metadata");
        return absRoot != null && absRoot.getAttributeInt("isMapProjected", 0) == 1;
    }

    private static Product createSubsampledProduct(final Product product, String[] selectedBands) throws IOException {

        final String quicklookBandName = ProductUtils.findSuitableQuicklookBandName(product);
        final ProductSubsetDef productSubsetDef = new ProductSubsetDef("subset");
        productSubsetDef.setVirtualBandsAsRealBands(true);
        productSubsetDef.setNodeNames(selectedBands);
        Product productSubset = product.createSubset(productSubsetDef, product.getName(), null);

        if(!isMapProjected(product)) {
            final MapInfo mapInfo = ProductUtils.createSuitableMapInfo(productSubset,
                                                MapProjectionRegistry.getProjection(IdentityTransformDescriptor.NAME),
                                                0.0,
                                                product.getBand(quicklookBandName).getNoDataValue());
            productSubset = productSubset.createProjectedProduct(mapInfo, quicklookBandName, null);
        }

        return productSubset;
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

        final Tile sourceTile = getSourceTile(projectedProduct.getBand(targetBand.getName()), targetTile.getRectangle(), pm);
        targetTile.setRawSamples(sourceTile.getRawSamples());
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
            super(MapProjectionOp.class);
            //super.setOperatorUI(MapProjectionOpUI.class);
        }
    }
}