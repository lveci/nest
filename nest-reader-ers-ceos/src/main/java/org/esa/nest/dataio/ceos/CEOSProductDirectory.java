package org.esa.nest.dataio.ceos;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.util.Guardian;
import org.esa.nest.dataio.IllegalBinaryFormatException;
import org.esa.nest.dataio.ReaderUtils;
import org.esa.nest.dataio.ceos.records.BaseRecord;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;

import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;

/**

 */
public abstract class CEOSProductDirectory {

    protected CEOSConstants constants = null;
    protected File _baseDir = null;
    protected CEOSVolumeDirectoryFile _volumeDirectoryFile = null;
    protected boolean isProductSLC = false;
    protected String productType = null;

    protected abstract void readProductDirectory() throws IOException, IllegalBinaryFormatException;

    public abstract Product createProduct() throws IOException, IllegalBinaryFormatException;

    public abstract CEOSImageFile getImageFile(final Band band) throws IOException, IllegalBinaryFormatException;

    public abstract void close() throws IOException;

    protected void readVolumeDirectoryFile() throws IOException, IllegalBinaryFormatException {
        Guardian.assertNotNull("_baseDir", _baseDir);
        Guardian.assertNotNull("constants", constants);

        if(_volumeDirectoryFile == null)
            _volumeDirectoryFile = new CEOSVolumeDirectoryFile(_baseDir, constants);

        productType = _volumeDirectoryFile.getProductType();
        isProductSLC = productType.contains("SLC") || productType.contains("COMPLEX");
    }

    public boolean isSLC() {
        return isProductSLC;
    }
    
    public String getSampleType() {
        if(isProductSLC)
            return "COMPLEX";
        else
            return "DETECTED";
    }

    public String getProductType() {
        return productType;
    }

    protected static String getPolarization(String theID) {
        final String id = theID.toUpperCase();
        if(id.contains("HH") || id.contains("H/H") || id.contains("H-H"))
            return "HH";
        else if(id.contains("VV") || id.contains("V/V") || id.contains("V-V"))
            return "VV";
        else if(id.contains("HV") || id.contains("H/V") || id.contains("H-V"))
            return "HV";
        else if(id.contains("VH") || id.contains("V/H") || id.contains("V-H"))
            return "VH";
        return id;
    }

    protected static void addGeoCoding(final Product product, final float[] latCorners, final float[] lonCorners) {

        if(latCorners == null || lonCorners == null) return;

        int gridWidth = 10;
        int gridHeight = 10;

        final float[] fineLatTiePoints = new float[gridWidth*gridHeight];
        ReaderUtils.createFineTiePointGrid(2, 2, gridWidth, gridHeight, latCorners, fineLatTiePoints);

        float subSamplingX = (float)product.getSceneRasterWidth() / (gridWidth - 1);
        float subSamplingY = (float)product.getSceneRasterHeight() / (gridHeight - 1);

        final TiePointGrid latGrid = new TiePointGrid("latitude", gridWidth, gridHeight, 0.5f, 0.5f,
                subSamplingX, subSamplingY, fineLatTiePoints);
        latGrid.setUnit(Unit.DEGREES);

        final float[] fineLonTiePoints = new float[gridWidth*gridHeight];
        ReaderUtils.createFineTiePointGrid(2, 2, gridWidth, gridHeight, lonCorners, fineLonTiePoints);

        final TiePointGrid lonGrid = new TiePointGrid("longitude", gridWidth, gridHeight, 0.5f, 0.5f,
                subSamplingX, subSamplingY, fineLonTiePoints, TiePointGrid.DISCONT_AT_180);
        lonGrid.setUnit(Unit.DEGREES);

        final TiePointGeoCoding tpGeoCoding = new TiePointGeoCoding(latGrid, lonGrid, Datum.WGS_84);

        product.addTiePointGrid(latGrid);
        product.addTiePointGrid(lonGrid);
        product.setGeoCoding(tpGeoCoding);
    }

    protected static void addTiePointGrids(final Product product, final BaseRecord facility, final BaseRecord scene)
            throws IllegalBinaryFormatException, IOException {

        final int gridWidth = 11;
        final int gridHeight = 11;

        final float subSamplingX = (float)product.getSceneRasterWidth() / (float)(gridWidth - 1);
        final float subSamplingY = (float)product.getSceneRasterHeight() / (float)(gridHeight - 1);

        // add incidence angle tie point grid
        if(facility != null) {

            final double angle1 = facility.getAttributeDouble("Incidence angle at first range pixel");
            final double angle2 = facility.getAttributeDouble("Incidence angle at centre range pixel");
            final double angle3 = facility.getAttributeDouble("Incidence angle at last valid range pixel");

            final float[] angles = new float[]{(float)angle1, (float)angle2, (float)angle3};
            final float[] fineAngles = new float[gridWidth*gridHeight];

            ReaderUtils.createFineTiePointGrid(3, 1, gridWidth, gridHeight, angles, fineAngles);

            final TiePointGrid incidentAngleGrid = new TiePointGrid("incident_angle", gridWidth, gridHeight, 0, 0,
                    subSamplingX, subSamplingY, fineAngles);
            incidentAngleGrid.setUnit(Unit.DEGREES);

            product.addTiePointGrid(incidentAngleGrid);
        }
        // add slant range time tie point grid
        if(scene != null) {

            final double time1 = scene.getAttributeDouble("Zero-doppler range time of first range pixel")*1000000; // ms to ns
            final double time2 = scene.getAttributeDouble("Zero-doppler range time of centre range pixel")*1000000; // ms to ns
            final double time3 = scene.getAttributeDouble("Zero-doppler range time of last range pixel")*1000000; // ms to ns

            final float[] times = new float[]{(float)time1, (float)time2, (float)time3};
            final float[] fineTimes = new float[gridWidth*gridHeight];

            ReaderUtils.createFineTiePointGrid(3, 1, gridWidth, gridHeight, times, fineTimes);

            final TiePointGrid slantRangeTimeGrid = new TiePointGrid("slant_range_time", gridWidth, gridHeight, 0, 0,
                    subSamplingX, subSamplingY, fineTimes);
            slantRangeTimeGrid.setUnit(Unit.NANOSECONDS);
            
            product.addTiePointGrid(slantRangeTimeGrid);
        }
    }

