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

    protected boolean isProductSLC = false;
    protected String productType = null;

    protected abstract void readProductDirectory() throws IOException, IllegalBinaryFormatException;

    public abstract Product createProduct() throws IOException, IllegalBinaryFormatException;

    public abstract CEOSImageFile getImageFile(final Band band) throws IOException, IllegalBinaryFormatException;

    public abstract void close() throws IOException;

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

    protected static ProductData.UTC getUTCScanStartTime(BaseRecord sceneRec) {
        if(sceneRec == null) return new ProductData.UTC(0);
        final String startTime = sceneRec.getAttributeString("Zero-doppler azimuth time of first azimuth pixel");
        return AbstractMetadata.parseUTC(startTime);
    }

    protected static ProductData.UTC getUTCScanStopTime(BaseRecord sceneRec) {
        if(sceneRec == null) return new ProductData.UTC(0);
        final String endTime = sceneRec.getAttributeString("Zero-doppler azimuth time of last azimuth pixel");
        return AbstractMetadata.parseUTC(endTime);
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

    protected static void addOrbitStateVectors(final MetadataElement absRoot, final BaseRecord platformPosRec) {
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
        final MetadataElement srgrCoefficientsElem = absRoot.getElement(AbstractMetadata.srgr_coefficients);

        final MetadataElement srgrListElem = new MetadataElement("srgr_coef_list");
        srgrCoefficientsElem.addElement(srgrListElem);

        final ProductData.UTC utcTime = absRoot.getAttributeUTC(AbstractMetadata.first_line_time, new ProductData.UTC(0));
        srgrListElem.setAttributeUTC(AbstractMetadata.srgr_coef_time, utcTime);
        AbstractMetadata.addAbstractedAttribute(srgrListElem, AbstractMetadata.ground_range_origin,
                ProductData.TYPE_FLOAT64, "m", "Ground Range Origin");
        AbstractMetadata.setAttribute(srgrListElem, AbstractMetadata.ground_range_origin, 0.0);

        addSRGRCoef(srgrListElem, facilityRec,
                "coefficients of the ground range to slant range conversion polynomial 1");
        addSRGRCoef(srgrListElem, facilityRec,
                "coefficients of the ground range to slant range conversion polynomial 2");
        addSRGRCoef(srgrListElem, facilityRec,
                "coefficients of the ground range to slant range conversion polynomial 3");
        addSRGRCoef(srgrListElem, facilityRec,
                "coefficients of the ground range to slant range conversion polynomial 4");
    }

    private static void addSRGRCoef(final MetadataElement srgrListElem, final BaseRecord facilityRec, final String tag) {
        final MetadataElement coefElem = new MetadataElement(AbstractMetadata.coefficient);
        srgrListElem.addElement(coefElem);

        AbstractMetadata.addAbstractedAttribute(coefElem, AbstractMetadata.srgr_coef,
                ProductData.TYPE_FLOAT64, "", "SRGR Coefficient");
        AbstractMetadata.setAttribute(coefElem, AbstractMetadata.srgr_coef, facilityRec.getAttributeDouble(tag));
    }

    protected static ImageInputStream createInputStream(final File file) throws IOException {
        return new FileImageInputStream(file);
    }
}