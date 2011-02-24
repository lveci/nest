/*
 * Copyright (C) 2010 Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.nest.dataio.ceos.ers;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.dataop.maptransf.TransverseMercatorDescriptor;
import org.esa.beam.util.Guardian;
import org.esa.nest.dataio.IllegalBinaryFormatException;
import org.esa.nest.dataio.ReaderUtils;
import org.esa.nest.dataio.ceos.CEOSImageFile;
import org.esa.nest.dataio.ceos.CEOSProductDirectory;
import org.esa.nest.dataio.ceos.CeosHelper;
import org.esa.nest.dataio.ceos.records.BaseRecord;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.util.Constants;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * This class represents a product directory.
 *
 */
class ERSProductDirectory extends CEOSProductDirectory {

    private ERSImageFile[] _imageFiles = null;
    private ERSLeaderFile _leaderFile = null;

    private int _sceneWidth = 0;
    private int _sceneHeight = 0;

    private final transient Map<String, ERSImageFile> bandImageFileMap = new HashMap<String, ERSImageFile>(1);

    public ERSProductDirectory(final File dir) {
        Guardian.assertNotNull("dir", dir);

        constants = new ERSConstants();
        _baseDir = dir;
    }

    @Override
    protected void readProductDirectory() throws IOException, IllegalBinaryFormatException {
        readVolumeDirectoryFile();
        _leaderFile = new ERSLeaderFile(
                createInputStream(CeosHelper.getCEOSFile(_baseDir, constants.getLeaderFilePrefix())));

        final String[] imageFileNames = CEOSImageFile.getImageFileNames(_baseDir, constants.getImageFilePrefix());
        final int numImageFiles = imageFileNames.length;
        _imageFiles = new ERSImageFile[numImageFiles];
        for (int i = 0; i < numImageFiles; i++) {
            _imageFiles[i] = new ERSImageFile(createInputStream(new File(_baseDir, imageFileNames[i])));
        }

        _sceneWidth = _imageFiles[0].getRasterWidth();
        _sceneHeight = _imageFiles[0].getRasterHeight();
        assertSameWidthAndHeightForAllImages(_imageFiles, _sceneWidth, _sceneHeight);
    }

    public boolean isERS() throws IOException {
        if(productType == null || _volumeDirectoryFile == null)
            readVolumeDirectoryFile();
        return isERS1() || isERS2();
    }

    private boolean isERS1() {
        return !productType.contains("RAW") &&
                (productType.contains("ERS1") || productType.contains("ERS-1") || productType.contains("ERS_1"));
    }

    private boolean isERS2() {
        return !productType.contains("RAW") &&
                (productType.contains("ERS2") || productType.contains("ERS-2") || productType.contains("ERS_2"));
    }

    String getMission() {
        if(isERS1())
            return "ERS1";
        else if(isERS2())
            return "ERS2";
        return "";
    }

    @Override
    public Product createProduct() throws IOException {
        final Product product = new Product(getProductName(),
                                            productType,
                                            _sceneWidth, _sceneHeight);

        if(_imageFiles.length > 1) {
            int index = 1;
            for (final ERSImageFile imageFile : _imageFiles) {

                if(isProductSLC) {
                    final Band bandI = createBand(product, "i_" + index, Unit.REAL, imageFile);
                    final Band bandQ = createBand(product, "q_" + index, Unit.IMAGINARY, imageFile);
                    ReaderUtils.createVirtualIntensityBand(product, bandI, bandQ, "_"+index);
                    ReaderUtils.createVirtualPhaseBand(product, bandI, bandQ, "_"+index);
                } else {
                    Band band = createBand(product, "Amplitude_" + index, Unit.AMPLITUDE, imageFile);
                    ReaderUtils.createVirtualIntensityBand(product, band, "_"+index);
                }
                ++index;
            }
        } else {
            final ERSImageFile imageFile = _imageFiles[0];
            if(isProductSLC) {
                final Band bandI = createBand(product, "i", Unit.REAL, imageFile);
                final Band bandQ = createBand(product, "q", Unit.IMAGINARY, imageFile);
                ReaderUtils.createVirtualIntensityBand(product, bandI, bandQ, "");
                ReaderUtils.createVirtualPhaseBand(product, bandI, bandQ, "");
            } else {
                Band band = createBand(product, "Amplitude", Unit.AMPLITUDE, imageFile);
                ReaderUtils.createVirtualIntensityBand(product, band, "");
            }
        }

        product.setStartTime(getUTCScanStartTime(_leaderFile.getSceneRecord(), null));
        product.setEndTime(getUTCScanStopTime(_leaderFile.getSceneRecord(), null));
        product.setDescription(getProductDescription());

        ReaderUtils.addGeoCoding(product, _leaderFile.getLatCorners(), _leaderFile.getLonCorners());
        addTiePointGrids(product, _leaderFile.getFacilityRecord(), _leaderFile.getSceneRecord());
        addMetaData(product);
        
        if(productType.contains("GEC") || productType.contains("IMG")) {
            ReaderUtils.createMapGeocoding(product, TransverseMercatorDescriptor.NAME,
                product.getBandAt(0).getNoDataValue());
        }

        return product;
    }

