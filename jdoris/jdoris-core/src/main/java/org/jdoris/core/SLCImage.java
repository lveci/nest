package org.jdoris.core;

import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.util.Constants;
import org.esa.nest.util.GeoUtils;
import org.jdoris.core.io.ResFile;

import java.io.File;

public final class SLCImage {

    // TODO: refactor to BuilderPattern

    // file & format
    private File resFileName;
    private String fileName;
    private int formatFlag; // not used

    // sensor
    private String sensor;
    private String sarProcessor;
    private double radar_wavelength; // TODO: close this modifier

    // geo & orientation
    private Point approxRadarCentreOriginal = new Point(); // use PixelPos as double!
    private GeoPos approxGeoCentreOriginal = new GeoPos();
    private Point approxXYZCentreOriginal = new Point();

    private double averageHeight;

    // azimuth annotations
    private double PRF;
    private double azimuthBandwidth;
    private double tAzi1;
    private String azimuthWeightingWindow;

    // range annotations
    private double rsr2x;
    private double rangeBandwidth;
    private double tRange1;
    private String rangeWeightingWindow;

    // doppler
    // private static double[] f_DC; // TODO
    private double f_DC_a0;                // constant term Hz
    private double f_DC_a1;                // linear term Hz/s
    private double f_DC_a2;                // quadratic term Hz/s/s

    // ______ offset = X(l,p) - X(L,P) ______
    // ______ Where l,p are in the local slave coordinate system and ______
    // ______ where L,P are in the local master coordinate system ______
    // ______ These variables are stored in the slaveinfo variable only ______
    private int coarseOrbitOffsetL;     // orbit offset in line (azimuth) direction
    private int coarseOrbitOffsetP;     // orbit offset in pixel (range) direction
    private int coarseOffsetL;          // offset in line (azimuth) direction
    private int coarseOffsetP;          // offset in pixel (range) direction

    // oversampling factors
    private int ovsAz;                 // oversampling of SLC
    private int ovsRg;                 // oversampling of SLC

    // relative to master geometry, or
    // absolute timing error of master
    // relative to master geometry, or
    // absolute timing error of master
    // timing errors
    private int azTimingError;        // timing error in azimuth direction

    // units: lines
    private int rgTimingError;        // timing error in range direction

    // units: pixels
    private boolean absTimingErrorFlag;   // FALSE if master time is NOT updated,

    // true if it is
    //    private static Rectangle originalWindow;       // position and size of the full scene
    Window originalWindow = new Window();       // position and size of the full scene
    Window currentWindow = new Window();        // position and size of the subset
    Window slaveMasterOffsets = new Window();   // overlapping slave window in master coordinates

    public SLCImage() {

        sensor = "SLC_ERS";                    // default (vs. SLC_ASAR, JERS, RSAT)
        sarProcessor = "SARPR_VMP";            // (VMP (esa paf) or ATLANTIS or TUDELFT) // TODO PGS update?
        formatFlag = 0;                        // format of file on disk

        approxXYZCentreOriginal.x = 0.0;
        approxXYZCentreOriginal.y = 0.0;
        approxXYZCentreOriginal.z = 0.0;

        radar_wavelength = 0.0565646;          // [m] default ERS2
        tAzi1 = 0.0;                           // [s] sec of day
        tRange1 = 5.5458330 / 2.0e3;           // [s] one way, default ERS2
        rangeWeightingWindow = "HAMMING";
        rangeBandwidth = 15.55e6;              // [Hz] default ERS2

        PRF = 1679.902;                        // [Hz] default ERS2
        azimuthBandwidth = 1378.0;             // [Hz] default ERS2
        azimuthWeightingWindow = "HAMMING";

        f_DC_a0 = 0.0;                         // [Hz] default ERS2
        f_DC_a1 = 0.0;
        f_DC_a2 = 0.0;
        rsr2x = 18.9624680 * 2.0e6;            // [Hz] default ERS2

        coarseOffsetL = 0;                     // by default
        coarseOffsetP = 0;                     // by default
        coarseOrbitOffsetL = 0;                // by default
        coarseOrbitOffsetP = 0;                // by default

        ovsRg = 1;                             // by default
        ovsAz = 1;                             // by default

        absTimingErrorFlag = false;
        azTimingError = 0;                     // by default, unit lines
        rgTimingError = 0;                     // by default, unit pixels

        currentWindow  = new Window(1, 25000, 1, 5000);
        originalWindow = new Window(1, 25000, 1, 5000);
//        slavemasteroffsets.l00  = 0;               // window in master coordinates
//        slavemasteroffsets.p00  = 0;
//        slavemasteroffsets.l0N  = 0;
//        slavemasteroffsets.p0N  = 0;
//        slavemasteroffsets.lN0  = 0;
//        slavemasteroffsets.pN0  = 0;
//        slavemasteroffsets.lNN  = 0;
//        slavemasteroffsets.pNN  = 0;
    }

