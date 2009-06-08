package org.esa.nest.dataio;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.maptransf.MapInfo;
import org.esa.beam.framework.dataop.maptransf.MapProjectionRegistry;
import org.esa.beam.framework.dataop.maptransf.IdentityTransformDescriptor;
import org.esa.beam.util.math.MathUtils;
import org.esa.beam.util.ProductUtils;
import org.esa.nest.datamodel.Unit;

import java.io.File;

/**
 * Common functions for readers
 */
public class ReaderUtils {


    public static void createVirtualPhaseBand(Product product, Band bandI, Band bandQ, String countStr) {
        final String expression = "atan2("+bandQ.getName()+ ',' +bandI.getName()+ ')';

        final VirtualBand virtBand = new VirtualBand("Phase" + countStr,
                ProductData.TYPE_FLOAT32,
                product.getSceneRasterWidth(),
                product.getSceneRasterHeight(),
                expression);
        virtBand.setSynthetic(true);
        virtBand.setUnit(Unit.PHASE);
        virtBand.setDescription("Phase from complex data");
        product.addBand(virtBand);
    }

    public static void createVirtualIntensityBand(Product product, Band bandI, Band bandQ, String countStr) {
        final String expression = bandI.getName() + " * " + bandI.getName() + " + " +
                bandQ.getName() + " * " + bandQ.getName();

        final VirtualBand virtBand = new VirtualBand("Intensity" + countStr,
                ProductData.TYPE_FLOAT32,
                product.getSceneRasterWidth(),
                product.getSceneRasterHeight(),
                expression);
        virtBand.setSynthetic(true);
        virtBand.setUnit(Unit.INTENSITY);
        virtBand.setDescription("Intensity from complex data");
        product.addBand(virtBand);

        // set as band to use for quicklook
        product.setQuicklookBandName(virtBand.getName());
    }

    public static void createVirtualIntensityBand(Product product, Band band, String countStr) {
        final String expression = band.getName() + " * " + band.getName();

        final VirtualBand virtBand = new VirtualBand("Intensity" + countStr,
                ProductData.TYPE_FLOAT32,
                product.getSceneRasterWidth(),
                product.getSceneRasterHeight(),
                expression);
        virtBand.setSynthetic(true);
        virtBand.setUnit(Unit.INTENSITY);
        virtBand.setDescription("Intensity from complex data");
        product.addBand(virtBand);
    }

    /**
     * Returns a <code>File</code> if the given input is a <code>String</code> or <code>File</code>,
     * otherwise it returns null;
     *
     * @param input an input object of unknown type
     *
     * @return a <code>File</code> or <code>null</code> it the input can not be resolved to a <code>File</code>.
     */
    public static File getFileFromInput(final Object input) {
        if (input instanceof String) {
            return new File((String) input);
        } else if (input instanceof File) {
            return (File) input;
        }
        return null;
    }

    public static void createFineTiePointGrid(int coarseGridWidth,
                                          int coarseGridHeight,
                                          int fineGridWidth,
                                          int fineGridHeight,
                                          float[] coarseTiePoints,
                                          float[] fineTiePoints) {

        if (coarseTiePoints == null || coarseTiePoints.length != coarseGridWidth*coarseGridHeight) {
            throw new IllegalArgumentException(
                    "coarse tie point array size does not match 'coarseGridWidth' x 'coarseGridHeight'");
        }

        if (fineTiePoints == null || fineTiePoints.length != fineGridWidth*fineGridHeight) {
            throw new IllegalArgumentException(
                    "fine tie point array size does not match 'fineGridWidth' x 'fineGridHeight'");
        }

        int k = 0;
        for (int r = 0; r < fineGridHeight; r++) {

            final float lambdaR = (float)(r) / (float)(fineGridHeight - 1);
            final float betaR = lambdaR*(coarseGridHeight - 1);
            final int j0 = (int)(betaR);
            final int j1 = Math.min(j0 + 1, coarseGridHeight - 1);
            final float wj = betaR - j0;

            for (int c = 0; c < fineGridWidth; c++) {

                final float lambdaC = (float)(c) / (float)(fineGridWidth - 1);
                final float betaC = lambdaC*(coarseGridWidth - 1);
                final int i0 = (int)(betaC);
                final int i1 = Math.min(i0 + 1, coarseGridWidth - 1);
                final float wi = betaC - i0;

                fineTiePoints[k++] = MathUtils.interpolate2D(wi, wj,
                                                           coarseTiePoints[i0 + j0 * coarseGridWidth],
                                                           coarseTiePoints[i1 + j0 * coarseGridWidth],
                                                           coarseTiePoints[i0 + j1 * coarseGridWidth],
                                                           coarseTiePoints[i1 + j1 * coarseGridWidth]);
            }
        }
    }

    public static void createMapGeocoding(final Product targetProduct, final double noDataValue) {
        final MapInfo mapInfo = ProductUtils.createSuitableMapInfo(targetProduct,
                                                MapProjectionRegistry.getProjection(IdentityTransformDescriptor.NAME),
                                                0.0,
                                                noDataValue);
        mapInfo.setSceneWidth(targetProduct.getSceneRasterWidth());
        mapInfo.setSceneHeight(targetProduct.getSceneRasterHeight());

        targetProduct.setGeoCoding(new MapGeoCoding(mapInfo));
    }

    public static double getLineTimeInterval(ProductData.UTC startUTC, ProductData.UTC endUTC, int sceneHeight) {
        final double startTime = startUTC.getMJD() * 24 * 3600;
        final double stopTime = endUTC.getMJD() * 24 * 3600;
        return (stopTime-startTime) / (sceneHeight-1);
    }

    public static int getTotalSize(Product product) {
        return (int)(product.getRawStorageSize() / (1024.0f * 1024.0f));
    }

    public static void verifyProduct(Product product) throws Exception {
        if(product == null)
            throw new Exception("product is null");
        if(product.getGeoCoding() == null)
            throw new Exception("geocoding is null");
        if(product.getMetadataRoot() == null)
            throw new Exception("metadataroot is null");
        if(product.getNumBands() == 0)
            throw new Exception("numbands is zero");
        if(product.getProductType() == null || product.getProductType().isEmpty())
            throw new Exception("productType is null");
        if(product.getStartTime() == null)
            throw new Exception("startTime is null");

        for(Band b : product.getBands()) {
            if(b.getUnit() == null || b.getUnit().isEmpty())
                throw new Exception("band " + b.getName() + " has null unit");
        }
    }
}
