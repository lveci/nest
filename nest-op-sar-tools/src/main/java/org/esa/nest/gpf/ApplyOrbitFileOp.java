/*
 * Copyright (C) 2002-2007 by ?
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.esa.nest.gpf;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.dataio.envisat.EnvisatOrbitReader;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.gpf.OperatorUtils;
import org.esa.nest.util.Settings;
import org.esa.nest.util.GeoUtils;
import org.esa.nest.util.Constants;
import org.esa.nest.dataio.OrbitalDataRecordReader;
import org.esa.nest.dataio.PrareOrbitReader;

import java.util.Date;
import java.io.File;
import java.io.IOException;

import Jama.Matrix;

/**
 * This operator applies orbit file to a given product.
 *
 * The following major processing steps are implemented:
 *
 * 1. Get orbit file with valid time period and user specified orbit file type.
 * 2. Get the old tie point grid of the source image: latitude, longitude, slant range time and incidence angle.
 * 3. Repeat the following steps for each new tie point in the new tie point grid:
 *    1)  Get the range line index y for the tie point;
 *    2)  Get zero Doppler time t for the range line.
 *    3)  Compute satellite position and velocity for the zero Doppler time t using cubic interpolation. (dorisReader)
 *    4)  Get sample number x (index in the range line).
 *    5)  Get slant range time for pixel (x, y) from the old slant range time tie point grid.
 *    6)  Get incidence angle for pixel (x, y) from the old incidence angle tie point grid.
 *    7)  Get latitude for pixel (x, y) from the old latitude tie point grid.
 *    8)  Get longitude for pixel (x, y) from the old longitude tie point grid.
 *    9)  Convert (latitude, longitude, h = 0) to global Cartesian coordinate (x0, y0, z0).
 *    10) Solve Range equation, Doppler equation and Earth equation system for accurate (x, y, z) using Newton’s
 *        method with (x0, y0, z0) as initial point.
 *    11) Convert (x, y, z) back to (latitude, longitude, h).
 *    12) Save the new latitude and longitude for current tie point.
 * 4. Create new geocoding with the newly computed latitude and longitude tie points.
 * 5. Update orbit state vectors in the metadata:
 *    1) Get zero Doppler time for each orbit state vector in the metadata of the source image.
 *    2) Compute new orbit state vector for the zero Doppler time using cubic interpolation.
 *    3) Save the new orbit state vector in the target product.
 */

