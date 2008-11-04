package org.esa.nest.dataio.ceos;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.VirtualBand;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.util.math.MathUtils;
import org.esa.nest.datamodel.AbstractMetadata;

import java.io.IOException;

/**

 */
public abstract class CEOSProductDirectory {

    protected boolean isProductSLC = false;
    protected String productType;

    protected abstract void readProductDirectory() throws IOException, IllegalCeosFormatException;

    public abstract Product createProduct() throws IOException, IllegalCeosFormatException;

    public abstract CEOSImageFile getImageFile(final Band band) throws IOException, IllegalCeosFormatException;

    public abstract void close() throws IOException;

    public boolean isSLC() {
        return isProductSLC;
    }
    
    public String getSampleType() {
        if(isProductSLC)
            return "COMPLEX";
        else
            return "DETECTED";
    }

    public String getProductType() {
        return productType;
    }

    protected static void createVirtualPhaseBand(Product product, Band bandI, Band bandQ, String countStr) {
        String expression = "atan2("+bandQ.getName()+","+bandI.getName()+")";

        VirtualBand virtBand = new VirtualBand("Phase" + countStr,
                ProductData.TYPE_FLOAT64,
                product.getSceneRasterWidth(),
                product.getSceneRasterHeight(),
                expression);
        virtBand.setSynthetic(true);
        virtBand.setUnit("phase");
        virtBand.setDescription("Phase from complex data");
        product.addBand(virtBand);
    }

    protected static void createVirtualIntensityBand(Product product, Band bandI, Band bandQ, String countStr) {
        String expression = bandI.getName() + " * " + bandI.getName() + " + " +
                bandQ.getName() + " * " + bandQ.getName();

        VirtualBand virtBand = new VirtualBand("Intensity" + countStr,
                ProductData.TYPE_FLOAT64,
                product.getSceneRasterWidth(),
                product.getSceneRasterHeight(),
                expression);
        virtBand.setSynthetic(true);
        virtBand.setUnit("intensity");
        virtBand.setDescription("Intensity from complex data");
        product.addBand(virtBand);

        // set as band to use for quicklook
        product.setQuicklookBandName(virtBand.getName());
    }

    protected static void createVirtualIntensityBand(Product product, Band band, String countStr) {
        String expression = band.getName() + " * " + band.getName();

        VirtualBand virtBand = new VirtualBand("Intensity" + countStr,
                ProductData.TYPE_FLOAT64,
                product.getSceneRasterWidth(),
                product.getSceneRasterHeight(),
                expression);
        virtBand.setSynthetic(true);
        virtBand.setUnit("intensity");
        virtBand.setDescription("Intensity from complex data");
        product.addBand(virtBand);
    }

    protected String getPolarization(String id) {
        id = id.toUpperCase();
        if(id.contains("HH") || id.contains("H/H") || id.contains("H-H"))
            return "HH";
        else if(id.contains("VV") || id.contains("V/V") || id.contains("V-V"))
            return "VV";
        else if(id.contains("HV") || id.contains("H/V") || id.contains("H-V"))
            return "HV";
        else if(id.contains("VH") || id.contains("V/H") || id.contains("V-H"))
            return "VH";
        return id;
    }

    protected void createFineTiePointGrid(int coarseGridWidth,
                                          int coarseGridHeight,
                                          int fineGridWidth,
                                          int fineGridHeight,
                                          float[] coarseTiePoints,
                                          float[] fineTiePoints) {

        if (coarseTiePoints.length != coarseGridWidth*coarseGridHeight) {
            throw new IllegalArgumentException(
                    "coarse tie point array size does not match 'coarseGridWidth' x 'coarseGridHeight'");
        }

        if (fineTiePoints.length != fineGridWidth*fineGridHeight) {
            throw new IllegalArgumentException(
                    "fine tie point array size does not match 'fineGridWidth' x 'fineGridHeight'");
        }

        int k = 0;
        for (int r = 0; r < fineGridHeight; r++) {
            for (int c = 0; c < fineGridWidth; c++) {

                float lambdaC = (float)(c) / (float)(fineGridWidth - 1);
                float lambdaR = (float)(r) / (float)(fineGridHeight - 1);

                float betaC = lambdaC*(coarseGridWidth - 1);
                float betaR = lambdaR*(coarseGridHeight - 1);

                int i0 = (int)(betaC);
                int j0 = (int)(betaR);

                int i1 = Math.min(i0 + 1, coarseGridWidth - 1);
                int j1 = Math.min(j0 + 1, coarseGridHeight - 1);

                float wi = betaC - i0;
                float wj = betaR - j0;

                fineTiePoints[k] = MathUtils.interpolate2D(wi,
                                                           wj,
                                                           coarseTiePoints[i0 + j0 * coarseGridWidth],
                                                           coarseTiePoints[i1 + j0 * coarseGridWidth],
                                                           coarseTiePoints[i0 + j1 * coarseGridWidth],
                                                           coarseTiePoints[i1 + j1 * coarseGridWidth]);

                k++;
            }
        }
    }

}