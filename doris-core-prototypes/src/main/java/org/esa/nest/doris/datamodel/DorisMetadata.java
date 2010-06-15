package org.esa.nest.doris.datamodel;

import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;

/**
 * Created by IntelliJ IDEA.
 * User: pmar
 * Date: May, 2010
 */
public class DorisMetadata {

    public static final int NO_METADATA = 99999;
    private static final short NO_METADATA_BYTE = 0;
    public static final String NO_METADATA_STRING = " ";

    // dummy tags just for testing here
    public static final String DORIS_PROCSTEP_METADATA_ROOT = "Doris_Metadata_Root";
    public static final String DORIS_MAIN_ELEMENT = "Doris_Metadata_Element";

//    public static final String DORIS_ROOT_0 = "Doris_Abstract";
//    public static final String DORIS_ROOT_1 = "Doris_Static";
//
//    public static final String DORIS_PAIR_0 = "master0_slave0";
//    public static final String DORIS_PAIR_1 = "master0_slave1";
//
//    public static final String DORIS_GROUP_0 = "baseline_geometry";
//
//    public static final String DORIS_GROUP_1 = "coregistration";
//    public static final String DORIS_GROUP_1_1 = "coarse_orbits";
//    public static final String DORIS_GROUP_1_2 = "coarse_correlation";
//    public static final String DORIS_GROUP_1_3 = "fine";
//    public static final String DORIS_GROUP_1_4 = "cpm";
//    public static final String DORIS_GROUP_1_5 = "resampling";
//
//    public static final String DORIS_GROUP_2 = "products";
//    public static final String DORIS_GROUP_2_1 = "interferogram";
//    public static final String DORIS_GROUP_2_2 = "srp";
//    public static final String DORIS_GROUP_2_3 = "subtr_srp";
//    public static final String DORIS_GROUP_2_4 = "srd";
//    public static final String DORIS_GROUP_2_5 = "subtr_srd";
//    public static final String DORIS_GROUP_2_6 = "coherence";


//    public static MetadataElement getDorisProcessingGroup(MetadataElement root) {
//    }
//
//    public static MetadataElement setDorisProcessingGroup(MetadataElement root){
//    }
//
//    public static MetadataElement appendDorisProcessingGroup(MetadataElement )
//

    /**
     * Construct metadata for an InSAR operator, that is to be used by other operators and framework
     *
     * @param root the InSAR processing step metadata root (eg. coarse_orbits_coregistration)
     * @return metadata root
     */
    public static MetadataElement addDorisMetadataHeader(MetadataElement root) {
        MetadataElement absRoot;
        if (root == null) {
            absRoot = new MetadataElement(DORIS_PROCSTEP_METADATA_ROOT);
        } else {
            absRoot = root;
        }
        addDorisAttribute(absRoot, DORIS_MAIN_ELEMENT, ProductData.TYPE_ASCII, "", "Main DorisMetadata Class Element");
        return absRoot;
    }

    /**
     * Set Metadata values of an InSAR operator, that is to be used by other operators and framework
     *
     * @param root the InSAR processing step metadata root (eg. coarse_orbits_coregistration)
     * @return metadata root
     */
    public static MetadataElement setDorisMetadataHeader(MetadataElement root) {
        setAttribute(root, DORIS_MAIN_ELEMENT, "initial_value");
        return root;
    }

    /**
     * Adds an attribute into dest
     *
     * @param dest     the destination element
     * @param tag      the name of the attribute
     * @param dataType the ProductData type
     * @param unit     The unit
     * @param desc     The description
     * @return the newly created attribute
     */
    public static MetadataAttribute addDorisAttribute(final MetadataElement dest, final String tag, final int dataType,
                                                      final String unit, final String desc) {
        final MetadataAttribute attribute = new MetadataAttribute(tag, dataType, 1);
        if (dataType == ProductData.TYPE_ASCII) {
            attribute.getData().setElems(NO_METADATA_STRING);
        } else if (dataType == ProductData.TYPE_INT8 || dataType == ProductData.TYPE_UINT8) {
            attribute.getData().setElems(new String[]{String.valueOf(NO_METADATA_BYTE)});
        } else if (dataType != ProductData.TYPE_UTC) {
            attribute.getData().setElems(new String[]{String.valueOf(NO_METADATA)});
        }
        attribute.setUnit(unit);
        attribute.setDescription(desc);
        attribute.setReadOnly(false);
        dest.addAttribute(attribute);
        return attribute;
    }

    /**
     * Sets an attribute as a string
     *
     * @param dest  the destination element
     * @param tag   the name of the attribute
     * @param value the string value
     */
    public static void setAttribute(final MetadataElement dest, final String tag, final String value) {
        final MetadataAttribute attrib = dest.getAttribute(tag);
        if (attrib != null && value != null) {
            if (value.isEmpty())
                attrib.getData().setElems(NO_METADATA_STRING);
            else
                attrib.getData().setElems(value);
        } else {
            if (attrib == null)
                System.out.println(tag + " not found in metadata");
            if (value == null)
                System.out.println(tag + " metadata value is null");
        }
    }

    /**
     * Sets an attribute as a UTC
     *
     * @param dest  the destination element
     * @param tag   the name of the attribute
     * @param value the UTC value
     */
    public static void setAttribute(final MetadataElement dest, final String tag, final ProductData.UTC value) {
        final MetadataAttribute attrib = dest.getAttribute(tag);
        if (attrib != null && value != null) {
            attrib.getData().setElems(value.getArray());
        } else {
            if (attrib == null)
                System.out.println(tag + " not found in metadata");
            if (value == null)
                System.out.println(tag + " metadata value is null");
        }
    }

