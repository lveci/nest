package org.esa.nest.dataio.terrasarx;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.util.Guardian;
import org.esa.beam.dataio.dimap.FileImageInputStreamExtImpl;
import org.esa.nest.dataio.XMLProductDirectory;
import org.esa.nest.dataio.ReaderUtils;
import org.esa.nest.dataio.ImageIOFile;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;
import org.jdom.Element;
import org.jdom.Text;

import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * This class represents a product directory.
 *
 */
public class TerraSarXProductDirectory extends XMLProductDirectory {

    private String productName = "TerraSar-X";
    private String productType = "TerraSar-X";
    private String productDescription = "";

    private final float[] latCorners = new float[4];
    private final float[] lonCorners = new float[4];
    private final float[] slantRangeCorners = new float[4];
    private final float[] incidenceCorners = new float[4];

    private final ArrayList<File> cosarFileList = new ArrayList<File>(1);
    private final Map<Band, ImageInputStream> cosarBandMap = new HashMap<Band, ImageInputStream>(1);

    public TerraSarXProductDirectory(final File headerFile, final File imageFolder) {
        super(headerFile, imageFolder);
    }

    @Override
    protected void addAbstractedMetadataHeader(Product product, MetadataElement root) {

        final MetadataElement absRoot = AbstractMetadata.addAbstractedMetadataHeader(root);

        final String defStr = AbstractMetadata.NO_METADATA_STRING;
        final int defInt = AbstractMetadata.NO_METADATA;

        final MetadataElement level1Elem = root.getElementAt(1);
        final MetadataElement generalHeader = level1Elem.getElement("generalHeader");
        final MetadataElement productInfo = level1Elem.getElement("productInfo");
        final MetadataElement missionInfo = productInfo.getElement("missionInfo");
        final MetadataElement productVariantInfo = productInfo.getElement("productVariantInfo");
        final MetadataElement imageDataInfo = productInfo.getElement("imageDataInfo");
        final MetadataElement sceneInfo = productInfo.getElement("sceneInfo");
        final MetadataElement processing = level1Elem.getElement("processing");
        final MetadataElement instrument = level1Elem.getElement("instrument");
        
        MetadataAttribute attrib = generalHeader.getAttribute("fileName");
        if(attrib != null)
            productName = attrib.getData().getElemString();

        //mph
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT, productName);
        productType = productVariantInfo.getAttributeString("productType", defStr);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT_TYPE, productType);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.SPH_DESCRIPTOR,
                generalHeader.getAttributeString("itemName", defStr));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.MISSION, "TSX1");
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PROC_TIME, getTime(generalHeader, "generationTime"));

        MetadataElement elem = generalHeader.getElement("generationSystem");
        if(elem != null) {
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ProcessingSystemIdentifier,
                elem.getAttributeString("generationSystem", defStr));
        }

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.CYCLE, missionInfo.getAttributeInt("orbitCycle", defInt));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.REL_ORBIT, missionInfo.getAttributeInt("relOrbit", defInt));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ABS_ORBIT, missionInfo.getAttributeInt("absOrbit", defInt));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PASS, missionInfo.getAttributeString("orbitDirection", defStr));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.SAMPLE_TYPE, imageDataInfo.getAttributeString("imageDataType", defStr));

        final MetadataElement acquisitionInfo = productInfo.getElement("acquisitionInfo");
        final MetadataElement polarisationList = acquisitionInfo.getElement("polarisationList");
        MetadataAttribute[] polList = polarisationList.getAttributes();
        if(polList.length > 0) {
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.mds1_tx_rx_polar, polList[0].getData().getElemString());
        } if(polList.length > 1) {
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.mds2_tx_rx_polar, polList[1].getData().getElemString());
        }

        final ProductData.UTC startTime = getTime(sceneInfo.getElement("start"), "timeUTC");
        final ProductData.UTC stopTime = getTime(sceneInfo.getElement("stop"), "timeUTC");
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_line_time, startTime);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_line_time, stopTime);
        product.setStartTime(startTime);
        product.setEndTime(stopTime);

        getCornerCoords(sceneInfo);

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_near_lat, latCorners[0]);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_near_long, lonCorners[0]);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_far_lat, latCorners[1]);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_far_long, lonCorners[1]);

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_near_lat, latCorners[2]);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_near_long, lonCorners[2]);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_far_lat, latCorners[3]);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_far_long, lonCorners[3]);  

        final MetadataElement imageRaster = imageDataInfo.getElement("imageRaster");
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_looks,
                imageRaster.getAttributeDouble("azimuthLooks", defInt));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_looks,
                imageRaster.getAttributeDouble("rangeLooks", defInt));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_output_lines,
                imageRaster.getAttributeInt("numberOfRows", defInt));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_samples_per_line,
                imageRaster.getAttributeInt("numberOfColumns", defInt));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.TOT_SIZE, getTotalSize(product));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.data_type, getDataTypeString());

        final MetadataElement rowSpacing = imageRaster.getElement("rowSpacing");
        final MetadataElement columnSpacing = imageRaster.getElement("columnSpacing");
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_spacing,
                columnSpacing.getAttributeDouble("columnSpacing", defInt));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spacing,
                rowSpacing.getAttributeDouble("rowSpacing", defInt));

        final MetadataElement settings = instrument.getElement("settings");
        final MetadataElement settingRecord = settings.getElement("settingRecord");
        final MetadataElement PRF = settingRecord.getElement("PRF");
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.pulse_repetition_frequency,
                PRF.getAttributeDouble("PRF", defInt));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.line_time_interval,
                ReaderUtils.getLineTimeInterval(startTime, stopTime, product.getSceneRasterHeight()));

        setFlag(productVariantInfo, "projection", "GROUNDRANGE", absRoot, AbstractMetadata.srgr_flag);
        setFlag(productVariantInfo, "radiometricCorrection", "CALIBRATED", absRoot, AbstractMetadata.abs_calibration_flag);
        
        final MetadataElement processingFlags = processing.getElement("processingFlags");
        setFlag(processingFlags, "rangeSpreadingLossCorrectedFlag", "true", absRoot, AbstractMetadata.range_spread_comp_flag);
        setFlag(processingFlags, "elevationPatternCorrectedFlag", "true", absRoot, AbstractMetadata.ant_elev_corr_flag);

        final MetadataElement calibration = level1Elem.getElement("calibration");
        if(calibration != null) {
            final MetadataElement calibrationConstant = calibration.getElement("calibrationConstant");
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.calibration_factor,
                    calibrationConstant.getAttributeDouble("calFactor", defInt));
        }
    }

    private static void setFlag(MetadataElement elem, String attribTag, String trueValue,
                                MetadataElement absRoot, String absTag) {
        int val = 0;
        if(elem.getAttributeString(attribTag, " ").equalsIgnoreCase(trueValue))
            val = 1;
        AbstractMetadata.setAttribute(absRoot, absTag, val);
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

    private void getCornerCoords(MetadataElement sceneInfo) {

        int maxRow = 0, maxCol = 0;
        int minRow = Integer.MAX_VALUE, minCol = Integer.MAX_VALUE;
        final ArrayList<CornerCoord> coordList = new ArrayList<CornerCoord>();

        final MetadataElement[] children = sceneInfo.getElements();
        for(MetadataElement child : children) {
            if(child.getName().equals("sceneCornerCoord")) {
                final int refRow = child.getAttributeInt("refRow", 0);
                final int refCol = child.getAttributeInt("refColumn", 0);

                coordList.add( new CornerCoord(refRow, refCol,
                                                (float)child.getAttributeDouble("lat", 0),
                                                (float)child.getAttributeDouble("lon", 0),
                                                (float)child.getAttributeDouble("rangeTime", 0),
                                                (float)child.getAttributeDouble("incidenceAngle", 0)) );

                if(refRow > maxRow) maxRow = refRow;
                if(refCol > maxCol) maxCol = refCol;
                if(refRow < minRow) minRow = refRow;
                if(refCol < minCol) minCol = refCol;
            }
        }

        int index = 0;
        for(CornerCoord coord : coordList) {
            if(minRow == maxRow && minCol == maxCol) {
                latCorners[index] = coord.lat;
                lonCorners[index] = coord.lon;
                slantRangeCorners[index] = coord.rangeTime;
                incidenceCorners[index] = coord.incidenceAngle;
                ++index;
            } else {
                index = -1;
                if(coord.refRow == minRow) {
                    if(Math.abs(coord.refCol - minCol) < Math.abs(coord.refCol - maxCol)) {            // UL
                        index = 0;
                    } else {     // UR
                        index = 1;
                    }
                } else if(coord.refRow == maxRow) {
                    if(Math.abs(coord.refCol - minCol) < Math.abs(coord.refCol - maxCol)) {            // LL
                        index = 2;
                    } else {     // LR
                        index = 3;
                    }
                }
                if(index >= 0) {
                    latCorners[index] = coord.lat;
                    lonCorners[index] = coord.lon;
                    slantRangeCorners[index] = coord.rangeTime;
                    incidenceCorners[index] = coord.incidenceAngle;
                }
            }
        }
    }

    @Override
    protected void addImageFile(final File file) throws IOException {
        if (file.getName().toUpperCase().endsWith("COS")) {
            cosarFileList.add(file);

            setSceneDimensions();
        } else {
            super.addImageFile(file);
        }
    }

    private void setSceneDimensions() throws IOException {

        final Element root = getXMLRootElement();
        final Element productInfo = getElement(root, "productInfo");
        final Element imageDataInfo = getElement(productInfo, "imageDataInfo");
        final Element imageRaster = getElement(imageDataInfo, "imageRaster");
        final Element numRows = getElement(imageRaster, "numberOfRows");
        final Element numColumns = getElement(imageRaster, "numberOfColumns");


        final int width = Integer.parseInt(getElementText(numRows).getValue());
        final int height = Integer.parseInt(getElementText(numColumns).getValue());
        setSceneWidthHeight(width, height);
    }

    private static Text getElementText(final Element root) throws IOException {
        final List children = root.getContent();
        for (Object aChild : children) {
            if (aChild instanceof Text) {
                return (Text)aChild;
            }
        }
        throw new IOException("Element Text not found");
    }

    private static Element getElement(final Element root, final String name) throws IOException {
        final List children = root.getContent();
        for (Object aChild : children) {
            if (aChild instanceof Element) {
                final Element elem = (Element) aChild;
                if(elem.getName().equalsIgnoreCase(name))
                    return elem;
            }
        }
        throw new IOException("Element "+name+" not found");
    }

    @Override
    protected void addGeoCoding(final Product product) {

        addGeoCoding(product, latCorners, lonCorners);
    }

    @Override
    protected void addTiePointGrids(final Product product) {

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

        final float[] fineSlantRange = new float[gridWidth*gridHeight];

        ReaderUtils.createFineTiePointGrid(2, 2, gridWidth, gridHeight, slantRangeCorners, fineSlantRange);

        final TiePointGrid slantRangeGrid = new TiePointGrid("slant_range", gridWidth, gridHeight, 0, 0,
                subSamplingX, subSamplingY, fineSlantRange);
        slantRangeGrid.setUnit(Unit.NANOSECONDS);

        product.addTiePointGrid(slantRangeGrid);
    }

    @Override
    protected void addBands(final Product product) {
        int bandCnt = 1;
        final Set ImageKeys = bandImageFileMap.keySet();                           // The set of keys in the map.
        for (Object key : ImageKeys) {
            final ImageIOFile img = bandImageFileMap.get(key);

            for(int i=0; i < img.getNumImages(); ++i) {

                for(int b=0; b < img.getNumBands(); ++b) {
                    final Band band = new Band(img.getName()+bandCnt++, img.getDataType(),
                                       img.getSceneWidth(), img.getSceneHeight());
                    band.setUnit(Unit.AMPLITUDE);
                    product.addBand(band);

                    ReaderUtils.createVirtualIntensityBand(product, band, '_'+img.getName());

                    bandMap.put(band, new ImageIOFile.BandInfo(img, i, b));
                }
            }
        }

        if(!cosarFileList.isEmpty()) {
            final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(product);
            final int width = absRoot.getAttributeInt(AbstractMetadata.num_samples_per_line, 0);
            final int height = absRoot.getAttributeInt(AbstractMetadata.num_output_lines, 0);

            for (final File file : cosarFileList) {

                // todo get polarizations

                final Band realBand = new Band("i_"+file.getName(),
                        ProductData.TYPE_INT16,
                        width, height);
                realBand.setUnit(Unit.REAL);
                product.addBand(realBand);

                final Band imaginaryBand = new Band("q_"+file.getName(),
                        ProductData.TYPE_INT16,
                        width, height);
                imaginaryBand.setUnit(Unit.IMAGINARY);
                product.addBand(imaginaryBand);

                ReaderUtils.createVirtualIntensityBand(product, realBand, imaginaryBand, '_'+file.getName());
                ReaderUtils.createVirtualPhaseBand(product, realBand, imaginaryBand, '_'+file.getName());

                try {
                    cosarBandMap.put(realBand, FileImageInputStreamExtImpl.createInputStream(file));
                    cosarBandMap.put(imaginaryBand, FileImageInputStreamExtImpl.createInputStream(file));
                } catch(Exception e) {
                    //
                }
            }
        }
    }

    ImageInputStream getCosarImageInputStream(final Band band) {
        return cosarBandMap.get(band);
    }

    @Override
    public void close() throws IOException {
        super.close();
        final Set keys = cosarBandMap.keySet();                           // The set of keys in the map.
        for (Object key : keys) {
            final ImageInputStream img = cosarBandMap.get(key);
            img.close();
        }
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

    private static class CornerCoord {
        final int refRow, refCol;
        final float lat, lon;
        final float rangeTime, incidenceAngle;

        CornerCoord(int row, int col, float lt, float ln, float range, float angle) {
            refRow = row; refCol = col;
            lat = lt; lon = ln;
            rangeTime = range; incidenceAngle = angle;
        }
    }
}