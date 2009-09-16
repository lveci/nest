package org.esa.nest.gpf;

import org.esa.beam.dataio.dimap.DimapProductConstants;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.StringUtils;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.datamodel.AbstractMetadata;

import java.text.DateFormat;
import java.util.ArrayList;

/**
 * Helper methods for working with Operators
 */
public class OperatorUtils {

    public static final String TPG_SLANT_RANGE_TIME = "slant_range_time";
    public static final String TPG_INCIDENT_ANGLE = "incident_angle";
    public static final String TPG_LATITUDE = "latitude";
    public static final String TPG_LONGITUDE = "longitude";

    /**
     * Get incidence angle tie point grid.
     * @param sourceProduct The source product.
     * @param tiePointGridName The tie point grid name.
     * @return srcTPG The incidence angle tie point grid.
     */
    private static TiePointGrid getTiePointGrid(final Product sourceProduct, final String tiePointGridName) {

        for (int i = 0; i < sourceProduct.getNumTiePointGrids(); i++) {
            final TiePointGrid srcTPG = sourceProduct.getTiePointGridAt(i);
            if (srcTPG.getName().equals(tiePointGridName)) {
                return srcTPG;
            }
        }

        return null;
    }

    /**
     * Get incidence angle tie point grid.
     * @param sourceProduct The source product.
     * @return srcTPG The incidence angle tie point grid.
     */
    public static TiePointGrid getIncidenceAngle(final Product sourceProduct) {

        return getTiePointGrid(sourceProduct, TPG_INCIDENT_ANGLE);
    }

    /**
     * Get slant range time tie point grid.
     * @param sourceProduct The source product.
     * @return srcTPG The slant range time tie point grid.
     */
    public static TiePointGrid getSlantRangeTime(final Product sourceProduct) {

        return getTiePointGrid(sourceProduct, TPG_SLANT_RANGE_TIME);
    }

    /**
     * Get latitude tie point grid.
     * @param sourceProduct The source product.
     * @return srcTPG The latitude tie point grid.
     */
    public static TiePointGrid getLatitude(final Product sourceProduct) {

        return getTiePointGrid(sourceProduct, TPG_LATITUDE);
    }

    /**
     * Get longitude tie point grid.
     * @param sourceProduct The source product.
     * @return srcTPG The longitude tie point grid.
     */
    public static TiePointGrid getLongitude(final Product sourceProduct) {

        return getTiePointGrid(sourceProduct, TPG_LONGITUDE);
    }

    public static String getPolarizationFromBandName(final String bandName) {

        final int idx = bandName.lastIndexOf('_');
        if (idx != -1) {
            final String pol = bandName.substring(idx+1).toLowerCase();
            if (!pol.contains("hh") && !pol.contains("vv") && !pol.contains("hv") && !pol.contains("vh")) {
                return null;
            } else {
                return pol;
            }
        } else {
            return null;
        }
    }

    /**
     * Get product polarizations for each band in the product.
     * @param absRoot the AbstractMetadata
     * @param mdsPolar the string array to hold the polarization names
     * @throws Exception The exceptions.
     */
    public static void getProductPolarization(final MetadataElement absRoot, final String[] mdsPolar) throws Exception {

        if(mdsPolar == null || mdsPolar.length < 2) {
            throw new Exception("mdsPolar is not valid");
        }
        String polarName = absRoot.getAttributeString(AbstractMetadata.mds1_tx_rx_polar);
        mdsPolar[0] = null;
        if (polarName.contains("HH") || polarName.contains("HV") || polarName.contains("VH") || polarName.contains("VV")) {
            mdsPolar[0] = polarName.toLowerCase();
        }

        mdsPolar[1] = null;
        polarName = absRoot.getAttributeString(AbstractMetadata.mds2_tx_rx_polar);
        if (polarName.contains("HH") || polarName.contains("HV") || polarName.contains("VH") || polarName.contains("VV")) {
            mdsPolar[1] = polarName.toLowerCase();
        }
    }

    public static String getSuffixFromBandName(final String bandName) {

        final int idx1 = bandName.lastIndexOf('_');
        if (idx1 != -1) {
            return bandName.substring(idx1+1).toLowerCase();
        }
        final int idx2 = bandName.lastIndexOf('-');
        if (idx2 != -1) {
            return bandName.substring(idx2+1).toLowerCase();
        }
        final int idx3 = bandName.lastIndexOf('.');
        if (idx3 != -1) {
            return bandName.substring(idx3+1).toLowerCase();
        }
        return null;
    }

    public static void copyProductNodes(final Product sourceProduct, final Product targetProduct) {
        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyFlagCodings(sourceProduct, targetProduct);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
    }

    public static void copyVirtualBand(final Product product, final VirtualBand srcBand, final String name) {

        final VirtualBand virtBand = new VirtualBand(name,
                srcBand.getDataType(),
                srcBand.getSceneRasterWidth(),
                srcBand.getSceneRasterHeight(),
                srcBand.getExpression());
        virtBand.setSynthetic(true);
        virtBand.setUnit(srcBand.getUnit());
        virtBand.setDescription(srcBand.getDescription());
        virtBand.setNoDataValue(srcBand.getNoDataValue());
        virtBand.setNoDataValueUsed(srcBand.isNoDataValueUsed());
        product.addBand(virtBand);
    }

