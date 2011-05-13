package org.jdoris.core.utils;

import org.apache.log4j.Logger;
import org.jblas.Decompose;
import org.jblas.DoubleMatrix;
import org.jblas.Solve;

import static org.jblas.MatrixFunctions.abs;

public class PolyUtils {

    public static Logger logger = Logger.getLogger(PolyUtils.class.getName());

    public static double normalize2(double data, final int min, final int max) {
        data -= (0.5 * (min + max));
        data /= (0.25 * (max - min));
        return data;
    }

    public static double normalize2(double data, final double min, final double max) {
        data -= (0.5 * (min + max));
        data /= (0.25 * (max - min));
        return data;
    }

    public static DoubleMatrix normalize(DoubleMatrix t) {
        return t.sub(t.get(t.length / 2)).div(10.0);
    }

    public static int degreeFromCoefficients(int numOfCoefficients) {
//        return (int) (0.5 * (-1 + (int) (Math.sqrt((double) (1 + 8 * numOfCoefficients))))) - 1;
        return (int) (0.5 * (-1 + (int) (Math.sqrt((double) (1 + 8 * numOfCoefficients))))) - 1;
    }

    public static int numberOfCoefficients(final int degree) {
        return (int) (0.5 * (Math.pow(degree + 1, 2) + degree + 1));
    }

    /**
     * polyfit
     * <p/>
     * Compute coefficients of x=a0+a1*t+a2*t^2+a3*t3 polynomial
     * for orbit interpolation.  Do this to facilitate a method
     * in case only a few datapoints are given.
     * Data t is normalized approximately [-x,x], then polynomial
     * coefficients are computed.  For poly_val this is repeated
     * see getxyz, etc.
     * <p/>
     * input:
     * - matrix by getdata with time and position info
     * output:
     * - matrix with coeff.
     * (input for interp. routines)
     */
    public static double[] polyFitNormalized(DoubleMatrix t, DoubleMatrix y, final int degree) throws Exception {
        return polyFit(normalize(t), y, degree);
    }

    public static double[] polyFit(DoubleMatrix t, DoubleMatrix y, final int degree) throws Exception {

        if (t.length != y.length) {
            logger.error("polyfit: require same size vectors.");
            throw new Exception("polyfit: require same size vectors.");
        }

        // Normalize _posting_ for numerical reasons
        final int numOfPoints = t.length;
//        DoubleMatrix normPosting = normalize(t);

        // Check redundancy
        final int numOfUnknowns = degree + 1;
        logger.debug("Degree of interpolating polynomial: " + degree);
        logger.debug("Number of unknowns: " + numOfUnknowns);
        logger.debug("Number of data points: " + numOfPoints);

        if (numOfPoints < numOfUnknowns) {
            logger.error("Number of points is smaller than parameters solved for.");
            throw new Exception("Number of points is smaller than parameters solved for.");
        }

        // Set up system of equations to solve coeff :: Design matrix
        logger.debug("Setting up linear system of equations");
        DoubleMatrix A = new DoubleMatrix(numOfPoints, numOfUnknowns);
        // work with columns
        for (int j = 0; j <= degree; j++) {
            DoubleMatrix normPostingTemp = t.dup();
            normPostingTemp = LinearAlgebraUtils.matrixPower(normPostingTemp, (double) j);
            A.putColumn(j, normPostingTemp);
        }

        // Fit polynomial through computed vector of phases
        logger.debug("Solving lin. system of equations with Cholesky.");

//        DoubleMatrix y = y.dup();
        DoubleMatrix N = A.transpose().mmul(A);
        DoubleMatrix rhs = A.transpose().mmul(y);

        // solution seems to be OK up to 10^-09!
        DoubleMatrix x = Solve.solve(N, rhs);

        // TODO: JBLAS returns UPPER triangular, while we work(!!!!) with the LOWER triangular -- make uniform!
        DoubleMatrix Qx_hat = Decompose.cholesky(N).transpose();

        // get covarinace matrix of normalized unknowns
        Qx_hat = LinearAlgebraUtils.invertChol(Qx_hat); // this could be more efficient

        // Test inverse: repair matrix!!
        logger.debug("reparing cholesky decomposed matrix");
        for (int i = 0; i < Qx_hat.rows; i++) {
            for (int j = 0; j < i; j++) {
                Qx_hat.put(j, i, Qx_hat.get(i, j));
            }
        }

        double maxDeviation = abs(N.mmul(Qx_hat).sub(DoubleMatrix.eye(Qx_hat.rows))).max();
        logger.debug("polyfit orbit: max(abs(N*inv(N)-I)) = " + maxDeviation);

        // ___ report max error... (seems sometimes this can be extremely large) ___
        if (maxDeviation > 1e-6) {
            logger.warn("polyfit orbit: max(abs(N*inv(N)-I)) = " + maxDeviation);
            logger.warn("polyfit orbit interpolation unstable!");
        }

        // work out residuals
        DoubleMatrix y_hat = A.mmul(x);
        DoubleMatrix e_hat = y.sub(y_hat);

        DoubleMatrix e_hat_abs = abs(e_hat);

        // TODO: absMatrix(e_hat_abs).max() there is a simpleBlas function that implements this!
        // 0.05 is already 1 wavelength! (?)
        if (LinearAlgebraUtils.absMatrix(e_hat_abs).max() > 0.02) {
            logger.warn("WARNING: Max. approximation error at datapoints (x,y,or z?): " + abs(e_hat).max() + " m");

        } else {
            logger.info("Max. approximation error at datapoints (x,y,or z?): " + abs(e_hat).max() + " m");
        }

        logger.debug("REPORTING POLYFIT LEAST SQUARES ERRORS");
        logger.debug(" time \t\t\t y \t\t\t yhat  \t\t\t ehat");
        for (int i = 0; i < numOfPoints; i++) {
            logger.debug(" " + t.get(i) + "\t" + y.get(i) + "\t" + y_hat.get(i) + "\t" + e_hat.get(i));
        }

        for (int i = 0; i < numOfPoints - 1; i++) {
            // ___ check if dt is constant, not necessary for me, but may ___
            // ___ signal error in header data of SLC image ___
            double dt = t.get(i + 1) - t.get(i);
            logger.debug("Time step between point " + i + 1 + " and " + i + "= " + dt);

            if (Math.abs(dt - (t.get(1) - t.get(0))) > 0.001)// 1ms of difference we allow...
                logger.warn("WARNING: Orbit: data does not have equidistant time interval?");
        }

        return x.toArray();
    }

