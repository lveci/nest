package org.esa.nest.gpf;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.dataio.dimap.DimapProductConstants;
import org.esa.beam.util.StringUtils;
import org.esa.beam.util.ProductUtils;
import org.esa.nest.datamodel.AbstractMetadata;

import java.text.DateFormat;

/**
 * Helper methods for working with Operators
 */
public class OperatorUtils {


    /**
     * Get incidence angle tie point grid.
     * @param sourceProduct The source product.
     * @param tiePointGridName The tie point grid name.
     * @return srcTPG The incidence angle tie point grid.
     */
    private static TiePointGrid getTiePointGrid(final Product sourceProduct, String tiePointGridName) {

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

        return getTiePointGrid(sourceProduct, "incident_angle");
    }

    /**
     * Get slant range time tie point grid.
     * @param sourceProduct The source product.
     * @return srcTPG The slant range time tie point grid.
     */
    public static TiePointGrid getSlantRangeTime(final Product sourceProduct) {

        return getTiePointGrid(sourceProduct, "slant_range_time");
    }

    /**
     * Get latitude tie point grid.
     * @param sourceProduct The source product.
     * @return srcTPG The latitude tie point grid.
     */
    public static TiePointGrid getLatitude(final Product sourceProduct) {

        return getTiePointGrid(sourceProduct, "latitude");
    }

    /**
     * Get longitude tie point grid.
     * @param sourceProduct The source product.
     * @return srcTPG The longitude tie point grid.
     */
    public static TiePointGrid getLongitude(final Product sourceProduct) {

        return getTiePointGrid(sourceProduct, "longitude");
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

            if (srcTPG.getName().equals("latitude")) {
                latGrid = tgtTPG;
            } else if (srcTPG.getName().equals("longitude")) {
                lonGrid = tgtTPG;
            }
        }

        final TiePointGeoCoding gc = new TiePointGeoCoding(latGrid, lonGrid);

        targetProduct.setGeoCoding(gc);
    }

    public static void catchOperatorException(String opName, Exception e) throws OperatorException {
        if(opName.contains("$"))
            opName = opName.substring(0, opName.indexOf('$'));
        if(e.getMessage() != null)
            throw new OperatorException(opName + ":" + e.getMessage());
        else
            throw new OperatorException(opName + ":" + e.toString());
    }
}
