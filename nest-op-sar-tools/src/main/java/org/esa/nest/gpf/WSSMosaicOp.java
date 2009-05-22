package org.esa.nest.gpf;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.ProductUtils;

import java.awt.*;

/**
 * Mosaic a debursted ASAR WSS product
 */
@OperatorMetadata(alias = "WSS-Mosaic",
                  description = "Mosaics two or more detected sub-swaths of an ASA_WSS_1P",
                  internal = true)
public class WSSMosaicOp extends Operator {

    @SourceProduct
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    BandLimits[] bandLimitList;

    /**
     * Default constructor. The graph processing framework
     * requires that an operator has a default constructor.
     */
    public WSSMosaicOp() {
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

        // check product type 
        if (!sourceProduct.getProductType().equals("ASA_WSS_1P")) {
            throw new OperatorException("Source product is not an ASA_WSS_1P");
        }

        targetProduct = new Product(sourceProduct.getName() + "_MOSAIC",
                sourceProduct.getProductType(),
                sourceProduct.getSceneRasterWidth() * sourceProduct.getNumBands(),
                sourceProduct.getSceneRasterHeight());

        Band targetBand = targetProduct.addBand("Mosaic", ProductData.TYPE_FLOAT32);
        targetBand.setUnit("intensity");

        // copy meta data from source to target
        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyFlagCodings(sourceProduct, targetProduct);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
        
        int numSrcBands = sourceProduct.getNumBands();
        bandLimitList = new BandLimits[numSrcBands];
        int x = 0;

        Band[] srcBands = sourceProduct.getBands();
        for(int i=0; i < numSrcBands; ++i) {
            int width = srcBands[i].getRasterWidth();
            bandLimitList[i] = new BandLimits(x, width);
            x += width;
        }

        targetProduct.setPreferredTileSize(sourceProduct.getSceneRasterWidth(), 256);
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

        Rectangle targetTileRectangle = targetTile.getRectangle();
        int x = targetTileRectangle.x;
        int y = targetTileRectangle.y;
        int w = targetTileRectangle.width;
        int h = targetTileRectangle.height;

        int numSrcBands = sourceProduct.getNumBands();
        Band[] srcBands = sourceProduct.getBands();
        double noDataValue = srcBands[0].getNoDataValue();
        for(int b=0; b < numSrcBands; ++b) {

            BandLimits limit = bandLimitList[b];
            if(limit.startX > x + w || limit.endX < x)
                continue;

            int x0 = Math.max(x - limit.startX, 0);
            int w0 = Math.min(w, limit.endX - x);
            if(x < limit.startX)
                w0 = x + w - limit.startX;
            Rectangle srcRect = new Rectangle(x0, y, w0, h);

            //System.out.println("x="+x +" w="+w +" x0="+x0 +" w0="+w0 +
            //        " StartX="+limit.startX + " width="+limit.width);


            try {
            Tile sourceRaster = getSourceTile(srcBands[b], srcRect, pm);
            float[] srcPixels = (float[])sourceRaster.getRawSamples().getElems();

            float val;
            for(int j = y; j < y + h; ++j) {
                int srcStride = (j-y) * w0;
                for(int i = x, srcX = 0; srcX < srcRect.width; ++srcX) {

                    val = srcPixels[srcStride + srcX];
                    if(val != noDataValue) {
                        targetTile.setSample(i, j, val);
                        ++i;
                    }
                }
            }

            } catch(Exception e) {
                System.out.println(e.toString());
            }

        }
    }

    private class BandLimits {
        int startX;
        int endX;
        int width;
        BandLimits(int x, int w) {
            startX = x;
            width = w-1;
            endX = x + width;
        }
    }

    /**
     * The SPI is used to register this operator in the graph processing framework
     * via the SPI configuration file
     * {@code META-INF/services/org.esa.beam.framework.gpf.OperatorSpi}.
     * This class may also serve as a factory for new operator instances.
     *
     * @see org.esa.beam.framework.gpf.OperatorSpi#createOperator()
     * @see org.esa.beam.framework.gpf.OperatorSpi#createOperator(java.util.Map, java.util.Map)
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(WSSMosaicOp.class);
        }
    }
}