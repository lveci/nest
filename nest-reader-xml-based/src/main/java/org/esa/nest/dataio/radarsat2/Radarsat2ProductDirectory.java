package org.esa.nest.dataio.radarsat2;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.nest.dataio.ImageIOFile;
import org.esa.nest.dataio.ReaderUtils;
import org.esa.nest.dataio.XMLProductDirectory;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;

import java.io.File;
import java.util.*;

/**
 * This class represents a product directory.
 *
 */
public class Radarsat2ProductDirectory extends XMLProductDirectory {

    private String productName = "Radarsat2";
    private String productType = "Radarsat2";
    private final String productDescription = "";

    private boolean isSLC = false;
    private final transient Map<String, String> polarizationMap = new HashMap<String, String>(4);

    private final static String timeFormat = "yyyy-MM-dd HH:mm:ss";

    public Radarsat2ProductDirectory(final File headerFile, final File imageFolder) {
        super(headerFile, imageFolder);
    }

    @Override
    protected void addBands(final Product product) {

        String bandName;
        boolean real = true;
        Band lastRealBand = null;
        String unit;

        final Set<String> keys = bandImageFileMap.keySet();                           // The set of keys in the map.
        for (String key : keys) {
            final ImageIOFile img = bandImageFileMap.get(key);

            for(int i=0; i < img.getNumImages(); ++i) {

                if(isSLC) {
                    for(int b=0; b < img.getNumBands(); ++b) {
                        final String imgName = img.getName().toLowerCase();
                        if(real) {
                            bandName = "i_" + polarizationMap.get(imgName);
                            unit = Unit.REAL;
                        } else {
                            bandName = "q_" + polarizationMap.get(imgName);
                            unit = Unit.IMAGINARY;
                        }

                        final Band band = new Band(bandName, img.getDataType(),
                                           img.getSceneWidth(), img.getSceneHeight());
                        band.setUnit(unit);

                        product.addBand(band);
                        bandMap.put(band, new ImageIOFile.BandInfo(img, i, b));

                        if(real)
                            lastRealBand = band;
                        else {
                            ReaderUtils.createVirtualIntensityBand(product, lastRealBand, band,
                                    '_'+polarizationMap.get(imgName));
                            ReaderUtils.createVirtualPhaseBand(product, lastRealBand, band,
                                    '_'+polarizationMap.get(imgName));
                        }
                        real = !real;
                    }
                } else {
                    for(int b=0; b < img.getNumBands(); ++b) {
                        final String imgName = img.getName().toLowerCase();
                        bandName = "Amplitude_" + polarizationMap.get(imgName);
                        final Band band = new Band(bandName, img.getDataType(),
                                           img.getSceneWidth(), img.getSceneHeight());
                        band.setUnit(Unit.AMPLITUDE);

                        product.addBand(band);
                        bandMap.put(band, new ImageIOFile.BandInfo(img, i, b));

                        ReaderUtils.createVirtualIntensityBand(product, band,
                                    '_'+polarizationMap.get(imgName));
                    }
                }
            }
        }
    }

    @Override
    protected void addAbstractedMetadataHeader(Product product, MetadataElement root) {

        final MetadataElement absRoot = AbstractMetadata.addAbstractedMetadataHeader(root);

        final String defStr = AbstractMetadata.NO_METADATA_STRING;
        final int defInt = AbstractMetadata.NO_METADATA;

        final MetadataElement productElem = root.getElement("product");

        // sourceAttributes
        final MetadataElement sourceAttributes = productElem.getElement("sourceAttributes");

        final MetadataElement radarParameters = sourceAttributes.getElement("radarParameters");
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.SPH_DESCRIPTOR,
                radarParameters.getAttributeString("acquisitionType", defStr));
        
