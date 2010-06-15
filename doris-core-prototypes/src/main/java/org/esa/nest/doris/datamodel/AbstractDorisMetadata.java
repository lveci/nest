package org.esa.nest.doris.datamodel;

import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.nest.datamodel.AbstractMetadata;

//import org.esa.nesg.doris.

/**
 * Created by IntelliJ IDEA.
 * User: pmar
 * Date: May, 2010
 * Time: 0:00:00 PM
 * To change this template use File | Settings | File Templates.
 */
public class AbstractDorisMetadata extends DorisMetadata{

    public static final double SOL =  299792458.; // pull from constants

    public static final int NO_METADATA = 99999;
    private static final short NO_METADATA_BYTE = 0;
    public static final String NO_METADATA_STRING = " ";

//    public static final String DORIS_PROCSTEP_METADATA_ROOT = "Doris_Metadata_Root";
//    public static final String DORIS_MAIN_ELEMENT = "Doris_Metadata_Element";
//    public static final String NESTDORIS_METADATA_ROOT = "Nest_Doris_Processing";

    // tag for the processing step
    public static final String DORIS_PROCSTEP_METADATA_ROOT = "Doris_Abstracted";

    // abstracted metadata header information
    public static final String product = "product";
    public static final String product_creation = "product_creation";
    public static final String product_identifier = "product_identifier";
    public static final String product_facility = "product_facility";
    public static final String radar_wavelength = "radar_wavelength";
    public static final String scene_identification = "scene_identification";
    public static final String scene_location = "scene_location";
    public static final String scene_centre_latitude = "scene_centre_latitude";
    public static final String scene_centre_longitude = "scene_centre_longitude";
    public static final String scene_sensing_starttime = "scene_sensing_starttime";
    public static final String scene_sensing_stoptime = "scene_sensing_stoptime";
    public static final String scene_numberlines_original = "scene_numberlines_original";
    public static final String scene_numberpixels_original = "scene_numberpixels_original";
    public static final String prf = "prf";
    public static final String azimuth_total_bandwidth = "azimuth_total_bandwidth";
    public static final String azimuth_weighting_window = "azimuth_weighting_window";
    public static final String xtrack_f_dc_constant = "xtrack_f_dc_constant";
    public static final String xtrack_f_dc_linear = "xtrack_f_dc_linear";
    public static final String xtrack_f_dc_quadratic = "xtrack_f_dc_quadratic";
    public static final String range_time_to_first_pixel = "range_time_to_first_pixel";
    public static final String rsr = "rsr";
    public static final String range_total_bandwidth = "range_total_bandwidth";
    public static final String range_weighting_window = "range_weighting_window";

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

    // control_flow
    public static final String DORIS_CONTROL_FLOW = "Processing_Control_Flow";