    @Override
    public CEOSImageFile getImageFile(final Band band) {
        return bandImageFileMap.get(band.getName());
    }

    @Override
    public void close() throws IOException {
        for (int i = 0; i < _imageFiles.length; i++) {
            _imageFiles[i].close();
            _imageFiles[i] = null;
        }
        _imageFiles = null;
    }

    private Band createBand(final Product product, final String name, final String unit, final ERSImageFile imageFile) {
        int dataType = ProductData.TYPE_UINT16;
        if(isSLC()) {
            dataType = ProductData.TYPE_INT16;
        }
        final Band band = new Band(name, dataType,
                                   _sceneWidth, _sceneHeight);
        band.setDescription(name);
        band.setUnit(unit);
        product.addBand(band);
        bandImageFileMap.put(name, imageFile);

      /*
        final int bandIndex = index;
        final double scalingFactor = _leaderFile.getAbsoluteCalibrationGain(bandIndex);
        final double scalingOffset = _leaderFile.getAbsoluteCalibrationOffset(bandIndex);
        band.setScalingFactor(scalingFactor);
        band.setScalingOffset(scalingOffset);
        band.setNoDataValueUsed(false);
        final int[] histogramBins = _trailerFile.getHistogramBinsForBand(bandIndex);
        final float scaledMinSample = (float) (getMinSampleValue(histogramBins) * scalingFactor + scalingOffset);
        final float scaledMaxSample = (float) (getMaxSampleValue(histogramBins) * scalingFactor + scalingOffset);
        final ImageInfo imageInfo = new ImageInfo(scaledMinSample, scaledMaxSample, histogramBins);
        band.setImageInfo(imageInfo);
        band.setDescription("Radiance band " + ImageFile.getBandIndex());
        */
        return band;
    }

    private void addMetaData(final Product product) throws IOException {
        final MetadataElement root = product.getMetadataRoot();

        final MetadataElement leadMetadata = new MetadataElement("Leader");
        _leaderFile.addLeaderMetadata(leadMetadata);
        root.addElement(leadMetadata);

        final MetadataElement volMetadata = new MetadataElement("Volume");
        _volumeDirectoryFile.assignMetadataTo(volMetadata);
        root.addElement(volMetadata);

        int c = 1;
        for (final ERSImageFile imageFile : _imageFiles) {
            imageFile.assignMetadataTo(root, c++);
        }

        addSummaryMetadata(new File(_baseDir, ERSConstants.SUMMARY_FILE_NAME), "Summary Information", root);
        addAbstractedMetadataHeader(product, root);
    }

