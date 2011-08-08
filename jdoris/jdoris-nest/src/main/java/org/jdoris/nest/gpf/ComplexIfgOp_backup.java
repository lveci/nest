package org.jdoris.nest.gpf;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.ProductUtils;
import org.esa.nest.dataio.ReaderUtils;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.gpf.OperatorUtils;
import org.esa.nest.util.Constants;
import org.esa.nest.util.GeoUtils;
import org.jblas.*;

import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import java.awt.*;
import java.awt.image.RenderedImage;
import java.util.HashMap;
import java.util.Map;


@OperatorMetadata(alias = "ComplexIfg",
        category = "InSAR Products",
        description = "Compute interferograms from stack of coregistered images : JBLAS implementation", internal = false)
public class ComplexIfgOp_backup extends Operator {

    @SourceProduct
    private Product sourceProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter( valueSet = {"1","2","3", "4", "5", "6", "7", "8"},
                description = "Order of 'Flat earth phase' polynomial",
                defaultValue = "5",
                label="Degree of \"Flat Earth\" polynomial")
    private int srpPolynomialDegree = 5;

    @Parameter(valueSet = {"301", "401", "501", "601", "701", "801", "901", "1001"},
            description = "Number of points for the 'flat earth phase' polynomial estimation",
            defaultValue = "501",
            label="Number of 'Flat earth' estimation points")
    private int srpNumberPoints = 501;


    @Parameter(valueSet = {"1", "2", "3", "4", "5"},
            description = "Degree of orbit (polynomial) interpolator",
            defaultValue = "3",
            label="Orbit interpolation degree")
    private int orbitPolynomialDegree = 3;
//
//        @Parameter(description = "Orbit interpolation method", valueSet = {"polynomial"},
//                defaultValue = "5",
//                label="SRP Polynomial Order")
//        private int srpPolynomialDegree = 5;

    private Band masterBand1 = null;
    private Band masterBand2 = null;

    private final Map<Band, Band> slaveRasterMap = new HashMap<Band, Band>(10);

    // see comment in finalize{} block of computeTileStack
    private int totalTileCount;
    private ThreadSafeCounter threadCounter = new ThreadSafeCounter();
    private int sourceImageWidth;
    private int sourceImageHeight;

    // Metadata
    private MetadataElement masterRoot;
    private MetadataElement slaveRoot;
    private MetadataDoris masterMetadata;
    private MetadataDoris slaveMetadata;
    private OrbitsDoris masterOrbit;
    private OrbitsDoris slaveOrbit;

    private DoubleMatrix flatEarthPolyCoefs;

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

            masterBand1 = sourceProduct.getBandAt(0);
            if (masterBand1.getUnit() != null && masterBand1.getUnit().equals(Unit.REAL) && sourceProduct.getNumBands() > 1) {
                masterBand2 = sourceProduct.getBandAt(1);
            }

            checkUserInput();
            getMetadata();
            getSourceImageDimension();

            // getSourceImageGeocodings();

            estimateFlatEarthPolynomial();

            // updateTargetProductMetadata();

            // updateTargetProductGeocoding();

