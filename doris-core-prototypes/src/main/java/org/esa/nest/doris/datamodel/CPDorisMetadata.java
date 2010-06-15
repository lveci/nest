package org.esa.nest.doris.datamodel;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.gpf.OperatorException;

/**
 * Created by IntelliJ IDEA.
 * User: pmar
 * Date: May, 2010
 * To change this template use File | Settings | File Templates.
 */
public class CPDorisMetadata {

    // GENERAL CONTROL POINT ------
    public static final String control_point = "Control_Points";
    public static final String control_point_idx = "idx";
    public static final String control_point_x_pos = "x_pos";
    public static final String control_point_y_pos = "y_pos";
    public static final String control_point_observation = "observation";

//    // GEO-CONTROL POINT ------
//    public static final String geo_control_point = "Control_Points";
//    public static final String geo_control_point_idx = "idx";
//    public static final String geo_control_point_phi = "phi";
//    public static final String geo_control_point_lam = "lam";
//    public static final String geo_control_point_height = "height";
//    public static final String geo_control_point_observation = "observation";

    // RADAR CONTROL POINT ------
    public static final String radar_control_point = "Control_Points";
    public static final String radar_control_point_idx = "idx";
    public static final String radar_control_point_line = "line";
    public static final String radar_control_point_pixel = "pixel";
//    public static final String radar_control_point_height = "height";
    public static final String radar_control_point_offset_line = "offset_line";
    public static final String radar_control_point_offset_pixel = "offset_pixel";
    public static final String radar_control_point_coherence = "coherence";

    // GEO-RADAR CONTROL POINT ------
    public static final String geo_radar_control_point = "Control_Points";
    public static final String geo_radar_control_point_idx = "idx";
    public static final String geo_radar_control_point_line = "line";
    public static final String geo_radar_control_point_pixel = "pixel";
    public static final String geo_radar_control_point_phi = "phi";
    public static final String geo_radar_control_point_lam = "lam";
    public static final String geo_radar_control_point_height = "height";
    public static final String geo_radar_control_point_offset_line = "offset_line";
    public static final String geo_radar_control_point_offset_pixel = "offset_pixel";
    public static final String geo_radar_control_point_coherence = "coherence";

    // declaration of a general control point class : used in eg. flat earth phase computations
    public static class ControlPoint {
        public int idx;
        public double x_pos, y_pos, observation;

        public ControlPoint() {
            this.idx = 0;
            this.x_pos = 0.00;
            this.y_pos = 0.00;
            this.observation = 0.00;
        }

        public ControlPoint(final int idx,
                            final double xpos,
                            final double ypos,
                            final double obsrv) {
            this.idx = idx;
            this.x_pos = xpos;
            this.y_pos = ypos;
            this.observation = obsrv;
        }

    }

    /**
     * Set array of radar control points in metadata element.
     *
     * @param absRoot  Metadata root.
     * @param controlPointsArray The array of control points
     */
    public static void setControlPoints(final MetadataElement absRoot, ControlPoint[] controlPointsArray) {

        final MetadataElement elemRoot = absRoot.getElement(control_point);
        final int numElems = elemRoot.getNumElements();
        if (numElems != controlPointsArray.length) {
            throw new OperatorException("Mismatch between target and source control point arrays");
        }

        for (int i = 0; i < numElems; i++) {
            final ControlPoint controlPoint = controlPointsArray[i];
            final MetadataElement subElemRoot = elemRoot.getElement(control_point + (i + 1));
            subElemRoot.setAttributeInt(control_point_idx, controlPoint.idx);
            subElemRoot.setAttributeDouble(control_point_x_pos, controlPoint.x_pos);
            subElemRoot.setAttributeDouble(control_point_y_pos, controlPoint.y_pos);
            subElemRoot.setAttributeDouble(control_point_observation, controlPoint.observation);
        }
    }

    /**
     * Append array of control points in metadata element.
     *
     * @param absRoot  Metadata root.
     * @param controlPointsArray The array of control points
     */
    public static void appendControlPoints(final MetadataElement absRoot, ControlPoint[] controlPointsArray) {

        final MetadataElement elemRoot = absRoot.getElement(control_point);
        final int numElems = elemRoot.getNumElements();
        if (numElems == 0) {
            System.out.println("Appending control points entries in metadata with: [" + controlPointsArray.length + "] elements");
        } else if (numElems != controlPointsArray.length) {
            throw new OperatorException("Mismatch between target and source control points arrays");
        }

        // TODO: Convert orbit vector time in DORIS.c++ and getorb compatible format!
        for (int i = 0; i < controlPointsArray.length; i++) {
            final ControlPoint controlPoint = controlPointsArray[i];
            final MetadataElement subElemRoot = elemRoot.getElement(control_point + (i + 1));
            subElemRoot.setAttributeInt(control_point_idx, controlPoint.idx);
            subElemRoot.setAttributeDouble(control_point_x_pos, controlPoint.x_pos);
            subElemRoot.setAttributeDouble(control_point_y_pos, controlPoint.y_pos);
            subElemRoot.setAttributeDouble(control_point_observation, controlPoint.observation);
        }
    }

