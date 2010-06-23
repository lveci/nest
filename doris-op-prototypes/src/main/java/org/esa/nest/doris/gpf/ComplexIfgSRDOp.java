package org.esa.nest.doris.gpf;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.maptransf.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.math.MathUtils;
import org.esa.nest.dataio.ReaderUtils;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.gpf.OperatorUtils;

import org.jblas.*;

import org.esa.nest.util.Constants;
import org.esa.nest.util.GeoUtils;
import org.esa.beam.framework.datamodel.GeoPos;


import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import java.awt.*;
import java.awt.image.RenderedImage;
import java.util.HashMap;
import java.util.Map;


@OperatorMetadata(alias = "ComplexIfgSRD",
        category = "InSAR Prototypes",
        description = "Subtract DEM phase from stack of interferograms (JBLAS implementation)", internal = true)
public class ComplexIfgSRDOp extends Operator {

    @SourceProduct
    private Product sourceProduct;

    @TargetProduct
    private Product targetProduct;

//    private Band masterBand1 = null;
//    private Band masterBand2 = null;
//
//    private final Map<Band, Band> slaveRasterMap = new HashMap<Band, Band>(10);

    private final Map<Band, Band> sourceRasterMap = new HashMap<Band, Band>(10);
    private final Map<Band, Band> sourceIfgMap = new HashMap<Band, Band>(10);
    private final Map<Band, Band> targetIfgMap = new HashMap<Band, Band>(10);

    // see comment in finalize{} block of computeTileStack
    private int totalTileCount;
    private ThreadSafeCounter threadCounter = new ThreadSafeCounter();
    private int sourceImageWidth;
    private int sourceImageHeight;

    private GeoCoding targetGeoCoding = null;

    // Metadata
    private MetadataElement masterRoot;
    private MetadataElement slaveRoot;
    private MetadataDoris masterMetadata;
    private MetadataDoris slaveMetadata;
    private OrbitsDoris masterOrbit;
    private OrbitsDoris slaveOrbit;

    private DoubleMatrix flatEarthPolyCoefs;
    private String elevationBandName = "elevation";

    private double delLat = 0.0;
    private double delLon = 0.0;
    private double slaveMinPi4divLam;


//    final RasterDataNode raster = getRaster();
//    final Product product = getProduct();


    // thread safe counter for testing: copyed and pasted from DorisOpUtils.java
    public final class ThreadSafeCounter {
        private long value = 0;

        public synchronized long getValue() {
            return value;
        }

        public synchronized long increment() {
            return ++value;
        }
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
        try {

//            masterBand1 = sourceProduct.getBandAt(0);
//            if (masterBand1.getUnit() != null && masterBand1.getUnit().equals(Unit.REAL) && sourceProduct.getNumBands() > 1) {
//                masterBand2 = sourceProduct.getBandAt(1);
//            }

            checkUserInput();
            getMetadata();
            getSourceImageDimension();

            // getSourceImageGeocodings();

            // estimateFlatEarthPolynomial();

            // computeSensorPositionsAndVelocities();

            // updateTargetProductMetadata();

            // updateTargetProductGeocoding();

            createTargetProduct();

            double masterMinPi4divLam = (-4 * Math.PI * Constants.lightSpeed) / masterMetadata.radar_wavelength;
            slaveMinPi4divLam = (-4 * Math.PI * Constants.lightSpeed) / slaveMetadata.radar_wavelength;

        } catch (Exception e) {
            throw new OperatorException(e);
        }
    }

    private void getSourceImageGeocodings() {
        final GeoCoding geoCoding = targetProduct.getGeoCoding();
    }

    private void updateTargetProductMetadata() {
        // update metadata of target product for the estimated polynomial
    }

    private void updateTargetProductGeocoding() {
        // update metadata of target product for the estimated polynomial
    }

    private void getSourceImageDimension() {
        sourceImageWidth = sourceProduct.getSceneRasterWidth();
        sourceImageHeight = sourceProduct.getSceneRasterHeight();
    }

    private void getMetadata() {

        // MASTER METADATA
        // get DorisMetadata for this operator
        // get Orbits for NestDorisProcessing
        masterRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct);
        masterMetadata = new MetadataDoris(masterRoot);
        masterOrbit = new OrbitsDoris();
        masterOrbit.setOrbit(masterRoot);

