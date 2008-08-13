package org.esa.nest.dataio;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.ProductData;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Aug 12, 2008
 * To change this template use File | Settings | File Templates.
 */
public class AbstractMetadata {


    /**
     * Abstract common metadata from products to be used uniformly by all operators
     * @param root the product metadata root
     */
    public static void addAbstractedMetadataHeader(MetadataElement root) {
        MetadataElement absRoot = root.getElement(Product.ABSTRACTED_METADATA_ROOT_NAME);
        if(absRoot == null) {
            absRoot = new MetadataElement(Product.ABSTRACTED_METADATA_ROOT_NAME);
            root.addElement(absRoot);
        }

        // MPH

        // SPH
        addAbstractedAttribute("pass", absRoot, ProductData.TYPE_ASCII);
        addAbstractedAttribute("swath", absRoot, ProductData.TYPE_ASCII);
        addAbstractedAttribute("sample_type", absRoot, ProductData.TYPE_ASCII);

        // SRGR
        addAbstractedAttribute("srgr_flag", absRoot, ProductData.TYPE_INT8);
        addAbstractedAttribute("range_spacing", absRoot, ProductData.TYPE_FLOAT64);

    }

    /**
     * Adds an attribute from src to dest
     * @param tag the name of the attribute
     * @param dest the destination element
     * @param dataType the ProductData type
     */
    private static void addAbstractedAttribute(String tag, MetadataElement dest, int dataType) {
        MetadataAttribute attribute = new MetadataAttribute(tag, dataType, 1);
        if(dataType == ProductData.TYPE_ASCII)
            attribute.getData().setElems(" ");
        attribute.setReadOnly(false);
        dest.addAttributeFast(attribute);
    }
}