    private void addAbstractedMetadataHeader(Product product, MetadataElement root) {

        final MetadataElement absRoot = AbstractMetadata.addAbstractedMetadataHeader(root);

        final BaseRecord sceneRec = _leaderFile.getSceneRecord();
        final BaseRecord mapProjRec = _leaderFile.getMapProjRecord();
        final BaseRecord facilityRec = _leaderFile.getFacilityRecord();

        //mph
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT, getProductName());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT_TYPE, getProductType());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.SPH_DESCRIPTOR,
                sceneRec.getAttributeString("Product type descriptor"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.MISSION, getMission());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ACQUISITION_MODE, "Stripmap");

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PROC_TIME,
                getProcTime(_volumeDirectoryFile.getVolumeDescriptorRecord()));

        final ProductData.UTC startTime = getUTCScanStartTime(sceneRec, null);
        final ProductData.UTC endTime = getUTCScanStopTime(sceneRec, null);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_line_time, startTime);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_line_time, endTime);

        if(mapProjRec != null) {
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_near_lat,
                    mapProjRec.getAttributeDouble("1st line 1st pixel geodetic latitude"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_near_long,
                    mapProjRec.getAttributeDouble("1st line 1st pixel geodetic longitude"));

            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_far_lat,
                    mapProjRec.getAttributeDouble("1st line last valid pixel geodetic latitude"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_far_long,
                    mapProjRec.getAttributeDouble("1st line last valid pixel geodetic longitude"));

            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_near_lat,
                    mapProjRec.getAttributeDouble("Last line 1st pixel geodetic latitude"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_near_long,
                    mapProjRec.getAttributeDouble("Last line 1st pixel geodetic longitude"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_far_lat,
                    mapProjRec.getAttributeDouble("Last line last valid pixel geodetic latitude"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_far_long,
                    mapProjRec.getAttributeDouble("Last line last valid pixel geodetic longitude"));

            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PASS, getPass(mapProjRec, sceneRec));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spacing,
                mapProjRec.getAttributeDouble("Nominal inter-pixel distance in output scene"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_spacing,
                mapProjRec.getAttributeDouble("Nominal inter-line distance in output scene"));

            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.srgr_flag, isGroundRange(mapProjRec));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.map_projection, getMapProjection(mapProjRec));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.geo_ref_system,
                mapProjRec.getAttributeString("Name of reference ellipsoid"));
        } else if(sceneRec != null) {
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spacing,
                sceneRec.getAttributeDouble("Pixel spacing"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_spacing,
                sceneRec.getAttributeDouble("Line spacing"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PASS, getPass(mapProjRec, sceneRec));
        }

        //sph
        String psID = "VMP";
        if(sceneRec != null) {
            psID = sceneRec.getAttributeString("Processing system identifier").trim();
            if (psID.contains("PGS")) {
                AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ProcessingSystemIdentifier, "PGS");
            } else { // VMP
                AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ProcessingSystemIdentifier, "VMP");
            }

            final int absOrbit = Integer.parseInt(sceneRec.getAttributeString("Orbit number").trim());
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.CYCLE, getCycle(absOrbit));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.REL_ORBIT, getRelOrbit(absOrbit));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ABS_ORBIT, absOrbit);

            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.mds1_tx_rx_polar,
                    ReaderUtils.findPolarizationInBandName(
                            sceneRec.getAttributeString("Sensor ID and mode of operation for this channel")));

            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.algorithm,
                    sceneRec.getAttributeString("Processing algorithm identifier"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_looks,
                    sceneRec.getAttributeDouble("Nominal number of looks processed in azimuth"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_looks,
                    sceneRec.getAttributeDouble("Nominal number of looks processed in range"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.pulse_repetition_frequency,
                    sceneRec.getAttributeDouble("Pulse Repetition Frequency"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.radar_frequency,
                    sceneRec.getAttributeDouble("Radar frequency") * 1000.0);
            final double slantRangeTime = sceneRec.getAttributeDouble("Zero-doppler range time of first range pixel")*0.001; //s
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.slant_range_to_first_pixel,
                    slantRangeTime*Constants.lightSpeed*0.5);

            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_sampling_rate,
                sceneRec.getAttributeDouble("Range sampling rate"));
        }

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.SAMPLE_TYPE, getSampleType());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.line_time_interval,
                ReaderUtils.getLineTimeInterval(startTime, endTime, _sceneHeight));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_output_lines,
                product.getSceneRasterHeight());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_samples_per_line,
                product.getSceneRasterWidth());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.TOT_SIZE, ReaderUtils.getTotalSize(product));

        if(facilityRec != null) {
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.STATE_VECTOR_TIME, AbstractMetadata.parseUTC(
                facilityRec.getAttributeString("Time of input state vector used to processed the image")));
            
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ant_elev_corr_flag,
                    facilityRec.getAttributeInt("Antenna pattern correction flag"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spread_comp_flag,
                    facilityRec.getAttributeInt("Range spreading loss compensation flag"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.replica_power_corr_flag, 0);
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.abs_calibration_flag, 0);
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.calibration_factor,
                    facilityRec.getAttributeDouble("Absolute calibration constant K"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.coregistered_stack, 0);
        }

        addOrbitStateVectors(absRoot, _leaderFile.getPlatformPositionRecord());
        addSRGRCoefficients(absRoot, facilityRec);

        // convert srgr coefficients that are used to compute slant range time to new coefficients that are used to compute slant range
        if (!psID.contains("PGS")) { // VMP

            final double Fr = sceneRec.getAttributeDouble("Range sampling rate")*1000000; // MHz to Hz
            final double T0 = sceneRec.getAttributeDouble("Zero-doppler range time of first range pixel")/1000; // ms to s

            final MetadataElement srgrCoefficientsElem = absRoot.getElement(AbstractMetadata.srgr_coefficients);
            final MetadataElement srgrListElem = srgrCoefficientsElem.getElement(AbstractMetadata.srgr_coef_list);

            MetadataElement coefElem = srgrListElem.getElementAt(0);
            double c0 = coefElem.getAttributeDouble(AbstractMetadata.srgr_coef);
            c0 = (c0/Fr + T0)* Constants.halfLightSpeed;
            AbstractMetadata.setAttribute(coefElem, AbstractMetadata.srgr_coef, c0);

            coefElem = srgrListElem.getElementAt(1);
            double c1 = coefElem.getAttributeDouble(AbstractMetadata.srgr_coef);
            c1 = c1/Fr*Constants.halfLightSpeed;
            AbstractMetadata.setAttribute(coefElem, AbstractMetadata.srgr_coef, c1);

            coefElem = srgrListElem.getElementAt(2);
            double c2 = coefElem.getAttributeDouble(AbstractMetadata.srgr_coef);
            c2 = c2/Fr*Constants.halfLightSpeed;
            AbstractMetadata.setAttribute(coefElem, AbstractMetadata.srgr_coef, c2);

            coefElem = srgrListElem.getElementAt(3);
            double c3 = coefElem.getAttributeDouble(AbstractMetadata.srgr_coef);
            c3 = c3/Fr*Constants.halfLightSpeed;
            AbstractMetadata.setAttribute(coefElem, AbstractMetadata.srgr_coef, c3);
        }

        addDopplerCentroidCoefficients(absRoot, sceneRec);
    }

    private String getMapProjection(BaseRecord mapProjRec) {
        if(productType.contains("IMG") || productType.contains("GEC")) {
            return mapProjRec.getAttributeString("Map projection descriptor");
        }
        return " ";
    }

    private int getCycle(final int absOrbit) {
        if(isERS1()) {
            if(absOrbit < 12754) {              // phase C
                final int orbitsPerCycle = 501;
                return (absOrbit + 37930)/orbitsPerCycle;
            } else if(absOrbit < 14302) {       // phase D
                final int orbitsPerCycle = 43;
                return (absOrbit - 8342)/orbitsPerCycle;
            } else if(absOrbit < 16747) {       // phase E
                final int orbitsPerCycle = 2411;
                return ((absOrbit-12511)/orbitsPerCycle) + 139;
            } else if(absOrbit < 19248) {       // phase F
                final int orbitsPerCycle = 2411;
                return ((absOrbit - 14391)/orbitsPerCycle) + 141;
            } else {                            // phase G
                final int orbitsPerCycle = 501;
                return ((absOrbit - 19027)/orbitsPerCycle) + 144;
            }
        } else {
            final int orbitsPerCycle = 501;
            return (absOrbit + 145)/orbitsPerCycle;
        }
    }

    private int getRelOrbit(final int absOrbit) {
        if(isERS1()) {
            if(absOrbit < 12754) {               // phase C
                final int orbitsPerCycle = 501;
                return absOrbit + 37931 - getCycle(absOrbit) * orbitsPerCycle;
            } else if(absOrbit < 14302) {        // phase D
                final int orbitsPerCycle = 43;
                return absOrbit - 8341 - getCycle(absOrbit) * orbitsPerCycle;
            } else if(absOrbit < 16747) {        // phase E
                final int orbitsPerCycle = 2411;
                return absOrbit - 12510 -(getCycle(absOrbit)-139) * orbitsPerCycle;
            } else if(absOrbit < 19248) {        // phase F
                final int orbitsPerCycle = 2411;
                return absOrbit - 14390 -(getCycle(absOrbit)-141) * orbitsPerCycle;
            } else {                             // phase G
                final int orbitsPerCycle = 501;
                return absOrbit - 19026 - (getCycle(absOrbit)-144)*orbitsPerCycle;
            }
        } else {
            final int orbitsPerCycle = 501;
            return absOrbit + 146 - getCycle(absOrbit)*orbitsPerCycle; 
        }
    }

    private String getProductName() {
        return _volumeDirectoryFile.getProductName();
    }

    private String getProductDescription() {
        return ERSConstants.PRODUCT_DESCRIPTION_PREFIX + _leaderFile.getProductLevel();
    }
}
