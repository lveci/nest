package org.esa.nest.doris.util;

import org.jblas.DoubleMatrix;
//import static java.lang.Math;

/*
* 4) polyval: evaluates polynomial at (x,y)
*      IMPORTANT: similar fnc is already somewhere in NEST
*    a) real8 polyval(real8 x, real8 y, const matrix<real8> &coeff)
*    b) real8 polyval(real8 x, real8 y, const matrix<real8> &coeff, int32 degreee)
*
* 5) polyval: evaluates 2D polynomial at regular grid
*    a) matrix<Type> polyval(const matrix<real4> &x, const matrix<real4> &y, const matrix<real8> &coeff, int32 degreee)
*
*/

class PolyUtils {

    PolyUtils() {
    }

    public double polyval(double x, double y, final DoubleMatrix coeff) {

        final int degreee = polyDegree(coeff.length);

        if (coeff.length != coeff.rows) {
            System.out.println("WARNING: polyal functions require standing data vectors!");
        }

        if (degreee < 0 || degreee > 1000) {
            System.out.println("WARNING: polyal degree < 0 || degree > 1000 ????");
        }

        double sum = coeff.get(0, 0);

        // speed up
        if (degreee == 1) {
            sum += (coeff.get(1, 0) * x
                    + coeff.get(2, 0) * y);
        } else if (degreee == 2) {
            sum += (coeff.get(1, 0) * x
                    + coeff.get(2, 0) * y
                    + coeff.get(3, 0) * Math.pow(x, 2)
                    + coeff.get(4, 0) * x * y
                    + coeff.get(5, 0) * Math.pow(y, 2));
        } else if (degreee == 3) {
            final double xx = Math.pow(x, 2);
            final double yy = Math.pow(y, 2);
            sum += (coeff.get(1, 0) * x
                    + coeff.get(2, 0) * y
                    + coeff.get(3, 0) * xx
                    + coeff.get(4, 0) * x * y
                    + coeff.get(5, 0) * yy
                    + coeff.get(6, 0) * xx * x
                    + coeff.get(7, 0) * xx * y
                    + coeff.get(8, 0) * x * yy
                    + coeff.get(9, 0) * yy * y);
        } else if (degreee == 4) {
            final double xx = Math.pow(x, 2);
            final double xxx = xx * x;
            final double yy = Math.pow(y, 2);
            final double yyy = yy * y;
            sum += (coeff.get(1, 0) * x
                    + coeff.get(2, 0) * y
                    + coeff.get(3, 0) * xx
                    + coeff.get(4, 0) * x * y
                    + coeff.get(5, 0) * yy
                    + coeff.get(6, 0) * xxx
                    + coeff.get(7, 0) * xx * y
                    + coeff.get(8, 0) * x * yy
                    + coeff.get(9, 0) * yyy
                    + coeff.get(10, 0) * xx * xx
                    + coeff.get(11, 0) * xxx * y
                    + coeff.get(12, 0) * xx * yy
                    + coeff.get(13, 0) * x * yyy
                    + coeff.get(14, 0) * yy * yy);
        } else if (degreee == 5) {
            final double xx = Math.pow(x, 2);
            final double xxx = xx * x;
            final double xxxx = xxx * x;
            final double yy = Math.pow(y, 2);
            final double yyy = yy * y;
            final double yyyy = yyy * y;
            sum += (coeff.get(1, 0) * x
                    + coeff.get(2, 0) * y
                    + coeff.get(3, 0) * xx
                    + coeff.get(4, 0) * x * y
                    + coeff.get(5, 0) * yy
                    + coeff.get(6, 0) * xxx
                    + coeff.get(7, 0) * xx * y
                    + coeff.get(8, 0) * x * yy
                    + coeff.get(9, 0) * yyy
                    + coeff.get(10, 0) * xxxx
                    + coeff.get(11, 0) * xxx * y
                    + coeff.get(12, 0) * xx * yy
                    + coeff.get(13, 0) * x * yyy
                    + coeff.get(14, 0) * yyyy
                    + coeff.get(15, 0) * xxxx * x
                    + coeff.get(16, 0) * xxxx * y
                    + coeff.get(17, 0) * xxx * yy
                    + coeff.get(18, 0) * xx * yyy
                    + coeff.get(19, 0) * x * yyyy
                    + coeff.get(20, 0) * yyyy * y);
        } else if (degreee != 0) {                // degreee > 5
            sum = 0.0;
            int coeffIndex = 0;
            for (int l = 0; l <= degreee; l++) {
                for (int k = 0; k <= l; k++) {
                    sum += coeff.get(coeffIndex, 0) * Math.pow(x, (double) (l - k)) * Math.pow(y, (double) (k));
                    coeffIndex++;
                }
            }
        }

        return sum;
    }

