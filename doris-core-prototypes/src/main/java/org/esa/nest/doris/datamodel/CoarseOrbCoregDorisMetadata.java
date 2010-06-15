package org.esa.nest.doris.datamodel;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.ProductData;

import org.esa.nest.doris.datamodel.CPDorisMetadata;

/**
 * Created by IntelliJ IDEA.
 * User: pmar
 * Date: May, 2010
 */
public class CoarseOrbCoregDorisMetadata extends CoregistrationMetadata{


   // tag for the processing step
    public static final String DORIS_PROCSTEP_METADATA_ROOT = "coarse_orb_coreg";

    // abstracted metadata header information
    public static final String offset_estimated_line  = "offset_estimated_line";
    public static final String offset_estimated_pixel = "offset_estimated_pixel";
    public static final String offset_initial_line  = "offset_initial_line";
    public static final String offset_initial_pixel = "offset_initial_pixel";
    public static final String control_point_total_number  = "control_point_total_number";
    public static final String control_point_max_offset  = "control_point_max_offset";

    public static final String control_points = "control_points";

    /**
         * Construct metadata for an InSAR operator, that is to be used by other operators and framework
         * @param root the InSAR processing step metadata root (eg. coarse_orbits_coregistration)
        * @return metadata root
        */
    public static MetadataElement addDorisMetadataHeader(MetadataElement root) {
        MetadataElement absRoot;
        if(root == null) {
            absRoot = new MetadataElement(DORIS_PROCSTEP_METADATA_ROOT);
        } else {
            absRoot = root;
        }

        addDorisAttribute(absRoot,offset_estimated_line, ProductData.TYPE_INT32,"","Estimated offset in azimuth");
        addDorisAttribute(absRoot,offset_estimated_pixel, ProductData.TYPE_INT32,"","Estimated offset in range");
        addDorisAttribute(absRoot,offset_initial_line, ProductData.TYPE_INT32,"","Initial offset in azimuth");
        addDorisAttribute(absRoot,offset_initial_pixel, ProductData.TYPE_INT32,"","Initial offset in range");
        addDorisAttribute(absRoot,control_point_total_number, ProductData.TYPE_INT32,"","Maximum offset that can be estimated");
        addDorisAttribute(absRoot,control_point_max_offset, ProductData.TYPE_INT32,"","Maximum offset that can be estimated");

        absRoot.addElement(new MetadataElement(control_points));
        return absRoot;
    }

}
