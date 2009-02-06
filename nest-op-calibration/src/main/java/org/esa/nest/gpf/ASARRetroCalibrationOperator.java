/*
 * Copyright (C) 2002-2007 by ?
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.esa.nest.gpf;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatAuxReader;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.SystemUtils;
import org.esa.beam.util.math.MathUtils;
import org.esa.nest.util.Settings;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;

import java.awt.*;
import java.io.IOException;
import java.io.File;
import java.util.ArrayList;

/**
 * Retro-Calibration for ASAR data products.
 */
@OperatorMetadata(alias = "ASAR-RetroCalibration",
        description = "RetroCalibration of ASAR data products")
public class ASARRetroCalibrationOperator extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            sourceProductId="source", label="Source Band")
    String[] sourceBandNames;

    @Parameter(description = "The antenne elevation pattern gain auxiliary data file.")
    private File externalAntennaPatternFile;

    @Parameter(description = "Output image scale", defaultValue = "false")
    private boolean outputImageScaleInDb;

    private Band sourceBand;
    private Band targetBand;

    private MetadataElement abstractedMetadata;
    private TiePointGrid incidenceAngle;
    private TiePointGrid slantRangeTime;

    private String[] mdsPolar = new String[2]; // polarizations for the two bands in the product

    private boolean antElevPatCorrFlag;
    private boolean absCalibrationFlag;

    private double oldRefElevationAngle; // reference elevation angle for given swath in old aux file, in degree
    private double newRefElevationAngle; // reference elevation angle for given swath in new aux file, in degree
    private double[] targetTileOldAntPat; // old antenna pattern gains for row pixels in a tile, in linear scale
    private double[] targetTileNewAntPat; // new antenna pattern gains for row pixels in a tile, in linear scale
    private double[] rSat; // the distance from satellite to the Earth center for each MPP ADSR record, in m

    private float[][] oldAntPat; // old antenna pattern gains for given swath and 201 elevation angles, in dB
    private float[][] newAntPat; // new antenna pattern gains for given swath and 201 elevation angles, in dB

    private int swath;
    private int numMPPRecords; // number of MPP ADSR records
    private int[] lastLineIndex; // the index of the last line covered by each MPP ADSR record

    private static final double lightSpeed = 299792458; //  m / s
    private static final double halfLightSpeed = lightSpeed / 2;
    private static final double underFlowFloat = 1.0e-30;
    private static final int numTiePoints = 11;
    private static final int numOfGains = 201; // number of antenna pattern gain values for a given swath and
                                               // polarization in the aux file
    /**
     * Default constructor. The graph processing framework
     * requires that an operator has a default constructor.
     */
    public ASARRetroCalibrationOperator() {
    }

    /**
     * Initializes this operator and sets the one and only target product.
     * <p>The target product can be either defined by a field of type {@link org.esa.beam.framework.datamodel.Product} annotated with the
     * {@link org.esa.beam.framework.gpf.annotations.TargetProduct TargetProduct} annotation or
     * by calling {@link #setTargetProduct} method.</p>
     * <p>The framework calls this method after it has created this operator.
     * Any client code that must be performed before computation of tile data
     * should be placed here.</p>
     *
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs during operator initialisation.
     * @see #getTargetProduct()
     */
    @Override
    public void initialize() throws OperatorException {

        abstractedMetadata = OperatorUtils.getAbstractedMetadata(sourceProduct);
        getCalibrationFlags();
        getPolarization();
        swath = ASARCalibrationOperator.getSwath(abstractedMetadata);

        getOldAntennaPatternGain();
        getNewAntennaPatternGain();

        slantRangeTime = OperatorUtils.getSlantRangeTime(sourceProduct);
        incidenceAngle = OperatorUtils.getIncidenceAngle(sourceProduct);

        getNumOfRecordsInMainProcParam();
        getSatelliteToEarthCenterDistance();

        createTargetProduct();
    }

    /**
     * Called by the framework in order to compute a tile for the given target band.
     * <p>The default implementation throws a runtime exception with the message "not implemented".</p>
     *
     * @param targetBand The target band.
     * @param targetTile The current tile associated with the target band to be computed.
     * @param pm         A progress monitor which should be used to determine computation cancelation requests.
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs during computation of the target raster.
     */
    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        final Rectangle targetTileRectangle = targetTile.getRectangle();
        final int x0 = targetTileRectangle.x;
        final int y0 = targetTileRectangle.y;
        final int w = targetTileRectangle.width;
        final int h = targetTileRectangle.height;

        sourceBand = sourceProduct.getBand(targetBand.getName());
        final Tile sourceRaster = getSourceTile(sourceBand, targetTileRectangle, pm);

        final Unit.UnitType bandUnit = Unit.getUnitType(sourceBand);

        // copy band if unit is phase
        if(bandUnit == Unit.UnitType.PHASE) {
            targetTile.setRawSamples(sourceRaster.getRawSamples());
            return;
        }

        final String pol = OperatorUtils.getPolarizationFromBandName(sourceBand.getName());
        int prodBand = 0;
        if (pol != null && mdsPolar[1] != null && pol.contains(mdsPolar[1])) {
            prodBand = 1;
        }

        computeOldAndNewAntPatForCurrentTile(x0, y0, w, h, prodBand);

        double sigma;
        for (int x = x0; x < x0 + w; x++) {

            for (int y = y0; y < y0 + h; y++) {

                sigma = sourceRaster.getSampleDouble(x, y);

                if (bandUnit == Unit.UnitType.INTENSITY_DB) {
                    sigma = Math.pow(10, sigma / 10.0); // convert dB to linear scale
                } else if (bandUnit == Unit.UnitType.AMPLITUDE) {
                    sigma *= sigma;
                }

                // remove old antenna elevation pattern gain
                sigma *= targetTileOldAntPat[x - x0];

                // apply new antenna elevation pattern gain
                sigma /= targetTileNewAntPat[x - x0];

                if (outputImageScaleInDb) { // convert calibration result to dB
                    if (sigma < underFlowFloat) {
                        sigma = -underFlowFloat;
                    } else {
                        sigma = 10.0 * Math.log10(sigma);
                    }
                }

                targetTile.setSample(x, y, sigma);
            }
        }
    }

    /**
     * Get default aux file name for the external calibration auxiliary data from DSD in the SPH.
     *
     * @return The default auxiliary data file name.
     */
    private String getDefaultAuxFile() {

        final MetadataElement dsd = sourceProduct.getMetadataRoot().getElement("DSD").getElement("DSD.17");
        if (dsd == null) {
            throw new OperatorException("DSD not found");
        }

        final MetadataAttribute auxFileNameAttr = dsd.getAttribute("file_name");
        if (auxFileNameAttr == null) {
            throw new OperatorException("file_name not found");
        }
        final String auxFileName = auxFileNameAttr.getData().getElemString();
        //System.out.println("aux file name is " + auxFileName);

        String auxFilePath;
        auxFilePath = Settings.instance().get("envisatAuxDataPath") + File.separator + auxFileName;

        return auxFilePath;
    }

    /**
     * Get calibration flags.
     */
    private void getCalibrationFlags() {

        MetadataAttribute antElevCorrFlagAttr = abstractedMetadata.getAttribute(AbstractMetadata.ant_elev_corr_flag);
        if (antElevCorrFlagAttr == null) {
            throw new OperatorException(AbstractMetadata.ant_elev_corr_flag + " not found");
        }
        antElevPatCorrFlag = antElevCorrFlagAttr.getData().getElemBoolean();
        //System.out.println("Antenna pattern correction flag is " + antElevPatCorrFlag);
        if (!antElevPatCorrFlag) {
            throw new OperatorException("Antenna elevation patter correction has not been applied");
        }

        MetadataAttribute absCalibrationFlagAttr = abstractedMetadata.getAttribute(AbstractMetadata.abs_calibration_flag);
        if (absCalibrationFlagAttr == null) { // no absolute calibration has been applied
            throw new OperatorException(AbstractMetadata.abs_calibration_flag + " not found");
        }

        absCalibrationFlag = absCalibrationFlagAttr.getData().getElemBoolean();
    }

    /**
     * Get polarizations for each band in the product.
     */
    private void getPolarization() {

        MetadataAttribute polarAttr = abstractedMetadata.getAttribute(AbstractMetadata.mds1_tx_rx_polar);
        if (polarAttr == null) {
            throw new OperatorException(AbstractMetadata.mds1_tx_rx_polar + " not found");
        }

        mdsPolar[0] = null;
        String polarName = polarAttr.getData().getElemString();
        if (polarName.contains("HH") || polarName.contains("HV") || polarName.contains("VH") || polarName.contains("VV")) {
            mdsPolar[0] = polarName.toLowerCase();
        }

        polarAttr = abstractedMetadata.getAttribute(AbstractMetadata.mds2_tx_rx_polar);
        if (polarAttr == null) {
            throw new OperatorException(AbstractMetadata.mds2_tx_rx_polar + " not found");
        }

        mdsPolar[1] = null;
        polarName = polarAttr.getData().getElemString();
        if (polarName.contains("HH") || polarName.contains("HV") || polarName.contains("VH") || polarName.contains("VV")) {
            mdsPolar[1] = polarName.toLowerCase();
        }

        //System.out.println("MDS1 polarization is " + mdsPolar[0]);
        //System.out.println("MDS2 polarization is " + mdsPolar[1]);
    }

    /**
     * Get the old antenna pattern gain from the default aux file specified in the metadata.
     */
    private void getOldAntennaPatternGain() {

        final String defAuxFileName = getDefaultAuxFile();
        oldAntPat = new float[2][numOfGains];
        oldRefElevationAngle = getAntennaPatternGainFromAuxData(defAuxFileName, oldAntPat);
    }

    /**
     * Get the new antenna pattern gain from the user provided aux file.
     */
    private void getNewAntennaPatternGain() {

        final String extAuxFileName = externalAntennaPatternFile.getAbsolutePath();
        newAntPat = new float[2][numOfGains];
        newRefElevationAngle = getAntennaPatternGainFromAuxData(extAuxFileName, newAntPat);
    }

    /**
     * Obtain from auxiliary data the elevation angle for a given swath and the antenna elevation
     * pattern gains for the swath and given polarization of the product.
     *
     * @param fileName The auxiliary data file name.
     * @param antPatArray The antenna pattern array.
     * @return The reference elevation angle for the given swath.
     * @throws OperatorException The IO exception.
     */
    private double getAntennaPatternGainFromAuxData(String fileName, float[][] antPatArray) throws OperatorException {

        double refElevationAngle;
        final EnvisatAuxReader reader = new EnvisatAuxReader();

        try {
            reader.readProduct(fileName);

            final String[] swathName = {"is1", "is2", "is3_ss2", "is4_ss3", "is5_ss4", "is6_ss5", "is7", "ss1"};
            final String elevAngName = "elev_ang_" + swathName[swath];

            final ProductData elevAngleData = reader.getAuxData(elevAngName);
            refElevationAngle = (double) elevAngleData.getElemFloat();
            //System.out.println("elevation angle is " + refElevationAngle);
            //System.out.println();

            final String patternName = "pattern_" + swathName[swath];
            final ProductData patData = reader.getAuxData(patternName);
            final float[] pattern = ((float[]) patData.getElems());

            if (pattern.length != 804) {
                throw new OperatorException("Incorret array length for " + patternName);
            }
            /*
            System.out.print("num values " + pattern.length);
            System.out.println();
            for (float val : pattern) {
                System.out.print(val + ", ");
            }
            System.out.println();
            */

            for (int i = 0; i < 2 && mdsPolar[i] != null && mdsPolar[i].length() != 0; i++) {
                if (mdsPolar[i].contains("hh")) {
                    System.arraycopy(pattern, 0, antPatArray[i], 0, numOfGains);
                } else if (mdsPolar[i].contains("vv")) {
                    System.arraycopy(pattern, numOfGains, antPatArray[i], 0, numOfGains);
                } else if (mdsPolar[i].contains("hv")) {
                    System.arraycopy(pattern, 2 * numOfGains, antPatArray[i], 0, numOfGains);
                } else if (mdsPolar[i].contains("vh")) {
                    System.arraycopy(pattern, 3 * numOfGains, antPatArray[i], 0, numOfGains);
                }
            }
            /*
            for (float val : newAntPat[0]) {
                System.out.print(val + ", ");
            }
            System.out.println();
            for (float val : newAntPat[1]) {
                System.out.print(val + ", ");
            }
            System.out.println();
            */
        } catch (IOException e) {
            throw new OperatorException(e);
        }

        return refElevationAngle;
    }

    /**
     * Get number of records in Main Processing Params data set.
     */
    private void getNumOfRecordsInMainProcParam() {

        final MetadataElement dsd = sourceProduct.getMetadataRoot().getElement("DSD").getElement("DSD.3");
        if (dsd == null) {
            throw new OperatorException("DSD not found");
        }

        final MetadataAttribute numRecordsAttr = dsd.getAttribute("num_records");
        if (numRecordsAttr == null) {
            throw new OperatorException("num_records not found");
        }
        numMPPRecords = numRecordsAttr.getData().getElemInt();
        if (numMPPRecords < 1) {
            throw new OperatorException("Invalid num_records.");
        }
        //System.out.println("The number of Main Processing Params records is " + numMPPRecords);
    }

    /**
     * Compute distance from satellite to the Earth center using satellite corrodinate in Metadata.
     */
    private void getSatelliteToEarthCenterDistance() {

        lastLineIndex = new int[numMPPRecords];
        rSat = new double[numMPPRecords];

        final MetadataElement mppAds = sourceProduct.getMetadataRoot().getElement("MAIN_PROCESSING_PARAMS_ADS");
        if (mppAds == null) {
            throw new OperatorException("MAIN_PROCESSING_PARAMS_ADS not found");
        }

        MetadataElement ads;
        for (int i = 0; i < numMPPRecords; i++) {

            if (numMPPRecords == 1) {
                ads = mppAds;
            } else {
                ads = mppAds.getElement("MAIN_PROCESSING_PARAMS_ADS." + (i+1));
            }

            final MetadataAttribute numOutputLinesAttr = ads.getAttribute("num_output_lines");
            if (numOutputLinesAttr == null) {
                throw new OperatorException("num_output_lines not found");
            }
            final int numLinesPerRecord = numOutputLinesAttr.getData().getElemInt();

            if (i == 0) {
                lastLineIndex[i] = numLinesPerRecord - 1;
            } else {
                lastLineIndex[i] = lastLineIndex[i - 1] + numLinesPerRecord;
            }

            final MetadataAttribute xPositionAttr = ads.getAttribute("ASAR_Main_ADSR.sd/orbit_state_vectors.3.x_pos_1");
            if (xPositionAttr == null) {
                throw new OperatorException("x_pos_1 not found");
            }
            final float x_pos = xPositionAttr.getData().getElemInt() / 100.0f; // divide 100 to convert unit from 10^-2 m to m
            //System.out.println("x position is " + x_pos);

            final MetadataAttribute yPositionAttr = ads.getAttribute("ASAR_Main_ADSR.sd/orbit_state_vectors.3.y_pos_1");
            if (yPositionAttr == null) {
                throw new OperatorException("y_pos_1 not found");
            }
            final float y_pos = yPositionAttr.getData().getElemInt() / 100.0f; // divide 100 to convert unit from 10^-2 m to m
            //System.out.println("y position is " + y_pos);

            final MetadataAttribute zPositionAttr = ads.getAttribute("ASAR_Main_ADSR.sd/orbit_state_vectors.3.z_pos_1");
            if (zPositionAttr == null) {
                throw new OperatorException("z_pos_1 not found");
            }
            final float z_pos = zPositionAttr.getData().getElemInt() / 100.0f; // divide 100 to convert unit from 10^-2 m to m
            //System.out.println("z position is " + z_pos);

            final double r = Math.sqrt(x_pos * x_pos + y_pos * y_pos + z_pos * z_pos); // in m
            if (Double.compare(r, 0.0) == 0) {
                throw new OperatorException("x, y and z positions in orbit_state_vectors are all zeros");
            }
            rSat[i] = r;
        }
    }

    /**
     * Create target product.
     */
    private void createTargetProduct() {

        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    sourceProduct.getSceneRasterWidth(),
                                    sourceProduct.getSceneRasterHeight());

        addSelectedBands();

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyFlagCodings(sourceProduct, targetProduct);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        updateTargetProductMetadata();
    }

    private void addSelectedBands() {

        if (sourceBandNames == null || sourceBandNames.length == 0) {
            final Band[] bands = sourceProduct.getBands();
            ArrayList<String> bandNameList = new ArrayList<String>(sourceProduct.getNumBands());
            for (Band band : bands) {
                bandNameList.add(band.getName());
            }
            sourceBandNames = bandNameList.toArray(new String[bandNameList.size()]);
        }

        final Band[] sourceBands = new Band[sourceBandNames.length];
        for (int i = 0; i < sourceBandNames.length; i++) {
            final String sourceBandName = sourceBandNames[i];
            final Band sourceBand = sourceProduct.getBand(sourceBandName);
            if (sourceBand == null) {
                throw new OperatorException("Source band not found: " + sourceBandName);
            }
            sourceBands[i] = sourceBand;
        }

        for (Band srcBand : sourceBands) {

            String targetBandName = srcBand.getName();
            String targetUnit = "intensity";

            final String unit = srcBand.getUnit();
            if (unit != null && unit.contains("phase")) {
                targetUnit = "phase";
            }

            // add band only if it doean't already exist
            if(targetProduct.getBand(targetBandName) == null) {
                final Band targetBand = new Band(targetBandName,
                                           ProductData.TYPE_FLOAT64,
                                           sourceProduct.getSceneRasterWidth(),
                                           sourceProduct.getSceneRasterHeight());

                if (outputImageScaleInDb && !targetUnit.equals("phase")) {
                    targetUnit = "intensity_db";
                }
                targetBand.setUnit(targetUnit);
                targetProduct.addBand(targetBand);
            }
        }
    }

    /**
     * Update the metadata in the target product.
     */
    private void updateTargetProductMetadata() {

        updateAuxFileName();
        updateAntennaElevationPatternRecord();
    }

    /**
     * Update auxiliary file name in the metadata in the target product.
     */
    private void updateAuxFileName() {

        final String auxFileName = externalAntennaPatternFile.getName();
        final MetadataElement abs = targetProduct.getMetadataRoot().getElement("Abstracted Metadata");
        MetadataAttribute attr = abs.getAttribute(AbstractMetadata.external_calibration_file);
        abs.removeAttribute(attr);
        abs.addAttribute(new MetadataAttribute(AbstractMetadata.external_calibration_file,
                                               ProductData.createInstance(auxFileName),
                                               false));
        /*
        String auxFileName = externalAntennaPatternFile.getName();
        MetadataElement dsd17Ads = targetProduct.getMetadataRoot().getElement("DSD").getElement("DSD.17");
        MetadataAttribute att = dsd17Ads.getAttribute("file_name");
        dsd17Ads.removeAttribute(att);
        dsd17Ads.addAttribute(new MetadataAttribute("file_name", ProductData.createInstance(auxFileName), false));
        */
    }

    /**
     * Update antenna pattern record in the metadata in the target product.
     */
    private void updateAntennaElevationPatternRecord() {

        for (int band = 0; band < 2 && mdsPolar[band] != null && mdsPolar[band].length() != 0; band++) {

            int numOldAEPRecords = getNumOfAntPatRecords(band);

            // get antenna elevation pattern records
            String adsName;
            if (band == 0) {
                adsName = "ANTENNA_ELEV_PATTERN_ADS";
            } else {
                adsName = "ANTENNA_ELEV_PATTERN_ADS" + (band + 1);
            }

            final MetadataElement antElevPatADS = targetProduct.getMetadataRoot().getElement(adsName);
            if (antElevPatADS == null) {
                throw new OperatorException("ANTENNA_ELEV_PATTERN_ADS not found");
            }

            for (int i = 0; i < numOldAEPRecords; i++) {

                final MetadataElement record = antElevPatADS.getElement(adsName + "." + (i+1));
                if (record == null) {
                    throw new OperatorException(adsName + "." + (i+1) + " not found");
                }

                final MetadataAttribute antennaPatternAttr =
                        record.getAttribute("ASAR_Antenna_ADSR.sd/elevation_pattern.antenna_pattern");
                if (antennaPatternAttr == null) {
                    throw new OperatorException("antenna_pattern not found");
                }

                final boolean readOnlyFlag = antennaPatternAttr.isReadOnly();
                antennaPatternAttr.setReadOnly(false);

                final MetadataAttribute elevationAngleAttr =
                        record.getAttribute("ASAR_Antenna_ADSR.sd/elevation_pattern.elevation_angles");
                if (elevationAngleAttr == null) {
                    throw new OperatorException("elevation_angles not found");
                }

                for (int j = 0; j < numTiePoints; j++) {

                    final double oldAEPElevationAngles = elevationAngleAttr.getData().getElemFloatAt(j);
                    double newGain = computeAntPatGain(oldAEPElevationAngles, newRefElevationAngle, newAntPat[band]);
                    if (newGain < underFlowFloat) {
                        newGain = -underFlowFloat;
                    } else {
                        newGain = 10.0 * Math.log10(newGain);
                    }

                    antennaPatternAttr.getData().setElemFloatAt(j, (float)newGain);
                    //System.out.println("newGain[" + i + "][" + j + "] = " + newGain);
                }
                antennaPatternAttr.setReadOnly(readOnlyFlag);
            }
        }
    }

    /**
     * Get the number of Antenna Elevation Pattern records for given band.
     * @return The number of antenna pattern records.
     */
    private int getNumOfAntPatRecords(int band) {

        final MetadataElement dsd = targetProduct.getMetadataRoot().getElement("DSD").getElement("DSD." + (7 + band));
        if (dsd == null) {
            throw new OperatorException("DSD not found");
        }

        final MetadataAttribute numRecordsAttr = dsd.getAttribute("num_records");
        if (numRecordsAttr == null) {
            throw new OperatorException("num_records not found");
        }

        final int numAEPRecords = numRecordsAttr.getData().getElemInt();
        if (numAEPRecords < 1) {
            throw new OperatorException("Invalid num_records for ANTENNA ELEVATION PATTERN.");
        }
        //System.out.println("The number of Antenna Elevation Pattern records is " + numAEPRecords);

        return numAEPRecords;
    }

    /**
     * Compute antenna elevation pattern gain for pixels in the middle row of the given tile.
     * Here it is assumed that the elevation angles for pixels in the same column are the same.
     *
     * @param x0 The x coordinate of the upper left point in the current tile.
     * @param y0 The y coordinate of the upper left point in the current tile.
     * @param w The width of the current tile.
     * @param h The height of the current tile.
     */
    private void computeOldAndNewAntPatForCurrentTile(int x0, int y0, int w, int h, int band) {

        final int y = y0 + h / 2;

        double rsat = 0.0;
        for (int i = 0; i < numMPPRecords; i++) {
            if (y <= lastLineIndex[i]) {
                rsat = rSat[i];
                break;
            }
        }

        targetTileOldAntPat = new double[w];
        targetTileNewAntPat = new double[w];

        for (int x = x0; x < x0 + w; x++) {
            final double theta = computeElevationAngle(x, y, rsat); // in degree
            targetTileNewAntPat[x - x0] = computeAntPatGain(theta, newRefElevationAngle, newAntPat[band]);
            targetTileOldAntPat[x - x0] = computeAntPatGain(theta, oldRefElevationAngle, oldAntPat[band]);
        }
    }

    /**
     * Compute elevation angle (in degree) for the given pixel.
     *
     * @param x The x coordinate of the given pixel.
     * @param y The y coordinate of the given pixel.
     * @param rsat The distance from satellite to the Earth center (in m).
     * @return The elevation angle.
     */
    private double computeElevationAngle(int x, int y, double rsat) {

        final double alpha = incidenceAngle.getPixelFloat(x + 0.5f, y + 0.5f) * MathUtils.DTOR; // in radian
        final double time = slantRangeTime.getPixelFloat(x + 0.5f, y + 0.5f) / 1000000000.0; //convert ns to s
        final double r = time * halfLightSpeed; // in m
        return (alpha - (float) Math.asin(Math.sin(alpha) * r / rsat)) * MathUtils.RTOD; // in degree
    }

    /**
     * Compute antenna pattern gains for the given elevation angle using linear interpolation.
     *
     * @param elevAngle The elevation angle (in degree) of a given pixel.
     * @param refElevationAngle The reference elevation angle (in degree).
     * @param antPatArray The antenna pattern array.
     * @return The antenna pattern gain (in linear scale).
     */
    private static double computeAntPatGain(double elevAngle, double refElevationAngle, float[] antPatArray) {

        final double delta = 0.05;
        final int k = (int) ((elevAngle - refElevationAngle + 5.0) / delta);
        final double theta1 = refElevationAngle - 5.0 + k * delta;
        final double theta2 = theta1 + delta;
        final double gain1 = Math.pow(10, (double) antPatArray[k] / 10.0); // convert dB to linear scale
        final double gain2 = Math.pow(10, (double) antPatArray[k + 1] / 10.0);
        /*
        System.out.println("Reference elevation angle is " + refElevationAngle);
        System.out.println("Pixel elevation angle is " + theta);
        System.out.println("theta1 = " + theta1);
        System.out.println("theta2 = " + theta2);
        System.out.println("gain1 = " + newAntPat[k]);
        System.out.println("gain2 = " + newAntPat[k+1]);
        System.out.println("gain = " + targetTileNewAntPat[x - x0]);
        */
        return ((theta2 - elevAngle) * gain1 + (elevAngle - theta1) * gain2) / (theta2 - theta1);
    }

    /**
    * Set the path to the external antenna pattern file.
    * This function is used by unit test only.
    * @param path The path to the external antenna pattern file.
    */
    public void setExternalAntennaPatternFile(String path) {

        externalAntennaPatternFile = new File(path);
        if (!externalAntennaPatternFile.exists()) {
            throw new OperatorException("External antenna pattern file for unit test does not exist");
        }
    }

    /**
     * The SPI is used to register this operator in the graph processing framework
     * via the SPI configuration file
     * {@code META-INF/services/org.esa.beam.framework.gpf.OperatorSpi}.
     * This class may also serve as a factory for new operator instances.
     *
     * @see OperatorSpi#createOperator()
     * @see OperatorSpi#createOperator(java.util.Map, java.util.Map)
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(ASARRetroCalibrationOperator.class);
        }
    }
}