    /**
     * Get array of control points from metadata element.
     *
     * @param absRoot  Metadata root.
     * @return controlPointsArray Array of control points.
     * @throws OperatorException The exceptions.
     */
    public static ControlPoint[] getControlPoints(final MetadataElement absRoot) {

        final MetadataElement elemRoot = absRoot.getElement(control_point);
        if (elemRoot == null) {
            throw new OperatorException("This product has no Control Points");
        }
        final int numElems = elemRoot.getNumElements();
        final ControlPoint[] controlPointArray = new ControlPoint[numElems];
        for (int i = 0; i < numElems; i++) {

            final MetadataElement subElemRoot = elemRoot.getElement(control_point + (i + 1));
            final ControlPoint controlPoint = new ControlPoint(
                    subElemRoot.getAttributeInt(control_point_idx),
                    subElemRoot.getAttributeDouble(control_point_x_pos),
                    subElemRoot.getAttributeDouble(control_point_y_pos),
                    subElemRoot.getAttributeDouble(control_point_observation));
            controlPointArray[i] = controlPoint;
        }
        return controlPointArray;
        
    }

    // -----------------------------------------------------------------------------------------------------
    // declaration of RADAR control point class : used in eg. flat earth phase computations

    public static class RadarControlPoint{

        public int idx;
        public int line, pixel;
//        public double height;
        public double offset_line, offset_pixel;
        public double coherence;

        public RadarControlPoint() {

            this.idx = 0;
            this.line = 0;
            this.pixel = 0;
//            this.height = 0;
            this.offset_line = 0.00;
            this.offset_pixel = 0.00;
            this.coherence = 0.00;
        }

        public RadarControlPoint(final int idx,
                            final int line,
                            final int pixel,
//                            final double height,
                            final double offset_l,
                            final double offset_p,
                            final double coherence) {

            this.idx = idx;
            this.line = line;
            this.pixel = pixel;
//            this.height = height;
            this.offset_line = offset_l;
            this.offset_pixel = offset_p;
            this.coherence = coherence;
        }

    }

    /**
     * Set array of radar control points in metadata element.
     *
     * @param absRoot  Metadata root.
     * @param controlRadarPointsArray The array of radar control points
     */
    public static void setRadarControlPoints(final MetadataElement absRoot, RadarControlPoint[] controlRadarPointsArray) {

        final MetadataElement elemRoot = absRoot.getElement(radar_control_point);
        final int numElems = elemRoot.getNumElements();
        if (numElems != controlRadarPointsArray.length) {
            throw new OperatorException("Mismatch between target and source radar control point arrays");
        }

        // TODO: Convert orbit vector time in DORIS.c++ and getorb compatible format!
        for (int i = 0; i < numElems; i++) {
            final RadarControlPoint radarControlPoint = controlRadarPointsArray[i];
            final MetadataElement subElemRoot = elemRoot.getElement(radar_control_point + (i + 1));
            subElemRoot.setAttributeInt(radar_control_point_idx, radarControlPoint.idx);
            subElemRoot.setAttributeDouble(radar_control_point_line, radarControlPoint.line);
            subElemRoot.setAttributeDouble(radar_control_point_pixel, radarControlPoint.pixel);
            subElemRoot.setAttributeDouble(radar_control_point_offset_line, radarControlPoint.offset_line);
            subElemRoot.setAttributeDouble(radar_control_point_offset_pixel, radarControlPoint.offset_pixel);
            subElemRoot.setAttributeDouble(radar_control_point_coherence, radarControlPoint.coherence);
        }
    }

