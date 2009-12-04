package org.esa.nest.dataio;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.maptransf.IdentityTransformDescriptor;
import org.esa.beam.framework.dataop.maptransf.MapInfo;
import org.esa.beam.framework.dataop.maptransf.MapProjectionRegistry;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.Guardian;
import org.esa.beam.util.math.MathUtils;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.datamodel.AbstractMetadata;

import java.io.File;
import java.util.Arrays;

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

    public static void createMapGeocoding(final Product targetProduct, final String projectionName, final double noDataValue) {
        final MapInfo mapInfo = ProductUtils.createSuitableMapInfo(targetProduct,
                                                MapProjectionRegistry.getProjection(projectionName),
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

    public static void verifyProduct(Product product, boolean verifyTimes) throws Exception {
        verifyProduct(product, verifyTimes, true);    
    }

    public static void verifyProduct(Product product, boolean verifyTimes, boolean verifyGeoCoding) throws Exception {
        if(product == null)
            throw new Exception("product is null");
        if(verifyGeoCoding && product.getGeoCoding() == null)
            throw new Exception("geocoding is null");
        if(product.getMetadataRoot() == null)
            throw new Exception("metadataroot is null");
        if(product.getNumBands() == 0)
            throw new Exception("numbands is zero");
        if(product.getProductType() == null || product.getProductType().isEmpty())
            throw new Exception("productType is null");
        if(verifyTimes) {
            if(product.getStartTime() == null)
                throw new Exception("startTime is null");
             if(product.getEndTime() == null)
                throw new Exception("endTime is null");
        }
        for(Band b : product.getBands()) {
            if(b.getUnit() == null || b.getUnit().isEmpty())
                throw new Exception("band " + b.getName() + " has null unit");
        }
    }

    public static ProductData.UTC getTime(final MetadataElement elem, final String tag, final String timeFormat) {
        final String timeStr = createValidUTCString(elem.getAttributeString(tag, " ").toUpperCase(),
                new char[]{':','.','-'}, ' ').trim();
        return AbstractMetadata.parseUTC(timeStr, timeFormat);
    }

    private static String createValidUTCString(final String name, final char[] validChars, final char replaceChar) {
        Guardian.assertNotNull("name", name);
        char[] sortedValidChars = null;
        if (validChars == null) {
            sortedValidChars = new char[5];
        } else {
            sortedValidChars = (char[]) validChars.clone();
        }
        Arrays.sort(sortedValidChars);
        final StringBuilder validName = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            final char ch = name.charAt(i);
            if (Character.isDigit(ch)) {
                validName.append(ch);
            } else if (Arrays.binarySearch(sortedValidChars, ch) >= 0) {
                validName.append(ch);
            } else {
                validName.append(replaceChar);
            }
        }
        return validName.toString();
    }


    public static String findPolarizationInBandName(final String bandName) {

        final String name = bandName.toUpperCase();
        if(name.contains("HH"))
            return "HH";
        else if(name.contains("VV"))
            return "VV";
        else if(name.contains("HV"))
            return "HV";
        else if(name.contains("VH"))
            return "VH";

        return null;
    }
}
