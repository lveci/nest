package org.esa.nest.datamodel;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.OperatorException;

import java.text.ParseException;
import java.io.File;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Aug 12, 2008
 * To change this template use File | Settings | File Templates.
 */
public class AbstractMetadata {

    public static final int NO_METADATA = 99999;
    //public static final short NO_METADATA_BYTE = 99;
    public static final short NO_METADATA_BYTE = 0;
    public static final String NO_METADATA_STRING = " ";

    public static final String SLAVE_METADATA_ROOT = "Slave Metadata";

    public static final String PRODUCT = "PRODUCT";
    public static final String PRODUCT_TYPE = "PRODUCT_TYPE";
    public static final String SPH_DESCRIPTOR = "SPH_DESCRIPTOR";
    public static final String MISSION = "MISSION";
    public static final String PROC_TIME = "PROC_TIME";
    public static final String ProcessingSystemIdentifier = "Processing system identifier";
    public static final String CYCLE = "CYCLE";
    public static final String REL_ORBIT = "REL_ORBIT";
    public static final String ABS_ORBIT = "ABS_ORBIT";
    public static final String STATE_VECTOR_TIME = "STATE_VECTOR_TIME";
    public static final String VECTOR_SOURCE = "VECTOR_SOURCE";
    public static final String TOT_SIZE = "TOT_SIZE";

    // SPH
    public static final String NUM_SLICES = "NUM_SLICES";
    public static final String first_line_time = "first_line_time";
    public static final String last_line_time = "last_line_time";
    public static final String first_near_lat = "first_near_lat";
    public static final String first_near_long = "first_near_long";
    public static final String first_mid_lat = "first_mid_lat";
    public static final String first_mid_long = "first_mid_long";
    public static final String first_far_lat = "first_far_lat";
    public static final String first_far_long = "first_far_long";
    public static final String last_near_lat = "last_near_lat";
    public static final String last_near_long = "last_near_long";
    public static final String last_mid_lat = "last_mid_lat";
    public static final String last_mid_long = "last_mid_long";
    public static final String last_far_lat = "last_far_lat";
    public static final String last_far_long = "last_far_long";

    public static final String SWATH = "SWATH";
    public static final String PASS = "PASS";
    public static final String SAMPLE_TYPE = "SAMPLE_TYPE";
    public static final String mds1_tx_rx_polar = "mds1_tx_rx_polar";
    public static final String mds2_tx_rx_polar = "mds2_tx_rx_polar";
    public static final String algorithm = "algorithm";
    public static final String azimuth_looks = "azimuth_looks";
    public static final String range_looks = "range_looks";
    public static final String range_spacing = "range_spacing";
    public static final String azimuth_spacing = "azimuth_spacing";
    public static final String pulse_repetition_frequency = "pulse_repetition_frequency";
    public static final String line_time_interval = "line_time_interval";
    public static final String data_type = "data_type";

    public static final String num_output_lines = "num_output_lines";
    public static final String num_samples_per_line = "num_samples_per_line";
    // SRGR
    public static final String srgr_flag = "srgr_flag";
    public static final String isMapProjected = "isMapProjected";

    // calibration
    public static final String ant_elev_corr_flag = "ant_elev_corr_flag";
    public static final String range_spread_comp_flag = "range_spread_comp_flag";
    public static final String abs_calibration_flag = "abs_calibration_flag";
    public static final String calibration_factor = "calibration_factor";
    public static final String replica_power_corr_flag = "replica_power_corr_flag";
    public static final String external_calibration_file = "external_calibration_file";
    public static final String orbit_state_vector_file = "orbit_state_vector_file";

    public static final String range_sampling_rate = "range_sampling_rate";

    // orbit state vectors
    public static final String orbit_state_vectors = "Orbit_State_Vectors";
    public static final String orbit_vector = "orbit_vector";
    public static final String orbit_vector_time = "time";
    public static final String orbit_vector_x_pos = "x_pos";
    public static final String orbit_vector_y_pos = "y_pos";
    public static final String orbit_vector_z_pos = "z_pos";
    public static final String orbit_vector_x_vel = "x_vel";
    public static final String orbit_vector_y_vel = "y_vel";
    public static final String orbit_vector_z_vel = "z_vel";