    /**
     * Sets an attribute as an int
     *
     * @param dest  the destination element
     * @param tag   the name of the attribute
     * @param value the string value
     */
    public static void setAttribute(final MetadataElement dest, final String tag, final int value) {
        final MetadataAttribute attrib = dest.getAttribute(tag);
        if (attrib == null)
            System.out.println(tag + " not found in metadata");
        else
            attrib.getData().setElemInt(value);
    }

    /**
     * Sets an attribute as a double
     *
     * @param dest  the destination element
     * @param tag   the name of the attribute
     * @param value the string value
     */
    public static void setAttribute(final MetadataElement dest, final String tag, final double value) {
        final MetadataAttribute attrib = dest.getAttribute(tag);
        if (attrib == null)
            System.out.println(tag + " not found in metadata");
        else
            attrib.getData().setElemDouble(value);
    }

    public static void setAttribute(final MetadataElement dest, final String tag, final Double value) {
        final MetadataAttribute attrib = dest.getAttribute(tag);
        if (attrib == null)
            System.out.println(tag + " not found in metadata");
        else if (value != null)
            attrib.getData().setElemDouble(value);
    }

    public static ProductData.UTC parseUTC(final String timeStr) {
        try {
            if (timeStr == null)
                return new ProductData.UTC(0);
            return ProductData.UTC.parse(timeStr);
        } catch (ParseException e) {
            return new ProductData.UTC(0);
        }
    }

    public static ProductData.UTC parseUTC(final String timeStr, final String format) {
        try {
            final int dotPos = timeStr.lastIndexOf(".");
            if (dotPos > 0) {
                final String newTimeStr = timeStr.substring(0, Math.min(dotPos + 6, timeStr.length()));
                return ProductData.UTC.parse(newTimeStr, format);
            }
            return ProductData.UTC.parse(timeStr, format);
        } catch (ParseException e) {
            System.out.println("UTC parse error:" + e.toString());
            return new ProductData.UTC(0);
        }
    }

    public static boolean getAttributeBoolean(final MetadataElement elem, final String tag) throws Exception {
        final int val = elem.getAttributeInt(tag);
        if (val == NO_METADATA)
            throw new Exception("Metadata " + tag + " has not been set");
        return val != 0;
    }

    public static double getAttributeDouble(final MetadataElement elem, final String tag) throws Exception {
        final double val = elem.getAttributeDouble(tag);
        if (val == NO_METADATA)
            throw new Exception("Metadata " + tag + " has not been set");
        return val;
    }

    public static int getAttributeInt(final MetadataElement elem, final String tag) throws Exception {
        final int val = elem.getAttributeInt(tag);
        if (val == NO_METADATA)
            throw new Exception("Metadata " + tag + " has not been set");
        return val;
    }

    public static boolean loadExternalMetadata(final Product product, final MetadataElement absRoot,
                                               final File productFile) throws IOException {
        // load metadata xml file if found
        final String inputStr = productFile.getAbsolutePath();
        final String metadataStr = inputStr.substring(0, inputStr.lastIndexOf('.')) + ".xml";
        File metadataFile = new File(metadataStr);
        if (metadataFile.exists() && DorisMetadataIO.Load(product, absRoot, metadataFile)) {
            return true;
        } else {
            metadataFile = new File(productFile.getParentFile(), "metadata.xml");
            return metadataFile.exists() && DorisMetadataIO.Load(product, absRoot, metadataFile);
        }
    }

    public static void saveExternalMetadata(final Product product, final MetadataElement absRoot, final File productFile) {
        // load metadata xml file if found
        final String inputStr = productFile.getAbsolutePath();
        final String metadataStr = inputStr.substring(0, inputStr.lastIndexOf('.')) + ".xml";
        final File metadataFile = new File(metadataStr);
        DorisMetadataIO.Save(product, absRoot, metadataFile);
    }


    /**
     * Get metadata.
     *
     * @param sourceProduct the product
     * @return MetadataElement
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          if abs metadata not found
     */
//    public static MetadataElement getMetadata(final Product sourceProduct,
//                                              final String processingStepName) throws OperatorException {
    public static MetadataElement getMetadata(final Product sourceProduct) throws OperatorException {

        final MetadataElement root = sourceProduct.getMetadataRoot();
        if (root == null) {
            throw new OperatorException("Root Metadata not found");
        }
        MetadataElement dorisProcStepMetadata = root.getElement(DORIS_PROCSTEP_METADATA_ROOT);
//        MetadataElement dorisProcStepMetadata = root.getElement(processingStepName);
        if (dorisProcStepMetadata == null) {
            dorisProcStepMetadata = addDorisMetadataHeader(root);
        }
        patchMissingMetadata(dorisProcStepMetadata);

        return dorisProcStepMetadata;
    }

    private static void patchMissingMetadata(final MetadataElement dorisProcStepMetadata) {
        final MetadataElement tmpElem = new MetadataElement("tmp");
        final MetadataElement completeMetadata = addDorisMetadataHeader(tmpElem);

        final MetadataAttribute[] attribs = completeMetadata.getAttributes();
        for (MetadataAttribute at : attribs) {
            if (dorisProcStepMetadata.getAttribute(at.getName()) == null) {
                dorisProcStepMetadata.addAttribute(at);
                dorisProcStepMetadata.getProduct().setModified(false);
            }
        }
    }

    /**
     * Create sub-metadata element.
     *
     * @param root The root metadata element.
     * @param tag  The sub-metadata element name.
     * @return The sub-metadata element.
     */
    public static MetadataElement addElement(final MetadataElement root, final String tag) {

        MetadataElement subElemRoot = root.getElement(tag);
        if (subElemRoot == null) {
            subElemRoot = new MetadataElement(tag);
            root.addElement(subElemRoot);
        }
        return subElemRoot;
    }

}