    public double polyval(double x, double y, final DoubleMatrix coeff, int degreee) {

        if (coeff.length != coeff.rows) {
            System.out.println("WARNING: polyal functions require standing data vectors!");
        }

        if (degreee < 0 || degreee > 1000) {
            System.out.println("WARNING: polyal degree < 0 || degree > 1000 ????");
        }

        double sum = coeff.get(0, 0);

        // speed up
        if (degreee == 1) {
            sum += (coeff.get(1, 0) * x
                    + coeff.get(2, 0) * y);
        } else if (degreee == 2) {
            sum += (coeff.get(1, 0) * x
                    + coeff.get(2, 0) * y
                    + coeff.get(3, 0) * Math.pow(x, 2)
                    + coeff.get(4, 0) * x * y
                    + coeff.get(5, 0) * Math.pow(y, 2));
        } else if (degreee == 3) {
            final double xx = Math.pow(x, 2);
            final double yy = Math.pow(y, 2);
            sum += (coeff.get(1, 0) * x
                    + coeff.get(2, 0) * y
                    + coeff.get(3, 0) * xx
                    + coeff.get(4, 0) * x * y
                    + coeff.get(5, 0) * yy
                    + coeff.get(6, 0) * xx * x
                    + coeff.get(7, 0) * xx * y
                    + coeff.get(8, 0) * x * yy
                    + coeff.get(9, 0) * yy * y);
        } else if (degreee == 4) {
            final double xx = Math.pow(x, 2);
            final double xxx = xx * x;
            final double yy = Math.pow(y, 2);
            final double yyy = yy * y;
            sum += (coeff.get(1, 0) * x
                    + coeff.get(2, 0) * y
                    + coeff.get(3, 0) * xx
                    + coeff.get(4, 0) * x * y
                    + coeff.get(5, 0) * yy
                    + coeff.get(6, 0) * xxx
                    + coeff.get(7, 0) * xx * y
                    + coeff.get(8, 0) * x * yy
                    + coeff.get(9, 0) * yyy
                    + coeff.get(10, 0) * xx * xx
                    + coeff.get(11, 0) * xxx * y
                    + coeff.get(12, 0) * xx * yy
                    + coeff.get(13, 0) * x * yyy
                    + coeff.get(14, 0) * yy * yy);
        } else if (degreee == 5) {
            final double xx = Math.pow(x, 2);
            final double xxx = xx * x;
            final double xxxx = xxx * x;
            final double yy = Math.pow(y, 2);
            final double yyy = yy * y;
            final double yyyy = yyy * y;
            sum += (coeff.get(1, 0) * x
                    + coeff.get(2, 0) * y
                    + coeff.get(3, 0) * xx
                    + coeff.get(4, 0) * x * y
                    + coeff.get(5, 0) * yy
                    + coeff.get(6, 0) * xxx
                    + coeff.get(7, 0) * xx * y
                    + coeff.get(8, 0) * x * yy
                    + coeff.get(9, 0) * yyy
                    + coeff.get(10, 0) * xxxx
                    + coeff.get(11, 0) * xxx * y
                    + coeff.get(12, 0) * xx * yy
                    + coeff.get(13, 0) * x * yyy
                    + coeff.get(14, 0) * yyyy
                    + coeff.get(15, 0) * xxxx * x
                    + coeff.get(16, 0) * xxxx * y
                    + coeff.get(17, 0) * xxx * yy
                    + coeff.get(18, 0) * xx * yyy
                    + coeff.get(19, 0) * x * yyyy
                    + coeff.get(20, 0) * yyyy * y);
        } else if (degreee != 0) {                // degreee > 5
            sum = 0.0;
            int coeffIndex = 0;
            for (int l = 0; l <= degreee; l++) {
                for (int k = 0; k <= l; k++) {
                    sum += coeff.get(coeffIndex, 0) * Math.pow(x, (double) (l - k)) * Math.pow(y, (double) (k));
                    coeffIndex++;
                }
            }
        }

        return sum;
    }

