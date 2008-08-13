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
        addAbstractedAttribute(absRoot, "PRODUCT", ProductData.TYPE_ASCII, "", "Product Name");
        addAbstractedAttribute(absRoot, "PRODUCT_TYPE", ProductData.TYPE_ASCII, "", "Product Type");
        addAbstractedAttribute(absRoot, "SPH_DESCRIPTOR", ProductData.TYPE_ASCII, "", "");
        addAbstractedAttribute(absRoot, "MISSION", ProductData.TYPE_ASCII, "", "");
        addAbstractedAttribute(absRoot, "PROC_TIME", ProductData.TYPE_UTC, "", "");
        addAbstractedAttribute(absRoot, "CYCLE", ProductData.TYPE_INT32, "", "");
        addAbstractedAttribute(absRoot, "REL_ORBIT", ProductData.TYPE_INT32, "", "");
        addAbstractedAttribute(absRoot, "ABS_ORBIT", ProductData.TYPE_INT32, "", "");
        addAbstractedAttribute(absRoot, "STATE_VECTOR_TIME", ProductData.TYPE_ASCII, "", "");
        addAbstractedAttribute(absRoot, "VECTOR_SOURCE", ProductData.TYPE_ASCII, "", "");
        addAbstractedAttribute(absRoot, "TOT_SIZE", ProductData.TYPE_UINT32, "bytes", "");

        // SPH
        addAbstractedAttribute(absRoot, "NUM_SLICES", ProductData.TYPE_INT32, "", "");
        addAbstractedAttribute(absRoot, "first_line_time", ProductData.TYPE_UTC, "", "");
        addAbstractedAttribute(absRoot, "last_line_time", ProductData.TYPE_UTC, "", "");
        addAbstractedAttribute(absRoot, "first_near_lat", ProductData.TYPE_INT32, "deg", "");
        addAbstractedAttribute(absRoot, "first_near_long", ProductData.TYPE_INT32, "deg", "");
        addAbstractedAttribute(absRoot, "first_mid_lat", ProductData.TYPE_INT32, "deg", "");
        addAbstractedAttribute(absRoot, "first_mid_long", ProductData.TYPE_INT32, "deg", "");
        addAbstractedAttribute(absRoot, "first_far_lat", ProductData.TYPE_INT32, "deg", "");
        addAbstractedAttribute(absRoot, "first_far_long", ProductData.TYPE_INT32, "deg", "");
        addAbstractedAttribute(absRoot, "last_near_lat", ProductData.TYPE_INT32, "deg", "");
        addAbstractedAttribute(absRoot, "last_near_long", ProductData.TYPE_INT32, "deg", "");
        addAbstractedAttribute(absRoot, "last_mid_lat", ProductData.TYPE_INT32, "deg", "");
        addAbstractedAttribute(absRoot, "last_mid_long", ProductData.TYPE_INT32, "deg", "");
        addAbstractedAttribute(absRoot, "last_far_lat", ProductData.TYPE_INT32, "deg", "");
        addAbstractedAttribute(absRoot, "last_far_long", ProductData.TYPE_INT32, "deg", "");
        
        addAbstractedAttribute(absRoot, "SWATH", ProductData.TYPE_ASCII, "", "Swath name");
        addAbstractedAttribute(absRoot, "PASS", ProductData.TYPE_ASCII, "", "ASCENDING or DESCENDING");
        addAbstractedAttribute(absRoot, "SAMPLE_TYPE", ProductData.TYPE_ASCII, "", "DETECTED or COMPLEX");
        addAbstractedAttribute(absRoot, "mds1_tx_rx_polar", ProductData.TYPE_ASCII, "", "");
        addAbstractedAttribute(absRoot, "mds2_tx_rx_polar", ProductData.TYPE_ASCII, "", "");
        addAbstractedAttribute(absRoot, "compression", ProductData.TYPE_ASCII, "", "");
        addAbstractedAttribute(absRoot, "azimuth_looks", ProductData.TYPE_INT32, "", "");
        addAbstractedAttribute(absRoot, "range_looks", ProductData.TYPE_INT32, "", "");
        addAbstractedAttribute(absRoot, "range_spacing", ProductData.TYPE_FLOAT64, "m", "Range sample spacing");
        addAbstractedAttribute(absRoot, "azimuth_spacing", ProductData.TYPE_FLOAT64, "m", "");
        addAbstractedAttribute(absRoot, "line_time_interval", ProductData.TYPE_FLOAT64, "s", "");
        addAbstractedAttribute(absRoot, "data_type", ProductData.TYPE_ASCII, "", "");         

        addAbstractedAttribute(absRoot, "num_output_lines", ProductData.TYPE_UINT32, "lines", "");
        addAbstractedAttribute(absRoot, "num_samples_per_line", ProductData.TYPE_UINT32, "samples", "");

        // SRGR
        addAbstractedAttribute(absRoot, "srgr_flag", ProductData.TYPE_UINT8, "flag", "SRGR applied");

    }

    /**
     * Adds an attribute into dest
     * @param dest the destination element
     * @param tag the name of the attribute
     * @param dataType the ProductData type
     * @param unit The unit
     * @param desc The description
     */
    private static void addAbstractedAttribute(MetadataElement dest, String tag, int dataType,
                                               String unit, String desc) {
        MetadataAttribute attribute = new MetadataAttribute(tag, dataType, 1);
        if(dataType == ProductData.TYPE_ASCII)
            attribute.getData().setElems(" ");
        attribute.setUnit(unit);
        attribute.setDescription(desc);
        attribute.setReadOnly(false);
        dest.addAttributeFast(attribute);
    }

    /**
     * Sets an attribute as a string
     * @param dest the destination element
     * @param tag the name of the attribute
     * @param value the string value
     */
    public static void setAttributeString(MetadataElement dest, String tag, String value) {
        MetadataAttribute attrib = dest.getAttribute(tag);
        if(attrib != null)
            attrib.getData().setElems(value);
    }
}
