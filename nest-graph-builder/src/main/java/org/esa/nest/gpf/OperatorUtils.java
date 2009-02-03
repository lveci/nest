package org.esa.nest.gpf;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.TiePointGrid;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.dataio.dimap.DimapProductConstants;
import org.esa.beam.util.StringUtils;
import org.esa.beam.util.ProductUtils;

/**
 * Helper methods for working with Operators
 */
public class OperatorUtils {


    /**
     * Get abstracted metadata.
     * @param sourceProduct the product
     * @return MetadataElement
     */
    public static MetadataElement getAbstractedMetadata(final Product sourceProduct) {

        final MetadataElement abstractedMetadata = sourceProduct.getMetadataRoot().getElement("Abstracted Metadata");
        if (abstractedMetadata == null) {
            throw new OperatorException("Abstracted Metadata not found");
        }
        return abstractedMetadata;
    }

    /**
     * Get incidence angle tie point grid.
     * @param sourceProduct the source
     * @return srcTPG The incidence angle tie point grid.
     */
    public static TiePointGrid getIncidenceAngle(final Product sourceProduct) {

        for (int i = 0; i < sourceProduct.getNumTiePointGrids(); i++) {
            final TiePointGrid srcTPG = sourceProduct.getTiePointGridAt(i);
            if (srcTPG.getName().equals("incident_angle")) {
                return srcTPG;
            }
        }

        return null;
    }

    /**
     * Get slant range time tie point grid.
     * @param sourceProduct the source
     * @return srcTPG The slant range time tie point grid.
     */
    public static TiePointGrid getSlantRangeTime(final Product sourceProduct) {

        for (int i = 0; i < sourceProduct.getNumTiePointGrids(); i++) {
            final TiePointGrid srcTPG = sourceProduct.getTiePointGridAt(i);
            if (srcTPG.getName().equals("slant_range_time")) {
                return srcTPG;
            }
        }

        return null;
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
}