        // SLAVE METADATA
        // hash table or just map for slaves - now it supports only a single slave image
        slaveRoot = sourceProduct.getMetadataRoot().getElement(AbstractMetadata.SLAVE_METADATA_ROOT).getElementAt(0);
        slaveMetadata = new MetadataDoris(slaveRoot);
        slaveOrbit = new OrbitsDoris();
        slaveOrbit.setOrbit(slaveRoot);

    }

    private void checkUserInput() {
        // check whether there are any eleveation bands in the product

        try {

            for (Band band : sourceProduct.getBands()) {

                if (band.getName().equalsIgnoreCase(elevationBandName)) {
                    String projectionName = IdentityTransformDescriptor.NAME;
                    final MapInfo mapInfo = ProductUtils.createSuitableMapInfo(
                            sourceProduct,
                            MapProjectionRegistry.getProjection(projectionName),
                            0.0,
                            sourceProduct.getBandAt(0).getNoDataValue());

                    delLat = mapInfo.getPixelSizeX();
                    delLon = mapInfo.getPixelSizeY();
                }
            }
        } catch (Exception e) {
            throw new OperatorException("Band " + elevationBandName + " doesn't exists. Check input.");

        }
    }

    private double normalize(double data, int min, int max) {
        data -= (float) (0.5 * (min + max));
        data /= (float) (0.25 * (max - min));
        return data;
    }

    private Point2d xyz2t(Point3d position, MetadataDoris metadata, OrbitsDoris orbit) {

        Point3d delta = new Point3d();
        Point2d returnVector = new Point2d();

        final int MAXITER = 100;
        final double CRITERTIM = Math.pow(10, -10);
        final double SOL = Constants.lightSpeed;

        // inital value
        double timeAzimuth = line2ta(0.5 * metadata.approxRadarCentreOriginal.y, metadata);

        int iter;
        Point3d satellitePosition = null;
        double solution = 0;

        for (iter = 0; iter <= MAXITER; ++iter) {
            satellitePosition = getSatelliteXYZ(timeAzimuth, orbit);
            Point3d satelliteVelocity = getSatelliteXYZDot(timeAzimuth, orbit);
            Point3d satelliteAcceleration = getSatelliteXYZDDot(timeAzimuth, orbit);
            delta.x = position.x - satellitePosition.x;
            delta.y = position.y - satellitePosition.y;
            delta.z = position.z - satellitePosition.z;

            // update solution
            solution = -1 * (satelliteVelocity.x * delta.x + satelliteVelocity.y * delta.y + satelliteVelocity.z * delta.z) /
                    (satelliteAcceleration.x * delta.x + satelliteAcceleration.y * delta.y + satelliteAcceleration.z * delta.z -
                            Math.pow(satelliteVelocity.x, 2) - Math.pow(satelliteVelocity.y, 2) - Math.pow(satelliteVelocity.z, 2));

            timeAzimuth += solution;

            if (Math.abs(solution) < CRITERTIM) {
                break;
            }

        }
        // ______ Check number of iterations _____
        if (iter >= MAXITER) {
            System.out.println("WARNING: x,y,z -> line, pix: maximum iterations (" + MAXITER + ") reached. " + "Criterium (s):" + CRITERTIM + "dta (s)=" + solution);
        }

        // ====== Compute range time ======
        // ______ Update equations ______
        //satellitePosition = getSatelliteXYZ(timeAzimuth,orbit);
        delta.x = position.x - satellitePosition.x;
        delta.y = position.y - satellitePosition.y;
        delta.z = position.z - satellitePosition.z;
        double timeRange = Math.sqrt(Math.pow(delta.x, 2) + Math.pow(delta.y, 2) + Math.pow(delta.z, 2)) / SOL;

        returnVector.y = timeAzimuth;
        returnVector.x = timeRange;

        return returnVector;
    }

    private Point3d getSatelliteXYZDot(double azTime, OrbitsDoris orbit) {

        //TODO: sanity check
        Point3d satelliteVelocity = new Point3d();

        // normalize time
        azTime = (azTime - orbit.time.get(orbit.time.length / 2)) / 10;

        // NOTE: orbit interpolator is simple polynomial
        satelliteVelocity.x = orbit.coeff_X.get(1);
        satelliteVelocity.y = orbit.coeff_Y.get(1);
        satelliteVelocity.z = orbit.coeff_Z.get(1);

        for (int i = 2; i <= orbit.poly_degree; ++i) {
            double powT = (double) i * Math.pow(azTime, (double) (i - 1));
            satelliteVelocity.x += orbit.coeff_X.get(i) * powT;
            satelliteVelocity.y += orbit.coeff_Y.get(i) * powT;
            satelliteVelocity.z += orbit.coeff_Z.get(i) * powT;
        }

        satelliteVelocity.x /= 10.0d;
        satelliteVelocity.y /= 10.0d;
        satelliteVelocity.z /= 10.0d;

        return satelliteVelocity;  //To change body of created methods use File | Settings | File Templates.

    }

    // make generic so it can work with arrays of lines as well

    private Point3d lp2xyz(double line, double pixel, MetadataDoris metadata, OrbitsDoris orbits) {

        int MAXITER = 10;
        final double CRITERPOS = Math.pow(10, -6);
        final double SOL = Constants.lightSpeed;
        final int refHeight = 0;

        final double ell_a = Constants.semiMajorAxis;
        final double ell_b = Constants.semiMinorAxis;

        Point3d satellitePosition;
        Point3d satelliteVelocity;
        Point3d ellipsoidPosition;

        // put stuff that makes sense here!
        double azTime = line2ta(line, metadata);
        double rgTime = pix2tr(pixel, metadata);

        satellitePosition = getSatelliteXYZ(azTime, orbits);
        satelliteVelocity = getSatelliteXYZDot(azTime, orbits);

        ellipsoidPosition = metadata.approxXYZCentreOriginal;

        // declare matrices
        // DoubleMatrix ellipsoidPositionSolution = DoubleMatrix.zeros(3,1);
        DoubleMatrix equationSet = DoubleMatrix.zeros(3);
        DoubleMatrix partialsXYZ = DoubleMatrix.zeros(3, 3);

        for (int iter = 0; iter <= MAXITER; iter++) {
            //   update equations and slove system
            double dsat_Px = ellipsoidPosition.x - satellitePosition.x;   // vector of 'satellite to P on ellipsoid'
            double dsat_Py = ellipsoidPosition.y - satellitePosition.y;   // vector of 'satellite to P on ellipsoid'
            double dsat_Pz = ellipsoidPosition.z - satellitePosition.z;   // vector of 'satellite to P on ellipsoid'

            equationSet.put(0,
                    -(satelliteVelocity.x * dsat_Px +
                            satelliteVelocity.y * dsat_Py +
                            satelliteVelocity.z * dsat_Pz));

            equationSet.put(1,
                    -(dsat_Px * dsat_Px +
                            dsat_Py * dsat_Py +
                            dsat_Pz * dsat_Pz - Math.pow(SOL * rgTime, 2)));

            equationSet.put(2,
                    -((ellipsoidPosition.x * ellipsoidPosition.x + ellipsoidPosition.y * ellipsoidPosition.y) / (Math.pow(ell_a + refHeight, 2)) +
                            Math.pow(ellipsoidPosition.z / (ell_b + refHeight), 2) - 1.0));

            partialsXYZ.put(0, 0, satelliteVelocity.x);
            partialsXYZ.put(0, 1, satelliteVelocity.y);
            partialsXYZ.put(0, 2, satelliteVelocity.z);
            partialsXYZ.put(1, 0, 2 * dsat_Px);
            partialsXYZ.put(1, 1, 2 * dsat_Py);
            partialsXYZ.put(1, 2, 2 * dsat_Pz);
            partialsXYZ.put(2, 0, (2 * ellipsoidPosition.x) / (Math.pow(ell_a + refHeight, 2)));
            partialsXYZ.put(2, 1, (2 * ellipsoidPosition.y) / (Math.pow(ell_a + refHeight, 2)));
            partialsXYZ.put(2, 2, (2 * ellipsoidPosition.z) / (Math.pow(ell_a + refHeight, 2)));

            // solve system [NOTE!] orbit has to be normalized, otherwise close to singular
            DoubleMatrix ellipsoidPositionSolution = Solve.solve(partialsXYZ, equationSet);
            // DoubleMatrix ellipsoidPositionSolution = solve33(partialsXYZ, equationSet);

            // update solution
            ellipsoidPosition.x = ellipsoidPosition.x + ellipsoidPositionSolution.get(0);
            ellipsoidPosition.y = ellipsoidPosition.y + ellipsoidPositionSolution.get(1);
            ellipsoidPosition.z = ellipsoidPosition.z + ellipsoidPositionSolution.get(2);

            // check convergence
            if (Math.abs(ellipsoidPositionSolution.get(0)) < CRITERPOS &&
                    Math.abs(ellipsoidPositionSolution.get(1)) < CRITERPOS &&
                    Math.abs(ellipsoidPositionSolution.get(2)) < CRITERPOS) {
                break;
            } else if (iter >= MAXITER) {
                MAXITER = MAXITER + 1;
                System.out.println("WARNING: line, pix -> x,y,z: maximum iterations (" + MAXITER + ") reached. " + "Criterium (m): " + CRITERPOS +
                        "dx,dy,dz=" + ellipsoidPositionSolution.get(0) + ", " + ellipsoidPositionSolution.get(1) + ", " + ellipsoidPositionSolution.get(2, 0));
            }
        }

        return ellipsoidPosition;
    }

    // EXPERIMENTAL
    /*
       Solves setof 3 equations by straightforward (no pivotting) LU
       y=Ax (unknown x)

        input:
          - matrix righthandside 3x1 (y)
          - matrix partials 3x3 (A)

        output/return:
         - matrix result 3x1 unknown
    */

    private DoubleMatrix solve33(DoubleMatrix A, DoubleMatrix rhs) {

        DoubleMatrix result = DoubleMatrix.zeros(3, 1);

//      if (A.lines() != 3 || A.pixels() != 3){
//        throw "solve33: input: size of A not 33.")
//      }
//      if (rhs.lines() != 3 || rhs.pixels() != 1) {
//        throw "solve33: input: size rhs not 3x1.")
//      }

        // ______  real8 L10, L20, L21: used lower matrix elements
        // ______  real8 U11, U12, U22: used upper matrix elements
        // ______  real8 b0,  b1,  b2:  used Ux=b
        final double L10 = A.get(1, 0) / A.get(0, 0);
        final double L20 = A.get(2, 0) / A.get(0, 0);
        final double U11 = A.get(1, 1) - L10 * A.get(0, 1);
        final double L21 = (A.get(2, 1) - (A.get(0, 1) * L20)) / U11;
        final double U12 = A.get(1, 2) - L10 * A.get(0, 2);
        final double U22 = A.get(2, 2) - L20 * A.get(0, 2) - L21 * U12;

        // ______ Solution: forward substitution ______
        final double b0 = rhs.get(0, 0);
        final double b1 = rhs.get(1, 0) - b0 * L10;
        final double b2 = rhs.get(2, 0) - b0 * L20 - b1 * L21;

        // ______ Solution: backwards substitution ______
        result.put(2, 0, b2 / U22);
        result.put(1, 0, (b1 - U12 * result.get(2, 0)) / U11);
        result.put(0, 0, (b0 - A.get(0, 1) * result.get(1, 0) - A.get(0, 2) * result.get(2, 0)) / A.get(0, 0));

        return result;

    }

    private Point3d getSatelliteXYZ(double azTime, OrbitsDoris orbit) {

        //TODO: sanity check!
        Point3d satelliteXYZPosition = new Point3d();

        // normalize time
        azTime = (azTime - orbit.time.get(orbit.time.length / 2)) / 10;

        satelliteXYZPosition.x = polyVal1d(azTime, orbit.coeff_X);
        satelliteXYZPosition.y = polyVal1d(azTime, orbit.coeff_Y);
        satelliteXYZPosition.z = polyVal1d(azTime, orbit.coeff_Z);

        return satelliteXYZPosition;  //To change body of created methods use File | Settings | File Templates.
    }

    private Point3d getSatelliteXYZDDot(double azTime, OrbitsDoris orbit) {

        //TODO: sanity check
        Point3d satelliteAcceleration = new Point3d();

        // normalize time
        azTime = (azTime - orbit.time.get(orbit.time.length / 2)) / 10.0d;

        // NOTE: orbit interpolator is simple polynomial
        // 2a_2 + 2*3a_3*t^1 + 3*4a_4*t^2...
        satelliteAcceleration.x = 0;
        satelliteAcceleration.y = 0;
        satelliteAcceleration.z = 0;

        for (int i = 2; i <= orbit.poly_degree; ++i) {
            double powT = (double) ((i-1)*i) * Math.pow(azTime, (double) (i - 2));
            satelliteAcceleration.x += orbit.coeff_X.get(i) * powT;
            satelliteAcceleration.y += orbit.coeff_Y.get(i) * powT;
            satelliteAcceleration.z += orbit.coeff_Z.get(i) * powT;
        }

        satelliteAcceleration.x /= 100.0d;
        satelliteAcceleration.y /= 100.0d;
        satelliteAcceleration.z /= 100.0d;

        return satelliteAcceleration;  //To change body of created methods use File | Settings | File Templates.

    }

    private double polyVal1d(double x, DoubleMatrix coefficients) {

        double sum = 0.0;
        for (int d = coefficients.length - 1; d >= 0; --d) {
            sum *= x;
            sum += coefficients.get(d);
        }
        return sum;
    }

    private double pix2tr(double pixel, MetadataDoris metadata) {
        return metadata.t_range1 + (pixel - 1.0) / metadata.rsr2x;
    }

    private double line2ta(double line, MetadataDoris metadata) {
        return metadata.t_azi1 + (line - 1.0) / metadata.PRF;
    }

    private double[][] distributePoints(int numOfPoints, Rectangle rectangle) {

        double lines = (rectangle.getMaxY() - rectangle.getMinY() + 1);
        double pixels = (rectangle.getMaxX() - rectangle.getMinX() + 1);

        double[][] result = new double[numOfPoints][2];

        // ______ Distribution for dl=dp ______
        double wp = Math.sqrt(numOfPoints / (lines / pixels));   // wl: #windows in line direction
        double wl = numOfPoints / wp;                   // wp: #windows in pixel direction
        if (wl < wp) {
            // switch wl,wp : later back
            wl = wp;
        }

        double wlint = Math.ceil(wl); // round largest
        double deltal = (lines - 1) / (wlint - 1);
        double totp = Math.ceil(pixels * wlint);
        double deltap = (totp - 1) / (numOfPoints - 1);
        double p = -deltap;
        double l = 0.;
        double lcnt = 0;
        int i;
        for (i = 0; i < numOfPoints; i++) {
            p += deltap;
            while (Math.ceil(p) >= pixels) // ceil
            {
                p -= pixels;
                lcnt++;
            }
            l = lcnt * deltal;

            result[i][0] = (int) Math.ceil(l);
            result[i][1] = (int) Math.ceil(p);
        }

        //        // ______ Correct distribution to window ______
        //        for (i=0; i<numOfPoints; i++){
        //            result[i][0] += (int)rectangle.getMinY();
        //            result[i][1] += (int)rectangle.getMinX();
        //        }
        return result;
    }

    // input: matrix by with posting and observations eg. time and position
    // output: float matrix with coefficients
    private DoubleMatrix polyFit(DoubleMatrix posting, DoubleMatrix observations, int polyDegree) {

        // TODO: check on the vector size
        // TODO: check on order of posting: has to be ascending
        /*
                if (time.pixels() != 1 || y.pixels() != 1){
                    PRINT_ERROR("code 902: polyfit: wrong input.");
                    throw(input_error);
                }
                if (time.lines() != y.lines()) {
                    PRINT_ERROR("code 902: polyfit: require same size vectors.");
                    throw(input_error);
                }
            */

        // Normalize _posting_ for numerical reasons
        final int numOfPoints = posting.length;
        DoubleMatrix normPosting = posting.sub(posting.get(numOfPoints / 2)).div(10.0);

        // Check redundancy
        final int numOfUnknowns = polyDegree + 1;
        System.out.println("Degree of orbit interpolating polynomial: " + polyDegree);
        System.out.println("Number of unknowns: " + numOfUnknowns);
        System.out.println("Number of data points (orbit): " + numOfPoints);
        if (numOfPoints < numOfUnknowns) {
            throw new OperatorException("Number of points is smaller than parameters solved for.");
        }

        // Setup system of equation
        System.out.println("Setting up linear system of equations");
        DoubleMatrix A = new DoubleMatrix(numOfPoints, numOfUnknowns);// designmatrix
        for (int j = 0; j <= polyDegree; j++) {
            DoubleMatrix normPostingTemp = normPosting.dup();
            normPostingTemp = matrixPower(normPostingTemp, (double) j);
            A.putColumn(j, normPostingTemp);
        }

        System.out.println("A = " + A);

        System.out.println("Solving lin. system of equations with Cholesky.");
        // Fit polynomial through computed vector of phases
        DoubleMatrix y = observations.dup();
        DoubleMatrix N = A.transpose().mmul(A);
        DoubleMatrix rhs = A.transpose().mmul(y);

        // solution seems to be OK up to 10^-09!
        DoubleMatrix x = Solve.solve(N, rhs);

        // JBLAS returns UPPER triangular, while we work with the LOWER triangular
        DoubleMatrix Qx_hat = Decompose.cholesky(N).transpose();

        // get covarinace matrix of normalized unknowns
        Qx_hat = invertCholesky(Qx_hat); // this could be more efficient

        // ______Test inverse______
        // repair matrix!! (see doris.core code for numerical example)
        for (int i = 0; i < Qx_hat.rows; i++) {
            for (int j = 0; j < i; j++) {
                Qx_hat.put(j, i, Qx_hat.get(i, j));
            }
        }

        DoubleMatrix maxDeviation = N.mmul(Qx_hat).sub(DoubleMatrix.eye(Qx_hat.rows));

        System.out.println("polyFit orbit: max(abs(N*inv(N)-I)) = " + maxDeviation.get(1, 1));

        // ___ report max error... (seems sometimes this can be extremely large) ___
        if (maxDeviation.get(1, 1) > 1e-6) {
            System.out.println("polyfit orbit interpolation unstable!");
        }

        // work out residuals
        DoubleMatrix y_hat = A.mmul(x);
        DoubleMatrix e_hat = y.sub(y_hat);

        DoubleMatrix e_hat_abs = absMatrix(e_hat);

        // TODO: absMatrix(e_hat_abs).max() there is a simpleBlas function that implements this!
        // 0.05 is already 1 wavelength! (?)
        if (absMatrix(e_hat_abs).max() > 0.02) {
            System.out.println("WARNING: Max. approximation error at datapoints (x,y,or z?): " + absMatrix(e_hat).max() + "m");
        } else {
            System.out.println("Max. approximation error at datapoints (x,y,or z?): " + absMatrix(e_hat).max() + "m");
        }

        System.out.println("REPORTING POLYFIT LEAST SQUARES ERRORS");
        System.out.println(" time \t\t\t y \t\t\t yhat  \t\t\t ehat");
        for (int i = 0; i < numOfPoints; i++) {
            System.out.println(" " + posting.get(i) + "\t" + y.get(i) + "\t" + y_hat.get(i) + "\t" + e_hat.get(i));
        }

        for (int i = 0; i < numOfPoints - 1; i++) {
            // ___ check if dt is constant, not necessary for me, but may ___
            // ___ signal error in header data of SLC image ___
            double dt = posting.get(i + 1) - posting.get(i);
            System.out.println("Time step between point " + i + 1 + " and " + i + "= " + dt);

            if (Math.abs(dt - (posting.get(1) - posting.get(0))) > 0.001)// 1ms of difference we allow...
                System.out.println("WARNING: Orbit: data does not have equidistant time interval?");
        }

        return x;
    } // END polyfit

    private DoubleMatrix absMatrix(DoubleMatrix matrix) {
        System.out.println("matrix = " + matrix);
        for (int i = 0; i < matrix.rows; i++) {
            for (int j = 0; j < matrix.columns; j++) {
                matrix.put(i, j, Math.abs(matrix.get(i, j)));
            }
        }
        return matrix;
    }

    // input: cholesky decomposed matrix
    // return: inverted matrix
    // note: performance can be improved
    private DoubleMatrix invertCholesky(DoubleMatrix matrix) {
        int numOfRows = matrix.rows;
        double sum;
        int i, j, k;
        // ______ Compute inv(L) store in lower of A ______
        for (i = 0; i < numOfRows; ++i) {
            matrix.put(i, i, 1. / matrix.get(i, i));
            for (j = i + 1; j < numOfRows; ++j) {
                sum = 0.;
                for (k = i; k < j; ++k) {
                    sum -= matrix.get(j, k) * matrix.get(k, i);
                }
                matrix.put(j, i, sum / matrix.get(j, j));
            }
        }

        // ______ Compute inv(A)=inv(LtL) store in lower of A ______
        for (i = 0; i < numOfRows; ++i) {
            for (j = i; j < numOfRows; ++j) {
                sum = 0.;
                for (k = j; k < numOfRows; ++k) {
                    sum += matrix.get(k, i) * matrix.get(k, j);                 // transpose
                }
                matrix.put(j, i, sum);
            }
        }
        return matrix;
    } // END invertchol BK

    private DoubleMatrix matrixPower(DoubleMatrix data, double scalar) {
        for (int i = 0; i < data.rows; ++i) {
            for (int j = 0; j < data.columns; ++j) {
                data.put(i, j, Math.pow(data.get(i, j), scalar));
            }
        }
        return data;
    }

    private int numberOfCoefficients(int degree) {
        return (int) (0.5 * (Math.pow(degree + 1, 2) + degree + 1));
    }

    private int degreeFromCoefficients(int numOfCoefficients) {
        // TODO: validate this?!
        return (int) (0.5*(-1 + (int)(Math.sqrt(1+8*numOfCoefficients))))-1;
    }

    private double tr2pix(double timeRange, MetadataDoris metadata) {
        return 1 + metadata.rsr2x * (timeRange - metadata.t_range1);
    }

    private double ta2line(double timeAzimuth, MetadataDoris metadata) {
        return 1 + metadata.PRF * (timeAzimuth - metadata.t_azi1);
    }


    /**
     * Create target product.
     */
    private void createTargetProduct() {

        // total number of bands in stack: master and slaves
        final int totalNumOfBands = sourceProduct.getNumBands();

        targetProduct = new Product(sourceProduct.getName(),
                sourceProduct.getProductType(),
                sourceProduct.getSceneRasterWidth(),
                sourceProduct.getSceneRasterHeight());

        int inc = 2; // increment for bands!

        // construct target product only for interferogram bands: totalNumOfBands - 2 (i/q for master)
        // assume that master bands are first 2 bands in the stack!
        for (int i = 0; i < totalNumOfBands; i += inc) {

            System.out.println("i = " + i);

            if (sourceProduct.getBandAt(i).getUnit().equals(Unit.REAL)) {
                final Band srcBandI = sourceProduct.getBandAt(i);
                System.out.println("srcBandI.getName() = " + srcBandI.getName());

                if (sourceProduct.getBandAt(i + 1).getUnit().equals(Unit.IMAGINARY)) {

                    final Band srcBandQ = sourceProduct.getBandAt(i + 1);
                    System.out.println("srcBandQ.getName() = " + srcBandQ.getName());
                    sourceIfgMap.put(srcBandI, srcBandQ);

                    String trgtBandIName = srcBandI.getName().replace("ifg", "ifgsrd");
                    String trgtBandQName = srcBandQ.getName().replace("ifg", "ifgsrd");

                    final Band targetBandI = targetProduct.addBand(trgtBandIName, ProductData.TYPE_FLOAT32);
                    ProductUtils.copyRasterDataNodeProperties(srcBandI, targetBandI);
                    sourceRasterMap.put(srcBandI,targetBandI);

                    final Band targetBandQ = targetProduct.addBand(trgtBandQName, ProductData.TYPE_FLOAT32);
                    ProductUtils.copyRasterDataNodeProperties(srcBandQ, targetBandQ);
                    sourceRasterMap.put(srcBandQ,targetBandQ);

                    targetIfgMap.put(targetBandI, targetBandQ);

                    ReaderUtils.createVirtualIntensityBand(targetProduct, targetBandI, targetBandQ, "");
                    ReaderUtils.createVirtualPhaseBand(targetProduct, targetBandI, targetBandQ, "");
                }
            }
        }

//        targetGeoCoding = targetProduct.getGeoCoding();
        targetProduct.setPreferredTileSize(500,500);
//        targetProduct.setPreferredTileSize(targetProduct.getSceneRasterWidth(), targetProduct.getSceneRasterHeight());
        
        final Dimension tileSize = targetProduct.getPreferredTileSize();
        final int rasterHeight = targetProduct.getSceneRasterHeight();
        final int rasterWidth = targetProduct.getSceneRasterWidth();

        int tileCountX = MathUtils.ceilInt(rasterWidth / (double) tileSize.width);
        int tileCountY = MathUtils.ceilInt(rasterHeight / (double) tileSize.height);
        totalTileCount = tileCountX * tileCountY;

        System.out.println("TotalTileCount: [" + totalTileCount + "]");

    }

    /**
     * Called by the framework in order to compute a tile for the given target band.
     * <p>The default implementation throws a runtime exception with the message "not implemented".</p>
     *
     * @param targetTileMap   The target tiles associated with all target bands to be computed.
     * @param targetRectangle The rectangle of target tile.
     * @param pm              A progress monitor which should be used to determine computation cancelation requests.
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs during computation of the target raster.
     */
    @Override
    public void computeTileStack(Map<Band, Tile> targetTileMap, Rectangle targetRectangle, ProgressMonitor pm)
            throws OperatorException {
        try {

            int x0 = targetRectangle.x;
            int y0 = targetRectangle.y;
            int w = targetRectangle.width;
            int h = targetRectangle.height;
            System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

            ComplexFloatMatrix srpIfg = null;
            FloatMatrix elevationMatrix = null;
            GeoPos geoPos;
            GeoCoding geoCoding = null;

            FloatMatrix dataRealMatrix;
            FloatMatrix dataImagMatrix;

            RenderedImage srcImage;
            float[] dataArray;

            // these are not very optimal loops: redo the looping to a single for Band loop with using HashMaps
            for (Band sourceBand : sourceProduct.getBands()) {

                // only ifgs are in the stack!
                String sourceBandUnit = sourceBand.getUnit();
                if (sourceBandUnit != null && sourceBandUnit.contains(Unit.REAL)) {

                    Band ifgBandQ = sourceIfgMap.get(sourceBand);

                    Tile ifgRasterI = getSourceTile(sourceBand, targetRectangle, pm);
                    srcImage = ifgRasterI.getRasterDataNode().getSourceImage();
                    dataArray = srcImage.getData(targetRectangle).getSamples(x0, y0, w, h, 0, (float[]) null);

                    dataRealMatrix = new FloatMatrix(ifgRasterI.getHeight(), ifgRasterI.getWidth(), dataArray);

                    Tile ifgRasterQ = getSourceTile(ifgBandQ, targetRectangle, pm);
                    srcImage = ifgRasterQ.getRasterDataNode().getSourceImage();
                    dataArray = srcImage.getData(targetRectangle).getSamples(x0, y0, w, h, 0, (float[]) null);

                    dataImagMatrix = new FloatMatrix(ifgRasterQ.getHeight(), ifgRasterQ.getWidth(), dataArray);

                    srpIfg = new ComplexFloatMatrix(dataRealMatrix, dataImagMatrix);

                }

                // ------------------------------------------------
                // get Elevation for this TILE: this is NOT radarcoded elevation!!!!
                if (sourceBand.getName().equalsIgnoreCase(elevationBandName) && sourceBandUnit.contains(Unit.METERS)) {

                    Tile elevationTile = getSourceTile(sourceBand, targetRectangle, pm);
                    srcImage = elevationTile.getRasterDataNode().getSourceImage();
                    float[] elevationArray = srcImage.getData(targetRectangle).getSamples(x0, y0, w, h, 0, (float[]) null);
                    elevationMatrix = new FloatMatrix(elevationTile.getHeight(), elevationTile.getWidth(), elevationArray);

                    // geocodings per tile
                    geoCoding = sourceBand.getGeoCoding();

                }

            }


            // DEVELOPMENT PART --------
            // Memo: this block computes the DEM phase in DEM reference system
            // TODO: implement triangulation implementation : Delaunay or something third?
            // - see doris.core code for implementation reference
            // - see TriScatteredInterp class of Matlab
            // - NEEDED: triangle.c in Java, alternatives could be:
            //                 a) JAVA MESCHER: http://code.google.com/p/jmescher/
            //                 b) Numerical Recipes in Java
            //                 c) re-implementation of triangle.c in Java

            float height;
            double[] demXYZ = new double[3];
            final FloatMatrix topographicPhase = new FloatMatrix(elevationMatrix.rows,elevationMatrix.columns);
//            final FloatMatrix topographicPhaseTemp1 = new FloatMatrix(elevationMatrix.rows, elevationMatrix.columns);
//            final FloatMatrix topographicPhaseTemp2 = new FloatMatrix(elevationMatrix.rows, elevationMatrix.columns);
            Point2d masterRadarPointTime;
            Point2d masterRadarPointLP = new Point2d();
            Point2d slaveRadarPointLP = new Point2d();

            for (int line = 0; line < elevationMatrix.rows; ++line) {
                for (int pixel = 0; pixel < elevationMatrix.columns; ++pixel) {

                    // System.out.println("line,pixel :" + (line + y0) + ", " + (pixel + x0));

                    // get geoposition of this pixel in elevation band
                    geoPos = geoCoding.getGeoPos(new PixelPos(pixel + x0, line + y0), null);


                    // Testing Block: Info per pixel [start]
                    final Point3d testPoint = lp2xyz(line + y0, pixel + x0, masterMetadata, masterOrbit);

                    GeoPos testGeo = new GeoPos();
                    GeoUtils.xyz2geo(new double[] {testPoint.x,testPoint.y, testPoint.z},testGeo, GeoUtils.EarthModel.WGS84);

                    PixelPos pixelPos = geoCoding.getPixelPos(geoPos, null);
                    PixelPos pixelTestPos = geoCoding.getPixelPos(testGeo, null);

                    System.out.println("pixelPos (input): " + (line + y0) + " , " + (pixel+x0));
                    System.out.println("pixelPos (interpolated): " + pixelPos.y + " , " + pixelPos.x);
                    System.out.println("pixelTestPos (interpolated) (lin,pix) -> (xyz) -> (phi,lam) -> (lin,pix): " + pixelTestPos.y + " , " + pixelTestPos.x);

                    System.out.println("geoPos (getGeoPos) = " + geoPos.lat + " , " + geoPos.lon);
                    System.out.println("geoPos (lp2xyz) = " + testGeo.lat +  " , " + testGeo.lon);

                    System.out.println("--------------");

                    // Testing Block: Info per pixel [stop]

                    // get correspoinding height in DEM SYSTEM!!!
                    height = elevationMatrix.get(line, pixel);

                    // - i already have (l,p) counters and the correspoding height
                    // - however most likely the input DEM is constructed incorrectly!

                    // convert to xyz
                    GeoUtils.geo2xyz(geoPos.lat, geoPos.lon, height, demXYZ, GeoUtils.EarthModel.WGS84);

                    // get radar master time for this point : this is for DEM assisted coregistration
                    masterRadarPointTime = xyz2t(new Point3d(demXYZ), masterMetadata, masterOrbit);

                    // get master timing
                    masterRadarPointLP.x = tr2pix(masterRadarPointTime.x, masterMetadata);
                    masterRadarPointLP.y = ta2line(masterRadarPointTime.y, masterMetadata);

//                    System.out.println("masterRadarPointLP = " + masterRadarPointLP.y + " , " + masterRadarPointLP.x);
//                    System.out.println("--------------");

                    // compute topo phase
                    final Point3d masterXYZPoint = lp2xyz(masterRadarPointLP.y, masterRadarPointLP.x, masterMetadata, masterOrbit);

                    // get radar slave time for this point
                    Point2d slaveRadarPointTimeTopo = xyz2t(new Point3d(demXYZ), slaveMetadata, slaveOrbit);

                    // get slave timing
                    slaveRadarPointLP.x = tr2pix(slaveRadarPointTimeTopo.x, masterMetadata);
                    slaveRadarPointLP.y = ta2line(slaveRadarPointTimeTopo.y, masterMetadata);

                    final Point2d slaveRadarPointTimeFlat = xyz2t(masterXYZPoint, slaveMetadata, slaveOrbit);

                    topographicPhase.put(line, pixel, (float) (slaveMinPi4divLam *
                            (slaveRadarPointTimeFlat.x - slaveRadarPointTimeTopo.x)));

                }
            }

            ComplexFloatMatrix cplxReferencePhase;
            cplxReferencePhase = new ComplexFloatMatrix(MatrixFunctions.cos(topographicPhase),
                    MatrixFunctions.sin(topographicPhase));

            // dump the reference phase instead of SRD interferogram
            srpIfg.copy(cplxReferencePhase);
            //  srpIfg.muli(cplxReferencePhase.conji());

            for (Band targetBand : targetProduct.getBands()) {

                String targetBandUnit = targetBand.getUnit();

                final Tile targetTile = targetTileMap.get(targetBand);

                // all bands except for virtual ones
                if (targetBandUnit.contains(Unit.REAL)) {

                    //  dataArray = topographicPhaseTemp1.transpose().toArray();
                    dataArray = srpIfg.real().toArray();
                    System.out.println("dataArray.getClass() = " + dataArray.length);

                    targetTile.setRawSamples(ProductData.createInstance(dataArray));

                } else if (targetBandUnit.contains(Unit.IMAGINARY)) {

                    // dataArray = topographicPhaseTemp2.toArray();
                    dataArray = srpIfg.imag().toArray();
                    System.out.println("dataArray.getClass() = " + dataArray.length);

                    targetTile.setRawSamples(ProductData.createInstance(dataArray));
                }
            }


        } catch (Exception e) {
            OperatorUtils.catchOperatorException(getId(), e);
        } finally {
            threadCounter.increment();
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
            super(ComplexIfgSRDOp.class);
        }
    }

    private class MetadataDoris {

        public double radar_wavelength;

        public double PRF;
        public double t_azi1;

        public double t_range1;
        public double rsr2x;

        public Point2d approxRadarCentreOriginal; // use PixelPos as double!
        public GeoPos approxGeoCentreOriginal;
        public Point3d approxXYZCentreOriginal;

        private MetadataDoris() {
        }

        private MetadataDoris(MetadataElement element) {

            // meters
            radar_wavelength = (Constants.lightSpeed / Math.pow(10,6)) / element.getAttributeDouble(AbstractMetadata.radar_frequency);

            System.out.println("radar_wavelength = " + radar_wavelength);

            //
            PRF = element.getAttributeDouble(AbstractMetadata.pulse_repetition_frequency);
            ProductData.UTC t_azi1_UTC = element.getAttributeUTC(AbstractMetadata.first_line_time);

            System.out.println("t_azi1_UTC = " + t_azi1_UTC);

            // work with seconds of the day!
            t_azi1 = (t_azi1_UTC.getMJD() - (int)t_azi1_UTC.getMJD())*24*3600;

            System.out.println("t_azi1 = " + t_azi1);

            // 2 times range sampling rate [HZ]
            rsr2x = element.getAttributeDouble(AbstractMetadata.range_sampling_rate) * Math.pow(10,6) * 2;

            System.out.println("rsr2x = " + rsr2x);

            // one way time to first range pixels [sec]
            t_range1 = element.getAttributeDouble(AbstractMetadata.slant_range_to_first_pixel)/Constants.lightSpeed;

            System.out.println("t_range1 = " + t_range1);

            approxRadarCentreOriginal = new Point2d();

            approxRadarCentreOriginal.x = element.getAttributeDouble(AbstractMetadata.num_samples_per_line) / 2.0d;  // x direction is range!
            approxRadarCentreOriginal.y = element.getAttributeDouble(AbstractMetadata.num_output_lines) / 2.0d;  // y direction is azimuth

            approxGeoCentreOriginal = new GeoPos();

            System.out.println("approxGeoCentreOriginal = " + approxGeoCentreOriginal);
            System.out.println("element.getAttributeDouble(AbstractMetadata.first_near_lat = " + element.getAttributeDouble(AbstractMetadata.first_near_lat));

            // TODO: replace computation of the centre using getGeoPos()
            // simple averaging of the corners
            approxGeoCentreOriginal.lat = (float) ((element.getAttributeDouble(AbstractMetadata.first_near_lat) +
                    element.getAttributeDouble(AbstractMetadata.first_far_lat) +
                    element.getAttributeDouble(AbstractMetadata.last_near_lat) +
                    element.getAttributeDouble(AbstractMetadata.last_far_lat)) / 4);

            approxGeoCentreOriginal.lon = (float) ((element.getAttributeDouble(AbstractMetadata.first_near_long) +
                    element.getAttributeDouble(AbstractMetadata.first_far_long) +
                    element.getAttributeDouble(AbstractMetadata.last_near_long) +
                    element.getAttributeDouble(AbstractMetadata.last_far_long)) / 4);


            double[] xyz = new double[3];

            GeoUtils.geo2xyz(approxGeoCentreOriginal, xyz);

            approxXYZCentreOriginal = new Point3d();
            approxXYZCentreOriginal.x = xyz[0];
            approxXYZCentreOriginal.y = xyz[1];
            approxXYZCentreOriginal.z = xyz[2];

        }

    }

    private final class OrbitsDoris {

        public DoubleMatrix time;
        public DoubleMatrix data_X;
        public DoubleMatrix data_Y;
        public DoubleMatrix data_Z;
        public DoubleMatrix coeff_X;
        public DoubleMatrix coeff_Y;
        public DoubleMatrix coeff_Z;
        public int poly_degree;

        private OrbitsDoris() {
        }

        public void setOrbit(MetadataElement nestMetadataElement) {

            final AbstractMetadata.OrbitStateVector[] orbitStateVectors = AbstractMetadata.getOrbitStateVectors(nestMetadataElement);

            final int orbitStateVectorsElements = orbitStateVectors.length;

            time = new DoubleMatrix(orbitStateVectorsElements);
            data_X = new DoubleMatrix(orbitStateVectorsElements);
            data_Y = new DoubleMatrix(orbitStateVectorsElements);
            data_Z = new DoubleMatrix(orbitStateVectorsElements);
            coeff_X = new DoubleMatrix(orbitStateVectorsElements);
            coeff_Y = new DoubleMatrix(orbitStateVectorsElements);
            coeff_Z = new DoubleMatrix(orbitStateVectorsElements);

            for (int i = 0; i < orbitStateVectorsElements; i++) {
                // convert time to seconds of the acquisition day
                time.put(i, (orbitStateVectors[i].time_mjd - (int)orbitStateVectors[i].time_mjd) * (24*3600)); // Modified Julian Day 2000 (MJD2000)
                data_X.put(i, orbitStateVectors[i].x_pos);
                data_Y.put(i, orbitStateVectors[i].y_pos);
                data_Z.put(i, orbitStateVectors[i].z_pos);
            }

            // compute coefficients of orbit interpolator
            final int polyDegree = 3; //HARDCODED because of how AbstractedMetadata Handles orbits

            coeff_X = polyFit(time, data_X, polyDegree);
            coeff_Y = polyFit(time, data_Y, polyDegree);
            coeff_Z = polyFit(time, data_Z, polyDegree);
            poly_degree = polyDegree;

        }

    }

}