    // control flow flags
    public static final String read_files = "read_files";
    public static final String precise_orbits = "orbits";
    public static final String crop = "crop";
    public static final String oversample = "oversample";
    public static final String filt_azimuth = "filt_azimuth";
    public static final String filt_range = "filt_range";
    public static final String coreg_coarse_orbits = "coreg_coarse_orbits";
    public static final String coreg_coarse_correl = "coreg_coarse_correl";
    public static final String coreg_fine = "coreg_fine";
    public static final String coreg_comp_cpm = "coreg_comp_cpm";
    public static final String resample = "resample";
    public static final String timing_error = "timing_error";
    public static final String dem_assisted = "dem_assisted";
    public static final String interfero = "interfero";
    public static final String coherence = "coherence";
    public static final String comp_refphase = "comp_refphase";
    public static final String subtr_refphase = "comp_refdem";
    public static final String comp_refdem = "comp_refdem";
    public static final String subtr_refdem = "subtr_refdem";
    public static final String filtphase = "filtphase";
    public static final String unwrap = "unwrap";
    public static final String slant2h = "slant2h";
    public static final String geocoding = "geocoding";
    public static final String dinsar = "dinsar";
    public static final String NOTUSED_1 = "NOTUSED_1";
    public static final String NOTUSED_2 = "NOTUSED_2";

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
        addDorisAttribute(absRoot,product,ProductData.TYPE_ASCII,"","Product name");
        addDorisAttribute(absRoot,product_creation,ProductData.TYPE_UTC,"","Location and date/time of product creation");
        addDorisAttribute(absRoot,product_identifier,ProductData.TYPE_ASCII,"","Unique product identifier");
        addDorisAttribute(absRoot,product_facility,ProductData.TYPE_ASCII,"","Processing facility");
        // ADD more info: on platform, tho this for interferometry is not relevant and could be sourced from NEST metadata
        addDorisAttribute(absRoot,radar_wavelength,ProductData.TYPE_FLOAT32,"", "Radar Wavelength");
        addDorisAttribute(absRoot,scene_identification,ProductData.TYPE_INT32,"","Orbit number");
        addDorisAttribute(absRoot,scene_location,ProductData.TYPE_INT32,"", "Frame number");
        addDorisAttribute(absRoot,scene_centre_latitude,ProductData.TYPE_FLOAT32,"","Scene centre latitude");
        addDorisAttribute(absRoot,scene_centre_longitude,ProductData.TYPE_FLOAT32,"","Scene centre longitude");
        addDorisAttribute(absRoot,scene_sensing_starttime,ProductData.TYPE_UTC,"","First pixel azimuth time (UTC)");
        addDorisAttribute(absRoot,scene_sensing_stoptime,ProductData.TYPE_UTC,"","Last line time (UTC)");
        addDorisAttribute(absRoot,scene_numberlines_original,ProductData.TYPE_UINT32,"","Number of original lines");
        addDorisAttribute(absRoot,scene_numberpixels_original,ProductData.TYPE_UINT32,"","Number of original pixels");
        addDorisAttribute(absRoot,prf,ProductData.TYPE_FLOAT32,"","Pulse Repetition Frequency (Hz)");
        addDorisAttribute(absRoot,azimuth_total_bandwidth,ProductData.TYPE_FLOAT32,"","Total Azimuth Bandwidth");
        addDorisAttribute(absRoot,azimuth_weighting_window,ProductData.TYPE_ASCII,"", "Azimuth weighting window");
        // put doppler coefficients into the list
        addDorisAttribute(absRoot,xtrack_f_dc_constant,ProductData.TYPE_FLOAT32,"", "fDC constant component (Hz,early edge)");
        addDorisAttribute(absRoot,xtrack_f_dc_linear,ProductData.TYPE_FLOAT32,"", "fDC linear component (Hz/s,early edge)");
        addDorisAttribute(absRoot,xtrack_f_dc_quadratic,ProductData.TYPE_FLOAT32,"", "fDC quadratic component (Hz/s/s,early edge)");
        addDorisAttribute(absRoot,range_time_to_first_pixel,ProductData.TYPE_FLOAT32,"", "Range time to first pixel (2way, ms)");
        addDorisAttribute(absRoot,rsr,ProductData.TYPE_FLOAT32,"", "Range Sampling Rate/Frequency (MHz)");
        addDorisAttribute(absRoot,range_total_bandwidth,ProductData.TYPE_FLOAT32,"","Total range bandwidth (MHz)");
        addDorisAttribute(absRoot,range_weighting_window,ProductData.TYPE_ASCII,"","Range weighting window");

        absRoot.addElement(new MetadataElement(orbit_state_vectors));