    protected static ProductData.UTC getProcTime(BaseRecord volDescRec) {
        try {
            final String procDate = volDescRec.getAttributeString("Logical volume preparation date").trim();
            final String procTime = volDescRec.getAttributeString("Logical volume preparation time").trim();

            return ProductData.UTC.parse(procDate + procTime, "yyyyMMddHHmmss");
        } catch(ParseException e) {
            System.out.println(e.toString());
            return new ProductData.UTC(0);
        }
    }

    protected static String getPass(BaseRecord mapProjRec) {
        if(mapProjRec == null) return " ";
        final double heading = mapProjRec.getAttributeDouble("Platform heading at nadir corresponding to scene centre");
        if(heading > 90 && heading < 270) return "DESCENDING";
        else return "ASCENDING";
    }

    protected static ProductData.UTC getUTCScanStartTime(final BaseRecord sceneRec, final BaseRecord detailProcRec) {
        if(sceneRec != null) {
            final String startTime = sceneRec.getAttributeString("Zero-doppler azimuth time of first azimuth pixel");
            if(startTime != null)
                return AbstractMetadata.parseUTC(startTime);
        }
        if(detailProcRec != null) {
            final String startTime = detailProcRec.getAttributeString("Processing start time");
            return AbstractMetadata.parseUTC(startTime, "yyyy-DDD-HH:mm:ss");
        }
        return new ProductData.UTC(0);
    }

    protected static ProductData.UTC getUTCScanStopTime(final BaseRecord sceneRec, final BaseRecord detailProcRec) {
        if(sceneRec != null) {
            final String endTime = sceneRec.getAttributeString("Zero-doppler azimuth time of last azimuth pixel");
            if(endTime != null)
                return AbstractMetadata.parseUTC(endTime);
        }
        if(detailProcRec != null) {
            final String endTime = detailProcRec.getAttributeString("Processing stop time");
            return AbstractMetadata.parseUTC(endTime, "yyyy-DDD-HH:mm:ss");
        }
        return new ProductData.UTC(0);
    }

    protected static void addSummaryMetadata(final File summaryFile, final String name, final MetadataElement parent) 
                                            throws IOException {
        if (!summaryFile.exists())
            return;

        final MetadataElement summaryMetadata = new MetadataElement(name);
        final Properties properties = new Properties();

        properties.load(new FileInputStream(summaryFile));
        final Set unsortedEntries = properties.entrySet();
        final TreeSet sortedEntries = new TreeSet(new Comparator() {
            public int compare(final Object a, final Object b) {
                final Map.Entry entryA = (Map.Entry) a;
                final Map.Entry entryB = (Map.Entry) b;
                return ((String) entryA.getKey()).compareTo((String) entryB.getKey());
            }
        });
        sortedEntries.addAll(unsortedEntries);
        for (Object sortedEntry : sortedEntries) {
            final Map.Entry entry = (Map.Entry) sortedEntry;
            final String data = (String) entry.getValue();
            // strip of double quotes
            final String strippedData = data.substring(1, data.length() - 1);
            final MetadataAttribute attribute = new MetadataAttribute((String) entry.getKey(),
                    new ProductData.ASCII(strippedData), true);
            summaryMetadata.addAttribute(attribute);
        }

        parent.addElement(summaryMetadata);
    }

    protected static void assertSameWidthAndHeightForAllImages(final CEOSImageFile[] imageFiles,
                                                      final int width, final int height) {
        for (int i = 0; i < imageFiles.length; i++) {
            final CEOSImageFile imageFile = imageFiles[i];
            Guardian.assertTrue("_sceneWidth == imageFile[" + i + "].getRasterWidth()",
                                width == imageFile.getRasterWidth());
            Guardian.assertTrue("_sceneHeight == imageFile[" + i + "].getRasterHeight()",
                                height == imageFile.getRasterHeight());
        }
    }