    public SLCImage(MetadataElement element) {

        // units [meters]
        radar_wavelength = (Constants.lightSpeed / Math.pow(10, 6)) / element.getAttributeDouble(AbstractMetadata.radar_frequency);

        // units [Hz]
        PRF = element.getAttributeDouble(AbstractMetadata.pulse_repetition_frequency);

        // work with seconds of the day!
        ProductData.UTC t_azi1_UTC = element.getAttributeUTC(AbstractMetadata.first_line_time);
        tAzi1 = (t_azi1_UTC.getMJD() - (int) t_azi1_UTC.getMJD()) * 24 * 3600;

        // 2 times range sampling rate [HZ]
        rsr2x = (element.getAttributeDouble(AbstractMetadata.range_sampling_rate) * Math.pow(10, 6) * 2);

        // one way (!!!) time to first range pixels [sec]
        tRange1 = element.getAttributeDouble(AbstractMetadata.slant_range_to_first_pixel) / Constants.lightSpeed;

        approxRadarCentreOriginal.x = element.getAttributeDouble(AbstractMetadata.num_samples_per_line) / 2.0d;  // x direction is range!
        approxRadarCentreOriginal.y = element.getAttributeDouble(AbstractMetadata.num_output_lines) / 2.0d;  // y direction is azimuth

        // TODO: replace computation of the centre using getGeoPos()
        // simple averaging of the corners : as approximation accurate enough
        approxGeoCentreOriginal.lat = (float) ((element.getAttributeDouble(AbstractMetadata.first_near_lat) +
                element.getAttributeDouble(AbstractMetadata.first_far_lat) +
                element.getAttributeDouble(AbstractMetadata.last_near_lat) +
                element.getAttributeDouble(AbstractMetadata.last_far_lat)) / 4);

        approxGeoCentreOriginal.lon = (float) ((element.getAttributeDouble(AbstractMetadata.first_near_long) +
                element.getAttributeDouble(AbstractMetadata.first_far_long) +
                element.getAttributeDouble(AbstractMetadata.last_near_long) +
                element.getAttributeDouble(AbstractMetadata.last_far_long)) / 4);

        double[] xyz = new double[3];
        GeoUtils.geo2xyz(getApproxGeoCentreOriginal(), xyz);

        approxXYZCentreOriginal.x = xyz[0];
        approxXYZCentreOriginal.y = xyz[1];
        approxXYZCentreOriginal.z = xyz[2];

        // set dopplers
        final AbstractMetadata.DopplerCentroidCoefficientList[] dopplersArray = AbstractMetadata.getDopplerCentroidCoefficients(element);

        // TODO: check correctness of this!!
        f_DC_a0 = dopplersArray[1].coefficients[0];
        f_DC_a1 = dopplersArray[1].coefficients[1];
        f_DC_a2 = dopplersArray[1].coefficients[2];

    }