    /**
     * Abstract common metadata from products to be used uniformly by all operators
     * @param root the product metadata root
     * @return abstracted metadata root
     */
    public static MetadataElement addAbstractedMetadataHeader(MetadataElement root) {
        MetadataElement absRoot = root.getElement(Product.ABSTRACTED_METADATA_ROOT_NAME);
        if(absRoot == null) {
            absRoot = new MetadataElement(Product.ABSTRACTED_METADATA_ROOT_NAME);
            root.addElementAt(absRoot, 0);
        }

        // MPH
        addAbstractedAttribute(absRoot, PRODUCT, ProductData.TYPE_ASCII, "", "Product Name");
        addAbstractedAttribute(absRoot, PRODUCT_TYPE, ProductData.TYPE_ASCII, "", "Product Type");
        addAbstractedAttribute(absRoot, SPH_DESCRIPTOR, ProductData.TYPE_ASCII, "", "");
        addAbstractedAttribute(absRoot, MISSION, ProductData.TYPE_ASCII, "", "");
        addAbstractedAttribute(absRoot, PROC_TIME, ProductData.TYPE_UTC, "utc", "");
        addAbstractedAttribute(absRoot, ProcessingSystemIdentifier, ProductData.TYPE_ASCII, "", "");
        addAbstractedAttribute(absRoot, CYCLE, ProductData.TYPE_INT32, "", "");
        addAbstractedAttribute(absRoot, REL_ORBIT, ProductData.TYPE_INT32, "", "");
        addAbstractedAttribute(absRoot, ABS_ORBIT, ProductData.TYPE_INT32, "", "");
        addAbstractedAttribute(absRoot, STATE_VECTOR_TIME, ProductData.TYPE_UTC, "utc", "");
        addAbstractedAttribute(absRoot, VECTOR_SOURCE, ProductData.TYPE_ASCII, "", "");
        addAbstractedAttribute(absRoot, TOT_SIZE, ProductData.TYPE_UINT32, "bytes", "");

        // SPH
        addAbstractedAttribute(absRoot, NUM_SLICES, ProductData.TYPE_INT32, "", "");
        addAbstractedAttribute(absRoot, first_line_time, ProductData.TYPE_UTC, "utc", "");
        addAbstractedAttribute(absRoot, last_line_time, ProductData.TYPE_UTC, "utc", "");
        addAbstractedAttribute(absRoot, first_near_lat, ProductData.TYPE_FLOAT64, "deg", "");
        addAbstractedAttribute(absRoot, first_near_long, ProductData.TYPE_FLOAT64, "deg", "");
        addAbstractedAttribute(absRoot, first_mid_lat, ProductData.TYPE_FLOAT64, "deg", "");
        addAbstractedAttribute(absRoot, first_mid_long, ProductData.TYPE_FLOAT64, "deg", "");
        addAbstractedAttribute(absRoot, first_far_lat, ProductData.TYPE_FLOAT64, "deg", "");
        addAbstractedAttribute(absRoot, first_far_long, ProductData.TYPE_FLOAT64, "deg", "");
        addAbstractedAttribute(absRoot, last_near_lat, ProductData.TYPE_FLOAT64, "deg", "");
        addAbstractedAttribute(absRoot, last_near_long, ProductData.TYPE_FLOAT64, "deg", "");
        addAbstractedAttribute(absRoot, last_mid_lat, ProductData.TYPE_FLOAT64, "deg", "");
        addAbstractedAttribute(absRoot, last_mid_long, ProductData.TYPE_FLOAT64, "deg", "");
        addAbstractedAttribute(absRoot, last_far_lat, ProductData.TYPE_FLOAT64, "deg", "");
        addAbstractedAttribute(absRoot, last_far_long, ProductData.TYPE_FLOAT64, "deg", "");
        
        addAbstractedAttribute(absRoot, SWATH, ProductData.TYPE_ASCII, "", "Swath name");
        addAbstractedAttribute(absRoot, PASS, ProductData.TYPE_ASCII, "", "ASCENDING or DESCENDING");
        addAbstractedAttribute(absRoot, SAMPLE_TYPE, ProductData.TYPE_ASCII, "", "DETECTED or COMPLEX");
        addAbstractedAttribute(absRoot, mds1_tx_rx_polar, ProductData.TYPE_ASCII, "", "");
        addAbstractedAttribute(absRoot, mds2_tx_rx_polar, ProductData.TYPE_ASCII, "", "");
        addAbstractedAttribute(absRoot, algorithm, ProductData.TYPE_ASCII, "", "Processing algorithm");
        addAbstractedAttribute(absRoot, azimuth_looks, ProductData.TYPE_FLOAT64, "", "");
        addAbstractedAttribute(absRoot, range_looks, ProductData.TYPE_FLOAT64, "", "");
        addAbstractedAttribute(absRoot, range_spacing, ProductData.TYPE_FLOAT64, "m", "Range sample spacing");
        addAbstractedAttribute(absRoot, azimuth_spacing, ProductData.TYPE_FLOAT64, "m", "");
        addAbstractedAttribute(absRoot, pulse_repetition_frequency, ProductData.TYPE_FLOAT64, "Hz", "");
        addAbstractedAttribute(absRoot, line_time_interval, ProductData.TYPE_FLOAT64, "s", "");
        addAbstractedAttribute(absRoot, data_type, ProductData.TYPE_ASCII, "", "");

        addAbstractedAttribute(absRoot, num_output_lines, ProductData.TYPE_UINT32, "lines", "");
        addAbstractedAttribute(absRoot, num_samples_per_line, ProductData.TYPE_UINT32, "samples", "");

        // SRGR
        addAbstractedAttribute(absRoot, srgr_flag, ProductData.TYPE_UINT8, "flag", "SRGR applied");
        addAbstractedAttribute(absRoot, isMapProjected, ProductData.TYPE_UINT8, "flag", "Map projection applied");

        // calibration
        addAbstractedAttribute(absRoot, ant_elev_corr_flag, ProductData.TYPE_UINT8, "flag", "Antenna elevation applied");
        addAbstractedAttribute(absRoot, range_spread_comp_flag, ProductData.TYPE_UINT8, "flag", "range spread compensation applied");
        addAbstractedAttribute(absRoot, replica_power_corr_flag, ProductData.TYPE_UINT8, "flag", "Replica pulse power correction applied");
        addAbstractedAttribute(absRoot, abs_calibration_flag, ProductData.TYPE_UINT8, "flag", "Product calibrated");
        addAbstractedAttribute(absRoot, calibration_factor, ProductData.TYPE_FLOAT64, "", "Calibration constant");

        addAbstractedAttribute(absRoot, range_sampling_rate, ProductData.TYPE_FLOAT64, "MHz", "Range Sampling Rate");

        addAbstractedAttribute(absRoot, external_calibration_file, ProductData.TYPE_ASCII, "", "External calibration file used");
        addAbstractedAttribute(absRoot, orbit_state_vector_file, ProductData.TYPE_ASCII, "", "Orbit file");

        absRoot.addElement(new MetadataElement(orbit_state_vectors));

        return absRoot;
    }

