package org.esa.nest.util;

import Jama.Matrix;

public final class MathUtils
{    
    private MathUtils()
    {
    }

    /**
     * Perform linear interpolation.
     * @param y0 First sample value.
     * @param y1 Second sample value.
     * @param mu A perameter in range [0,1] that defines the interpolated sample position between y0 and y1.
     *           A 0 value of mu corresponds to sample y0.
     * @return The interpolated sample value.
     */
    public static double interpolationLinear(final double y0, final double y1, final double mu) {
        return (1 - mu) * y0 + mu * y1;
    }

    /**
     * Perform cubic interpolation.
     * @param y0 First sample value.
     * @param y1 Second sample value.
     * @param y2 Third sample value.
     * @param y3 Forth sample value.
     * @param mu A perameter in range [0,1] that defines the interpolated sample position between y1 and y2.
     *           A 0 value of mu corresponds to sample y1.
     * @return The interpolated sample value.
     */
    public static double interpolationCubic(
            final double y0, final double y1, final double y2, final double y3, final double mu) {

        final double mu2 = mu*mu;
        final double a0 = -0.5*y0 + 1.5*y1 - 1.5*y2 + 0.5*y3;
        final double a1 = y0 - 2.5*y1 + 2*y2 - 0.5*y3;
        final double a2 = -0.5*y0 + 0.5*y2;
        final double a3 = y1;

        return (a0*mu*mu2 + a1*mu2 + a2*mu + a3);
    }

    /**
     * Perform cubic2 interpolation.
     * @param y0 First sample value.
     * @param y1 Second sample value.
     * @param y2 Third sample value.
     * @param y3 Forth sample value.
     * @param mu A perameter in range [0,1] that defines the interpolated sample position between y1 and y2.
     *           A 0 value of mu corresponds to sample y1.
     * @return The interpolated sample value.
     */
    public static double interpolationCubic2(
            final double y0, final double y1, final double y2, final double y3, final double mu) {

        final double mu2 = mu*mu;
        final double a0 = y3 - y2 - y0 + y1;
        final double a1 = y0 - y1 - a0;
        final double a2 = y2 - y0;
        final double a3 = y1;

        return (a0*mu*mu2 + a1*mu2 + a2*mu + a3);
    }

    /**
     * Perform Bi-linear interpolation.
     * @param v00 Sample value for pixel at (x0, y0).
     * @param v01 Sample value for pixel at (x1, y0).
     * @param v10 Sample value for pixel at (x0, y1).
     * @param v11 Sample value for pixel at (x1, y1).
     * @param muX A perameter in range [0,1] that defines the interpolated sample position between x0 and x1.
     *           A 0 value of muX corresponds to sample x0.
     * @param muY A perameter in range [0,1] that defines the interpolated sample position between y0 and y1.
     *           A 0 value of muY corresponds to sample y0.
     * @return The interpolated sample value.
     */
    public static double interpolationBiLinear(
            final double v00, final double v01, final double v10, final double v11, final double muX, final double muY) {
        return (1 - muY)*((1 - muX)*v00 + muX*v01) + muY*((1 - muX)*v10 + muX*v11);
    }

    /**
     * Get Vandermonde matrix constructed from a given array.
     * @param d The given range distance array.
     * @param warpPolynomialOrder The warp polynomial order.
     * @return The Vandermonde matrix.
     */
    public static Matrix createVandermondeMatrix(double[] d, int warpPolynomialOrder) {

        final int n = d.length;
        final double[][] array = new double[n][warpPolynomialOrder + 1];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= warpPolynomialOrder; j++) {
                array[i][j] = Math.pow(d[i], (double)j);
            }
        }
        return new Matrix(array);
    }

}
