
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

import java.awt.*;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/**
 */
@OperatorMetadata(alias="TileStackOp")
public class TileStackOp extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            sourceProductId="source", label="Source Bands")
    String[] sourceBandNames;

    private final HashMap<Band, Band> targetBandToSourceBandMap = new HashMap<Band, Band>();

    /**
	     * Default constructor. The graph processing framework
	     * requires that an operator has a default constructor.
	 */
    public TileStackOp() {
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

            getSourceMetadata();

            // create target product
            targetProduct = new Product(sourceProduct.getName(),
                                        sourceProduct.getProductType(),
                                        sourceProduct.getSceneRasterWidth(),
                                        sourceProduct.getSceneRasterHeight());

            // Add target bands
            addSelectedBands();

            // copy or create product nodes for metadata, tiepoint grids, geocoding, start/end times, etc.
            ProductUtils.copyMetadata(sourceProduct, targetProduct);
            ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
            ProductUtils.copyFlagCodings(sourceProduct, targetProduct);
            ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
            targetProduct.setStartTime(sourceProduct.getStartTime());
            targetProduct.setEndTime(sourceProduct.getEndTime());
            targetProduct.setDescription(sourceProduct.getDescription());

            // update the metadata with the affect of the processing
            updateTargetProductMetadata();
    }

    /**
     * Compute mean pixel spacing (in m).
     */
    private void getSourceMetadata() {

    }

    /**
     * Update metadata in the target product.
     */
    private void updateTargetProductMetadata() {

    }

    /**
     * Add the user selected bands to target product.
     * @throws OperatorException The exceptions.
     */
    private void addSelectedBands() throws OperatorException {

        // if user did not select any band then add all
        if (sourceBandNames == null || sourceBandNames.length == 0) {
            final Band[] bands = sourceProduct.getBands();
            final ArrayList<String> bandNameList = new ArrayList<String>(sourceProduct.getNumBands());
            for (Band band : bands) {
                    bandNameList.add(band.getName());
            }
            sourceBandNames = bandNameList.toArray(new String[bandNameList.size()]);
        }

        for (String srcBandName : sourceBandNames) {
            final Band srcBand = sourceProduct.getBand(srcBandName);

            final Band targetBand = ProductUtils.copyBand(srcBand.getName(), sourceProduct, targetProduct);

            targetBandToSourceBandMap.put(targetBand, srcBand);
        }
    }

	/**
     * Called by the framework in order to compute the stack of tiles for the given target bands.
     * <p>The default implementation throws a runtime exception with the message "not implemented".</p>
     *
     * @param targetTiles     The current tiles to be computed for each target band.
     * @param targetRectangle The area in pixel coordinates to be computed (same for all rasters in <code>targetRasters</code>).
     * @param pm              A progress monitor which should be used to determine computation cancelation requests.
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          if an error occurs during computation of the target rasters.
     */
    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws OperatorException {

        final Band[] targetBands = targetProduct.getBands();
        final Tile sourceRaster = getSourceTile(targetBandToSourceBandMap.get(targetBands[0]), targetRectangle, pm);

        final int x0 = targetRectangle.x;
        final int y0 = targetRectangle.y;
        final int w = targetRectangle.width;
        final int h = targetRectangle.height;
        //System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                final double v = sourceRaster.getSampleDouble(x, y);

                final int targetIndex = targetTiles.get(targetBands[0]).getDataBufferIndex(x, y);
                for(Band b : targetBands) {
                    targetTiles.get(b).getDataBuffer().setElemDoubleAt(targetIndex, y);
                }
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
            super(TileStackOp.class);
        }
    }
}