            createTargetProduct();

        } catch (Exception e) {
            throw new OperatorException(e);
        }
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
        // check for the logic in input paramaters
    }

    private void estimateFlatEarthPolynomial() {

        // estimation window : this works only for NEST "crop" logic
        int minLine = 0;
        int maxLine = sourceImageHeight;
        int minPixel = 0;
        int maxPixel = sourceImageWidth;

        Rectangle rectangle = new Rectangle();
        rectangle.setSize(maxPixel, maxLine);

        //        int srpPolynomialDegree = 5; // for flat earth phase
        int numberOfCoefficients = numberOfCoefficients(srpPolynomialDegree);

        double[][] position = distributePoints(srpNumberPoints, rectangle);

        // setup observation and design matrix
        DoubleMatrix y = new DoubleMatrix(srpNumberPoints);
        DoubleMatrix A = new DoubleMatrix(srpNumberPoints, numberOfCoefficients);

        double masterMinPi4divLam = (-4 * Math.PI * Constants.lightSpeed) / masterMetadata.radar_wavelength;
        double slaveMinPi4divLam = (-4 * Math.PI * Constants.lightSpeed) / slaveMetadata.radar_wavelength;

        // Loop throu a vector or distributedPoints()
        for (int i = 0; i < srpNumberPoints; ++i) {

            double line = position[i][0];
            double pixel = position[i][1];

            // compute azimuth/range time for this pixel
            final double masterTimeRange = pix2tr(pixel, masterMetadata);

            // compute xyz of this point : master
            Point3d xyzMaster = lp2xyz(line, pixel, masterMetadata, masterOrbit);

            final Point2d slaveTimeVector = xyz2t(xyzMaster, slaveMetadata, slaveOrbit);
            final double slaveTimeRange = slaveTimeVector.x;

            // observation vector
            y.put(i, (masterMinPi4divLam * masterTimeRange) - (slaveMinPi4divLam * slaveTimeRange));

            // set up a system of equations
            // ______Order unknowns: A00 A10 A01 A20 A11 A02 A30 A21 A12 A03 for degree=3______
            double posL = normalize(line, minLine, maxLine);
            double posP = normalize(pixel, minPixel, maxPixel);

            int index = 0;

            for (int j = 0; j <= srpPolynomialDegree; j++) {
                for (int k = 0; k <= j; k++) {
//                    System.out.println("A[" + i + "," + index + "]: "
//                            + Math.pow(posL, (float) (j - k)) * Math.pow(posP, (float) k));
                    A.put(i, index, (Math.pow(posL, (double) (j - k)) * Math.pow(posP, (double) k)));
                    index++;
                }
            }
        }

        // Fit polynomial through computed vector of phases
        DoubleMatrix Atranspose = A.transpose();
        DoubleMatrix N = Atranspose.mmul(A);
        DoubleMatrix rhs = Atranspose.mmul(y);

        // TODO: validate Cholesky decomposition of JBLAS: see how it is in polyfit and reuse!

        // this should be the coefficient of the reference phase
        flatEarthPolyCoefs = Solve.solve(N, rhs);

/*
        System.out.println("*******************************************************************");
        System.out.println("_Start_flat_earth");
        System.out.println("*******************************************************************");
        System.out.println("Degree_flat:" + polyDegree);
        System.out.println("Estimated_coefficients_flatearth:");
        int coeffLine = 0;
        int coeffPixel = 0;
        for (int i = 0; i < numberOfCoefficients; i++) {
            if (flatEarthPolyCoefs.get(i, 0) < 0.) {
                System.out.print(flatEarthPolyCoefs.get(i, 0));
            } else {
                System.out.print(" " + flatEarthPolyCoefs.get(i, 0));
            }

            System.out.print(" \t" + coeffLine + " " + coeffPixel + "\n");
            coeffLine--;
            coeffPixel++;
            if (coeffLine == -1) {
                coeffLine = coeffPixel;
                coeffPixel = 0;
            }
        }
        System.out.println("*******************************************************************");
        System.out.println("_End_flat_earth");
        System.out.println("*******************************************************************");
*/

        // TODO: test inverse : when cholesky is finished
        //  // ______Test inverse______
        //  for (i=0; i<Qx_hat.lines(); i++)
        //    for (j=0; j<i; j++)
        //      Qx_hat(j,i) = Qx_hat(i,j);// repair Qx_hat
        //  const real8 maxdev = max(abs(N*Qx_hat-eye(real8(Qx_hat.lines()))));
        //  INFO << "flatearth: max(abs(N*inv(N)-I)) = " << maxdev;
        //  INFO.print();
        //  if (maxdev > .01)
        //    {
        //    ERROR << "Deviation too large. Decrease degree or number of points?";
        //    PRINT_ERROR(ERROR.get_str())
        //    throw(some_error);
        //    }
        //  else if (maxdev > .001)
        //    {
        //    WARNING << "Deviation quite large. Decrease degree or number of points?";
        //    WARNING.print();
        //    }
        //  else
        //    {
        //    INFO.print("Deviation is OK.");
        //    }
        //          // ______Some other stuff, scale is ok______
        //          matrix<real8> y_hat           = A * rhs;
        //          matrix<real8> e_hat           = y - y_hat;
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
        double solution = 0;
        for (iter = 0; iter <= MAXITER; ++iter) {
            Point3d satellitePosition = getSatelliteXYZ(timeAzimuth, orbit);
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
        final Point3d satellitePosition = getSatelliteXYZ(timeAzimuth,orbit);
        delta.x = position.x - satellitePosition.x;
        delta.y = position.y - satellitePosition.y;
        delta.z = position.z - satellitePosition.z;
        double timeRange = Math.sqrt(Math.pow(delta.x, 2) + Math.pow(delta.y, 2) + Math.pow(delta.z, 2)) / SOL;

        returnVector.y = timeAzimuth;
        returnVector.x = timeRange;

//        System.out.println("xyz2t solution: " + returnVector);

        return returnVector;

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

        // allocate matrices
//        DoubleMatrix ellipsoidPositionSolution = DoubleMatrix.zeros(3,1);
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
//                System.out.println("INFO: ellipsoidPosition (converged) = " + ellipsoidPosition);
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
//        System.out.println("Degree of orbit interpolating polynomial: " + polyDegree);
//        System.out.println("Number of unknowns: " + numOfUnknowns);
//        System.out.println("Number of data points (orbit): " + numOfPoints);
        if (numOfPoints < numOfUnknowns) {
            throw new OperatorException("Number of points is smaller than parameters solved for.");
        }

        // Setup system of equation
//        System.out.println("Setting up linear system of equations");
        DoubleMatrix A = new DoubleMatrix(numOfPoints, numOfUnknowns);// designmatrix
        for (int j = 0; j <= polyDegree; j++) {
            DoubleMatrix normPostingTemp = normPosting.dup();
            normPostingTemp = matrixPower(normPostingTemp, (double) j);
            A.putColumn(j, normPostingTemp);
        }

//        System.out.println("Solving lin. system of equations with Cholesky.");
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

//        System.out.println("polyFit orbit: max(abs(N*inv(N)-I)) = " + maxDeviation.get(1, 1));

//        // ___ report max error... (seems sometimes this can be extremely large) ___
//        if (maxDeviation.get(1, 1) > 1e-6) {
//            System.out.println("polyfit orbit interpolation unstable!");
//        }

        // work out residuals
        DoubleMatrix y_hat = A.mmul(x);
        DoubleMatrix e_hat = y.sub(y_hat);

        DoubleMatrix e_hat_abs = absMatrix(e_hat);

        // TODO: absMatrix(e_hat_abs).max() there is a simpleBlas function that implements this!
        // 0.05 is already 1 wavelength! (?)
        if (absMatrix(e_hat_abs).max() > 0.02) {
            System.out.println("WARNING: Max. approximation error at datapoints (x,y,or z?): " + absMatrix(e_hat).max() + "m");
        }
//        else {
//            System.out.println("Max. approximation error at datapoints (x,y,or z?): " + absMatrix(e_hat).max() + "m");
//        }

//        System.out.println("REPORTING POLYFIT LEAST SQUARES ERRORS");
//        System.out.println(" time \t\t\t y \t\t\t yhat  \t\t\t ehat");
//        for (int i = 0; i < numOfPoints; i++) {
//            System.out.println(" " + posting.get(i) + "\t" + y.get(i) + "\t" + y_hat.get(i) + "\t" + e_hat.get(i));
//        }

        for (int i = 0; i < numOfPoints - 1; i++) {
            // ___ check if dt is constant, not necessary for me, but may ___
            // ___ signal error in header data of SLC image ___
            double dt = posting.get(i + 1) - posting.get(i);
//            System.out.println("Time step between point " + i + 1 + " and " + i + "= " + dt);

            if (Math.abs(dt - (posting.get(1) - posting.get(0))) > 0.001)// 1ms of difference we allow...
                System.out.println("WARNING: Orbit: data does not have equidistant time interval?");
        }

        return x;
    } // END polyfit

    private DoubleMatrix absMatrix(DoubleMatrix matrix) {
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
//        return 0;  //To change body of created methods use File | Settings | File Templates.
        return (int) (0.5 * (Math.pow(degree + 1, 2) + degree + 1));
    }

    private int degreeFromCoefficients(int numOfCoefficients) {
//        return 0;  //To change body of created methods use File | Settings | File Templates.
        // TODO: validate this?!
        return (int) (0.5*(-1 + (int)(Math.sqrt(1+8*numOfCoefficients))))-1;
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

        OperatorUtils.copyProductNodes(sourceProduct, targetProduct);

//        targetProduct.setPreferredTileSize(sourceProduct.getSceneRasterWidth(), 100);
        targetProduct.setPreferredTileSize(500, 500); // polyval optimized for the equal size tiles!!!


        final Dimension tileSize = targetProduct.getPreferredTileSize();
        final int rasterHeight = targetProduct.getSceneRasterHeight();
        final int rasterWidth = targetProduct.getSceneRasterWidth();

//        int tileCountX = MathUtils.ceilInt(rasterWidth / (double) tileSize.width);
//        int tileCountY = MathUtils.ceilInt(rasterHeight / (double) tileSize.height);
//        totalTileCount = tileCountX * tileCountY;
//        System.out.println("TotalTileCount: [" + totalTileCount + "]");

//        // initialization of complex matrices
//        cplxMatrixMaster = new ComplexFloatMatrix(tileSize.width, tileSize.height);
//        cplxMatrixSlave = new ComplexFloatMatrix(tileSize.width, tileSize.height);

        int cnt = 1;
        int inc = 2;

        // band names
        String iBandName;
        String qBandName;

        // construct target product only for interferogram bands: totalNumOfBands - 2 (i/q for master)
        // assume that master bands are first 2 bands in the stack!
        for (int i = 0; i < totalNumOfBands; i += inc) {

            // TODO: coordinate this naming with metadata information
            final Band srcBandI = sourceProduct.getBandAt(i);
            final Band srcBandQ = sourceProduct.getBandAt(i + 1);
            if (srcBandI != masterBand1 && srcBandQ != masterBand2) {

                // TODO: beautify names of ifg bands
                if (srcBandI.getUnit().equals(Unit.REAL) && srcBandQ.getUnit().equals(Unit.IMAGINARY)) {

                    iBandName = "i_ifg" + cnt + "_" +
                            masterBand1.getName() + "_" +
                            srcBandI.getName();

                    final Band targetBandI = targetProduct.addBand(iBandName, ProductData.TYPE_FLOAT32);
                    ProductUtils.copyRasterDataNodeProperties(srcBandI, targetBandI);

//                    sourceRasterMap.put(targetBandI, srcBandI);

                    qBandName = "q_ifg" + cnt + "_" +
                            masterBand2.getName() + "_" +
                            srcBandQ.getName();

                    final Band targetBandQ = targetProduct.addBand(qBandName, ProductData.TYPE_FLOAT32);
                    ProductUtils.copyRasterDataNodeProperties(srcBandQ, targetBandQ);

//                    sourceRasterMap.put(targetBandQ, srcBandQ);

                    String suffix = "";
                    if (srcBandI != masterBand1) {
                        suffix = "_ifg" + cnt++;
                    }

                    slaveRasterMap.put(srcBandI, srcBandQ);
                    ReaderUtils.createVirtualIntensityBand(targetProduct, targetBandI, targetBandQ, suffix);
                    ReaderUtils.createVirtualPhaseBand(targetProduct, targetBandI, targetBandQ, suffix);

                }
            }
        }
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
//            System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

            ComplexFloatMatrix cplxMatrixMaster = null;
            ComplexFloatMatrix cplxMatrixSlave = null;
            RenderedImage srcImage;
            float[] dataArray;

//            pm.beginTask("Computation of interferogram", targetProduct.getNumBands());

            // these are not very optimal loops: redo the looping to a single for Band loop with using HashMaps
            for (Band sourceBand : sourceProduct.getBands()) {

                String sourceBandUnit = sourceBand.getUnit();

                if (sourceBand == masterBand1) {
                } else if (sourceBand == masterBand2) {

                    Tile masterRasterI = getSourceTile(masterBand1, targetRectangle);
                    srcImage = masterRasterI.getRasterDataNode().getSourceImage();
                    dataArray = srcImage.getData(targetRectangle).getSamples(x0, y0, w, h, 0, (float[]) null);

                    FloatMatrix dataRealMatrix = new FloatMatrix(masterRasterI.getHeight(), masterRasterI.getWidth(), dataArray);

                    Tile masterRasterQ = getSourceTile(masterBand2, targetRectangle);
                    srcImage = masterRasterQ.getRasterDataNode().getSourceImage();
                    dataArray = srcImage.getData(targetRectangle).getSamples(x0, y0, w, h, 0, (float[]) null);

//                    FloatMatrix dataImagMatrix = new FloatMatrix(masterRasterQ.getWidth(), masterRasterQ.getHeight(), dataArray);
                    FloatMatrix dataImagMatrix = new FloatMatrix(masterRasterQ.getHeight(), masterRasterQ.getWidth(), dataArray);

                    cplxMatrixMaster = new ComplexFloatMatrix(dataRealMatrix, dataImagMatrix);

                } else if (sourceBandUnit != null && sourceBandUnit.contains(Unit.REAL)) {

                    Tile slaveRasterI = getSourceTile(sourceBand, targetRectangle);
                    srcImage = slaveRasterI.getRasterDataNode().getSourceImage();
                    dataArray = srcImage.getData(targetRectangle).getSamples(x0, y0, w, h, 0, (float[]) null);

//                    FloatMatrix dataRealMatrix = new FloatMatrix(slaveRasterI.getWidth(), slaveRasterI.getHeight(), dataArray);
                    FloatMatrix dataRealMatrix = new FloatMatrix(slaveRasterI.getHeight(), slaveRasterI.getWidth(), dataArray);

                    Tile slaveRasterQ = getSourceTile(slaveRasterMap.get(sourceBand), targetRectangle, pm);
                    srcImage = slaveRasterQ.getRasterDataNode().getSourceImage();
                    dataArray = srcImage.getData(targetRectangle).getSamples(x0, y0, w, h, 0, (float[]) null);

                    FloatMatrix dataImagMatrix = new FloatMatrix(slaveRasterQ.getHeight(), slaveRasterQ.getWidth(), dataArray);

                    cplxMatrixSlave = new ComplexFloatMatrix(dataRealMatrix, dataImagMatrix);

                }
            }

            // TODO: integrate subtraction of Reference Phase
            ComplexFloatMatrix cplxIfg = cplxMatrixMaster.muli(cplxMatrixSlave.conji());

            // if flat earth phase flag is on
            // ------------------------------------------------

            // normalize pixel :: range_axis
            DoubleMatrix rangeAxisNormalized;
            rangeAxisNormalized = DoubleMatrix.linspace(x0, x0 + w - 1, cplxIfg.columns);
            rangeAxisNormalized.subi(0.5 * (1 + sourceImageWidth));
            rangeAxisNormalized.divi(0.25 * (sourceImageWidth-1));

            DoubleMatrix azimuthAxisNormalized;
            azimuthAxisNormalized = DoubleMatrix.linspace(y0, y0 + h - 1, cplxIfg.rows);
            azimuthAxisNormalized.subi(0.5 * (1 + sourceImageHeight));
            azimuthAxisNormalized.divi(0.25 * (sourceImageHeight - 1));

            DoubleMatrix realReferencePhase = polyValOnGrid(azimuthAxisNormalized, rangeAxisNormalized,
                    flatEarthPolyCoefs, degreeFromCoefficients(flatEarthPolyCoefs.length));

            FloatMatrix realReferencePhaseFlt = MatrixFunctions.doubleToFloat(realReferencePhase);
            ComplexFloatMatrix cplxReferencePhase = new ComplexFloatMatrix(MatrixFunctions.cos(realReferencePhaseFlt),
                    MatrixFunctions.sin(realReferencePhaseFlt));

//            cplxIfg = cplxIfg.muli(cplxReferencePhase.transpose().conji());
            cplxIfg.muli(cplxReferencePhase.transpose().conji());

            for (Band targetBand : targetProduct.getBands()) {

                String targetBandUnit = targetBand.getUnit();

                final Tile targetTile = targetTileMap.get(targetBand);

                // all bands except for virtual ones
                if (targetBandUnit.contains(Unit.REAL)) {

                    dataArray = cplxIfg.real().toArray();
                    targetTile.setRawSamples(ProductData.createInstance(dataArray));

                } else if (targetBandUnit.contains(Unit.IMAGINARY)) {

                    dataArray = cplxIfg.imag().toArray();
                    targetTile.setRawSamples(ProductData.createInstance(dataArray));

                }

            }

        } catch (Throwable e) {

            OperatorUtils.catchOperatorException(getId(), e);

        }
    }

    public DoubleMatrix polyValOnGrid(DoubleMatrix x, DoubleMatrix y, final DoubleMatrix coeff, int degreee) {

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

//        if (x.length > y.length) {
//            System.out.println("WARNING: polyal function, x larger than y, while optimized for y larger x");
//        }

        if (degreee == -1) {
            degreee = degreeFromCoefficients(coeff.length);
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
                final int STARTCOEFF = degreeFromCoefficients(STARTDEGREE - 1);   // 5-> 21 6->28 7->36 etc.
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
            super(ComplexIfgOp_backup.class);
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
//            System.out.println("radar_wavelength = " + radar_wavelength);

            //
            PRF = element.getAttributeDouble(AbstractMetadata.pulse_repetition_frequency);
            ProductData.UTC t_azi1_UTC = element.getAttributeUTC(AbstractMetadata.first_line_time);
//            System.out.println("t_azi1_UTC = " + t_azi1_UTC);

            // work with seconds of the day!
            t_azi1 = (t_azi1_UTC.getMJD() - (int)t_azi1_UTC.getMJD())*24*3600;
//            System.out.println("t_azi1 = " + t_azi1);

            // 2 times range sampling rate [HZ]
            rsr2x = element.getAttributeDouble(AbstractMetadata.range_sampling_rate) * Math.pow(10,6) * 2;
//            System.out.println("rsr2x = " + rsr2x);

            // one way time to first range pixels [sec]
            t_range1 = element.getAttributeDouble(AbstractMetadata.slant_range_to_first_pixel)/Constants.lightSpeed;
//            System.out.println("t_range1 = " + t_range1);

            approxRadarCentreOriginal = new Point2d();

            approxRadarCentreOriginal.x = element.getAttributeDouble(AbstractMetadata.num_samples_per_line) / 2.0d;  // x direction is range!
            approxRadarCentreOriginal.y = element.getAttributeDouble(AbstractMetadata.num_output_lines) / 2.0d;  // y direction is azimuth

            approxGeoCentreOriginal = new GeoPos();

//            System.out.println("approxGeoCentreOriginal = " + approxGeoCentreOriginal);
//            System.out.println("element.getAttributeDouble(AbstractMetadata.first_near_lat = " + element.getAttributeDouble(AbstractMetadata.first_near_lat));

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

//            // compute coefficients of orbit interpolator
//            final int polyDegree = 3; //HARDCODED because of how AbstractedMetadata Handles orbits

            coeff_X = polyFit(time, data_X, orbitPolynomialDegree);
            coeff_Y = polyFit(time, data_Y, orbitPolynomialDegree);
            coeff_Z = polyFit(time, data_Z, orbitPolynomialDegree);
            poly_degree = orbitPolynomialDegree;

        }

    }
}