@OperatorMetadata(alias="Apply-Orbit-File", description="Apply orbit file")
public final class ApplyOrbitFileOp extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(valueSet = {DORIS_POR, DORIS_VOR, DELFT_ENVISAT, DELFT_ERS_1, DELFT_ERS_2, PRARE_ERS_1, PRARE_ERS_2},
            defaultValue = DORIS_VOR, label="Orbit Type")
    private String orbitType = DORIS_VOR;

    private MetadataElement absRoot;
    private EnvisatOrbitReader dorisReader;
    private OrbitalDataRecordReader delftReader;
    private PrareOrbitReader prareReader;
    private File orbitFile;

    private int absOrbit;
    private int sourceImageWidth;
    private int sourceImageHeight;
    private int targetTiePointGridHeight;
    private int targetTiePointGridWidth;
    private int subSamplingX;
    private int subSamplingY;

    private double firstLineUTC;
    private double lastLineUTC;
    private double lineTimeInterval;

    private TiePointGrid slantRangeTime;
    private TiePointGrid incidenceAngle;
    private TiePointGrid latitude;
    private TiePointGrid longitude;

    public static final String DORIS_POR = "DORIS_POR";
    public static final String DORIS_VOR = "DORIS_VOR";
    public static final String DELFT_ENVISAT = "DELFT_ENVISAT";
    public static final String DELFT_ERS_1 = "DELFT_ERS_1";
    public static final String DELFT_ERS_2 = "DELFT_ERS_2";
    public static final String PRARE_ERS_1 = "PRARE_ERS_1";
    public static final String PRARE_ERS_2 = "PRARE_ERS_2";

    /**
     * Default constructor. The graph processing framework
     * requires that an operator has a default constructor.
     */
    public ApplyOrbitFileOp() {
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
            absRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct);

            if (orbitType.contains("DORIS")) {
                getDorisOrbitFile();
            } else if (orbitType.contains("DELFT")) {
                getDelftOrbitFile();
            } else if (orbitType.contains("PRARE")) {
                getPrareOrbitFile();
            }

            getTiePointGrid();

            getSourceImageDimension();

            getFirstLastLineUTC();

            createTargetProduct();

            updateTargetProductGEOCoding();

            updateOrbitStateVectors();

        } catch(Exception e) {
            throw new OperatorException(e.getMessage());
        }
    }

    /**
     * Called by the framework in order to compute a tile for the given target band.
     * <p>The default implementation throws a runtime exception with the message "not implemented".</p>
     *
     * @param targetBand The target band.
     * @param targetTile The current tile associated with the target band to be computed.
     * @param pm         A progress monitor which should be used to determine computation cancelation requests.
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs during computation of the target raster.
     */
    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        // copy source data to target
        targetTile.setRawSamples(getSourceTile(sourceProduct.getBand(targetBand.getName()),
                                               targetTile.getRectangle(), pm).getRawSamples());
    }

    /**
     * Get source product tie point grids for latitude, longitude, incidence angle and slant range time.
     */
    private void getTiePointGrid() {

        latitude = OperatorUtils.getLatitude(sourceProduct);
        longitude = OperatorUtils.getLongitude(sourceProduct);
        slantRangeTime = OperatorUtils.getSlantRangeTime(sourceProduct);
        incidenceAngle = OperatorUtils.getIncidenceAngle(sourceProduct);

        targetTiePointGridWidth = latitude.getRasterWidth();
        targetTiePointGridHeight = latitude.getRasterHeight();
    }

    /**
     * Get source image dimention.
     */
    private void getSourceImageDimension() {

        sourceImageWidth = sourceProduct.getSceneRasterWidth();
        sourceImageHeight = sourceProduct.getSceneRasterHeight();
    }

    /**
     * Get the first anf last line UTC in days.
     * @throws Exception The exceptions.
     */
    private void getFirstLastLineUTC() throws Exception {

        firstLineUTC = absRoot.getAttributeUTC(AbstractMetadata.first_line_time).getMJD();
        lastLineUTC = absRoot.getAttributeUTC(AbstractMetadata.last_line_time).getMJD();
        lineTimeInterval = absRoot.getAttributeDouble(AbstractMetadata.line_time_interval) / 86400.0; // s to day
        //System.out.println((new ProductData.UTC(firstLineUTC)).toString());
        //System.out.println((new ProductData.UTC(lastLineUTC)).toString());
    }

    /**
     * Create target product.
     * @throws Exception The exception.
     */
    void createTargetProduct() throws Exception {

        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    sourceProduct.getSceneRasterWidth(),
                                    sourceProduct.getSceneRasterHeight());

        ProductUtils.copyMetadata(sourceProduct, targetProduct);

        ProductUtils.copyFlagCodings(sourceProduct, targetProduct);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        for(Band band : sourceProduct.getBands()) {
            final Band targetBand = new Band(band.getName(),
                                             band.getDataType(),
                                             band.getRasterWidth(),
                                             band.getRasterHeight());

            targetBand.setUnit(band.getUnit());
            targetProduct.addBand(targetBand);
        }

        targetProduct.setPreferredTileSize(sourceProduct.getSceneRasterWidth(), 50);
    }

    /**
     * Update target product GEOCoding. A new tie point grid is generated.
     * @throws Exception The exceptions.
     */
    private void updateTargetProductGEOCoding() throws Exception {

        final float[] targetLatTiePoints = new float[targetTiePointGridHeight*targetTiePointGridWidth];
        final float[] targetLonTiePoints = new float[targetTiePointGridHeight*targetTiePointGridWidth];
        final float[] targetIncidenceAngleTiePoints = new float[targetTiePointGridHeight*targetTiePointGridWidth];
        final float[] targetSlantRangeTimeTiePoints = new float[targetTiePointGridHeight*targetTiePointGridWidth];

        computeSubSamplingXY();

        // Create new tie point grid
        int k = 0;
        for (int r = 0; r < targetTiePointGridHeight; r++) {

            // get the zero Doppler time for the rth line
            int y = getLineIndex(r);
            double curLineUTC = computeCurrentLineUTC(y);
            //System.out.println((new ProductData.UTC(curLineUTC)).toString());
            
            // compute the satellite position and velocity for the zero Doppler time using cubic interpolation
            OrbitData data = getOrbitData(curLineUTC);

            for (int c = 0; c < targetTiePointGridWidth; c++) {

                final int x = getSampleIndex(c);
                targetIncidenceAngleTiePoints[k] = incidenceAngle.getPixelFloat((float)x, (float)y);
                targetSlantRangeTimeTiePoints[k] = slantRangeTime.getPixelFloat((float)x, (float)y);

                final double slrgTime = (double)targetSlantRangeTimeTiePoints[k] / 1000000000.0; // ns to s;
                final GeoPos geoPos = computeLatLon(x, y, slrgTime, data);
                targetLatTiePoints[k] = geoPos.lat;
                targetLonTiePoints[k] = geoPos.lon;
                k++;
            }
        }

        final TiePointGrid angleGrid = new TiePointGrid("incident_angle", targetTiePointGridWidth, targetTiePointGridHeight,
                0.0f, 0.0f, (float)subSamplingX, (float)subSamplingY, targetIncidenceAngleTiePoints);

        final TiePointGrid slrgtGrid = new TiePointGrid("slant_range_time", targetTiePointGridWidth, targetTiePointGridHeight,
                0.0f, 0.0f, (float)subSamplingX, (float)subSamplingY, targetSlantRangeTimeTiePoints);

        final TiePointGrid latGrid = new TiePointGrid("latitude", targetTiePointGridWidth, targetTiePointGridHeight,
                0.0f, 0.0f, (float)subSamplingX, (float)subSamplingY, targetLatTiePoints);

        final TiePointGrid lonGrid = new TiePointGrid("longitude", targetTiePointGridWidth, targetTiePointGridHeight,
                0.0f, 0.0f, (float)subSamplingX, (float)subSamplingY, targetLonTiePoints, TiePointGrid.DISCONT_AT_180);

        final TiePointGeoCoding tpGeoCoding = new TiePointGeoCoding(latGrid, lonGrid, Datum.WGS_84);

        targetProduct.addTiePointGrid(angleGrid);
        targetProduct.addTiePointGrid(slrgtGrid);
        targetProduct.addTiePointGrid(latGrid);
        targetProduct.addTiePointGrid(lonGrid);
        targetProduct.setGeoCoding(tpGeoCoding);
    }

    /**
     * Compute subSamplingX and subSamplingY.
     */
    private void computeSubSamplingXY() {
        subSamplingX = sourceImageWidth / (targetTiePointGridWidth - 1);
        subSamplingY = sourceImageHeight / (targetTiePointGridHeight - 1);
    }

    /**
     * Get corresponding range line index for a given row index in the new tie point grid.
     * @param rowIdx The row index in the new tie point grid.
     * @return The range line index.
     */
    private int getLineIndex(int rowIdx) {

        if (rowIdx == targetTiePointGridHeight - 1) { // last row
            return sourceImageHeight - 1;
        } else { // other rows
            return rowIdx * subSamplingY;
        }
    }

    /**
     * Get corresponding sample index for a given column index in the new tie point grid.
     * @param colIdx The column index in the new tie point grid.
     * @return The sample index.
     */
    private int getSampleIndex(int colIdx) {

        if (colIdx == targetTiePointGridWidth - 1) { // last column
            return sourceImageWidth - 1;
        } else { // other columns
            return colIdx * subSamplingX;
        }
    }

    /**
     * Compute UTC for a given range line.
     * @param y The range line index.
     * @return The UTC in days.
     */
    private double computeCurrentLineUTC(int y) {
        return firstLineUTC + y*lineTimeInterval;
    }

    /**
     * Get orbit information for given time.
     * @param utc The UTC in days.
     * @return The orbit information.
     * @throws Exception The exceptions.
     */
    private OrbitData getOrbitData(double utc) throws Exception {

        OrbitData orbitData = new OrbitData();

        if (orbitType.contains("DORIS")) {

            EnvisatOrbitReader.OrbitVector orb = dorisReader.getOrbitVector(utc);
            orbitData.xPos = orb.xPos;
            orbitData.yPos = orb.yPos;
            orbitData.zPos = orb.zPos;
            orbitData.xVel = orb.xVel;
            orbitData.yVel = orb.yVel;
            orbitData.zVel = orb.zVel;

        } else if (orbitType.contains("DELFT")) {

            OrbitalDataRecordReader.OrbitVector orb = delftReader.getOrbitVector(utc);
            orbitData.xPos = orb.xPos;
            orbitData.yPos = orb.yPos;
            orbitData.zPos = orb.zPos;
            orbitData.xVel = orb.xVel;
            orbitData.yVel = orb.yVel;
            orbitData.zVel = orb.zVel;

        } else if (orbitType.contains("PRARE")) {

            PrareOrbitReader.OrbitVector orb = prareReader.getOrbitVector(utc);
            orbitData.xPos = orb.xPos;
            orbitData.yPos = orb.yPos;
            orbitData.zPos = orb.zPos;
            orbitData.xVel = orb.xVel;
            orbitData.yVel = orb.yVel;
            orbitData.zVel = orb.zVel;
        }

        return orbitData;
    }

    /**
     * Compute accurate target geo position.
     * @param x The x coordinate of the given pixel.
     * @param y The y coordinate of the given pixel.
     * @param slrgTime The slant range time of the given pixel.
     * @param data The orbit data.
     * @return The geo position of the target.
     */
    private GeoPos computeLatLon(int x, int y, double slrgTime, OrbitData data) {

        double[] xyz = new double[3];
        final float lat = latitude.getPixelFloat((float)x, (float)y);
        final float lon = longitude.getPixelFloat((float)x, (float)y);
        final GeoPos geoPos = new GeoPos(lat, lon);

        // compute initial (x,y,z) coordinate from lat/lon
        GeoUtils.geo2xyz(geoPos, xyz);

        // compute accurate (x,y,z) coordinate using Newton's method
        computeAccurateXYZ(data, xyz, slrgTime);

        // compute (lat, lon, alt) from accurate (x,y,z) coordinate
        GeoUtils.xyz2geo(xyz, geoPos);

        return geoPos;
    }

    /**
     * Compute accurate target position for given orbit information using Newton's method.
     * @param data The orbit data.
     * @param xyz The xyz coordinate for the target.
     * @param time The slant range time in seconds.
     */
    private void computeAccurateXYZ(OrbitData data, double[] xyz, double time) {

        final double a = Constants.semiMajorAxis;
        final double b = Constants.semiMinorAxis;
        final double del = 0.001;
        final int maxIter = 10;

        Matrix X = new Matrix(3, 1);
        Matrix F = new Matrix(3, 1);
        Matrix J = new Matrix(3, 3);

        X.set(0, 0, xyz[0]);
        X.set(1, 0, xyz[1]);
        X.set(2, 0, xyz[2]);

        J.set(0, 0, data.xVel);
        J.set(0, 1, data.yVel);
        J.set(0, 2, data.zVel);

        for (int i = 0; i < maxIter; i++) {

            final double x = X.get(0,0);
            final double y = X.get(1,0);
            final double z = X.get(2,0);

            final double dx = x - data.xPos;
            final double dy = y - data.yPos;
            final double dz = z - data.zPos;

            F.set(0, 0, data.xVel*dx + data.yVel*dy + data.zVel*dz);
            F.set(1, 0, dx*dx + dy*dy + dz*dz - Math.pow(time*Constants.halfLightSpeed, 2.0));
            F.set(2, 0, x*x/(a*a) + y*y/(a*a) + z*z/(b*b) - 1);

            J.set(1, 0, 2.0*dx);
            J.set(1, 1, 2.0*dy);
            J.set(1, 2, 2.0*dz);
            J.set(2, 0, 2.0*x/(a*a));
            J.set(2, 1, 2.0*y/(a*a));
            J.set(2, 2, 2.0*z/(b*b));

            X = X.minus(J.inverse().times(F));

            if (Math.abs(F.get(0,0)) <= del && Math.abs(F.get(1,0)) <= del && Math.abs(F.get(2,0)) <= del)  {
                break;
            }
        }

        xyz[0] = X.get(0,0);
        xyz[1] = X.get(1,0);
        xyz[2] = X.get(2,0);
    }

    /**
     * Update orbit state vectors using data from the orbit file.
     * @throws Exception The exceptions.
     */
    private void updateOrbitStateVectors() throws Exception {

        // get original orbit state vectors
        MetadataElement tgtAbsRoot = AbstractMetadata.getAbstractedMetadata(targetProduct);
        AbstractMetadata.OrbitStateVector[] orbitStateVectors = AbstractMetadata.getOrbitStateVectors(tgtAbsRoot);

        // compute new orbit state vectors
        for (int i = 0; i< orbitStateVectors.length; i++) {
            double time = orbitStateVectors[i].time.getMJD();
            OrbitData orbitData = getOrbitData(time);
            orbitStateVectors[i].x_pos = orbitData.xPos; // m
            orbitStateVectors[i].y_pos = orbitData.yPos; // m
            orbitStateVectors[i].z_pos = orbitData.zPos; // m
            orbitStateVectors[i].x_vel = orbitData.xVel; // m/s
            orbitStateVectors[i].y_vel = orbitData.yVel; // m/s
            orbitStateVectors[i].z_vel = orbitData.zVel; // m/s
        }

        // save new orbit state vectors
        AbstractMetadata.setOrbitStateVectors(tgtAbsRoot, orbitStateVectors);
    }
    
    // ====================================== DORIS ORBIT FILE ===============================================

    /**
     * Get DORIS orbit file.
     * @throws IOException The exception.
     */
    private void getDorisOrbitFile() throws IOException {

        dorisReader = new EnvisatOrbitReader();
        absOrbit = absRoot.getAttributeInt(AbstractMetadata.ABS_ORBIT, 0);

        // construct path to the orbit file folder
        String orbitPath = "";
        if(orbitType.contains(DORIS_VOR)) {
            orbitPath = Settings.instance().get("OrbitFiles/dorisVOROrbitPath");
        } else if(orbitType.contains(DORIS_POR)) {
            orbitPath = Settings.instance().get("OrbitFiles/dorisPOROrbitPath");
        }

        final Date startDate = sourceProduct.getStartTime().getAsDate();
        int month = startDate.getMonth()+1;
        String folder = String.valueOf(startDate.getYear() + 1900);
        if(month < 10) {
            folder +='0';
        }
        folder += month;
        orbitPath += File.separator + folder;

        // find orbit file in the folder
        orbitFile = FindDorisOrbitFile(dorisReader, new File(orbitPath), startDate, absOrbit);

        if(orbitFile == null) {
            throw new IOException("Unable to find suitable orbit file");
        }
    }

    /**
     * Find DORIS orbit file.
     * @param dorisReader The ENVISAT oribit reader.
     * @param path The path to the orbit file.
     * @param productDate The start date of the product.
     * @param absOrbit The absolute orbit number.
     * @return The orbit file.
     */
    private static File FindDorisOrbitFile(EnvisatOrbitReader dorisReader, File path, Date productDate, int absOrbit)
            throws IOException {

        final File[] list = path.listFiles();
        if(list == null) return null;

        // loop through all orbit files in the given folder
        for(File f : list) {

            if(f.isDirectory()) {
                final File foundFile = FindDorisOrbitFile(dorisReader, f, productDate, absOrbit);
                if(foundFile != null) {
                    return foundFile;
                }
            }

            // open each orbit file
            dorisReader.readProduct(f);

            // get the start and end dates and compare them against product start date
            final Date startDate = dorisReader.getSensingStart();
            final Date stopDate = dorisReader.getSensingStop();
            if (productDate.after(startDate) && productDate.before(stopDate)) {

                // get the absolute orbit code and compare it against the orbit code in the product
                dorisReader.readOrbitData();
                //EnvisatOrbitReader.OrbitVector orb = dorisReader.getOrbitVector(0);
                //if (absOrbit == orb.absOrbit) {
                    return f;
                //}
            }
        }

        return null;
    }

    // ====================================== DELFT ORBIT FILE ===============================================
    /**
     * Get DELFT orbit file.
     * @throws IOException The exceptions.
     */
    private void getDelftOrbitFile() throws Exception {

        delftReader = new OrbitalDataRecordReader();

        // construct path to the orbit file folder
        String orbitPath = "";
        if(orbitType.contains(DELFT_ENVISAT)) {
            orbitPath = Settings.instance().get("OrbitFiles/delftEnvisatOrbitPath");
        } else if(orbitType.contains(DELFT_ERS_1)) {
            orbitPath = Settings.instance().get("OrbitFiles/delftERS1OrbitPath");
        } else if(orbitType.contains(DELFT_ERS_2)) {
            orbitPath = Settings.instance().get("OrbitFiles/delftERS2OrbitPath");
        }

        // get product start time
        final Date startDate = sourceProduct.getStartTime().getAsDate();

        // find orbit file in the folder
        orbitFile = FindDelftOrbitFile(delftReader, new File(orbitPath), startDate);

        if(orbitFile == null) {
            throw new IOException("Unable to find suitable orbit file");
        }
    }

    /**
     * Find DELFT orbit file.
     * @param delftReader The DELFT oribit reader.
     * @param path The path to the orbit file.
     * @param productDate The start date of the product.
     * @return The orbit file.
     * @throws Exception The exceptions.
     */
    private static File FindDelftOrbitFile(OrbitalDataRecordReader delftReader, File path, Date productDate)
            throws Exception  {

        final File[] list = path.listFiles();
        if(list == null) {
            return null;
        }

        // loop through all orbit files in the given folder to find arclist file, then get the arc# of the orbit file
        int arcNum = OrbitalDataRecordReader.invalidArcNumber;
        for(File f : list) {
            if (f.getName().contains("arclist")) {
                if (!f.exists()) {
                    return null;
                } else {
                    arcNum = delftReader.getArcNumber(f, productDate);
                    break;
                }
            }
        }

        if (arcNum == OrbitalDataRecordReader.invalidArcNumber) {
            return null;
        }

        String orbitFileName = path.getAbsolutePath() + File.separator + "ODR.";
        if (arcNum < 10) {
            orbitFileName += "00" + arcNum;
        } else if (arcNum < 100) {
            orbitFileName += "0" + arcNum;
        } else {
            orbitFileName += arcNum;
        }

        File orbitFile = new File(orbitFileName);
        if (!orbitFile.exists()) {
            return null;
        }

        // read content of the orbit file
        delftReader.readOrbitFile(orbitFileName);

        return orbitFile;
    }

    // ====================================== PRARE ORBIT FILE ===============================================
    /**
     * Get PRARE orbit file.
     * @throws IOException The exceptions.
     */
    private void getPrareOrbitFile() throws IOException {

        prareReader = new PrareOrbitReader();

        // construct path to the orbit file folder
        String orbitPath = "";
        if(orbitType.contains(PRARE_ERS_1)) {
            orbitPath = Settings.instance().get("OrbitFiles/prareERS1OrbitPath");
        } else if(orbitType.contains(PRARE_ERS_2)) {
            orbitPath = Settings.instance().get("OrbitFiles/prareERS2OrbitPath");
        }

        // get product start time
        // todo the startDate below is different from the start time in the metadata, why?
        final Date startDate = sourceProduct.getStartTime().getAsDate();
        String folder = String.valueOf(startDate.getYear() + 1900);
        orbitPath += File.separator + folder;

        // find orbit file in the folder
        orbitFile = FindPrareOrbitFile(prareReader, new File(orbitPath), startDate);

        if(orbitFile == null) {
            throw new IOException("Unable to find suitable orbit file");
        }
    }

    /**
     * Find PRARE orbit file.
     * @param prareReader The PRARE oribit reader.
     * @param path The path to the orbit file.
     * @param productDate The start date of the product.
     * @return The orbit file.
     * @throws IOException The exceptions.
     */
    private static File FindPrareOrbitFile(PrareOrbitReader prareReader, File path, Date productDate)
            throws IOException {

        final File[] list = path.listFiles();
        if(list == null) return null;
        final float productDateInMJD = (float)ProductData.UTC.create(productDate, 0).getMJD(); // in days

        // loop through all orbit files in the given folder
        for(File f : list) {

            // read header record of each orbit file
            prareReader.readOrbitHeader(f);

            // get the start and end dates and compare them against product start date
            final float startDateInMJD = prareReader.getSensingStart(); // in days
            final float stopDateInMJD = prareReader.getSensingStop(); // in days
            if (startDateInMJD <= productDateInMJD && productDateInMJD < stopDateInMJD) {

                // read orbit data records in each orbit file
                prareReader.readOrbitData(f);
                return f;
            }
        }

        return null;
    }


    private final static class OrbitData {
        public double xPos;
        public double yPos;
        public double zPos;
        public double xVel;
        public double yVel;
        public double zVel;
    }

    private final static class ENVISATTiePointRecord {
        public int sampleNumber;
        public double slantRangeTime;
        public double incidenceAngle;
        public double latitude;
        public double longitude;
    }
    /**
     * The SPI is used to register this operator in the graph processing framework
     * via the SPI configuration file
     * {@code META-INF/services/org.esa.beam.framework.gpf.OperatorSpi}.
     * This class may also serve as a factory for new operator instances.
     * @see org.esa.beam.framework.gpf.OperatorSpi#createOperator()
     * @see org.esa.beam.framework.gpf.OperatorSpi#createOperator(java.util.Map, java.util.Map)
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(ApplyOrbitFileOp.class);
        }
    }
}