    protected static int isGroundRange(final BaseRecord mapProjRec) {
        final String projDesc = mapProjRec.getAttributeString("Map projection descriptor").toLowerCase();
        if(projDesc.contains("slant"))
            return 0;
        return 1;
    }

    protected static void addOrbitStateVectors(final MetadataElement absRoot, final BaseRecord platformPosRec) {
        if(platformPosRec == null) return;
        
        final MetadataElement orbitVectorListElem = absRoot.getElement(AbstractMetadata.orbit_state_vectors);
        final int numPoints = platformPosRec.getAttributeInt("Number of data points");

        for(int i=1; i <= numPoints; ++i) {
            addVector(AbstractMetadata.orbit_vector, orbitVectorListElem, platformPosRec, i);
        }

        if(absRoot.getAttributeUTC(AbstractMetadata.STATE_VECTOR_TIME, new ProductData.UTC(0)).
                equalElems(new ProductData.UTC(0))) {
            
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.STATE_VECTOR_TIME,
                getOrbitTime(platformPosRec, 1));
        }
    }

    private static void addVector(String name, MetadataElement orbitVectorListElem,
                                  BaseRecord platformPosRec, int num) {
        final MetadataElement orbitVectorElem = new MetadataElement(name+num);

        orbitVectorElem.setAttributeUTC(AbstractMetadata.orbit_vector_time, getOrbitTime(platformPosRec, num));
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_x_pos,
                platformPosRec.getAttributeDouble("Position vector X "+num));
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_y_pos,
                platformPosRec.getAttributeDouble("Position vector Y "+num));
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_z_pos,
                platformPosRec.getAttributeDouble("Position vector Z "+num));
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_x_vel,
                platformPosRec.getAttributeDouble("Velocity vector X' "+num));
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_y_vel,
                platformPosRec.getAttributeDouble("Velocity vector Y' "+num));
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_z_vel,
                platformPosRec.getAttributeDouble("Velocity vector Z' "+num));

        orbitVectorListElem.addElement(orbitVectorElem);
    }

    private static ProductData.UTC getOrbitTime(BaseRecord platformPosRec, int num) {
        final int year = platformPosRec.getAttributeInt("Year of data point");
        final int month = platformPosRec.getAttributeInt("Month of data point");
        final int day = platformPosRec.getAttributeInt("Day of data point");
        final float secondsOfDay = (float)platformPosRec.getAttributeDouble("Seconds of day");
        final float hoursf = secondsOfDay / 3600f;
        final int hour = (int)hoursf;
        final float minutesf = (hoursf - hour) * 60f;
        final int minute = (int)minutesf;
        float second = (minutesf - minute) * 60f;

        final float interval = (float)platformPosRec.getAttributeDouble("Time interval between DATA points");
        second += interval * (num-1);

        return AbstractMetadata.parseUTC(String.valueOf(year)+'-'+month+'-'+day+' '+
                                  hour+':'+minute+':'+second, "yyyy-MM-dd HH:mm:ss");
    }

    protected static void addSRGRCoefficients(final MetadataElement absRoot, final BaseRecord facilityRec) {
        if(facilityRec == null) return;

        final MetadataElement srgrCoefficientsElem = absRoot.getElement(AbstractMetadata.srgr_coefficients);

        final MetadataElement srgrListElem = new MetadataElement(AbstractMetadata.srgr_coef_list);
        srgrCoefficientsElem.addElement(srgrListElem);

        final ProductData.UTC utcTime = absRoot.getAttributeUTC(AbstractMetadata.first_line_time, new ProductData.UTC(0));
        srgrListElem.setAttributeUTC(AbstractMetadata.srgr_coef_time, utcTime);
        AbstractMetadata.addAbstractedAttribute(srgrListElem, AbstractMetadata.ground_range_origin,
                ProductData.TYPE_FLOAT64, "m", "Ground Range Origin");
        AbstractMetadata.setAttribute(srgrListElem, AbstractMetadata.ground_range_origin, 0.0);

        addSRGRCoef(srgrListElem, facilityRec,
                "coefficients of the ground range to slant range conversion polynomial 1", 1);
        addSRGRCoef(srgrListElem, facilityRec,
                "coefficients of the ground range to slant range conversion polynomial 2", 2);
        addSRGRCoef(srgrListElem, facilityRec,
                "coefficients of the ground range to slant range conversion polynomial 3", 3);
        addSRGRCoef(srgrListElem, facilityRec,
                "coefficients of the ground range to slant range conversion polynomial 4", 4);
    }

    protected static void addSRGRCoef(final MetadataElement srgrListElem, final BaseRecord rec, final String tag, int cnt) {
        final MetadataElement coefElem = new MetadataElement(AbstractMetadata.coefficient+'.'+cnt);
        srgrListElem.addElement(coefElem);

        AbstractMetadata.addAbstractedAttribute(coefElem, AbstractMetadata.srgr_coef,
                ProductData.TYPE_FLOAT64, "", "SRGR Coefficient");
        AbstractMetadata.setAttribute(coefElem, AbstractMetadata.srgr_coef, rec.getAttributeDouble(tag));
    }

    protected static ImageInputStream createInputStream(final File file) throws IOException {
        return new FileImageInputStream(file);
    }
}