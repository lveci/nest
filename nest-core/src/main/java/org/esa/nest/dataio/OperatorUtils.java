package org.esa.nest.dataio;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
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
}
