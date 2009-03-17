package org.esa.nest.dataio.radarsat2;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.util.Guardian;
import org.esa.nest.dataio.XMLProductDirectory;
import org.esa.nest.dataio.ImageIOFile;
import org.esa.nest.dataio.ReaderUtils;
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
    private String productDescription = "";

    private boolean isSLC = false;
    private transient Map<String, String> polarizationMap = new HashMap<String, String>(4);

    public Radarsat2ProductDirectory(final File headerFile, final File imageFolder) {
        super(headerFile, imageFolder);
    }

    @Override
    protected void addBands(final Product product) {

        if(productType.contains("SLC"))
            isSLC = true;

        String bandName;
        boolean real = true;
        Band lastRealBand = null;
        String unit;

        final Set keys = bandImageFileMap.keySet();                           // The set of keys in the map.
        for (Object key : keys) {
            final ImageIOFile img = bandImageFileMap.get(key);

            for(int i=0; i < img.getNumImages(); ++i) {

                if(isSLC) {
                    for(int b=0; b < img.getNumBands(); ++b) {
                        if(real) {
                            bandName = "i_" + polarizationMap.get(img.getName());
                            unit = Unit.REAL;
                        } else {
                            bandName = "q_" + polarizationMap.get(img.getName());
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
                                    '_'+polarizationMap.get(img.getName()));
                            ReaderUtils.createVirtualPhaseBand(product, lastRealBand, band,
                                    '_'+polarizationMap.get(img.getName()));
                        }
                        real = !real;
                    }
                } else {
                    for(int b=0; b < img.getNumBands(); ++b) {
                        bandName = "Amplitude_" + polarizationMap.get(img.getName());
                        final Band band = new Band(bandName, img.getDataType(),
                                           img.getSceneWidth(), img.getSceneHeight());
                        band.setUnit(Unit.AMPLITUDE);

                        product.addBand(band);
                        bandMap.put(band, new ImageIOFile.BandInfo(img, i, b));

                        ReaderUtils.createVirtualIntensityBand(product, band,
                                    '_'+polarizationMap.get(img.getName()));
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
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.orbit_state_vector_file,
                orbitInformation.getAttributeString("orbitDataFile", defStr));

        // imageGenerationParameters
        final MetadataElement imageGenerationParameters = productElem.getElement("imageGenerationParameters");
        final MetadataElement generalProcessingInformation = imageGenerationParameters.getElement("generalProcessingInformation");

        productType = generalProcessingInformation.getAttributeString("productType", defStr);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT_TYPE, productType);
        productName = getMission() +'-'+ productType + '-' + productElem.getAttributeString("productId", defStr);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT, productName);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.MISSION, getMission());

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PROC_TIME, getTime(generalProcessingInformation, "processingTime"));

        final MetadataElement sarProcessingInformation = imageGenerationParameters.getElement("sarProcessingInformation");
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ant_elev_corr_flag,
                getFlag(sarProcessingInformation, "elevationPatternCorrection"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spread_comp_flag,
                getFlag(sarProcessingInformation, "rangeSpreadingLossCorrection"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.abs_calibration_flag, 
                getFlag(sarProcessingInformation, "rawDataCorrection"));

        final ProductData.UTC startTime = getTime(sarProcessingInformation, "zeroDopplerTimeFirstLine");
        final ProductData.UTC stopTime = getTime(sarProcessingInformation, "zeroDopplerTimeLastLine");
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

        // imageAttributes
        final MetadataElement imageAttributes = productElem.getElement("imageAttributes");
        final MetadataElement rasterAttributes = imageAttributes.getElement("rasterAttributes");

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.SAMPLE_TYPE,
                rasterAttributes.getAttributeString("dataType", defStr).toUpperCase());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_output_lines,
                rasterAttributes.getAttributeInt("numberOfLines", defInt));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_samples_per_line,
                rasterAttributes.getAttributeInt("numberOfSamplesPerLine", defInt));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.TOT_SIZE, getTotalSize(product));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.data_type, getDataTypeString());
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

        
    }

    private static int getFlag(MetadataElement elem, String tag) {
        String valStr = elem.getAttributeString(tag, " ").toUpperCase();
        if(valStr.equals("FALSE") || valStr.equals("0"))
            return 0;
        else if(valStr.equals("TRUE") || valStr.equals("1"))
            return 1;
        return -1;
    }

    private static ProductData.UTC getTime(MetadataElement elem, String tag) {
        final String timeStr = createValidUTCString(elem.getAttributeString(tag, " ").toUpperCase(),
                new char[]{':','.','-'}, ' ').trim();
        return AbstractMetadata.parseUTC(timeStr, "yyyy-MM-dd HH:mm:ss");
    }

    private static String createValidUTCString(String name, char[] validChars, char replaceChar) {
        Guardian.assertNotNull("name", name);
        char[] sortedValidChars = null;
        if (validChars == null) {
            sortedValidChars = new char[0];
        } else {
            sortedValidChars = (char[]) validChars.clone();
        }
        Arrays.sort(sortedValidChars);
        StringBuilder validName = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            final char ch = name.charAt(i);
            if (Character.isDigit(ch)) {
                validName.append(ch);
            } else if (Arrays.binarySearch(sortedValidChars, ch) >= 0) {
                validName.append(ch);
            } else {
                validName.append(replaceChar);
            }
        }
        return validName.toString();
    }

    private void getPolarizations(MetadataElement imageAttributes) {
        MetadataElement[] imageAttribElems = imageAttributes.getElements();
        for(MetadataElement elem : imageAttribElems) {
            if(elem.getName().equals("fullResolutionImageData")) {

                polarizationMap.put(elem.getAttributeString("fullResolutionImageData", ""),
                                    elem.getAttributeString("pole", ""));
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
        final MetadataElement root = product.getMetadataRoot();
        final MetadataElement absRoot = root.getElement(Product.ABSTRACTED_METADATA_ROOT_NAME);

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
    }

    public static String getMission() {
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