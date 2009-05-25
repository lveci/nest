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
import org.esa.beam.util.math.MathUtils;
import org.esa.nest.util.Settings;
import org.esa.nest.util.Constants;
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
    private File externalAntennaPatternFile = null;

    @Parameter(description = "Output image scale", defaultValue = "false")
    private boolean outputImageScaleInDb = false;

    private MetadataElement absRoot = null;
    private TiePointGrid incidenceAngle;
    private TiePointGrid slantRangeTime;
    private TiePointGrid latitude;
    private TiePointGrid longitude;

    private String defAuxFileName;
    private String[] mdsPolar = new String[2]; // polarizations for the two bands in the product

    private boolean antElevCorrFlag;
    private boolean absCalibrationFlag;

    private double firstLineUTC = 0.0; // in days
    private double lineTimeInterval = 0.0; // in days
    private double avgSceneHeight = 0.0; // in m
    private double[] oldRefElevationAngle = null; // reference elevation angle for given swath in old aux file, in degree
    private double[] newRefElevationAngle = null; // reference elevation angle for given swath in new aux file, in degree
    private double[] targetTileOldAntPat = null; // old antenna pattern gains for row pixels in a tile, in linear scale
    private double[] targetTileNewAntPat = null; // new antenna pattern gains for row pixels in a tile, in linear scale

    private float[][] oldAntennaPatternSingleSwath = null; // old antenna pattern gains for single swath product, in dB
    private float[][] newAntennaPatternSingleSwath = null; // new antenna pattern gains for single swath product, in dB
    private float[][] oldAntennaPatternWideSwath = null; // old antenna pattern gains for single swath product, in dB
    private float[][] newAntennaPatternWideSwath = null; // new antenna pattern gains for single swath product, in dB

    private String  swath;
    private AbstractMetadata.OrbitStateVector[] orbitStateVectors = null;

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

        try {
            absRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct);

            getCalibrationFlags();

            getProductPolarization();

            getProductSwath();

            getDefaultAuxFile();

            getOrbitStateVectors();

            getOldAntennaPatternGain();

            getNewAntennaPatternGain();

            getFirstLineTime();

            getLineTimeInterval();

            getAverageSceneHeight();

            getTiePoints();

            createTargetProduct();

        } catch(Exception e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    /**
     * Get calibration flags.
     * @throws Exception The exceptions.
     */
    private void getCalibrationFlags() throws Exception {

        antElevCorrFlag = AbstractMetadata.getAttributeBoolean(absRoot, AbstractMetadata.ant_elev_corr_flag);
        if (!antElevCorrFlag) {
            throw new OperatorException("Antenna elevation patter correction has not been applied");
        }

        absCalibrationFlag = AbstractMetadata.getAttributeBoolean(absRoot, AbstractMetadata.abs_calibration_flag);
    }

    /**
     * Get product polarizations for each band in the product.
     * @throws Exception The exceptions.
     */
    private void getProductPolarization() throws Exception {

        String polarName = absRoot.getAttributeString(AbstractMetadata.mds1_tx_rx_polar);
        mdsPolar[0] = null;
        if (polarName.contains("HH") || polarName.contains("HV") || polarName.contains("VH") || polarName.contains("VV")) {
            mdsPolar[0] = polarName.toLowerCase();
        }

        mdsPolar[1] = null;
        polarName = absRoot.getAttributeString(AbstractMetadata.mds2_tx_rx_polar);
        if (polarName.contains("HH") || polarName.contains("HV") || polarName.contains("VH") || polarName.contains("VV")) {
            mdsPolar[1] = polarName.toLowerCase();
        }
    }

    /**
     * Get product swath.
     * @throws Exception The exceptions.
     */
    private void getProductSwath() throws Exception {
        swath = absRoot.getAttributeString(AbstractMetadata.SWATH);
    }

    /**
     * Get default aux file name for the external calibration auxiliary data.
     */
    private void getDefaultAuxFile() throws Exception {
        defAuxFileName = absRoot.getAttributeString(AbstractMetadata.external_calibration_file);
    }

    /**
     * Get orbit state vectors from the abstracted metadata.
     * @throws Exception The exceptions.
     */
    private void getOrbitStateVectors() throws Exception {
        orbitStateVectors = AbstractMetadata.getOrbitStateVectors(absRoot);
    }

    /**
     * Get first line time from the abstracted metadata (in days).
     * @throws Exception The exceptions.
     */
    private void getFirstLineTime() throws Exception {
        firstLineUTC = absRoot.getAttributeUTC(AbstractMetadata.first_line_time).getMJD(); // in days
    }

    /**
     * Get line time interval from the abstracted metadata (in days).
     * @throws Exception The exceptions.
     */
    private void getLineTimeInterval() throws Exception {
        lineTimeInterval = absRoot.getAttributeDouble(AbstractMetadata.line_time_interval) / 86400.0; // s to day
    }

    /**
     * Get average scene height from abstracted metadata.
     * @throws Exception The exceptions.
     */
    private void getAverageSceneHeight() throws Exception {
        avgSceneHeight = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.avg_scene_height);
    }

    /**
     * Get tie points.
     */
    private void getTiePoints() {
        slantRangeTime = OperatorUtils.getSlantRangeTime(sourceProduct);
        incidenceAngle = OperatorUtils.getIncidenceAngle(sourceProduct);
        latitude = OperatorUtils.getLatitude(sourceProduct);
        longitude = OperatorUtils.getLongitude(sourceProduct);
    }

    /**
     * Get the old antenna pattern gain from the default aux file specified in the metadata.
     */
    private void getOldAntennaPatternGain() {

        final String defAuxFilePath = Settings.instance().get("AuxData/envisatAuxDataPath") + File.separator + defAuxFileName;
        if (swath.contains("WS")) {

            oldRefElevationAngle = new double[5]; // reference elevation angles for 5 sub swathes
            oldAntennaPatternWideSwath = new float[5][numOfGains]; // antenna pattern gain for 5 sub swathes
            ASARCalibrationOperator.getWideSwathAntennaPatternGainFromAuxData(
                    defAuxFilePath, mdsPolar[0], numOfGains, oldRefElevationAngle, oldAntennaPatternWideSwath);

        } else {

            oldRefElevationAngle = new double[1]; // reference elevation angle for 1 swath
            oldAntennaPatternSingleSwath = new float[2][numOfGains]; // antenna pattern gain for 2 bands
            ASARCalibrationOperator.getSingleSwathAntennaPatternGainFromAuxData(
                    defAuxFilePath, swath, mdsPolar, numOfGains, oldRefElevationAngle, oldAntennaPatternSingleSwath);
        }
    }

    /**
     * Get the new antenna pattern gain from the user provided aux file.
     */
    private void getNewAntennaPatternGain() {

        final String extAuxFilePath = externalAntennaPatternFile.getAbsolutePath();
        if (swath.contains("WS")) {

            newRefElevationAngle = new double[5]; // reference elevation angles for 5 sub swathes
            newAntennaPatternWideSwath = new float[5][numOfGains]; // antenna pattern gain for 5 sub swathes
            ASARCalibrationOperator.getWideSwathAntennaPatternGainFromAuxData(
                    extAuxFilePath, mdsPolar[0], numOfGains, newRefElevationAngle, newAntennaPatternWideSwath);

        } else {

            newRefElevationAngle = new double[1]; // reference elevation angle for 1 swath
            newAntennaPatternSingleSwath = new float[2][numOfGains];  // antenna pattern gain for 2 bands
            ASARCalibrationOperator.getSingleSwathAntennaPatternGainFromAuxData(
                    extAuxFilePath,  swath, mdsPolar, numOfGains, newRefElevationAngle, newAntennaPatternSingleSwath);
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

    /**
     * Add user selected bands to target product.
     */
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
                                           ProductData.TYPE_FLOAT32,
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

        // For now the ANTENNA_ELEV_PATTERN_ADS is update only for product that is not calibrated by NEST
        if (!absCalibrationFlag) {
            if (swath.contains("WS")) {
                updateWideSwathAntennaElevationPatternRecord();
            } else {
                updateSingleSwathAntennaElevationPatternRecord();
            }
        }
    }

    /**
     * Update auxiliary file name in the metadata of target product.
     */
    private void updateAuxFileName() {
        final MetadataElement tgtAbsRoot = targetProduct.getMetadataRoot().getElement("Abstracted Metadata");
        AbstractMetadata.setAttribute(
                tgtAbsRoot, AbstractMetadata.external_calibration_file, externalAntennaPatternFile.getName());
    }

    /**
     * Update antenna pattern record in the metadata in the target for single swath product.
     */
    private void updateSingleSwathAntennaElevationPatternRecord() {

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
                    double newGain = ASARCalibrationOperator.computeAntPatGain(
                            oldAEPElevationAngles, newRefElevationAngle[0], newAntennaPatternSingleSwath[band]);
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
     * Update antenna pattern record in the metadata in the target for wide swath product.
     */
    private void updateWideSwathAntennaElevationPatternRecord() {

        int numOldAEPRecords = getNumOfAntPatRecords(0);

        String adsName = "ANTENNA_ELEV_PATTERN_ADS";
        final MetadataElement antElevPatADS = targetProduct.getMetadataRoot().getElement(adsName);
        if (antElevPatADS == null) {
            throw new OperatorException("ANTENNA_ELEV_PATTERN_ADS not found");
        }

        for (int i = 0; i < numOldAEPRecords; i++) {

            final MetadataElement record = antElevPatADS.getElement(adsName + "." + (i+1));
            if (record == null) {
                throw new OperatorException(adsName + "." + (i+1) + " not found");
            }

            final MetadataAttribute swathAttr = record.getAttribute("ASAR_Antenna_ADSR.sd/swath");
            if (swathAttr == null) {
                throw new OperatorException("swath not found");
            }
            final String swath = swathAttr.getData().getElemString();

            int subSwathIndex = 0;
            if (swath.contains("SS1")) {
                subSwathIndex = 0;
            } else if (swath.contains("SS2")) {
                subSwathIndex = 1;
            } else if (swath.contains("SS3")) {
                subSwathIndex = 2;
            } else if (swath.contains("SS4")) {
                subSwathIndex = 3;
            } else if (swath.contains("SS5")) {
                subSwathIndex = 4;
            } else {
                throw new OperatorException("Invalid swath");
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

            int numTiePoints = elevationAngleAttr.getData().getNumElems();
            for (int j = 0; j < numTiePoints; j++) {

                final double oldAEPElevationAngles = elevationAngleAttr.getData().getElemFloatAt(j);
                double newGain = ASARCalibrationOperator.computeAntPatGain(
                        oldAEPElevationAngles,
                        newRefElevationAngle[subSwathIndex],
                        newAntennaPatternSingleSwath[subSwathIndex]);
                if (newGain < underFlowFloat) {
                    newGain = -underFlowFloat;
                } else {
                    newGain = 10.0 * Math.log10(newGain);
                }

                antennaPatternAttr.getData().setElemFloatAt(j, (float)newGain);
            }
            antennaPatternAttr.setReadOnly(readOnlyFlag);
        }
    }

    /**
     * Get the number of Antenna Elevation Pattern records for given band.
     * @param band The band index.
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
        //System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

        final Band sourceBand = sourceProduct.getBand(targetBand.getName());
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

        if (swath.contains("WS")) {
            computeWideSwathAntennaPatternForCurrentTile(x0, y0, w, h);
        } else {
            computeSingleSwathAntennaPatternForCurrentTile(x0, y0, w, h, prodBand);
        }

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
                sigma *= targetTileOldAntPat[x - x0] * targetTileOldAntPat[x - x0];

                // apply new antenna elevation pattern gain
                sigma /= targetTileNewAntPat[x - x0] * targetTileNewAntPat[x - x0];

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
     * Compute antenna pattern for the middle row of the given tile for single swath product.
     * Here it is assumed that the elevation angles for pixels in the same column are the same.
     * @param x0 The x coordinate of the upper left point in the current tile.
     * @param y0 The y coordinate of the upper left point in the current tile.
     * @param w The width of the current tile.
     * @param h The height of the current tile.
     * @param band The band index.
     */
    private void computeSingleSwathAntennaPatternForCurrentTile(int x0, int y0, int w, int h, int band) {

        final int y = y0 + h / 2;
        final double zeroDopplerTime = firstLineUTC + y*lineTimeInterval;
        double satelitteHeight = ASARCalibrationOperator.computeSatelliteHeight(zeroDopplerTime, orbitStateVectors);

        targetTileOldAntPat = new double[w];
        targetTileNewAntPat = new double[w];
        for (int x = x0; x < x0 + w; x++) {

            final double slantRange = computeSlantRange(x, y); // in m

            final double earthRadius = ASARCalibrationOperator.computeEarthRadius(
                                            latitude.getPixelFloat(x,y), longitude.getPixelFloat(x,y)); // in m

            final double theta = ASARCalibrationOperator.computeElevationAngle(
                                            slantRange, satelitteHeight, avgSceneHeight + earthRadius); // in degree

            targetTileNewAntPat[x - x0] = ASARCalibrationOperator.computeAntPatGain(
                    theta, newRefElevationAngle[0], newAntennaPatternSingleSwath[band]);

            targetTileOldAntPat[x - x0] = ASARCalibrationOperator.computeAntPatGain(
                    theta, oldRefElevationAngle[0], oldAntennaPatternSingleSwath[band]);
        }
    }

    /**
     * Compute antenna pattern for the middle row of the given tile for wide swath product.
     * Here it is assumed that the elevation angles for pixels in the same column are the same.
     * @param x0 The x coordinate of the upper left point in the current tile.
     * @param y0 The y coordinate of the upper left point in the current tile.
     * @param w The width of the current tile.
     * @param h The height of the current tile.
     */
    private void computeWideSwathAntennaPatternForCurrentTile(int x0, int y0, int w, int h) {

        final int y = y0 + h / 2;
        final double zeroDopplerTime = firstLineUTC + y*lineTimeInterval;
        double satelitteHeight = ASARCalibrationOperator.computeSatelliteHeight(zeroDopplerTime, orbitStateVectors);

        targetTileOldAntPat = new double[w];
        targetTileNewAntPat = new double[w];
        for (int x = x0; x < x0 + w; x++) {

            final double slantRange = computeSlantRange(x, y); // in m

            final double earthRadius = ASARCalibrationOperator.computeEarthRadius(
                                            latitude.getPixelFloat(x,y), longitude.getPixelFloat(x,y)); // in m

            final double theta = ASARCalibrationOperator.computeElevationAngle(
                                            slantRange, satelitteHeight, avgSceneHeight + earthRadius); // in degree

            int subSwathIndex = ASARCalibrationOperator.findSubSwath(theta, newRefElevationAngle);

            targetTileNewAntPat[x - x0] = ASARCalibrationOperator.computeAntPatGain(
                    theta, newRefElevationAngle[subSwathIndex], newAntennaPatternWideSwath[subSwathIndex]);

            subSwathIndex = ASARCalibrationOperator.findSubSwath(theta, oldRefElevationAngle);

            targetTileOldAntPat[x - x0] = ASARCalibrationOperator.computeAntPatGain(
                    theta, oldRefElevationAngle[subSwathIndex], oldAntennaPatternWideSwath[subSwathIndex]);
        }
    }

    /**
     * Compute slant range for given pixel.
     * @param x The x coordinate of the pixel in the source image.
     * @param y The y coordinate of the pixel in the source image.
     * @return The slant range (in meters).
     */
    private double computeSlantRange(int x, int y) {
        final double time = slantRangeTime.getPixelFloat((float)x, (float)y) / 1000000000.0; //convert ns to s
        return time * Constants.halfLightSpeed; // in m
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
