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
import org.esa.beam.util.math.MathUtils;
import org.esa.beam.dataio.envisat.EnvisatOrbitReader;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.gpf.OperatorUtils;
import org.esa.nest.util.Settings;
import org.esa.nest.util.GeoUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Date;
import java.io.File;
import java.io.IOException;

import Jama.Matrix;

/**
 * This operator applies orbit file to a given product.
 */

@OperatorMetadata(alias="Apply-Orbit-File", description="Apply orbit file")
public final class ApplyOrbitFileOp extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(valueSet = {DORIS_POR, DORIS_VOR}, defaultValue = DORIS_POR, label="Orbit Type")
    private String orbitType = DORIS_POR;

    private MetadataElement absRoot;
    private EnvisatOrbitReader dorisReader;
    private File orbitFile;
    private int absOrbit;
    private int numLines;
    private int numSamplesPerLine;
    private double firstLineUTC;
    private double lastLineUTC;
    private double lineTimeInterval;
    private TiePointGrid slantRangeTime;
    private TiePointGrid incidenceAngle;
    private TiePointGrid latitude;
    private TiePointGrid longitude;

    public static final String DORIS_POR = "DORIS_POR";
    public static final String DORIS_VOR = "DORIS_VOR";
    private static final double a = 6378137; // m
    private static final double b = 6356752.315; // m
    private static final double earthFlatCoef = 298.257223563;
    private static final double e = 2 / earthFlatCoef - 1 / (earthFlatCoef * earthFlatCoef);
    private static final double lightSpeed = 299792458.0; //  m / s
    private static final double halfLightSpeed = lightSpeed / 2.0;
    private static final int targetTiePointGridHeight = 10;
    private static final int targetTiePointGridWidth = 10;

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
            absRoot = OperatorUtils.getAbstractedMetadata(sourceProduct);

            if (orbitType.contains("DORIS")) {
                getDorisOrbitFile();
            } else if (orbitType.contains("DELFT")) {
                getDelftOrbitFile();
            }

            createTargetProduct();
            updateTargetProductGEOCoding();

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
     * Create target product.
     * @throws Exception The exception.
     */
    void createTargetProduct() throws Exception {

        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    sourceProduct.getSceneRasterWidth(),
                                    sourceProduct.getSceneRasterHeight());

        ProductUtils.copyMetadata(sourceProduct, targetProduct);

        //ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        TiePointGrid srcTPG = sourceProduct.getTiePointGrid("slant_range_time");
        targetProduct.addTiePointGrid(srcTPG.cloneTiePointGrid());
        srcTPG = sourceProduct.getTiePointGrid("incident_angle");
        targetProduct.addTiePointGrid(srcTPG.cloneTiePointGrid());

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

    private void updateTargetProductGEOCoding() throws Exception {

        latitude = OperatorUtils.getLatitude(sourceProduct);
        longitude = OperatorUtils.getLongitude(sourceProduct);
        slantRangeTime = OperatorUtils.getSlantRangeTime(sourceProduct);
        incidenceAngle = OperatorUtils.getIncidenceAngle(sourceProduct);

        firstLineUTC = absRoot.getAttributeUTC(AbstractMetadata.first_line_time).getMJD();
        System.out.println((new ProductData.UTC(firstLineUTC)).toString());
        lastLineUTC = absRoot.getAttributeUTC(AbstractMetadata.last_line_time).getMJD();
        System.out.println((new ProductData.UTC(lastLineUTC)).toString());
        lineTimeInterval = absRoot.getAttributeDouble(AbstractMetadata.line_time_interval) / 86400.0; // s to day
        numLines = absRoot.getAttributeInt(AbstractMetadata.num_output_lines);
        numSamplesPerLine = absRoot.getAttributeInt(AbstractMetadata.num_samples_per_line);

        final float[] targetLatTiePoints = new float[targetTiePointGridHeight*targetTiePointGridWidth];
        final float[] targetLonTiePoints = new float[targetTiePointGridHeight*targetTiePointGridWidth];

        // Create a new 10x10 tie point grid
        int k = 0;
        for (int r = 0; r < targetTiePointGridHeight; r++) {

            // - get the zero Doppler time for the line
            int y = getLineIndex(r);
            double curLineUTC = computeCurrentLineUTC(y);
            System.out.println((new ProductData.UTC(curLineUTC)).toString());

            // - compute the satellite position and velocity for the zero Doppler time using cubic interpolation
            OrbitData data = getOrbitData(curLineUTC);

            for (int c = 0; c < targetTiePointGridWidth; c++) {

                // get slant range time for the tie point
                final int x = getSampleIndex(c);
                final double time = (double)slantRangeTime.getPixelFloat((float)x, (float)y) / 1000000000.0; // ns to s;
                double[] xyz = new double[3];

                // get the geo position (lat/lon) for the tie point
                final float lat = latitude.getPixelFloat((float)x, (float)y);
                final float lon = longitude.getPixelFloat((float)x, (float)y);
                final GeoPos geoPos = new GeoPos(lat, lon);

                // compute initial (x,y,z) coordinate from lat/lon
                GeoUtils.geo2xyz(geoPos, xyz);

                // compute accurate (x,y,z) coordinate using Newton's method
                computeAccurateXYZ(data, xyz, time);

                // compute (lat, lon, alt) from accurate (x,y,z) coordinate
                GeoUtils.xyz2geo(xyz, geoPos);

                // update tie point geocoding in target product
                targetLatTiePoints[k] = geoPos.lat;
                targetLonTiePoints[k] = geoPos.lon;
                k++;
            }
        }

        float subSamplingX = (float)targetProduct.getSceneRasterWidth() / (targetTiePointGridWidth - 1);
        float subSamplingY = (float)targetProduct.getSceneRasterHeight() / (targetTiePointGridWidth - 1);

        final TiePointGrid latGrid = new TiePointGrid("latitude", targetTiePointGridWidth, targetTiePointGridHeight,
                0.5f, 0.5f, subSamplingX, subSamplingY, targetLatTiePoints);

        final TiePointGrid lonGrid = new TiePointGrid("longitude", targetTiePointGridWidth, targetTiePointGridHeight,
                0.5f, 0.5f, subSamplingX, subSamplingY, targetLonTiePoints, TiePointGrid.DISCONT_AT_180);

        final TiePointGeoCoding tpGeoCoding = new TiePointGeoCoding(latGrid, lonGrid, Datum.WGS_84);

        targetProduct.addTiePointGrid(latGrid);
        targetProduct.addTiePointGrid(lonGrid);
        targetProduct.setGeoCoding(tpGeoCoding);
    }

    private int getLineIndex(int rowIdx) {

        if (rowIdx == targetTiePointGridHeight - 1) { // last row
            return numLines - 1;
        } else { // other rows
            return rowIdx * (numLines / (targetTiePointGridHeight - 1));
        }
    }

    private int getSampleIndex(int colIdx) {

        if (colIdx == targetTiePointGridWidth - 1) { // last column
            return numSamplesPerLine - 1;
        } else { // other columns
            return colIdx * (numSamplesPerLine / (targetTiePointGridWidth - 1));
        }
    }

    private double computeCurrentLineUTC(int y) {
        return firstLineUTC + y*lineTimeInterval;
    }

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
            // get orbit data with OrbitalDataRecordReader
        }

        return orbitData;
    }

    private void computeAccurateXYZ(OrbitData data, double[] xyz, double time) {

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
            F.set(1, 0, dx*dx + dy*dy + dz*dz - Math.pow(time*halfLightSpeed, 2.0));
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
            orbitPath = Settings.instance().get("dorisVOROrbitPath");
        } else if(orbitType.contains(DORIS_POR)) {
            orbitPath = Settings.instance().get("dorisPOROrbitPath");
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
     *
     * @param dorisReader The
     * @param path The
     * @param productDate The
     * @param absOrbit The
     * @return The
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
                //EnvisatOrbitReader.OrbitVector orb = dorisReader.getOrbitVector(startDate);
                //if (absOrbit == orb.absOrbit) {
                    return f;
                //}
            }
        }

        return null;
    }

    // ====================================== DELFT ORBIT FILE ===============================================
    private void getDelftOrbitFile() throws IOException {

    }

    // ============================== UPDATE GEOCODING FOR ENVISAT FORMAT PRODUCT ===================================

    /**
     * Get tie point record for ENVISAT format product for given Geolocation grid ADSR record and given line.
     * @param recordIdx The Geolocation grid ADSR record index
     * @param line Indicator of the first or last line in the record
     * @param pointIdx The tie point index
     * @return The tie point record
     * @throws Exception
     */
    private ENVISATTiePointRecord getENVISATTiePointRecord(int recordIdx, String line, int pointIdx) throws Exception {

        ENVISATTiePointRecord record = new ENVISATTiePointRecord();

        MetadataElement geoAds = sourceProduct.getMetadataRoot().getElement("GEOLOCATION_GRID_ADS");
        if (geoAds == null) {
            throw new OperatorException("GEOLOCATION_GRID_ADS not found");
        }

        MetadataElement recordAds= geoAds.getElement("GEOLOCATION_GRID_ADS." + recordIdx);
        if (recordAds == null) {
            throw new OperatorException("GEOLOCATION_GRID_ADS." + recordIdx + " not found");
        }

        String tag = "ASAR_Geo_Grid_ADSR.sd/" + line + "_tie_points.samp_numbers";
        MetadataAttribute sampNumAttr = recordAds.getAttribute(tag);
        if (sampNumAttr == null) {
            throw new OperatorException(tag + " not found");
        }
        record.sampleNumber = sampNumAttr.getData().getElemIntAt(pointIdx);
        System.out.println("Sample number is " + record.sampleNumber);

        tag = "ASAR_Geo_Grid_ADSR.sd/" + line + "_tie_points.slant_range_times";
        MetadataAttribute slantRgTimeAttr = recordAds.getAttribute(tag);
        if (slantRgTimeAttr == null) {
            throw new OperatorException(tag + " not found");
        }
        record.slantRangeTime = slantRgTimeAttr.getData().getElemDoubleAt(pointIdx) / 1000000000.0; // ns to s
        System.out.println("Slant range time is " + record.slantRangeTime);

        tag = "ASAR_Geo_Grid_ADSR.sd/" + line + "_tie_points.angles";
        MetadataAttribute angleAttr = recordAds.getAttribute(tag);
        if (angleAttr == null) {
            throw new OperatorException(tag + " not found");
        }
        record.incidenceAngle = angleAttr.getData().getElemDoubleAt(pointIdx);
        System.out.println("Incidence angle is " + record.incidenceAngle);

        tag = "ASAR_Geo_Grid_ADSR.sd/" + line + "_tie_points.lats";
        MetadataAttribute latAttr = recordAds.getAttribute(tag);
        if (latAttr == null) {
            throw new OperatorException(tag + " not found");
        }
        record.latitude = latAttr.getData().getElemDoubleAt(pointIdx) / 1000000.0; // 1e-6 degree to degree
        System.out.println("Latitude is " + record.latitude);

        tag = "ASAR_Geo_Grid_ADSR.sd/" + line + "_tie_points.longs";
        MetadataAttribute lonAttr = recordAds.getAttribute(tag);
        if (lonAttr == null) {
            throw new OperatorException(tag + " not found");
        }
        record.longitude = lonAttr.getData().getElemDoubleAt(pointIdx) / 1000000.0; // 1e-6 degree to degree
        System.out.println("Longitude is " + record.longitude);

        return record;
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