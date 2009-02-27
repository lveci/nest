package org.esa.nest.util;

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


}
