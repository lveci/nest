package org.esa.nest.doris.datamodel;

import org.esa.beam.framework.datamodel.MetadataElement;

/**
 * Created by IntelliJ IDEA.
 * User: pmar
 * Date: May, 2010
 */
public class StaticDorisMetadata {


    // tag for the processing step
    public static final String DORIS_STATIC_METADATA_ROOT = "Doris_Static";
    public static final String DORIS_STATIC_METADATA_COREGISTRATION = "Coregistration";
    public static final String DORIS_STATIC_METADATA_PRODUCTS = "Products";
    // constuct elements of DORIS static metadata

    // 1.0. coregistration
    // 1.1. coarse coregistration based on orbits
    // 1.2. coarse correlation coregistration
    // 1.3. fine correlation coregistration
    // 1.4. estimation of cpm
    // 1.5. resampling

    public void coregistrationStaticMetadata(MetadataElement root){
        MetadataElement absRoot;
        if(root == null) {
            absRoot = new MetadataElement(DORIS_STATIC_METADATA_ROOT);
        } else {
            absRoot = root;
        }

        if(absRoot.getElement(DORIS_STATIC_METADATA_COREGISTRATION) == null){
            absRoot.addElement(new MetadataElement(DORIS_STATIC_METADATA_COREGISTRATION));
        }
    }

    public void productsStaticMetadata(MetadataElement root){
        MetadataElement absRoot;
        if(root == null) {
            absRoot = new MetadataElement(DORIS_STATIC_METADATA_ROOT);
        } else {
            absRoot = root;
        }
        if(absRoot.getElement(DORIS_STATIC_METADATA_PRODUCTS) == null){
            absRoot.addElement(new MetadataElement(DORIS_STATIC_METADATA_PRODUCTS));
        }
    }

}