        return absRoot;
    }

    public static MetadataElement addDorisProcessingControlFlow(MetadataElement root) {

        MetadataElement absRoot;
        if(root == null) {
            absRoot = new MetadataElement(DORIS_CONTROL_FLOW);
        } else {
            absRoot = root;
        }

        absRoot.addElement(new MetadataElement(read_files));
        absRoot.addElement(new MetadataElement(precise_orbits));
        absRoot.addElement(new MetadataElement(crop));
        absRoot.addElement(new MetadataElement(oversample));
        absRoot.addElement(new MetadataElement(filt_azimuth));
        absRoot.addElement(new MetadataElement(filt_range));
        absRoot.addElement(new MetadataElement(coreg_coarse_orbits));
        absRoot.addElement(new MetadataElement(coreg_coarse_correl));
        absRoot.addElement(new MetadataElement(coreg_fine));
        absRoot.addElement(new MetadataElement(coreg_comp_cpm));
        absRoot.addElement(new MetadataElement(resample));
        absRoot.addElement(new MetadataElement(timing_error));
        absRoot.addElement(new MetadataElement(dem_assisted));
        absRoot.addElement(new MetadataElement(interfero));
        absRoot.addElement(new MetadataElement(coherence));
        absRoot.addElement(new MetadataElement(comp_refphase));
        absRoot.addElement(new MetadataElement(comp_refdem));
        absRoot.addElement(new MetadataElement(subtr_refphase));
        absRoot.addElement(new MetadataElement(subtr_refdem));
        absRoot.addElement(new MetadataElement(filtphase));
        absRoot.addElement(new MetadataElement(unwrap));
        absRoot.addElement(new MetadataElement(slant2h));
        absRoot.addElement(new MetadataElement(geocoding));
        absRoot.addElement(new MetadataElement(dinsar));
        absRoot.addElement(new MetadataElement(NOTUSED_1));
        absRoot.addElement(new MetadataElement(NOTUSED_2));

        return absRoot;
    }

    /**
         * Set Metadata values of an InSAR operator, that is to be used by other operators and framework
         * @param root the InSAR processing step metadata root (eg. coarse_orbits_coregistration)
         * @param sourceProduct reference of the source product from which to pull metadata information
        * @return metadata root
        */
    public static MetadataElement setDorisMetadataHeader(MetadataElement root,
                                                         Product sourceProduct) {

        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct);
        if(absRoot == null) {
            throw new OperatorException("AbstractMetadata for this product is null");
        }

        final String productMission = absRoot.getAttributeString(AbstractMetadata.MISSION);
        final String productType    = absRoot.getAttributeString(AbstractMetadata.PRODUCT_TYPE);
        final String productFormat;
        if (productType.contains("ERS")) {
            productFormat = "CEOS";
        } else if (productType.contains("ASA") || productType.contains("SAR")) {
            productFormat = "ENVISAT";
        } else {
            productFormat = "OTHER";
        }
        System.out.println("Product forat is " + productFormat);

        // info pulled from NEST.AbstractMetadata
        setAttribute(root,product,absRoot.getAttributeString(AbstractMetadata.PRODUCT));
        System.out.println("product is " + absRoot.getAttributeString(AbstractMetadata.PRODUCT));
        setAttribute(root,product_creation,absRoot.getAttributeUTC(AbstractMetadata.PROC_TIME));
        System.out.println("product creation is " + absRoot.getAttributeUTC(AbstractMetadata.PROC_TIME));
        setAttribute(root,product_identifier,"TODO");
        setAttribute(root,product_facility,absRoot.getAttributeString(AbstractMetadata.ProcessingSystemIdentifier));

        double radarWavelength = SOL / absRoot.getAttributeDouble(AbstractMetadata.radar_frequency);
        setAttribute(root,radar_wavelength,radarWavelength);

        setAttribute(root,scene_identification,absRoot.getAttributeInt(AbstractMetadata.ABS_ORBIT));
        setAttribute(root,scene_location,absRoot.getAttributeInt(AbstractMetadata.REL_ORBIT));

        setAttribute(root,scene_sensing_starttime,absRoot.getAttributeUTC(AbstractMetadata.first_line_time));
        setAttribute(root,scene_sensing_stoptime,absRoot.getAttributeUTC(AbstractMetadata.last_line_time));

        setAttribute(root,scene_numberlines_original,999999);
        setAttribute(root,scene_numberpixels_original,999999);
        setAttribute(root,prf,absRoot.getAttributeDouble(AbstractMetadata.pulse_repetition_frequency));

        setAttribute(root,azimuth_total_bandwidth,9999.99);
        setAttribute(root,azimuth_weighting_window,"TODO");

        // Gymnastics for Doppler: here as a blob, later from AbtractMetadata of NEST

        double a0 = 0.00;
        double a1 = 0.00;
        double a2 = 0.00;