    public void parseResFile(File resFileName) throws Exception {

        ResFile resFile = new ResFile(resFileName);

        resFile.setSubBuffer("_Start_readfiles","End_readfiles");

        this.sensor = resFile.parseStringValue("Sensor platform mission identifer");
        this.sarProcessor = resFile.parseStringValue("SAR_PROCESSOR");
        this.radar_wavelength = resFile.parseDoubleValue("Radar_wavelength \\(m\\)");

        this.approxGeoCentreOriginal.lat = (float) resFile.parseDoubleValue("Scene_centre_latitude");
        this.approxGeoCentreOriginal.lon = (float) resFile.parseDoubleValue("Scene_centre_longitude");
        this.averageHeight = 0.0;

        this.approxXYZCentreOriginal = Ellipsoid.ell2xyz(Math.toRadians(approxGeoCentreOriginal.lat),
                Math.toRadians(approxGeoCentreOriginal.lon), averageHeight);

        // azimuth annotations
        this.PRF = resFile.parseDoubleValue("Pulse_Repetition_Frequency \\(computed, Hz\\)");
        this.azimuthBandwidth = resFile.parseDoubleValue("Total_azimuth_band_width \\(Hz\\)");
        ProductData.UTC tAzi1_UTC = resFile.parseTimeValue("First_pixel_azimuth_time \\(UTC\\)");
        this.tAzi1 = (tAzi1_UTC.getMJD() - tAzi1_UTC.getDaysFraction()) * 24 * 3600;
        this.azimuthWeightingWindow = resFile.parseStringValue("Weighting_azimuth");

        // range annotations
        this.rsr2x = resFile.parseDoubleValue("Range_sampling_rate \\(computed, MHz\\)")*2*Math.pow(10,6);
        this.rangeBandwidth = resFile.parseDoubleValue("Total_range_band_width \\(MHz\\)");
        this.tRange1 = resFile.parseDoubleValue("Range_time_to_first_pixel \\(2way\\) \\(ms\\)")/2/1000;
        this.rangeWeightingWindow = resFile.parseStringValue("Weighting_range");

        // doppler
        this.f_DC_a0 = resFile.parseDoubleValue("Xtrack_f_DC_constant \\(Hz, early edge\\)");
        this.f_DC_a1 = resFile.parseDoubleValue("Xtrack_f_DC_linear \\(Hz/s, early edge\\)");
        this.f_DC_a2 = resFile.parseDoubleValue("Xtrack_f_DC_quadratic \\(Hz/s/s, early edge\\)");

        // data windows
        int numberOfLinesTEMP = resFile.parseIntegerValue("Number_of_lines_original");
        int numberOfPixelsTEMP = resFile.parseIntegerValue("Number_of_pixels_original");

        this.originalWindow = new Window(1, numberOfLinesTEMP, 1, numberOfPixelsTEMP);
        this.currentWindow = (Window) originalWindow.clone();

    }

    /*---  RANGE CONVERSIONS ----*/

    // Convert pixel number to range time (1 is first pixel)
    public double pix2tr(double pixel) {
        return tRange1 + ((pixel - 1.0) / rsr2x);
    }

    // Convert pixel number to range (1 is first pixel)
    public double pix2range(double pixel) {
        return Constants.lightSpeed * pix2tr(pixel);
    }

    // Convert range time to pixel number (1 is first pixel)
    public double tr2pix(double rangeTime) {
        return 1.0 + (rsr2x * (rangeTime - tRange1));
    }

    // Convert range pixel to fDC (1 is first pixel, can be ovs)
    public double pix2fdc(double pixel) {
        double tau = (pixel - 1.0) / (rsr2x / 2.0);// two-way time
        return f_DC_a0 + (f_DC_a1 * tau) + (f_DC_a2 * Math.pow(tau, 2));
    }

    /*---  AZIMUTH CONVERSIONS ----*/

    // Convert line number to azimuth time (1 is first line)
    public double line2ta(double line) {
        return tAzi1 + ((line - 1.0) / PRF);
    }

    // Convert azimuth time to line number (1 is first line)
    public double ta2line(double azitime) {
        return 1.0 + PRF * (azitime - tAzi1);
    }


    /* Getters and setters for Encapsulation */

    public double getRadarWavelength() {
        return radar_wavelength;
    }

    public Point getApproxRadarCentreOriginal() {
        return approxRadarCentreOriginal;
    }

    public GeoPos getApproxGeoCentreOriginal() {
        return approxGeoCentreOriginal;
    }

    public Point getApproxXYZCentreOriginal() {
        return approxXYZCentreOriginal;
    }

    public Window getCurrentWindow() {
        return currentWindow;
    }

    public double getPRF() {
        return PRF;
    }

    public double getAzimuthBandwidth() {
        return azimuthBandwidth;
    }

    public double getF_DC_a0() {
        return f_DC_a0;
    }

    public double getF_DC_a1() {
        return f_DC_a1;
    }

    public double getF_DC_a2() {
        return f_DC_a2;
    }

    public int getCoarseOffsetP() {
        return coarseOffsetP;
    }

    public double gettRange1() {
        return tRange1;
    }

    public void settRange1(double tRange1) {
        this.tRange1 = tRange1;
    }

    public double getRangeBandwidth() {
        return rangeBandwidth;
    }

    public void setRangeBandwidth(double rangeBandwidth) {
        this.rangeBandwidth = rangeBandwidth;
    }

    public double getRsr2x() {
        return rsr2x;
    }

    public void setRsr2x(double rsr2x) {
        this.rsr2x = rsr2x;
    }

}
