package org.esa.nest.doris.datamodel;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.ProductData;

/**
 * Created by IntelliJ IDEA.
 * User: pmar
 * Date: May, 2010
 */
public class CoregistrationMetadata extends DorisMetadata {

   // tag for the processing step
    public static final String DORIS_PROCSTEP_METADATA_ROOT = "Coregistration";

    // abstracted metadata header information
    public static final String offset_initial_line  = "offset_initial_line";
    public static final String offset_initial_pixel = "offset_initial_pixel";
    public static final String corr_win_total_number  = "corr_win_total_number";
    public static final String corr_win_size_line  = "corr_win_size_line";
    public static final String corr_win_size_pixel = "corr_win_size_pixel";
    public static final String corr_win_peak_search_line  = "corr_win_peak_search_line";
    public static final String corr_win_peak_sarch_pixel = "corr_win_peak_sarch_pixel";
    public static final String corr_win_ovsmp_line  = "corr_win_ovsmp_line";
    public static final String corr_win_ovsmp_pixel = "corr_win_ovsmp_pixel";
    public static final String corr_win_max_offset = "corr_win_max_offset";

    public static final String correlation_windows = "control_points";

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
        addDorisAttribute(absRoot,offset_initial_line, ProductData.TYPE_INT32,"","Initial offset in azimuth");
        addDorisAttribute(absRoot,offset_initial_pixel, ProductData.TYPE_INT32,"","Initial offset in range");
        addDorisAttribute(absRoot,corr_win_total_number, ProductData.TYPE_ASCII,"","Total number of correlation windows");
        addDorisAttribute(absRoot,corr_win_size_line, ProductData.TYPE_ASCII,"","Correlation window size in azimuth");
        addDorisAttribute(absRoot,corr_win_size_pixel, ProductData.TYPE_ASCII,"","Correlation window size in range");
        addDorisAttribute(absRoot,corr_win_peak_search_line, ProductData.TYPE_ASCII,"","Peak search in azimuth");
        addDorisAttribute(absRoot,corr_win_peak_sarch_pixel, ProductData.TYPE_ASCII,"","Peak search in range");
        addDorisAttribute(absRoot,corr_win_ovsmp_line, ProductData.TYPE_ASCII,"","Oversampling factor in azimuth");
        addDorisAttribute(absRoot,corr_win_ovsmp_pixel, ProductData.TYPE_ASCII,"","Oversampling factor in range");
        addDorisAttribute(absRoot,corr_win_max_offset, ProductData.TYPE_ASCII,"","Maximum offset that can be estimated");

        absRoot.addElement(new MetadataElement(correlation_windows));

        return absRoot;
    }

}