package org.esa.nest.dataio.ceos;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.util.math.MathUtils;

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

    protected static String getPolarization(String id) {
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

    protected static void createFineTiePointGrid(int coarseGridWidth,
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

    protected void addGeoCoding(final Product product, final float[] latCorners, final float[] lonCorners) {

        if(latCorners == null || lonCorners == null) return;

        int gridWidth = 10;
        int gridHeight = 10;

        final float[] fineLatTiePoints = new float[gridWidth*gridHeight];
        createFineTiePointGrid(2, 2, gridWidth, gridHeight, latCorners, fineLatTiePoints);

        float subSamplingX = (float)product.getSceneRasterWidth() / (gridWidth - 1);
        float subSamplingY = (float)product.getSceneRasterHeight() / (gridHeight - 1);

        final TiePointGrid latGrid = new TiePointGrid("lat", gridWidth, gridHeight, 0.5f, 0.5f,
                subSamplingX, subSamplingY, fineLatTiePoints);

        final float[] fineLonTiePoints = new float[gridWidth*gridHeight];
        createFineTiePointGrid(2, 2, gridWidth, gridHeight, lonCorners, fineLonTiePoints);

        final TiePointGrid lonGrid = new TiePointGrid("lon", gridWidth, gridHeight, 0.5f, 0.5f,
                subSamplingX, subSamplingY, fineLonTiePoints, TiePointGrid.DISCONT_AT_180);

        final TiePointGeoCoding tpGeoCoding = new TiePointGeoCoding(latGrid, lonGrid, Datum.WGS_84);

        product.addTiePointGrid(latGrid);
        product.addTiePointGrid(lonGrid);
        product.setGeoCoding(tpGeoCoding);
    }
}