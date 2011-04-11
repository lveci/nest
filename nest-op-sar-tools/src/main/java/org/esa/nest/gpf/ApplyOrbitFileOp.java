/*
 * Copyright (C) 2011 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.nest.gpf;

import Jama.Matrix;
import com.bc.ceres.core.NullProgressMonitor;
import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.swing.progress.DialogProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatOrbitReader;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.dataio.OrbitalDataRecordReader;
import org.esa.nest.dataio.PrareOrbitReader;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.util.Constants;
import org.esa.nest.util.GeoUtils;
import org.esa.nest.util.Settings;
import org.esa.nest.util.ftpUtils;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Set;

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
 *    4)  Get sample number x (index in the range line).            c
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

@OperatorMetadata(alias="Apply-Orbit-File",
        category = "SAR Tools",
        description="Apply orbit file")
public final class ApplyOrbitFileOp extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(valueSet = {DORIS_POR+" (ENVISAT)", DORIS_VOR+" (ENVISAT)",
            DELFT_PRECISE+" (ENVISAT, ERS1&2)", PRARE_PRECISE+" (ERS1&2)" },
            defaultValue = DORIS_VOR+" (ENVISAT)", label="Orbit Type")
    private String orbitType = null;

    private MetadataElement absRoot = null;
    private EnvisatOrbitReader dorisReader = null;
    private OrbitalDataRecordReader delftReader = null;
    private PrareOrbitReader prareReader = null;
    private File orbitFile = null;

    private int sourceImageWidth;
    private int sourceImageHeight;
    private int targetTiePointGridHeight;
    private int targetTiePointGridWidth;

    private double firstLineUTC;
    private double lineTimeInterval;

    private TiePointGrid slantRangeTime = null;
    private TiePointGrid incidenceAngle = null;
    private TiePointGrid latitude = null;
    private TiePointGrid longitude = null;

    private String mission;

    private static final String DORIS_POR = "DORIS Precise";
    private static final String DORIS_VOR = "DORIS Verified";
    private static final String DELFT_PRECISE = "DELFT Precise";
    private static final String PRARE_PRECISE = "PRARE Precise";

    private ftpUtils ftp = null;
    private Map<String, Long> fileSizeMap = null;

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

            mission = absRoot.getAttributeString(AbstractMetadata.MISSION);
            System.out.println("mission is "+mission);
            System.out.println("orbitType is "+orbitType);

            if(orbitType == null) {
                if(mission.equals("ENVISAT")) {
                    orbitType = DORIS_VOR;
                } else if(mission.equals("ERS1") || mission.equals("ERS2")) {
                    orbitType = PRARE_PRECISE;
                }
            }
            if(mission.equals("ENVISAT")) {
                if(!orbitType.startsWith(DELFT_PRECISE) && !orbitType.startsWith(DORIS_POR) && !orbitType.startsWith(DORIS_VOR)) {
                    throw new OperatorException(orbitType + " is not suitable for an ENVISAT product");
                }
            } else if(mission.equals("ERS1")) {
                if(!orbitType.startsWith(DELFT_PRECISE) && !orbitType.startsWith(PRARE_PRECISE)) {
                    throw new OperatorException(orbitType + " is not suitable for an ERS1 product");
                }
            } else if(mission.equals("ERS2")) {
                if(!orbitType.startsWith(DELFT_PRECISE) && !orbitType.startsWith(PRARE_PRECISE)) {
                    throw new OperatorException(orbitType + " is not suitable for an ERS2 product");
                }
            } else {
                throw new OperatorException(orbitType + " is not suitable for a "+mission+" product");
            }

            if (orbitType.contains("DORIS")) {
                getDorisOrbitFile();
            } else if (orbitType.contains("DELFT")) {
                getDelftOrbitFile();
            } else if (orbitType.contains("PRARE")) {
                getPrareOrbitFile();
            }

            getTiePointGrid();

            getSourceImageDimension();

            getFirstLineUTC();

            createTargetProduct();

//            updateTargetProductGEOCoding();

            updateOrbitStateVectors();
  
        } catch(Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
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
     * Get the first line UTC in days.
     */
    private void getFirstLineUTC() {

        firstLineUTC = absRoot.getAttributeUTC(AbstractMetadata.first_line_time).getMJD();
        lineTimeInterval = absRoot.getAttributeDouble(AbstractMetadata.line_time_interval) / 86400.0; // s to day
        //System.out.println((new ProductData.UTC(firstLineUTC)).toString());
    }

    /**
     * Create target product.
     */
    void createTargetProduct() {

        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    sourceProduct.getSceneRasterWidth(),
                                    sourceProduct.getSceneRasterHeight());

        OperatorUtils.copyProductNodes(sourceProduct, targetProduct);

        for(Band srcBand : sourceProduct.getBands()) {
            if(srcBand instanceof VirtualBand) {
                OperatorUtils.copyVirtualBand(targetProduct, (VirtualBand)srcBand, srcBand.getName());
            } else {
                final Band targetBand = ProductUtils.copyBand(srcBand.getName(), sourceProduct, targetProduct);
                targetBand.setSourceImage(srcBand.getSourceImage());
            }
        }
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

        final int subSamplingX = sourceImageWidth / (targetTiePointGridWidth - 1);
        final int subSamplingY = sourceImageHeight / (targetTiePointGridHeight - 1);

        // Create new tie point grid
        int k = 0;
        for (int r = 0; r < targetTiePointGridHeight; r++) {

            // get the zero Doppler time for the rth line
            int y;
            if (r == targetTiePointGridHeight - 1) { // last row
                y = sourceImageHeight - 1;
            } else { // other rows
                y = r * subSamplingY;
            }

            final double curLineUTC = computeCurrentLineUTC(y);
            //System.out.println((new ProductData.UTC(curLineUTC)).toString());
            
            // compute the satellite position and velocity for the zero Doppler time using cubic interpolation
            final OrbitData data = getOrbitData(curLineUTC);

            for (int c = 0; c < targetTiePointGridWidth; c++) {

                final int x = getSampleIndex(c, subSamplingX);
                targetIncidenceAngleTiePoints[k] = incidenceAngle.getPixelFloat((float)x, (float)y);
                targetSlantRangeTimeTiePoints[k] = slantRangeTime.getPixelFloat((float)x, (float)y);

                final double slrgTime = (double)targetSlantRangeTimeTiePoints[k] / 1000000000.0; // ns to s;
                final GeoPos geoPos = computeLatLon(x, y, slrgTime, data);
                targetLatTiePoints[k] = geoPos.lat;
                targetLonTiePoints[k] = geoPos.lon;
                k++;
            }
        }

        final TiePointGrid angleGrid = new TiePointGrid(OperatorUtils.TPG_INCIDENT_ANGLE, targetTiePointGridWidth, targetTiePointGridHeight,
                0.0f, 0.0f, (float)subSamplingX, (float)subSamplingY, targetIncidenceAngleTiePoints);
        angleGrid.setUnit(Unit.DEGREES);

        final TiePointGrid slrgtGrid = new TiePointGrid(OperatorUtils.TPG_SLANT_RANGE_TIME, targetTiePointGridWidth, targetTiePointGridHeight,
                0.0f, 0.0f, (float)subSamplingX, (float)subSamplingY, targetSlantRangeTimeTiePoints);
        slrgtGrid.setUnit(Unit.NANOSECONDS);

        final TiePointGrid latGrid = new TiePointGrid(OperatorUtils.TPG_LATITUDE, targetTiePointGridWidth, targetTiePointGridHeight,
                0.0f, 0.0f, (float)subSamplingX, (float)subSamplingY, targetLatTiePoints);
        latGrid.setUnit(Unit.DEGREES);

        final TiePointGrid lonGrid = new TiePointGrid(OperatorUtils.TPG_LONGITUDE, targetTiePointGridWidth, targetTiePointGridHeight,
                0.0f, 0.0f, (float)subSamplingX, (float)subSamplingY, targetLonTiePoints, TiePointGrid.DISCONT_AT_180);
        lonGrid.setUnit(Unit.DEGREES);

        final TiePointGeoCoding tpGeoCoding = new TiePointGeoCoding(latGrid, lonGrid, Datum.WGS_84);

        for(TiePointGrid tpg : targetProduct.getTiePointGrids()) {
            targetProduct.removeTiePointGrid(tpg);
        }

        targetProduct.addTiePointGrid(angleGrid);
        targetProduct.addTiePointGrid(slrgtGrid);
        targetProduct.addTiePointGrid(latGrid);
        targetProduct.addTiePointGrid(lonGrid);
        targetProduct.setGeoCoding(tpGeoCoding);
    }

    /**
     * Get corresponding sample index for a given column index in the new tie point grid.
     * @param colIdx The column index in the new tie point grid.
     * @param subSamplingX the x sub sampling
     * @return The sample index.
     */
    private int getSampleIndex(final int colIdx, final int subSamplingX) {

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

        final OrbitData orbitData = new OrbitData();

        if (orbitType.contains("DORIS")) {

            final EnvisatOrbitReader.OrbitVector orb = dorisReader.getOrbitVector(utc);
            orbitData.xPos = orb.xPos;
            orbitData.yPos = orb.yPos;
            orbitData.zPos = orb.zPos;
            orbitData.xVel = orb.xVel;
            orbitData.yVel = orb.yVel;
            orbitData.zVel = orb.zVel;

        } else if (orbitType.contains("DELFT")) {

            final OrbitalDataRecordReader.OrbitVector orb = delftReader.getOrbitVector(utc);
            orbitData.xPos = orb.xPos;
            orbitData.yPos = orb.yPos;
            orbitData.zPos = orb.zPos;
            orbitData.xVel = orb.xVel;
            orbitData.yVel = orb.yVel;
            orbitData.zVel = orb.zVel;

        } else if (orbitType.contains("PRARE")) {

            final PrareOrbitReader.OrbitVector orb = prareReader.getOrbitVector(utc);
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

        final double[] xyz = new double[3];
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
    private static void computeAccurateXYZ(OrbitData data, double[] xyz, double time) {

        final double a = Constants.semiMajorAxis;
        final double b = Constants.semiMinorAxis;
        final double a2 = a*a;
        final double b2 = b*b;
        final double del = 0.001;
        final int maxIter = 10;

        Matrix X = new Matrix(3, 1);
        final Matrix F = new Matrix(3, 1);
        final Matrix J = new Matrix(3, 3);

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
            F.set(2, 0, x*x/a2 + y*y/a2 + z*z/b2 - 1);

            J.set(1, 0, 2.0*dx);
            J.set(1, 1, 2.0*dy);
            J.set(1, 2, 2.0*dz);
            J.set(2, 0, 2.0*x/a2);
            J.set(2, 1, 2.0*y/a2);
            J.set(2, 2, 2.0*z/b2);

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
        final MetadataElement tgtAbsRoot = AbstractMetadata.getAbstractedMetadata(targetProduct);
        final AbstractMetadata.OrbitStateVector[] orbitStateVectors = AbstractMetadata.getOrbitStateVectors(tgtAbsRoot);

        // compute new orbit state vectors
        for (AbstractMetadata.OrbitStateVector orbitStateVector : orbitStateVectors) {
            final double time = orbitStateVector.time_mjd;
            final OrbitData orbitData = getOrbitData(time);
            orbitStateVector.x_pos = orbitData.xPos; // m
            orbitStateVector.y_pos = orbitData.yPos; // m
            orbitStateVector.z_pos = orbitData.zPos; // m
            orbitStateVector.x_vel = orbitData.xVel; // m/s
            orbitStateVector.y_vel = orbitData.yVel; // m/s
            orbitStateVector.z_vel = orbitData.zVel; // m/s
        }

        // save new orbit state vectors
        AbstractMetadata.setOrbitStateVectors(tgtAbsRoot, orbitStateVectors);

        // save orbit file name
        final String prefix = orbitType.substring(0, orbitType.indexOf("("));
        tgtAbsRoot.setAttributeString(AbstractMetadata.orbit_state_vector_file, prefix+" "+orbitFile.getName());
    }
    
    // ====================================== DORIS ORBIT FILE ===============================================

    /**
     * Get DORIS orbit file.
     * @throws IOException The exception.
     */
    private void getDorisOrbitFile() throws IOException {

        dorisReader = EnvisatOrbitReader.getInstance();
        final int absOrbit = absRoot.getAttributeInt(AbstractMetadata.ABS_ORBIT, 0);

        // construct path to the orbit file folder
        String orbitPath = "";
        String prefix = "";
        if(orbitType.contains(DORIS_VOR)) {
            orbitPath = Settings.instance().get("OrbitFiles/dorisVOROrbitPath");
            prefix = "vor";
        } else if(orbitType.contains(DORIS_POR)) {
            orbitPath = Settings.instance().get("OrbitFiles/dorisPOROrbitPath");
            prefix = "por";
        }

        final Date startDate = sourceProduct.getStartTime().getAsDate();
        final int month = startDate.getMonth()+1;
        String folder = String.valueOf(startDate.getYear() + 1900);
        if(month < 10) {
            folder +='0';
        }
        folder += month;
        orbitPath += File.separator + folder;
        final File localPath = new File(orbitPath);

        // find orbit file in the folder
        orbitFile = FindDorisOrbitFile(dorisReader, localPath, startDate, absOrbit);
        if(orbitFile == null) {
            final String remotePath = "/"+prefix + "/" + folder;
            getRemoteDorisFiles(remotePath, localPath);
            // find again in newly downloaded folder
            orbitFile = FindDorisOrbitFile(dorisReader, localPath, startDate, absOrbit);
        }

        if(orbitFile == null) {
            throw new IOException("Unable to find suitable DORIS orbit file in\n"+orbitPath);
        }
    }

    /**
     * Find DORIS orbit file.
     * @param dorisReader The ENVISAT oribit reader.
     * @param path The path to the orbit file.
     * @param productDate The start date of the product.
     * @param absOrbit The absolute orbit number.
     * @return The orbit file.
     * @throws IOException
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

            try {
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
            } catch(Exception e) {
                System.out.println(e.getMessage());
                // continue    
            }
        }

        return null;
    }

    private void getRemoteDorisFiles(final String remotePath, final File localPath) {
        final String dorisFTP = Settings.instance().get("OrbitFiles/dorisFTP");
        try {
            if(ftp == null) {
                ftp = new ftpUtils(dorisFTP, "dorisusr", "env_data");
                fileSizeMap = ftpUtils.readRemoteFileList(ftp, dorisFTP, remotePath);
            }

            if(!localPath.exists())
                localPath.mkdirs();

            if(VisatApp.getApp() != null) {
                final SwingWorker<Exception, Object> worker = new SwingWorker<Exception, Object>() {

                    @Override
                    protected Exception doInBackground() throws Exception {
                        final ProgressMonitor pm = new DialogProgressMonitor(VisatApp.getApp().getMainFrame(),
                                                            "Downloading Orbit...",
                                                            Dialog.ModalityType.APPLICATION_MODAL);

                        getRemoteFiles(remotePath, localPath, pm);
                        return null;
                    }
                };
                worker.execute();
            } else {
                getRemoteFiles(remotePath, localPath, new NullProgressMonitor());
            }

        } catch(Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private void getRemoteFiles(final String remotePath, final File localPath, final ProgressMonitor pm) {
        final Set<String> remoteFileNames = fileSizeMap.keySet();
        pm.beginTask("Downloading Orbit Files...", remoteFileNames.size());
        for(String fileName : remoteFileNames) {
            final long fileSize = fileSizeMap.get(fileName);
            final File localFile = new File(localPath, fileName);
            if(localFile.exists() && localFile.length() == fileSize)
                continue;
            try {
                final ftpUtils.FTPError result = ftp.retrieveFile(remotePath +"/"+ fileName, localFile, fileSize);
                if(result != ftpUtils.FTPError.OK) {
                    localFile.delete();
                }
            } catch(Exception e) {
                localFile.delete();
                System.out.println(e.getMessage());
            }

            pm.worked(1);
        }
        pm.done();
    }

    // ====================================== DELFT ORBIT FILE ===============================================
    /**
     * Get DELFT orbit file.
     * @throws IOException The exceptions.
     */
    private void getDelftOrbitFile() throws Exception {

        delftReader = OrbitalDataRecordReader.getInstance();
        // get product start time
        final Date startDate = sourceProduct.getStartTime().getAsDate();

        // find orbit file in the folder
        orbitFile = FindDelftOrbitFile(delftReader, startDate);

        if(orbitFile == null) {
            throw new IOException("Unable to find suitable orbit file.\n" +
                    "Please refer to http://www.deos.tudelft.nl/ers/precorbs/orbits/ \n" +
                    "ERS1 orbits are available until 1996\n" +
                    "ERS2 orbits are available until 2003\n" +
                    "ENVISAT orbits are available until 2008");
        }
    }

    /**
     * Find DELFT orbit file.
     * @param delftReader The DELFT oribit reader.
     * @param productDate The start date of the product.
     * @return The orbit file.
     * @throws Exception The exceptions.
     */
    private File FindDelftOrbitFile(OrbitalDataRecordReader delftReader, Date productDate)
            throws Exception  {

        // construct path to the orbit file folder
        String orbitPathStr = "";
        String delftFTPPath = "";
        if(mission.equals("ENVISAT")) {
            orbitPathStr = Settings.instance().get("OrbitFiles/delftEnvisatOrbitPath");
            delftFTPPath = Settings.instance().get("OrbitFiles/delftFTP_ENVISAT_precise_remotePath");
        } else if(mission.equals("ERS1")) {
            orbitPathStr = Settings.instance().get("OrbitFiles/delftERS1OrbitPath");
            delftFTPPath = Settings.instance().get("OrbitFiles/delftFTP_ERS1_precise_remotePath");
        } else if(mission.equals("ERS2")) {
            orbitPathStr = Settings.instance().get("OrbitFiles/delftERS2OrbitPath");
            delftFTPPath = Settings.instance().get("OrbitFiles/delftFTP_ERS2_precise_remotePath");
        }
        final File orbitPath = new File(orbitPathStr);
        final String delftFTP = Settings.instance().get("OrbitFiles/delftFTP");

        if(!orbitPath.exists())
            orbitPath.mkdirs();

        // find arclist file, then get the arc# of the orbit file
        final File arclistFile = new File(orbitPath, "arclist");
        if (!arclistFile.exists()) {
            if(!getRemoteFile(delftFTP, delftFTPPath, arclistFile))
                return null;
        }

        int arcNum = OrbitalDataRecordReader.getArcNumber(arclistFile, productDate);
        if (arcNum == OrbitalDataRecordReader.invalidArcNumber) {
            // force refresh of arclist file
            if(!getRemoteFile(delftFTP, delftFTPPath, arclistFile))
                return null;
            arcNum = OrbitalDataRecordReader.getArcNumber(arclistFile, productDate);
            if (arcNum == OrbitalDataRecordReader.invalidArcNumber)
                return null;
        }

        String orbitFileName = orbitPath.getAbsolutePath() + File.separator + "ODR.";
        if (arcNum < 10) {
            orbitFileName += "00" + arcNum;
        } else if (arcNum < 100) {
            orbitFileName += "0" + arcNum;
        } else {
            orbitFileName += arcNum;
        }

        final File orbitFile = new File(orbitFileName);
        if (!orbitFile.exists()) {
            if(!getRemoteFile(delftFTP, delftFTPPath, orbitFile))
                return null;
        }

        // read content of the orbit file
        delftReader.readOrbitFile(orbitFile.getAbsolutePath());

        return orbitFile;
    }

    private boolean getRemoteFile(String remoteFTP, String remotePath, File localFile) {
        try {
            if(ftp == null) {
                ftp = new ftpUtils(remoteFTP);
                fileSizeMap = ftpUtils.readRemoteFileList(ftp, remoteFTP, remotePath);
            }

            final String remoteFileName = localFile.getName();
            final Long fileSize = fileSizeMap.get(remoteFileName);

            final ftpUtils.FTPError result = ftp.retrieveFile(remotePath + remoteFileName, localFile, fileSize);
            if(result == ftpUtils.FTPError.OK) {
                return true;
            } else {
                localFile.delete();
            }

            return false;
        } catch(Exception e) {
            System.out.println(e.getMessage());
        }
        return false;
    }

    // ====================================== PRARE ORBIT FILE ===============================================
    /**
     * Get PRARE orbit file.
     * @throws IOException The exceptions.
     */
    private void getPrareOrbitFile() throws IOException {

        prareReader = PrareOrbitReader.getInstance();
        // construct path to the orbit file folder
        String orbitPath = "";
        if(mission.equals("ERS1")) {
            orbitPath = Settings.instance().get("OrbitFiles/prareERS1OrbitPath");
        } else if(mission.equals("ERS2")) {
            orbitPath = Settings.instance().get("OrbitFiles/prareERS2OrbitPath");
        }

        // get product start time
        // todo the startDate below is different from the start time in the metadata, why?
        final Date startDate = sourceProduct.getStartTime().getAsDate();
        final String folder = String.valueOf(startDate.getYear() + 1900);
        orbitPath += File.separator + folder;
        final File localPath = new File(orbitPath);

        // find orbit file in the folder
        orbitFile = FindPrareOrbitFile(prareReader, localPath, startDate);
        if(orbitFile == null) {
            final String remotePath = "/orbprc/"+mission + "/" + folder;
            getRemotePrareFiles(remotePath, localPath);
            // find again in newly downloaded folder
            orbitFile = FindPrareOrbitFile(prareReader, localPath, startDate);
        }

        if(orbitFile == null) {
            throw new IOException("Unable to find suitable orbit file \n"+orbitPath);
        }
    }

    private void getRemotePrareFiles(final String remotePath, final File localPath) {
        final String prareFTP = Settings.instance().get("OrbitFiles/prareFTP");
        try {
            if(ftp == null) {
                ftp = new ftpUtils(prareFTP, "dpafftp", "sunshine");
                fileSizeMap = ftpUtils.readRemoteFileList(ftp, prareFTP, remotePath);
            }

            if(!localPath.exists())
                localPath.mkdirs();

            if(VisatApp.getApp() != null) {
                final SwingWorker<Exception, Object> worker = new SwingWorker<Exception, Object>() {

                    @Override
                    protected Exception doInBackground() throws Exception {
                        final ProgressMonitor pm = new DialogProgressMonitor(VisatApp.getApp().getMainFrame(),
                                                            "Downloading Orbit...",
                                                            Dialog.ModalityType.APPLICATION_MODAL);

                        getRemoteFiles(remotePath, localPath, pm);
                        return null;
                    }
                };
                worker.execute();
            } else {
                getRemoteFiles(remotePath, localPath, new NullProgressMonitor());
            }

        } catch(Exception e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Find PRARE orbit file.
     * @param prareReader The PRARE oribit reader.
     * @param path The path to the orbit file.
     * @param productDate The start date of the product.
     * @return The orbit file.
     */
    private static File FindPrareOrbitFile(PrareOrbitReader prareReader, File path, Date productDate) {

        final File[] list = path.listFiles();
        if(list == null) return null;
        final float productDateInMJD = (float)ProductData.UTC.create(productDate, 0).getMJD(); // in days

        // loop through all orbit files in the given folder
        for(File f : list) {

            if (f.isDirectory()) {
                continue;
            }

            try {
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
            } catch(Exception e) {
                System.out.println(e.getMessage());
                // continue
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