    /**
     * Append array of radar control points in metadata element.
     *
     * @param absRoot  Metadata root.
     * @param controlRadarPointsArray The array of control points
     */
    public static void appendRadarControlPoints(final MetadataElement absRoot, RadarControlPoint[] controlRadarPointsArray) {

        final MetadataElement elemRoot = absRoot.getElement(radar_control_point);
        final int numElems = elemRoot.getNumElements();
        if (numElems == 0) {
            System.out.println("Appending radar control points entries in metadata with: [" + controlRadarPointsArray.length + "] elements");
        } else if (numElems != controlRadarPointsArray.length) {
            throw new OperatorException("Mismatch between target and source radar control points arrays");
        }

        for (int i = 0; i < controlRadarPointsArray.length; i++) {
            final RadarControlPoint radarControlPoint = controlRadarPointsArray[i];
            final MetadataElement subElemRoot = elemRoot.getElement(radar_control_point + (i + 1));
            subElemRoot.setAttributeInt(radar_control_point_idx, radarControlPoint.idx);
            subElemRoot.setAttributeDouble(radar_control_point_line, radarControlPoint.line);
            subElemRoot.setAttributeDouble(radar_control_point_pixel, radarControlPoint.pixel);
            subElemRoot.setAttributeDouble(radar_control_point_offset_line, radarControlPoint.offset_line);
            subElemRoot.setAttributeDouble(radar_control_point_offset_pixel, radarControlPoint.offset_pixel);
            subElemRoot.setAttributeDouble(radar_control_point_coherence, radarControlPoint.coherence);
        }
    }

    /**
     * Get array of control points from metadata element.
     *
     * @param absRoot  Metadata root.
     * @return controlPointsArray Array of radar control points.
     * @throws OperatorException The exceptions.
     */
    public static RadarControlPoint[] getRadarControlPoints(final MetadataElement absRoot) {

        final MetadataElement elemRoot = absRoot.getElement(radar_control_point);
        if (elemRoot == null) {
            throw new OperatorException("This product has no Radar Control Points");
        }
        final int numElems = elemRoot.getNumElements();
        final RadarControlPoint[] controlRadarPointArray = new RadarControlPoint[numElems];
        for (int i = 0; i < numElems; i++) {

            final MetadataElement subElemRoot = elemRoot.getElement(radar_control_point + (i + 1));
            final RadarControlPoint controlRadarPoint = new RadarControlPoint(
                    subElemRoot.getAttributeInt(radar_control_point_idx),
                    subElemRoot.getAttributeInt(radar_control_point_line),
                    subElemRoot.getAttributeInt(radar_control_point_pixel),
                    subElemRoot.getAttributeDouble(radar_control_point_offset_line),
                    subElemRoot.getAttributeDouble(radar_control_point_offset_pixel),
                    subElemRoot.getAttributeDouble(radar_control_point_coherence));
            controlRadarPointArray[i] = controlRadarPoint;
        }
        return controlRadarPointArray;

    }

    // -----------------------------------------------------------------------------------------------------
    // declaration of GEO-RADAR control point class : used in eg. flat earth phase computations
    public static class GeoRadarControlPoint{

        public int idx;
        public int line, pixel;
        public double phi, lam, height;
        public double offset_line, offset_pixel;
        public double coherence;

        public GeoRadarControlPoint() {

            this.idx = 0;
            this.line = 0;
            this.pixel = 0;
            this.phi = 0;
            this.lam = 0;
            this.height = 0;
            this.offset_line = 0.00;
            this.offset_pixel = 0.00;
            this.coherence = 0.00;
        }

        public GeoRadarControlPoint(final int idx,
                            final int line,
                            final int pixel,
                            final double phi,
                            final double lam,
                            final double height,
                            final double offset_l,
                            final double offset_p,
                            final double coherence) {

            this.idx = idx;
            this.line = line;
            this.pixel = pixel;
            this.phi = phi;
            this.lam = lam;
            this.height = height;
            this.offset_line = offset_l;
            this.offset_pixel = offset_p;
            this.coherence = coherence;
        }

    }

    /**
     * Set array of radar control points in metadata element.
     *
     * @param absRoot  Metadata root.
     * @param controlGeoRadarPointsArray The array of radar control points
     */
    public static void setGeoRadarControlPoints(final MetadataElement absRoot, GeoRadarControlPoint[] controlGeoRadarPointsArray) {

        final MetadataElement elemRoot = absRoot.getElement(geo_radar_control_point);
        final int numElems = elemRoot.getNumElements();
        if (numElems != controlGeoRadarPointsArray.length) {
            throw new OperatorException("Mismatch between target and source geo-radar control point arrays");
        }

        for (int i = 0; i < numElems; i++) {
            final GeoRadarControlPoint radarGeoControlPoint = controlGeoRadarPointsArray[i];
            final MetadataElement subElemRoot = elemRoot.getElement(geo_radar_control_point + (i + 1));
            subElemRoot.setAttributeInt(geo_radar_control_point_idx, radarGeoControlPoint.idx);
            subElemRoot.setAttributeDouble(geo_radar_control_point_line, radarGeoControlPoint.line);
            subElemRoot.setAttributeDouble(geo_radar_control_point_pixel, radarGeoControlPoint.pixel);
            subElemRoot.setAttributeDouble(geo_radar_control_point_phi, radarGeoControlPoint.lam);
            subElemRoot.setAttributeDouble(geo_radar_control_point_lam, radarGeoControlPoint.phi);
            subElemRoot.setAttributeDouble(geo_radar_control_point_height, radarGeoControlPoint.height);
            subElemRoot.setAttributeDouble(geo_radar_control_point_offset_line, radarGeoControlPoint.offset_pixel);
            subElemRoot.setAttributeDouble(geo_radar_control_point_offset_pixel, radarGeoControlPoint.offset_line);
            subElemRoot.setAttributeDouble(geo_radar_control_point_coherence, radarGeoControlPoint.coherence);
        }
    }


