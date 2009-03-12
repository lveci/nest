package org.esa.nest.datamodel;

import org.esa.beam.framework.datamodel.TiePointGrid;
import org.esa.nest.util.MathUtils;
import Jama.Matrix;

/**
 * Created by IntelliJ IDEA.
 * User: junlu
 * Date: Feb 10, 2009
 * Time: 5:31:11 PM
 * To change this template use File | Settings | File Templates.
 */
public class QuadInterpolator {

    private float subSamplinX;
    private float subSamplinY;
    private double[][] warpPolynomialCoef;
    private static int warpPolynomialOrder = 2;

    public QuadInterpolator(TiePointGrid tpg) {

        int imageWidth = tpg.getSceneRasterWidth();
        subSamplinX = tpg.getSubSamplingX();
        subSamplinY = tpg.getSubSamplingY();
        int width = tpg.getRasterWidth();
        int height = tpg.getRasterHeight();
        float[] tiePoints = tpg.getTiePoints();

        String tiePointGridName = tpg.getName();
        boolean imageFlipped = false;
        if ((tiePointGridName.contains("incidence_angle") || tiePointGridName.contains("slant_range_time")) &&
            (tiePoints[0] > tiePoints[width - 1])) {
            imageFlipped = true;
        }

        double[] sampleIndexArray = new double[width];
        for (int c = 0; c < width; c++) {
            if (imageFlipped) {
                sampleIndexArray[width - 1 - c] = imageWidth - 1 - Math.min(c*((int)subSamplinX - 1), imageWidth - 1);
            } else {
                sampleIndexArray[c] = Math.min(c*((int)subSamplinX - 1), imageWidth - 1);
            }
        }

        final Matrix A = MathUtils.createVandermondeMatrix(sampleIndexArray, warpPolynomialOrder);

        double[] tiePointArray = new double[width];
        warpPolynomialCoef = new double[height][warpPolynomialOrder + 1];
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                tiePointArray[c] = (double)tiePoints[r*width + c];
            }
            final Matrix b = new Matrix(tiePointArray, width);
            final Matrix x = A.solve(b);
            warpPolynomialCoef[r] = x.getColumnPackedCopy();
        }
    }

    /**
     * Get pixel values for given rectangle.
     * @param x0 The x coordinate for the upper left corner of the rectangle.
     * @param y0 The y coordinate for the upper left corner of the rectangle.
     * @param w The width of the rectangle.
     * @param h The height of the rectangle.
     * @param pixels The pixel array.
     * @return The pixel array.
     */
    public float[] getPixelFloats(int x0, int y0, int w, int h, float[] pixels) {

        int k = 0;
        for (int y = y0; y < y0 + h; y++) {
            int r = (int) (y / subSamplinY);
            final double a0 = warpPolynomialCoef[r][0];
            final double a1 = warpPolynomialCoef[r][1];
            final double a2 = warpPolynomialCoef[r][2];
            for (int x = x0; x < x0 + w; x++) {
                pixels[k++] = (float)(a0 + a1*x + a2*x*x);
            }
        }
        return pixels;
    }
}
