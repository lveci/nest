package org.esa.nest.dataio.terrasarx;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.util.Guardian;
import org.esa.nest.dataio.XMLProductDirectory;
import org.esa.nest.datamodel.AbstractMetadata;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;

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

    public TerraSarXProductDirectory(final File headerFile, final File imageFolder) {
        super(headerFile, imageFolder);
    }

    @Override
    protected void addAbstractedMetadataHeader(Product product, MetadataElement root) {

        AbstractMetadata.addAbstractedMetadataHeader(root);

        final MetadataElement absRoot = root.getElement(Product.ABSTRACTED_METADATA_ROOT_NAME);
        final MetadataElement level1Elem = root.getElementAt(1);
        final MetadataElement generalHeader = level1Elem.getElement("generalHeader");
        final MetadataElement productInfo = level1Elem.getElement("productInfo");
        final MetadataElement missionInfo = productInfo.getElement("missionInfo");
        final MetadataElement productVariantInfo = productInfo.getElement("productVariantInfo");
        final MetadataElement imageDataInfo = productInfo.getElement("imageDataInfo");
        final MetadataElement sceneInfo = productInfo.getElement("sceneInfo");

        MetadataAttribute attrib = generalHeader.getAttribute("fileName");
        if(attrib != null)
            productName = attrib.getData().getElemString();

        //mph
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT, productName);
        productType = productVariantInfo.getAttributeString("productType", " ");
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT_TYPE, productType);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.SPH_DESCRIPTOR,
                generalHeader.getAttributeString("itemName", " "));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.MISSION, generalHeader.getAttributeString("mission", " "));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PROC_TIME, getTime(generalHeader, "generationTime"));

        MetadataElement elem = generalHeader.getElement("generationSystem");
        if(elem != null) {
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ProcessingSystemIdentifier,
                elem.getAttributeString("generationSystem", " "));
        }

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.CYCLE, missionInfo.getAttributeInt("orbitCycle", 0));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.REL_ORBIT, missionInfo.getAttributeInt("relOrbit", 0));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ABS_ORBIT, missionInfo.getAttributeInt("absOrbit", 0));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PASS, missionInfo.getAttributeString("orbitDirection", " "));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.SAMPLE_TYPE, imageDataInfo.getAttributeString("imageDataType", " "));

        final ProductData.UTC startTime = getTime(sceneInfo.getElement("start"), "timeUTC");
        final ProductData.UTC stopTime = getTime(sceneInfo.getElement("stop"), "timeUTC");
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_line_time, startTime);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_line_time, stopTime);
        product.setStartTime(startTime);
        product.setEndTime(stopTime);

        getCornerCoords(sceneInfo);
   /*     AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_near_lat,
                mapProjRec.getAttributeDouble("1st line 1st pixel geodetic latitude"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_near_long,
                mapProjRec.getAttributeDouble("1st line 1st pixel geodetic longitude"));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_far_lat,
                mapProjRec.getAttributeDouble("1st line last valid pixel geodetic latitude"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_far_long,
                mapProjRec.getAttributeDouble("1st line last valid pixel geodetic longitude"));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_near_lat,
                mapProjRec.getAttributeDouble("Last line 1st pixel geodetic latitude"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_near_long,
                mapProjRec.getAttributeDouble("Last line 1st pixel geodetic longitude"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_far_lat,
                mapProjRec.getAttributeDouble("Last line last valid pixel geodetic latitude"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_far_long,
                mapProjRec.getAttributeDouble("Last line last valid pixel geodetic longitude"));  */

        int srgr = 0;
        if(productVariantInfo.getAttributeString("projection", " ").equalsIgnoreCase("GROUNDRANGE"))
            srgr = 1;
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.srgr_flag, srgr);

    }

    private ProductData.UTC getTime(MetadataElement elem, String tag) {
        final String timeStr = createValidUTCString(elem.getAttributeString(tag, " ").toUpperCase(), 
                new char[]{':','.','-'}, ' ').trim();
        return AbstractMetadata.parseUTC(timeStr, "yyyy-mm-dd HH:mm:ss");
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
        StringBuffer validName = new StringBuffer(name.length());
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
        ArrayList<CornerCoord> coordList = new ArrayList<CornerCoord>();

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

        for(CornerCoord coord : coordList) {
            int index = -1;
            if(coord.refRow == minRow) {
                if(coord.refCol == minCol) {            // UL
                    index = 0;
                } else if(coord.refCol == maxCol) {     // UR
                    index = 1;
                }
            } else if(coord.refRow == maxRow) {
                if(coord.refCol == minCol) {            // LL
                    index = 2;
                } else if(coord.refCol == maxCol) {     // LR
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

    protected void addGeoCoding(final Product product) {

        addGeoCoding(product, latCorners, lonCorners);
    }

    protected void addTiePointGrids(final Product product) {

        final int gridWidth = 4;
        final int gridHeight = 4;
        final float subSamplingX = (float)product.getSceneRasterWidth() / (float)(gridWidth - 1);
        final float subSamplingY = (float)product.getSceneRasterHeight() / (float)(gridHeight - 1);

        final float[] fineAngles = new float[gridWidth*gridHeight];

        createFineTiePointGrid(2, 2, gridWidth, gridHeight, incidenceCorners, fineAngles);

        final TiePointGrid incidentAngleGrid = new TiePointGrid("incident_angle", gridWidth, gridHeight, 0, 0,
                subSamplingX, subSamplingY, fineAngles);

        product.addTiePointGrid(incidentAngleGrid);

        final float[] fineSlantRange = new float[gridWidth*gridHeight];

        createFineTiePointGrid(2, 2, gridWidth, gridHeight, slantRangeCorners, fineSlantRange);

        final TiePointGrid slantRangeGrid = new TiePointGrid("slant_range", gridWidth, gridHeight, 0, 0,
                subSamplingX, subSamplingY, fineSlantRange);

        product.addTiePointGrid(slantRangeGrid);
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