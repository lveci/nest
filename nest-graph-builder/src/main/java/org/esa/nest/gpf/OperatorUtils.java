package org.esa.nest.gpf;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.TiePointGrid;
import org.esa.beam.framework.gpf.OperatorException;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Dec 3, 2008
 * Time: 1:05:18 PM
 * To change this template use File | Settings | File Templates.
 */
public class OperatorUtils {


    /**
     * Get abstracted metadata.
     */
    public static MetadataElement getAbstractedMetadata(Product sourceProduct) {

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
    public static TiePointGrid getIncidenceAngle(Product sourceProduct) {

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
    public static TiePointGrid getSlantRangeTime(Product sourceProduct) {

        for (int i = 0; i < sourceProduct.getNumTiePointGrids(); i++) {
            final TiePointGrid srcTPG = sourceProduct.getTiePointGridAt(i);
            if (srcTPG.getName().equals("slant_range_time")) {
                return srcTPG;
            }
        }

        return null;
    }

    public static String getPolarizationFromBandName(String bandName) {

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
}