    /**
     * Adds an attribute into dest
     * @param dest the destination element
     * @param tag the name of the attribute
     * @param dataType the ProductData type
     * @param unit The unit
     * @param desc The description
     */
    private static void addAbstractedAttribute(final MetadataElement dest, final String tag, final int dataType,
                                               final String unit, final String desc) {
        final MetadataAttribute attribute = new MetadataAttribute(tag, dataType, 1);
        if(dataType == ProductData.TYPE_ASCII) {
            attribute.getData().setElems(NO_METADATA_STRING);
        } else if(dataType == ProductData.TYPE_INT8 || dataType == ProductData.TYPE_UINT8) {
            attribute.getData().setElems( new String[] {String.valueOf(NO_METADATA_BYTE)} );
        } else if(dataType != ProductData.TYPE_UTC) {
            attribute.getData().setElems( new String[] {String.valueOf(NO_METADATA)} );
        }
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
    public static void setAttribute(final MetadataElement dest, final String tag, final String value) {
        final MetadataAttribute attrib = dest.getAttribute(tag);
        if(attrib != null && value != null) {
            attrib.getData().setElems(value);
        } else {
            if(attrib == null)
                System.out.println(tag + " not found in metadata");
            if(value == null)
                System.out.println(tag + " metadata value is null");
        }
    }

    /**
     * Sets an attribute as a UTC
     * @param dest the destination element
     * @param tag the name of the attribute
     * @param value the UTC value
     */
    public static void setAttribute(final MetadataElement dest, final String tag, final ProductData.UTC value) {
        final MetadataAttribute attrib = dest.getAttribute(tag);
        if(attrib != null && value != null) {
            attrib.getData().setElems(value.getArray());
        } else {
            if(attrib == null)
                System.out.println(tag + " not found in metadata");
            if(value == null)
                System.out.println(tag + " metadata value is null");
        }
    }

    /**
     * Sets an attribute as an int
     * @param dest the destination element
     * @param tag the name of the attribute
     * @param value the string value
     */
    public static void setAttribute(final MetadataElement dest, final String tag, final int value) {
        final MetadataAttribute attrib = dest.getAttribute(tag);
        if(attrib == null)
            System.out.println(tag + " not found in metadata");
        else
            attrib.getData().setElemInt(value);
    }

    /**
     * Sets an attribute as a double
     * @param dest the destination element
     * @param tag the name of the attribute
     * @param value the string value
     */
    public static void setAttribute(final MetadataElement dest, final String tag, final double value) {
        final MetadataAttribute attrib = dest.getAttribute(tag);
        if(attrib == null)
            System.out.println(tag + " not found in metadata");
        else
            attrib.getData().setElemDouble(value);
    }

    public static ProductData.UTC parseUTC(final String timeStr) {
        try {
            if(timeStr == null)
                return new ProductData.UTC(0);
            return ProductData.UTC.parse(timeStr);
        } catch(ParseException e) {
            return new ProductData.UTC(0);
        }
    }

    public static ProductData.UTC parseUTC(final String timeStr, final String format) {
        try {
            return ProductData.UTC.parse(timeStr, format);
        } catch(ParseException e) {
            System.out.println("UTC parse error:"+ e.toString());
            return new ProductData.UTC(0);
        }
    }

    public static boolean getAttributeBoolean(final MetadataElement elem, final String tag) throws Exception {
        final int val = elem.getAttributeInt(tag);
        if(val == NO_METADATA)
            throw new Exception("Metadata "+tag+" has not been set");
        return val != 0;
    }

    public static double getAttributeDouble(final MetadataElement elem, final String tag) throws Exception {       
        final double val = elem.getAttributeDouble(tag);
        if(val == NO_METADATA)
            throw new Exception("Metadata "+tag+" has not been set");
        return val;
    }

    public static int getAttributeInt(final MetadataElement elem, final String tag) throws Exception {
        final int val = elem.getAttributeInt(tag);
        if(val == NO_METADATA)
            throw new Exception("Metadata "+tag+" has not been set");
        return val;
    }

    public static void loadExternalMetadata(final Product product, final MetadataElement absRoot, final File productFile)
        throws IOException {
         // load metadata xml file if found
        final String inputStr = productFile.getAbsolutePath();
        final String metadataStr = inputStr.substring(0, inputStr.lastIndexOf('.')) + ".xml";
        final File metadataFile = new File(metadataStr);
        if(metadataFile.exists())
            AbstractMetadataIO.Load(product, absRoot, metadataFile);
    }

    public static void saveExternalMetadata(final Product product, final MetadataElement absRoot, final File productFile) {
         // load metadata xml file if found
        final String inputStr = productFile.getAbsolutePath();
        final String metadataStr = inputStr.substring(0, inputStr.lastIndexOf('.')) + ".xml";
        final File metadataFile = new File(metadataStr);
        AbstractMetadataIO.Save(product, absRoot, metadataFile);
    }

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
     * Create sub-metadata element.
     * @param root The root metadata element.
     * @param tag The sub-metadata element name.
     * @return The sub-metadata element.
     */
    public static MetadataElement addElement(final MetadataElement root, final String tag) {

        MetadataElement subElemRoot = root.getElement(tag);
        if(subElemRoot == null) {
            subElemRoot = new MetadataElement(tag);
            root.addElement(subElemRoot);
        }
        return subElemRoot;
    }
}