        final MetadataElement pulseRepetitionFrequency = radarParameters.getElement("pulseRepetitionFrequency");
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.pulse_repetition_frequency,
                pulseRepetitionFrequency.getAttributeDouble("pulseRepetitionFrequency", defInt));
        final MetadataElement radarCenterFrequency = radarParameters.getElement("radarCenterFrequency");
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.radar_frequency,
                radarCenterFrequency.getAttributeDouble("radarCenterFrequency", defInt) / 1000000.0);

        final MetadataElement orbitAndAttitude = sourceAttributes.getElement("orbitAndAttitude");
        final MetadataElement orbitInformation = orbitAndAttitude.getElement("orbitInformation");
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PASS,
                orbitInformation.getAttributeString("passDirection", defStr).toUpperCase());
        final String orbitFile = orbitInformation.getAttributeString("orbitDataFile", defStr);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.orbit_state_vector_file, orbitFile);

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ABS_ORBIT,
                Integer.parseInt(orbitFile.substring(0, orbitFile.indexOf("_")).trim()));

        // imageGenerationParameters
        final MetadataElement imageGenerationParameters = productElem.getElement("imageGenerationParameters");
        final MetadataElement generalProcessingInformation = imageGenerationParameters.getElement("generalProcessingInformation");

        productType = generalProcessingInformation.getAttributeString("productType", defStr);
        if(productType.contains("SLC"))
            isSLC = true;

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT_TYPE, productType);
        productName = getMission() +'-'+ productType + '-' + productElem.getAttributeString("productId", defStr);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT, productName);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.MISSION, getMission());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ProcessingSystemIdentifier,
                generalProcessingInformation.getAttributeString("processingFacility", defStr) +"-"+
                generalProcessingInformation.getAttributeString("softwareVersion", defStr));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PROC_TIME,
                ReaderUtils.getTime(generalProcessingInformation, "processingTime", timeFormat));

        final MetadataElement sarProcessingInformation = imageGenerationParameters.getElement("sarProcessingInformation");
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ant_elev_corr_flag,
                getFlag(sarProcessingInformation, "elevationPatternCorrection"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spread_comp_flag,
                getFlag(sarProcessingInformation, "rangeSpreadingLossCorrection"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.srgr_flag, isSLC ? 0 : 1);

        final ProductData.UTC startTime = ReaderUtils.getTime(sarProcessingInformation, "zeroDopplerTimeFirstLine", timeFormat);
        final ProductData.UTC stopTime = ReaderUtils.getTime(sarProcessingInformation, "zeroDopplerTimeLastLine", timeFormat);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_line_time, startTime);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_line_time, stopTime);
        product.setStartTime(startTime);
        product.setEndTime(stopTime);

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_line_time, startTime);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_line_time, stopTime);

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_looks,
                sarProcessingInformation.getAttributeInt("numberOfRangeLooks", defInt));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_looks,
                sarProcessingInformation.getAttributeInt("numberOfAzimuthLooks", defInt));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.slant_range_to_first_pixel,
                sarProcessingInformation.getElement("slantRangeNearEdge").getAttributeDouble("slantRangeNearEdge"));

        // imageAttributes
        final MetadataElement imageAttributes = productElem.getElement("imageAttributes");
        final MetadataElement rasterAttributes = imageAttributes.getElement("rasterAttributes");

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.SAMPLE_TYPE, getDataType(rasterAttributes));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_output_lines,
                rasterAttributes.getAttributeInt("numberOfLines", defInt));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_samples_per_line,
                rasterAttributes.getAttributeInt("numberOfSamplesPerLine", defInt));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.TOT_SIZE, ReaderUtils.getTotalSize(product));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.line_time_interval,
                ReaderUtils.getLineTimeInterval(startTime, stopTime, product.getSceneRasterHeight()));

        final MetadataElement sampledPixelSpacing = rasterAttributes.getElement("sampledPixelSpacing");
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spacing,
                sampledPixelSpacing.getAttributeDouble("sampledPixelSpacing", defInt));
        final MetadataElement sampledLineSpacing = rasterAttributes.getElement("sampledLineSpacing");
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_spacing,
                sampledLineSpacing.getAttributeDouble("sampledLineSpacing", defInt));

        // polarizations
        getPolarizations(imageAttributes);

        addOrbitStateVectors(absRoot, orbitInformation);
        addSRGRCoefficients(absRoot, imageGenerationParameters);
    }

    private static int getFlag(MetadataElement elem, String tag) {
        String valStr = elem.getAttributeString(tag, " ").toUpperCase();
        if(valStr.equals("FALSE") || valStr.equals("0"))
            return 0;
        else if(valStr.equals("TRUE") || valStr.equals("1"))
            return 1;
        return -1;
    }

    private void getPolarizations(MetadataElement imageAttributes) {
        final MetadataElement[] imageAttribElems = imageAttributes.getElements();
        for(MetadataElement elem : imageAttribElems) {
            if(elem.getName().equals("fullResolutionImageData")) {

                polarizationMap.put(elem.getAttributeString("fullResolutionImageData", "").toLowerCase(),
                                    elem.getAttributeString("pole", "").toUpperCase());
            }
        }
    }

    private static String getDataType(MetadataElement rasterAttributes) {
        final String dataType = rasterAttributes.getAttributeString("dataType", AbstractMetadata.NO_METADATA_STRING).toUpperCase();
        if(dataType.contains("COMPLEX"))
            return "COMPLEX";
        return "DETECTED";
    }

    private static void addOrbitStateVectors(MetadataElement absRoot, MetadataElement orbitInformation) {
        final MetadataElement orbitVectorListElem = absRoot.getElement(AbstractMetadata.orbit_state_vectors);

        final MetadataElement[] stateVectorElems = orbitInformation.getElements();
        for(int i=1; i <= stateVectorElems.length; ++i) {
            addVector(AbstractMetadata.orbit_vector, orbitVectorListElem, stateVectorElems[i-1], i);
        }

        // set state vector time
        if(absRoot.getAttributeUTC(AbstractMetadata.STATE_VECTOR_TIME, new ProductData.UTC(0)).
                equalElems(new ProductData.UTC(0))) {

            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.STATE_VECTOR_TIME,
                ReaderUtils.getTime(stateVectorElems[0], "timeStamp", timeFormat));
        }
    }

    private static void addVector(String name, MetadataElement orbitVectorListElem,
                                  MetadataElement srcElem, int num) {
        final MetadataElement orbitVectorElem = new MetadataElement(name+num);

        orbitVectorElem.setAttributeUTC(AbstractMetadata.orbit_vector_time,
                ReaderUtils.getTime(srcElem, "timeStamp", timeFormat));

        final MetadataElement xpos = srcElem.getElement("xPosition");
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_x_pos,
                xpos.getAttributeDouble("xPosition", 0));
        final MetadataElement ypos = srcElem.getElement("yPosition");
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_y_pos,
                ypos.getAttributeDouble("yPosition", 0));
        final MetadataElement zpos = srcElem.getElement("zPosition");
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_z_pos,
                zpos.getAttributeDouble("zPosition", 0));
        final MetadataElement xvel = srcElem.getElement("xVelocity");
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_x_vel,
                xvel.getAttributeDouble("xVelocity", 0));
        final MetadataElement yvel = srcElem.getElement("yVelocity");
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_y_vel,
                yvel.getAttributeDouble("yVelocity", 0));
        final MetadataElement zvel = srcElem.getElement("zVelocity");
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_z_vel,
                zvel.getAttributeDouble("zVelocity", 0));

        orbitVectorListElem.addElement(orbitVectorElem);
    }

    private static void addSRGRCoefficients(final MetadataElement absRoot, final MetadataElement imageGenerationParameters) {
        final MetadataElement srgrCoefficientsElem = absRoot.getElement(AbstractMetadata.srgr_coefficients);

        int listCnt = 1;
        for(MetadataElement elem : imageGenerationParameters.getElements()) {
            if(elem.getName().equalsIgnoreCase("slantRangeToGroundRange")) {
                final MetadataElement srgrListElem = new MetadataElement(AbstractMetadata.srgr_coef_list+'.'+listCnt);
                srgrCoefficientsElem.addElement(srgrListElem);
                ++listCnt;

                final ProductData.UTC utcTime = ReaderUtils.getTime(elem, "zeroDopplerAzimuthTime", timeFormat);
                srgrListElem.setAttributeUTC(AbstractMetadata.srgr_coef_time, utcTime);

                final double grOrigin = elem.getElement("groundRangeOrigin").getAttributeDouble("groundRangeOrigin", 0);
                AbstractMetadata.addAbstractedAttribute(srgrListElem, AbstractMetadata.ground_range_origin,
                        ProductData.TYPE_FLOAT64, "m", "Ground Range Origin");
                AbstractMetadata.setAttribute(srgrListElem, AbstractMetadata.ground_range_origin, grOrigin);

                final String coeffStr = elem.getAttributeString("groundToSlantRangeCoefficients", "");
                if(!coeffStr.isEmpty()) {
                    final StringTokenizer st = new StringTokenizer(coeffStr);
                    int cnt = 1;
                    while(st.hasMoreTokens()) {
                        final double coefValue = Double.parseDouble(st.nextToken());

                        final MetadataElement coefElem = new MetadataElement(AbstractMetadata.coefficient+'.'+cnt);
                        srgrListElem.addElement(coefElem);
                        ++cnt;
                        AbstractMetadata.addAbstractedAttribute(coefElem, AbstractMetadata.srgr_coef,
                                ProductData.TYPE_FLOAT64, "", "SRGR Coefficient");
                        AbstractMetadata.setAttribute(coefElem, AbstractMetadata.srgr_coef, coefValue);
                    }
                }
            }
        }
    }

    @Override
    protected void addGeoCoding(final Product product) {

        final MetadataElement root = product.getMetadataRoot();
        final MetadataElement productElem = root.getElement("product");
        final MetadataElement imageAttributes = productElem.getElement("imageAttributes");
        final MetadataElement geographicInformation = imageAttributes.getElement("geographicInformation");
        final MetadataElement geolocationGrid = geographicInformation.getElement("geolocationGrid");

        final MetadataElement[] geoGrid = geolocationGrid.getElements();

        float[] latList = new float[geoGrid.length];
        float[] lngList = new float[geoGrid.length];

        int gridWidth = 0, gridHeight = 0;
        int i=0;
        for(MetadataElement imageTiePoint : geoGrid) {
            final MetadataElement geodeticCoordinate = imageTiePoint.getElement("geodeticCoordinate");
            final MetadataElement latitude = geodeticCoordinate.getElement("latitude");
            final MetadataElement longitude = geodeticCoordinate.getElement("longitude");
            latList[i] = (float)latitude.getAttributeDouble("latitude", 0);
            lngList[i] = (float)longitude.getAttributeDouble("longitude", 0);

            final MetadataElement imageCoordinate = imageTiePoint.getElement("imageCoordinate");
            final double pix = imageCoordinate.getAttributeDouble("pixel", 0);
            if(pix == 0) {
                if(gridWidth == 0)
                    gridWidth = i;
                ++gridHeight;
            }

            ++i;
        }

        float subSamplingX = (float)product.getSceneRasterWidth() / (gridWidth - 1);
        float subSamplingY = (float)product.getSceneRasterHeight() / (gridHeight - 1);

        final TiePointGrid latGrid = new TiePointGrid("latitude", gridWidth, gridHeight, 0.5f, 0.5f,
                subSamplingX, subSamplingY, latList);
        latGrid.setUnit(Unit.DEGREES);

        final TiePointGrid lonGrid = new TiePointGrid("longitude", gridWidth, gridHeight, 0.5f, 0.5f,
                subSamplingX, subSamplingY, lngList, TiePointGrid.DISCONT_AT_180);
        lonGrid.setUnit(Unit.DEGREES);

        final TiePointGeoCoding tpGeoCoding = new TiePointGeoCoding(latGrid, lonGrid, Datum.WGS_84);

        product.addTiePointGrid(latGrid);
        product.addTiePointGrid(lonGrid);
        product.setGeoCoding(tpGeoCoding);

        setLatLongMetadata(product, latGrid, lonGrid);
    }

    private static void setLatLongMetadata(Product product, TiePointGrid latGrid, TiePointGrid lonGrid) {
        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(product);

        final int w = product.getSceneRasterWidth();
        final int h = product.getSceneRasterHeight();

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_near_lat, latGrid.getPixelFloat(0, 0));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_near_long, lonGrid.getPixelFloat(0, 0));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_far_lat, latGrid.getPixelFloat(w, 0));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_far_long, lonGrid.getPixelFloat(w, 0));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_near_lat, latGrid.getPixelFloat(0, h));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_near_long, lonGrid.getPixelFloat(0, h));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_far_lat, latGrid.getPixelFloat(w, h));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_far_long, lonGrid.getPixelFloat(w, h));
    }

    @Override
    protected void addTiePointGrids(final Product product) {
        final MetadataElement root = product.getMetadataRoot();
        final MetadataElement productElem = root.getElement("product");
        final MetadataElement imageGenerationParameters = productElem.getElement("imageGenerationParameters");
        final MetadataElement sarProcessingInformation = imageGenerationParameters.getElement("sarProcessingInformation");

        final MetadataElement incidenceAngleNearRange = sarProcessingInformation.getElement("incidenceAngleNearRange");
        final float nearRange = (float)incidenceAngleNearRange.getAttributeDouble("incidenceAngleNearRange", 0);
        final MetadataElement incidenceAngleFarRange = sarProcessingInformation.getElement("incidenceAngleFarRange");
        final float farRange = (float)incidenceAngleFarRange.getAttributeDouble("incidenceAngleFarRange", 0);

        float[] incidenceCorners = new float[] { nearRange, farRange, nearRange, farRange };

        final int gridWidth = 4;
        final int gridHeight = 4;
        final float subSamplingX = (float)product.getSceneRasterWidth() / (float)(gridWidth - 1);
        final float subSamplingY = (float)product.getSceneRasterHeight() / (float)(gridHeight - 1);

        final float[] fineAngles = new float[gridWidth*gridHeight];

        ReaderUtils.createFineTiePointGrid(2, 2, gridWidth, gridHeight, incidenceCorners, fineAngles);

        final TiePointGrid incidentAngleGrid = new TiePointGrid("incident_angle", gridWidth, gridHeight, 0, 0,
                subSamplingX, subSamplingY, fineAngles);
        incidentAngleGrid.setUnit(Unit.DEGREES);

        product.addTiePointGrid(incidentAngleGrid);

        addSlantRangeTime(product, imageGenerationParameters);
    }

    private static void addSlantRangeTime(final Product product, final MetadataElement imageGenerationParameters) {

        class coefList {
            double utcSeconds = 0.0;
            double grOrigin = 0.0;
            final ArrayList<Double> coefficients = new ArrayList<Double>();
        }

        final ArrayList<coefList> segmentsArray = new ArrayList<coefList>();

        for(MetadataElement elem : imageGenerationParameters.getElements()) {
            if(elem.getName().equalsIgnoreCase("slantRangeToGroundRange")) {
                final coefList coef = new coefList();
                segmentsArray.add(coef);
                coef.utcSeconds = ReaderUtils.getTime(elem, "zeroDopplerAzimuthTime", timeFormat).getMJD() * 24 * 3600;
                coef.grOrigin = elem.getElement("groundRangeOrigin").getAttributeDouble("groundRangeOrigin", 0);

                final String coeffStr = elem.getAttributeString("groundToSlantRangeCoefficients", "");
                if(!coeffStr.isEmpty()) {
                    final StringTokenizer st = new StringTokenizer(coeffStr);
                    while(st.hasMoreTokens()) {
                        coef.coefficients.add(Double.parseDouble(st.nextToken()));
                    }
                }
            }
        }

        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(product);
        final double lineTimeInterval = absRoot.getAttributeDouble(AbstractMetadata.line_time_interval, 0);
        final ProductData.UTC startTime = absRoot.getAttributeUTC(AbstractMetadata.first_line_time, new ProductData.UTC(0));
        final double startSeconds = startTime.getMJD() * 24 * 3600;
        final double pixelSpacing = absRoot.getAttributeDouble(AbstractMetadata.range_spacing, 0);

        final double halfSpeedOfLight = 299792458 / 2.0; // in m/s

        final int gridWidth = 11;
        final int gridHeight = 11;
        final int sceneWidth = product.getSceneRasterWidth();
        final int sceneHeight = product.getSceneRasterHeight();
        final int subSamplingX = sceneWidth / (gridWidth - 1);
        final int subSamplingY = sceneHeight / (gridHeight - 1);
        final float[] rangeDist = new float[gridWidth*gridHeight];
        final float[] rangeTime = new float[gridWidth*gridHeight];

        final coefList[] segments = segmentsArray.toArray(new coefList[segmentsArray.size()]);

        int k = 0;
        int c = 0;
        for (int j = 0; j < gridHeight; j++) {
            final double time = startSeconds + (j*lineTimeInterval);
            while(c < segments.length && segments[c].utcSeconds < time)
                ++c;
            if(c >= segments.length)
                c = segments.length-1;

            final coefList coef = segments[c];
            final double GR0 = coef.grOrigin;
            final double s0 = coef.coefficients.get(0);
            final double s1 = coef.coefficients.get(1);
            final double s2 = coef.coefficients.get(2);
            final double s3 = coef.coefficients.get(3);
            final double s4 = coef.coefficients.get(4);

            for(int i = 0; i < gridWidth; i++) {
                int x = i*subSamplingX;
                final double GR = x * pixelSpacing;
                final double g = GR-GR0;
                final double g2 = g*g;

                //SlantRange = s0 + s1(GR - GR0) + s2(GR-GR0)^2 + s3(GRGR0)^3 + s4(GR-GR0)^4;
                rangeDist[k++] = (float)(s0 + s1*g + s2*g2 + s3*g2*g + s4*g2*g2);
            }
        }

        // get slant range time in nanoseconds from range distance in meters
        for(int i = 0; i < rangeDist.length; i++) {
             rangeTime[i] = (float)(rangeDist[i] / halfSpeedOfLight * 1000000000.0); // in ns
        }

        final TiePointGrid slantRangeGrid = new TiePointGrid(
                "slant_range_time", gridWidth, gridHeight, 0, 0, subSamplingX, subSamplingY, rangeTime);

        product.addTiePointGrid(slantRangeGrid);
        slantRangeGrid.setUnit(Unit.NANOSECONDS);
    }

    private static String getMission() {
        return "RS2";
    }

    @Override
    protected String getProductName() {
        return productName;
    }

    @Override
    protected String getProductDescription() {
        return productDescription;
    }

    @Override
    protected String getProductType() {
        return productType;
    }
}