    public static double polyVal1D(double x, double[] coeffs) {
        double sum = 0.0;
        for (int d = coeffs.length - 1; d >= 0; --d) {
            sum *= x;
            sum += coeffs[d];
        }
        return sum;
    }

    public static DoubleMatrix polyval(final DoubleMatrix x, final DoubleMatrix y, final DoubleMatrix coeff, int degree) {

        if (!x.isColumnVector()) {
            logger.warn("polyValGrid: require (x) standing data vectors!");
            throw new IllegalArgumentException("polyval functions require (x) standing data vectors!");
        }

        if (!y.isColumnVector()) {
            logger.warn("polyValGrid: require (y) standing data vectors!");
            throw new IllegalArgumentException("polyval functions require (y) standing data vectors!");
        }

        if (!coeff.isColumnVector()) {
            logger.warn("polyValGrid: require (coeff) standing data vectors!");
            throw new IllegalArgumentException("polyval functions require (coeff) standing data vectors!");
        }

        if (degree < -1) {
            logger.warn("polyValGrid: degree < -1 ????");
        }

        if (x.length > y.length) {
            logger.warn("polValGrid: x larger than y, while optimized for y larger x");
        }

        if (degree == -1) {
            degree = degreeFromCoefficients(coeff.length);
        }

        // evaluate polynomial //
        DoubleMatrix result = new DoubleMatrix(x.length, y.length);
        int i;
        int j;
        double c00, c10, c01, c20, c11, c02, c30, c21, c12, c03, c40, c31, c22, c13, c04, c50, c41, c32, c23, c14, c05;

        switch (degree) {
            case 0:
                result.put(0, 0, coeff.get(0, 0));
                break;
            case 1:
                c00 = coeff.get(0, 0);
                c10 = coeff.get(1, 0);
                c01 = coeff.get(2, 0);
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
                c20 = coeff.get(3, 0);
                c11 = coeff.get(4, 0);
                c02 = coeff.get(5, 0);
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
                c30 = coeff.get(6, 0);
                c21 = coeff.get(7, 0);
                c12 = coeff.get(8, 0);
                c03 = coeff.get(9, 0);
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
                c40 = coeff.get(10, 0);
                c31 = coeff.get(11, 0);
                c22 = coeff.get(12, 0);
                c13 = coeff.get(13, 0);
                c04 = coeff.get(14, 0);
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
                break;

            // TODO: solve up to 5 efficiently, do rest in loop
            default:
                for (j = 0; j < result.columns; j++) {
                    double yy = y.get(j, 0);
                    for (i = 0; i < result.rows; i++) {
                        double xx = x.get(i, 0);
                        result.put(i, j, polyval(xx, yy, coeff, degree));
                    }
                }
        } // switch degree

        return result;
    }

    public static double polyval(final double x, final double y, final DoubleMatrix coeff, int degree) {

        if (!coeff.isColumnVector()) {
            logger.warn("polyValGrid: require (coeff) standing data vectors!");
            throw new IllegalArgumentException("polyval functions require (coeff) standing data vectors!");
        }

        if (degree < 0 || degree > 1000) {
            logger.warn("polyval: degree value [" + degree + "] not realistic!");
            throw new IllegalArgumentException("polyval: degree not realistic!");
        }

        //// Check default arguments ////
        if (degree < -1) {
            logger.warn("polyValGrid: degree < -1 ????");
            degree = degreeFromCoefficients(coeff.length);
        }

        //// Evaluate polynomial ////
        double sum = coeff.get(0, 0);

        if (degree == 1) {
            sum += (coeff.get(1, 0) * x
                    + coeff.get(2, 0) * y);
        } else if (degree == 2) {
            sum += (coeff.get(1, 0) * x
                    + coeff.get(2, 0) * y
                    + coeff.get(3, 0) * Math.pow(x, 2)
                    + coeff.get(4, 0) * x * y
                    + coeff.get(5, 0) * Math.pow(y, 2));
        } else if (degree == 3) {
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
        } else if (degree == 4) {
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
        } else if (degree == 5) {
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
        } else if (degree != 0)                // degreee > 5
        {
            sum = 0.0;
            int coeffIndex = 0;
            for (int l = 0; l <= degree; l++) {
                for (int k = 0; k <= l; k++) {
                    sum += coeff.get(coeffIndex, 0) * Math.pow(x, (double) (l - k)) * Math.pow(y, (double) k);
                    coeffIndex++;
                }
            }
        }

        return sum;

    }
}