    public static boolean isDIMAP(Product prod) {
        return StringUtils.contains(prod.getProductReader().getReaderPlugIn().getFormatNames(),
                                    DimapProductConstants.DIMAP_FORMAT_NAME);
    }

    public static boolean isMapProjected(Product product) {
        if(product.getGeoCoding() instanceof MapGeoCoding)
            return true;
        final MetadataElement absRoot = product.getMetadataRoot().getElement("Abstracted Metadata");
        return absRoot != null && !absRoot.getAttributeString("map_projection", "").trim().isEmpty();
    }

    /**
     * Copy master GCPs to target product.
     * @param group input master GCP group
     * @param targetGCPGroup output master GCP group
     */
    public static void copyGCPsToTarget(final ProductNodeGroup<Pin> group, final ProductNodeGroup<Pin> targetGCPGroup) {
        targetGCPGroup.removeAll();

        for(int i = 0; i < group.getNodeCount(); ++i) {
            final Pin sPin = group.get(i);
            final Pin tPin = new Pin(sPin.getName(),
                               sPin.getLabel(),
                               sPin.getDescription(),
                               sPin.getPixelPos(),
                               sPin.getGeoPos(),
                               sPin.getSymbol());

            targetGCPGroup.add(tPin);
        }
    }

    public static Product createDummyTargetProduct() {
        final Product targetProduct = new Product("tmp", "tmp", 1, 1);
        targetProduct.addBand(new Band("tmp", ProductData.TYPE_INT8, 1, 1));
        AbstractMetadata.addAbstractedMetadataHeader(targetProduct.getMetadataRoot());
        return targetProduct;
    }

    public static String getAcquisitionDate(MetadataElement root) {
        String dateString;
        try {
            final ProductData.UTC date = root.getAttributeUTC(AbstractMetadata.first_line_time);
            final DateFormat dateFormat = ProductData.UTC.createDateFormat("dd.MMM.yyyy");
            dateString = dateFormat.format(date.getAsDate());
        } catch(Exception e) {
            dateString = "";
        }
        return dateString;
    }

    public static void createNewTiePointGridsAndGeoCoding(
            Product sourceProduct,
            Product targetProduct,
            int gridWidth,
            int gridHeight,
            float subSamplingX,
            float subSamplingY,
            PixelPos[] newTiePointPos) {

        TiePointGrid latGrid = null;
        TiePointGrid lonGrid = null;

        for(TiePointGrid srcTPG : sourceProduct.getTiePointGrids()) {

            final float[] tiePoints = new float[gridWidth*gridHeight];
            for (int k = 0; k < newTiePointPos.length; k++) {
                tiePoints[k] = srcTPG.getPixelFloat(newTiePointPos[k].x, newTiePointPos[k].y);
            }

            final TiePointGrid tgtTPG = new TiePointGrid(srcTPG.getName(),
                                                   gridWidth,
                                                   gridHeight,
                                                   0.0f,
                                                   0.0f,
                                                   subSamplingX,
                                                   subSamplingY,
                                                   tiePoints);

            targetProduct.addTiePointGrid(tgtTPG);

            if (srcTPG.getName().equals(TPG_LATITUDE)) {
                latGrid = tgtTPG;
            } else if (srcTPG.getName().equals(TPG_LONGITUDE)) {
                lonGrid = tgtTPG;
            }
        }

        final TiePointGeoCoding gc = new TiePointGeoCoding(latGrid, lonGrid);

        targetProduct.setGeoCoding(gc);
    }

    /** get the selected bands
     * @param sourceProduct the input product
     * @param sourceBandNames the select band names
     * @return band list
     */
    public static Band[] getSourceBands(final Product sourceProduct, String[] sourceBandNames) {

        if (sourceBandNames == null || sourceBandNames.length == 0) {
            final Band[] bands = sourceProduct.getBands();
            final ArrayList<String> bandNameList = new ArrayList<String>(sourceProduct.getNumBands());
            for (Band band : bands) {
                bandNameList.add(band.getName());
            }
            sourceBandNames = bandNameList.toArray(new String[bandNameList.size()]);
        }

        final Band[] sourceBands = new Band[sourceBandNames.length];
        for (int i = 0; i < sourceBandNames.length; i++) {
            final String sourceBandName = sourceBandNames[i];
            final Band sourceBand = sourceProduct.getBand(sourceBandName);
            if (sourceBand == null) {
                throw new OperatorException("Source band not found: " + sourceBandName);
            }
            sourceBands[i] = sourceBand;
        }
        return sourceBands;
    }

    public static void catchOperatorException(String opName, Exception e) throws OperatorException {
        if(opName.contains("$"))
            opName = opName.substring(0, opName.indexOf('$'));
        String message = opName + ":";
        if(e.getMessage() != null)
            message += e.getMessage();
        else
            message += e.toString();

        System.out.println(message);
        if(VisatApp.getApp() != null) {
            VisatApp.getApp().showErrorDialog(message);
        }
        throw new OperatorException(message);
    }
}