    /**
     * Append array of radar control points in metadata element.
     *
     * @param absRoot  Metadata root.
     * @param controlGeoRadarPointsArray The array of control points
     */
    public static void appendGeoRadarControlPoints(final MetadataElement absRoot, GeoRadarControlPoint[] controlGeoRadarPointsArray) {

        final MetadataElement elemRoot = absRoot.getElement(geo_radar_control_point);
        final int numElems = elemRoot.getNumElements();
        if (numElems == 0) {
            System.out.println("Appending geo-radar control points entries in metadata with: [" + controlGeoRadarPointsArray.length + "] elements");
        } else if (numElems != controlGeoRadarPointsArray.length) {
            throw new OperatorException("Mismatch between target and source geo-radar control points arrays");
        }

        for (int i = 0; i < controlGeoRadarPointsArray.length; i++) {
            final GeoRadarControlPoint geoRadarControlPoint = controlGeoRadarPointsArray[i];
            final MetadataElement subElemRoot = elemRoot.getElement(geo_radar_control_point + (i + 1));
            subElemRoot.setAttributeInt(geo_radar_control_point_idx, geoRadarControlPoint.idx);
            subElemRoot.setAttributeDouble(geo_radar_control_point_line, geoRadarControlPoint.line);
            subElemRoot.setAttributeDouble(geo_radar_control_point_pixel, geoRadarControlPoint.pixel);
            subElemRoot.setAttributeDouble(geo_radar_control_point_phi, geoRadarControlPoint.phi);
            subElemRoot.setAttributeDouble(geo_radar_control_point_lam, geoRadarControlPoint.lam);
            subElemRoot.setAttributeDouble(geo_radar_control_point_height, geoRadarControlPoint.height);
            subElemRoot.setAttributeDouble(geo_radar_control_point_offset_line, geoRadarControlPoint.offset_line);
            subElemRoot.setAttributeDouble(geo_radar_control_point_offset_pixel, geoRadarControlPoint.offset_pixel);
            subElemRoot.setAttributeDouble(geo_radar_control_point_coherence, geoRadarControlPoint.coherence);
        }
    }

    /**
     * Get array of control points from metadata element.
     *
     * @param absRoot  Metadata root.
     * @return controlPointsArray Array of radar control points.
     * @throws OperatorException The exceptions.
     */
    public static GeoRadarControlPoint[] getGeoRadarControlPoints(final MetadataElement absRoot) {

        final MetadataElement elemRoot = absRoot.getElement(geo_radar_control_point);
        if (elemRoot == null) {
            throw new OperatorException("This product has no Geo-Radar Control Points");
        }
        final int numElems = elemRoot.getNumElements();
        final GeoRadarControlPoint[] controlGeoRadarPointArray = new GeoRadarControlPoint[numElems];
        for (int i = 0; i < numElems; i++) {

            final MetadataElement subElemRoot = elemRoot.getElement(geo_radar_control_point + (i + 1));
            final GeoRadarControlPoint controlGeoRadarPoint = new GeoRadarControlPoint(
                    subElemRoot.getAttributeInt(geo_radar_control_point_idx),
                    subElemRoot.getAttributeInt(geo_radar_control_point_line),
                    subElemRoot.getAttributeInt(geo_radar_control_point_pixel),
                    subElemRoot.getAttributeDouble(geo_radar_control_point_phi),
                    subElemRoot.getAttributeDouble(geo_radar_control_point_lam),
                    subElemRoot.getAttributeDouble(geo_radar_control_point_height),
                    subElemRoot.getAttributeDouble(geo_radar_control_point_offset_line),
                    subElemRoot.getAttributeDouble(geo_radar_control_point_offset_pixel),
                    subElemRoot.getAttributeDouble(geo_radar_control_point_coherence)
                    );
            controlGeoRadarPointArray[i] = controlGeoRadarPoint;
        }
        return controlGeoRadarPointArray;


    }



}