//        double a3 = 0.00;
//        double a4 = 0.00;

        if (productFormat.contains("CEOS")) { // CEOS

            final MetadataElement facility = sourceProduct.getMetadataRoot().getElement("Leader").getElement("Scene Parameters");
            MetadataAttribute attr = facility.getAttribute("Cross track Doppler frequency centroid constant term");
            a0 = attr.getData().getElemDouble();
            a1 = attr.getData().getElemDouble();
            a2 = attr.getData().getElemDouble();
            //System.out.println("Doppler frequency polynomial coefficients are " + a0 + ", " + a1 + ", " + a2);

        } else if (productFormat.contains("ENVISAT")) { // ENVISAT

            // get Doppler centroid coefficients: a0, a1, a2, a3 and a4
            final MetadataElement dsd = sourceProduct.getMetadataRoot().getElement("DOP_CENTROID_COEFFS_ADS");
            final MetadataAttribute coefAttr = dsd.getAttribute("dop_coef");
            a0 = coefAttr.getData().getElemFloatAt(0);
            a1 = coefAttr.getData().getElemFloatAt(1);
            a2 = coefAttr.getData().getElemFloatAt(2);
//            a3 = coefAttr.getData().getElemFloatAt(3); // TODO: include in doris_abstracted_metadata
//            a4 = coefAttr.getData().getElemFloatAt(4); // TODO: include in doris_abstracted_metadata

        } else {
            throw new OperatorException("Mission " + productMission + " in product format " + productFormat + "is currently not supported for InSAR processing.");
        }

        setAttribute(root,xtrack_f_dc_constant,a0);
        setAttribute(root,xtrack_f_dc_linear,a1);
        setAttribute(root,xtrack_f_dc_quadratic,a2);
        setAttribute(root,range_time_to_first_pixel,absRoot.getAttributeDouble(AbstractMetadata.slant_range_to_first_pixel));
        setAttribute(root,rsr,absRoot.getAttributeDouble(AbstractMetadata.range_sampling_rate));
        setAttribute(root,range_total_bandwidth,9999.99);   // not used in this scope: for filterings
        setAttribute(root,range_weighting_window,"TODO");  // note used in this scope: for filterings

        // TODO: fix this method?
        appendOrbitStateVectors(root,getOrbitStateVectors(absRoot));

        return root;
    }

    // Control Flow getters and setters: MAKE THEM MORE ROBUST AND GENERAL
    // --------
    public static void setControlFlowTag(MetadataElement root,
                                         String procStepName,
                                         String value) {

        MetadataElement controlFlowElement = root.getElement(procStepName);
        addDorisAttribute(controlFlowElement,product_identifier,ProductData.TYPE_ASCII,"", "Product name");
        setAttribute(controlFlowElement,product_identifier,value);

    }

    public static void getControlFlowTag(MetadataElement root,
                                         String procStepName,
                                         String value) {

        MetadataElement controlFlowElement = root.getElement(procStepName);
        try {
            getAttributeBoolean(controlFlowElement,value);
        } catch (Exception e) {
            System.out.println("Not executed processing step: " + procStepName + " on data: " + value);
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////////////
    // ---- ORBITS ------------------------------------------------------------------
    public static class OrbitStateVector {

        public final ProductData.UTC time;
        public final double time_mjd;
        public double x_pos, y_pos, z_pos;
        public double x_vel, y_vel, z_vel;

        public OrbitStateVector(final ProductData.UTC t,
                                final double xpos, final double ypos, final double zpos,
                                final double xvel, final double yvel, final double zvel) {
            this.time = t;
            time_mjd = t.getMJD();
            this.x_pos = xpos;
            this.y_pos = ypos;
            this.z_pos = zpos;
            this.x_vel = xvel;
            this.y_vel = yvel;
            this.z_vel = zvel;
        }
    }

    /**
     * Set orbit state vectors, assuming state vector elements are already defined in metadata
     * @param absRoot Abstracted metadata root.
     * @param orbitStateVectors The orbit state vectors.
     */
    public static void setOrbitStateVectors(final MetadataElement absRoot, OrbitStateVector[] orbitStateVectors) {

        final MetadataElement elemRoot = absRoot.getElement(orbit_state_vectors);
        final int numElems = elemRoot.getNumElements();
        if (numElems != orbitStateVectors.length) {
            throw new OperatorException("Mismatch between target and source orbit state vectors arrays");
        }

        // TODO: Convert orbit vector time in DORIS.c++ and getorb compatible format!
        for (int i = 0; i < numElems; i++) {
            final OrbitStateVector vector = orbitStateVectors[i];
            final MetadataElement subElemRoot = elemRoot.getElement(orbit_vector + (i+1));
            subElemRoot.setAttributeUTC(orbit_vector_time, vector.time);
            subElemRoot.setAttributeDouble(orbit_vector_x_pos, vector.x_pos);
            subElemRoot.setAttributeDouble(orbit_vector_y_pos, vector.y_pos);
            subElemRoot.setAttributeDouble(orbit_vector_z_pos, vector.z_pos);
            subElemRoot.setAttributeDouble(orbit_vector_x_vel, vector.x_vel);
            subElemRoot.setAttributeDouble(orbit_vector_y_vel, vector.y_vel);
            subElemRoot.setAttributeDouble(orbit_vector_z_vel, vector.z_vel);
        }
    }

    /**
     * Append orbit state vectors.
     * @param absRoot Abstracted metadata root.
     * @param orbitStateVectors The orbit state vectors.
     */
    public static void appendOrbitStateVectors(final MetadataElement absRoot, OrbitStateVector[] orbitStateVectors) {

        final MetadataElement elemRoot = absRoot.getElement(orbit_state_vectors);
        final int numElems = elemRoot.getNumElements();
        if (numElems == 0) {
            System.out.println("Appending state vectors in metadata array with: [" + orbitStateVectors.length +"] elements");
        } else if (numElems != orbitStateVectors.length)  {
            throw new OperatorException("Mismatch between target and source orbit state vectors arrays");
        }

        // I have to open an entry for the orbits?

        // TODO: Convert orbit vector time in DORIS.c++ and getorb compatible format!
        for (int i = 0; i < orbitStateVectors.length; i++) {
            final OrbitStateVector vector = orbitStateVectors[i];
            final String orbitElementTag = orbit_vector + (i+1);
            final MetadataElement orbitElement = new MetadataElement(orbitElementTag);
            elemRoot.addElement(orbitElement); // append state vector entry
            final MetadataElement subElemRoot = elemRoot.getElement(orbitElementTag);
            subElemRoot.setAttributeUTC(orbit_vector_time, vector.time);
            subElemRoot.setAttributeDouble(orbit_vector_x_pos, vector.x_pos);
            subElemRoot.setAttributeDouble(orbit_vector_y_pos, vector.y_pos);
            subElemRoot.setAttributeDouble(orbit_vector_z_pos, vector.z_pos);
            subElemRoot.setAttributeDouble(orbit_vector_x_vel, vector.x_vel);
            subElemRoot.setAttributeDouble(orbit_vector_y_vel, vector.y_vel);
            subElemRoot.setAttributeDouble(orbit_vector_z_vel, vector.z_vel);
        }
    }

    /**
     * Get orbit state vectors.
     * @param absRoot Abstracted metadata root.
     * @return orbitStateVectors Array of orbit state vectors.
     * @throws OperatorException The exceptions.
     */
    public static OrbitStateVector[] getOrbitStateVectors(final MetadataElement absRoot) throws OperatorException {

        final MetadataElement elemRoot = absRoot.getElement(orbit_state_vectors);
        if(elemRoot == null) {
            throw new OperatorException("This product has no orbit state vectors");
        }
        final int numElems = elemRoot.getNumElements();
        final OrbitStateVector[] orbitStateVectors = new OrbitStateVector[numElems];
        for (int i = 0; i < numElems; i++) {

            final MetadataElement subElemRoot = elemRoot.getElement(orbit_vector + (i+1));
            final OrbitStateVector vector = new OrbitStateVector(
                        subElemRoot.getAttributeUTC(orbit_vector_time),
                        subElemRoot.getAttributeDouble(orbit_vector_x_pos),
                        subElemRoot.getAttributeDouble(orbit_vector_y_pos),
                        subElemRoot.getAttributeDouble(orbit_vector_z_pos),
                        subElemRoot.getAttributeDouble(orbit_vector_x_vel),
                        subElemRoot.getAttributeDouble(orbit_vector_y_vel),
                        subElemRoot.getAttributeDouble(orbit_vector_z_vel));
            orbitStateVectors[i] = vector;
        }
        return orbitStateVectors;
    }

}