    // for grid only support for double
    // TODO: generics
    public DoubleMatrix polyval(DoubleMatrix x, DoubleMatrix y, final DoubleMatrix coeff, int degreee) {

        if (x.length != x.rows) {
            System.out.println("WARNING: polyal functions require (x) standing data vectors!");
        }

        if (y.length != y.rows) {
            System.out.println("WARNING: polyal functions require (y) standing data vectors!");
        }

        if (coeff.length != coeff.rows) {
            System.out.println("WARNING: polyal functions require (coeff) standing data vectors!");
        }

        if (degreee < -1) {
            System.out.println("WARNING: polyal degree < -1 ????");
        }

        if (x.length > y.length) {
            System.out.println("WARNING: polyal function, x larger tan y, while optimized for y larger x");
        }

        if (degreee == -1) {
            degreee = polyDegree(coeff.length);
        }

        // evaluate polynomial
        DoubleMatrix result = new DoubleMatrix(new double[x.length][y.length]);
        int i;
        int j;

//        double sum = coeff.get(0,0);
        switch (degreee) {
            case 0:
                result.put(0, 0, coeff.get(0, 0));
                break;
            case 1:
                double c00 = coeff.get(0, 0);
                double c10 = coeff.get(1, 0);
                double c01 = coeff.get(2, 0);
                for (j = 0; j < result.columns; j++) {
                    double c00pc01y1 = c00 + c01 * y.get(j, 0);
                    for (i = 0; i < result.rows; i++) {
                        result.put(i, j, c00pc01y1 + c10 * x.get(i, 0));
                    }
                }
                break;
            case 2:
                c00 = coeff.get(0, 0);
                c10 = coeff.get(1, 0);
                c01 = coeff.get(2, 0);
                double c20 = coeff.get(3, 0);
                double c11 = coeff.get(4, 0);
                double c02 = coeff.get(5, 0);
                for (j = 0; j < result.columns; j++) {
                    double y1 = y.get(j, 0);
                    double c00pc01y1 = c00 + c01 * y1;
                    double c02y2 = c02 * Math.pow(y1, 2);
                    double c11y1 = c11 * y1;
                    for (i = 0; i < result.rows; i++) {
                        double x1 = x.get(i, 0);
                        result.put(i, j, c00pc01y1
                                + c10 * x1
                                + c20 * Math.pow(x1, 2)
                                + c11y1 * x1
                                + c02y2);
                    }
                }
                break;
            case 3:
                c00 = coeff.get(0, 0);
                c10 = coeff.get(1, 0);
                c01 = coeff.get(2, 0);
                c20 = coeff.get(3, 0);
                c11 = coeff.get(4, 0);
                c02 = coeff.get(5, 0);
                double c30 = coeff.get(6, 0);
                double c21 = coeff.get(7, 0);
                double c12 = coeff.get(8, 0);
                double c03 = coeff.get(9, 0);
                for (j = 0; j < result.columns; j++) {
                    double y1 = y.get(j, 0);
                    double y2 = Math.pow(y1, 2);
                    double c00pc01y1 = c00 + c01 * y1;
                    double c02y2 = c02 * y2;
                    double c11y1 = c11 * y1;
                    double c21y1 = c21 * y1;
                    double c12y2 = c12 * y2;
                    double c03y3 = c03 * y1 * y2;
                    for (i = 0; i < result.rows; i++) {
                        double x1 = x.get(i, 0);
                        double x2 = Math.pow(x1, 2);
                        result.put(i, j, c00pc01y1
                                + c10 * x1
                                + c20 * x2
                                + c11y1 * x1
                                + c02y2
                                + c30 * x1 * x2
                                + c21y1 * x2
                                + c12y2 * x1
                                + c03y3);
                    }
                }
                break;

            case 4:
                c00 = coeff.get(0, 0);
                c10 = coeff.get(1, 0);
                c01 = coeff.get(2, 0);
                c20 = coeff.get(3, 0);
                c11 = coeff.get(4, 0);
                c02 = coeff.get(5, 0);
                c30 = coeff.get(6, 0);
                c21 = coeff.get(7, 0);
                c12 = coeff.get(8, 0);
                c03 = coeff.get(9, 0);
                double c40 = coeff.get(10, 0);
                double c31 = coeff.get(11, 0);
                double c22 = coeff.get(12, 0);
                double c13 = coeff.get(13, 0);
                double c04 = coeff.get(14, 0);
                for (j = 0; j < result.columns; j++) {
                    double y1 = y.get(j, 0);
                    double y2 = Math.pow(y1, 2);
                    double c00pc01y1 = c00 + c01 * y1;
                    double c02y2 = c02 * y2;
                    double c11y1 = c11 * y1;
                    double c21y1 = c21 * y1;
                    double c12y2 = c12 * y2;
                    double c03y3 = c03 * y1 * y2;
                    double c31y1 = c31 * y1;
                    double c22y2 = c22 * y2;
                    double c13y3 = c13 * y2 * y1;
                    double c04y4 = c04 * y2 * y2;
                    for (i = 0; i < result.rows; i++) {
                        double x1 = x.get(i, 0);
                        double x2 = Math.pow(x1, 2);
                        result.put(i, j, c00pc01y1
                                + c10 * x1
                                + c20 * x2
                                + c11y1 * x1
                                + c02y2
                                + c30 * x1 * x2
                                + c21y1 * x2
                                + c12y2 * x1
                                + c03y3
                                + c40 * x2 * x2
                                + c31y1 * x2 * x1
                                + c22y2 * x2
                                + c13y3 * x1
                                + c04y4);
                    }
                }
                break;
            case 5:
                c00 = coeff.get(0, 0);
                c10 = coeff.get(1, 0);
                c01 = coeff.get(2, 0);
                c20 = coeff.get(3, 0);
                c11 = coeff.get(4, 0);
                c02 = coeff.get(5, 0);
                c30 = coeff.get(6, 0);
                c21 = coeff.get(7, 0);
                c12 = coeff.get(8, 0);
                c03 = coeff.get(9, 0);
                c40 = coeff.get(10, 0);
                c31 = coeff.get(11, 0);
                c22 = coeff.get(12, 0);
                c13 = coeff.get(13, 0);
                c04 = coeff.get(14, 0);
                double c50 = coeff.get(15, 0);
                double c41 = coeff.get(16, 0);
                double c32 = coeff.get(17, 0);
                double c23 = coeff.get(18, 0);
                double c14 = coeff.get(19, 0);
                double c05 = coeff.get(20, 0);
                for (j = 0; j < result.columns; j++) {
                    double y1 = y.get(j, 0);
                    double y2 = Math.pow(y1, 2);
                    double y3 = y2 * y1;
                    double c00pc01y1 = c00 + c01 * y1;
                    double c02y2 = c02 * y2;
                    double c11y1 = c11 * y1;
                    double c21y1 = c21 * y1;
                    double c12y2 = c12 * y2;
                    double c03y3 = c03 * y3;
                    double c31y1 = c31 * y1;
                    double c22y2 = c22 * y2;
                    double c13y3 = c13 * y3;
                    double c04y4 = c04 * y2 * y2;
                    double c41y1 = c41 * y1;
                    double c32y2 = c32 * y2;
                    double c23y3 = c23 * y3;
                    double c14y4 = c14 * y2 * y2;
                    double c05y5 = c05 * y3 * y2;
                    for (i = 0; i < result.rows; i++) {
                        double x1 = x.get(i, 0);
                        double x2 = Math.pow(x1, 2);
                        double x3 = x1 * x2;
                        result.put(i, j, c00pc01y1
                                + c10 * x1
                                + c20 * x2
                                + c11y1 * x1
                                + c02y2
                                + c30 * x3
                                + c21y1 * x2
                                + c12y2 * x1
                                + c03y3
                                + c40 * x2 * x2
                                + c31y1 * x3
                                + c22y2 * x2
                                + c13y3 * x1
                                + c04y4
                                + c50 * x3 * x2
                                + c41y1 * x2 * x2
                                + c32y2 * x3
                                + c23y3 * x2
                                + c14y4 * x1
                                + c05y5);
                    }
                }
                break;

            // ______ solve up to 5 efficiently, do rest in loop ______
            default:
                c00 = coeff.get(0, 0);
                c10 = coeff.get(1, 0);
                c01 = coeff.get(2, 0);
                c20 = coeff.get(3, 0);
                c11 = coeff.get(4, 0);
                c02 = coeff.get(5, 0);
                c30 = coeff.get(6, 0);
                c21 = coeff.get(7, 0);
                c12 = coeff.get(8, 0);
                c03 = coeff.get(9, 0);
                c40 = coeff.get(10, 0);
                c31 = coeff.get(11, 0);
                c22 = coeff.get(12, 0);
                c13 = coeff.get(13, 0);
                c04 = coeff.get(14, 0);
                c50 = coeff.get(15, 0);
                c41 = coeff.get(16, 0);
                c32 = coeff.get(17, 0);
                c23 = coeff.get(18, 0);
                c14 = coeff.get(19, 0);
                c05 = coeff.get(20, 0);
                for (j = 0; j < result.columns; j++) {
                    double y1 = y.get(j, 0);
                    double y2 = Math.pow(y1, 2);
                    double y3 = y2 * y1;
                    double c00pc01y1 = c00 + c01 * y1;
                    double c02y2 = c02 * y2;
                    double c11y1 = c11 * y1;
                    double c21y1 = c21 * y1;
                    double c12y2 = c12 * y2;
                    double c03y3 = c03 * y3;
                    double c31y1 = c31 * y1;
                    double c22y2 = c22 * y2;
                    double c13y3 = c13 * y3;
                    double c04y4 = c04 * y2 * y2;
                    double c41y1 = c41 * y1;
                    double c32y2 = c32 * y2;
                    double c23y3 = c23 * y3;
                    double c14y4 = c14 * y2 * y2;
                    double c05y5 = c05 * y3 * y2;
                    for (i = 0; i < result.rows; i++) {
                        double x1 = x.get(i, 0);
                        double x2 = Math.pow(x1, 2);
                        double x3 = x1 * x2;
                        result.put(i, j, c00pc01y1
                                + c10 * x1
                                + c20 * x2
                                + c11y1 * x1
                                + c02y2
                                + c30 * x3
                                + c21y1 * x2
                                + c12y2 * x1
                                + c03y3
                                + c40 * x2 * x2
                                + c31y1 * x3
                                + c22y2 * x2
                                + c13y3 * x1
                                + c04y4
                                + c50 * x3 * x2
                                + c41y1 * x2 * x2
                                + c32y2 * x3
                                + c23y3 * x2
                                + c14y4 * x1
                                + c05y5);
                    }
                }

                final int STARTDEGREE = 6;
                final int STARTCOEFF = polyNumberCoeffs(STARTDEGREE - 1);   // 5-> 21 6->28 7->36 etc.
                for (j = 0; j < result.columns; j++) {
                    double yy = y.get(j, 0);
                    for (i = 0; i < result.rows; i++) {
                        double xx = x.get(i, 0);        // ??? this seems to be wrong (BK 9-feb-00)
                        double sum = 0.;
                        int coeffindex = STARTCOEFF;
                        for (int l = STARTDEGREE; l <= degreee; l++) {
                            for (int k = 0; k <= l; k++) {
                                sum += coeff.get(coeffindex, 0) * Math.pow(xx, (double) (l - k)) * Math.pow(yy, (double) (k));
                                coeffindex++;
                            }
                        }
                        result.put(i, j, result.get(i, j) + sum);
                    }
                }
        } // switch degree

        return result;

    }

    public static double polyval1d(double x, final DoubleMatrix coeff) {
        double sum = 0.;
        for (int d=coeff.length-1;d>=0;--d){
            sum *= x;
            sum += coeff.get(d,0);
        }
        return sum;
    }

    public static int polyNumberCoeffs(final int degree) {
        return (int) (0.5 * (Math.pow(degree + 1, 2) + degree + 1));
    }

    public static int polyDegree(final int numberCoeffs) {
        return (int) (0.5 * (-1 + (int) (Math.sqrt((float) (1 + 8 * numberCoeffs))))) - 1;
    }

    public static float remainder(final float number, final float divisor) {
        return number - (float) Math.floor((double) ((number / divisor) * divisor));
    }

    public static double remainder(final double number, final double divisor) {
        return number - Math.floor((number / divisor) * divisor);
    }


}