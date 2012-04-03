/*
 * Copyright (C) 2011 by Array Systems Computing Inc. http://www.array.ca
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
package org.esa.nest.dataio.ceos.alos;

import Jama.Matrix;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.util.Debug;
import org.esa.beam.util.Guardian;
import org.esa.beam.util.math.MathUtils;
import org.esa.nest.dataio.binary.BinaryRecord;
import org.esa.nest.dataio.binary.IllegalBinaryFormatException;
import org.esa.nest.gpf.ReaderUtils;
import org.esa.nest.dataio.ceos.CEOSImageFile;
import org.esa.nest.dataio.ceos.CEOSProductDirectory;
import org.esa.nest.dataio.ceos.CeosHelper;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Orbits;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.gpf.OperatorUtils;
import org.esa.nest.util.Constants;
import org.esa.nest.util.GeoUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.text.DateFormat;

/**
 * This class represents a product directory.
 * <p/>
 * <p>This class is public for the benefit of the implementation of another (internal) class and its API may
 * change in future releases of the software.</p>
 *
 */
class AlosPalsarProductDirectory extends CEOSProductDirectory {

    private AlosPalsarImageFile[] _imageFiles = null;
    private AlosPalsarLeaderFile _leaderFile = null;
    private AlosPalsarTrailerFile _trailerFile = null;

    private int _sceneWidth = 0;
    private int _sceneHeight = 0;

    private final transient Map<String, AlosPalsarImageFile> bandImageFileMap = new HashMap<String, AlosPalsarImageFile>(1);
    public static final DateFormat dateFormat1 = ProductData.UTC.createDateFormat("yyyyMMddHHmmssSSS");
    public static final DateFormat dateFormat2 = ProductData.UTC.createDateFormat("yyyyMMdd HH:mm:ss");

    public AlosPalsarProductDirectory(final File dir) {
        Guardian.assertNotNull("dir", dir);

        constants = new AlosPalsarConstants();
        _baseDir = dir;
    }

    @Override
    protected void readProductDirectory() throws IOException, IllegalBinaryFormatException {
        readVolumeDirectoryFile();

        updateProductType();

        _leaderFile = new AlosPalsarLeaderFile(
                createInputStream(CeosHelper.getCEOSFile(_baseDir, constants.getLeaderFilePrefix())));
        final File trlFile = CeosHelper.getCEOSFile(_baseDir, constants.getTrailerFilePrefix());
        if(trlFile != null) {
            _trailerFile = new AlosPalsarTrailerFile(createInputStream(trlFile));
        }

        final String[] imageFileNames = CEOSImageFile.getImageFileNames(_baseDir, constants.getImageFilePrefix());
        final List<AlosPalsarImageFile> imgArray = new ArrayList<AlosPalsarImageFile>(imageFileNames.length);
        for (String fileName : imageFileNames) {
            try {
                final AlosPalsarImageFile imgFile = new AlosPalsarImageFile(createInputStream(new File(_baseDir, fileName)),
                                                                            getProductLevel(), fileName);
                imgArray.add(imgFile);
            } catch (Exception e) {
                e.printStackTrace();
                // continue
            }
        }
        _imageFiles = imgArray.toArray(new AlosPalsarImageFile[imgArray.size()]);

        _sceneWidth = _imageFiles[0].getRasterWidth();
        _sceneHeight = _imageFiles[0].getRasterHeight();
        assertSameWidthAndHeightForAllImages(_imageFiles, _sceneWidth, _sceneHeight);

        if(_leaderFile.getProductLevel() == AlosPalsarConstants.LEVEL1_0 ||
           _leaderFile.getProductLevel() == AlosPalsarConstants.LEVEL1_1) {
            isProductSLC = true;
        }
    }

    private void updateProductType() {
        String prodType = productType.toUpperCase();
        while(prodType.endsWith("A") || prodType.endsWith("D") || prodType.endsWith("U") || prodType.endsWith("_")) {
            prodType = prodType.substring(0, prodType.length()-1);
        }
        productType = prodType;
    }

    public boolean isALOS() throws IOException {
        if(productType == null || _volumeDirectoryFile == null)
            readVolumeDirectoryFile();
        final String volID = _volumeDirectoryFile.getVolumeDescriptorRecord().getAttributeString("Volume set ID");
        return volID.toUpperCase().contains("ALOS");
    }

    private static String getMission() {
        return "ALOS";
    }

    public int getProductLevel() {
        return _leaderFile.getProductLevel();
    }

    @Override
    public Product createProduct() throws IOException {
        final Product product = new Product(getProductName(),
                                            productType, _sceneWidth, _sceneHeight);

        for (final AlosPalsarImageFile imageFile : _imageFiles) {
            final String pol = imageFile.getPolarization();

            if(isProductSLC) {
                final Band bandI = createBand(product, "i_" + pol, Unit.REAL, imageFile);
                final Band bandQ = createBand(product, "q_" + pol, Unit.IMAGINARY, imageFile);
                ReaderUtils.createVirtualIntensityBand(product, bandI, bandQ, "_"+pol);
                ReaderUtils.createVirtualPhaseBand(product, bandI, bandQ, "_"+pol);
            } else {                
                final Band band = createBand(product, "Amplitude_" + pol, Unit.AMPLITUDE, imageFile);
                ReaderUtils.createVirtualIntensityBand(product, band, "_"+pol);
            }
        }

        product.setStartTime(getUTCScanStartTime(_leaderFile.getSceneRecord(), null));
        product.setEndTime(getUTCScanStopTime(_leaderFile.getSceneRecord(), null));
        product.setDescription(getProductDescription());

        ReaderUtils.addGeoCoding(product, _leaderFile.getLatCorners(), _leaderFile.getLonCorners());
        addTiePointGrids(product);
        addMetaData(product);
        
        if(product.getGeoCoding() == null) {
            addGeoCodingFromWorkReport(product);
        }

        if (product.getGeoCoding() == null) {
            addTPGGeoCoding(product, _leaderFile.getSceneRecord());
        }
         
        return product;
    }

    private void addTiePointGrids(final Product product) {

        final int gridWidth = 11;
        final int gridHeight = 11;
        final int sceneWidth = product.getSceneRasterWidth();
        final int sceneHeight = product.getSceneRasterHeight();
        final int subSamplingX = sceneWidth / (gridWidth - 1);
        final int subSamplingY = sceneHeight / (gridHeight - 1);
        final float[] rangeDist = new float[gridWidth*gridHeight];
        final float[] rangeTime = new float[gridWidth*gridHeight];

        final BinaryRecord sceneRec = _leaderFile.getSceneRecord();

        // slant range time (2-way)
        if(_leaderFile.getProductLevel() == AlosPalsarConstants.LEVEL1_1) {

            final double samplingRate = sceneRec.getAttributeDouble("Range sampling rate") * 1000000.0;  // MHz to Hz

            final double tmp = subSamplingX * Constants.halfLightSpeed / samplingRate;
            int k = 0;
            for(int j = 0; j < gridHeight; j++) {
                final int slantRangeToFirstPixel = _imageFiles[0].getSlantRangeToFirstPixel(j*subSamplingY);
                for (int i = 0; i < gridWidth; i++) {
                    rangeDist[k++] = (float)(slantRangeToFirstPixel + i*tmp);
                }
            }

        } else if(_leaderFile.getProductLevel() == AlosPalsarConstants.LEVEL1_5) {

            int k = 0;
            for (int j = 0; j < gridHeight; j++) {
                final int y = Math.min(j*subSamplingY, sceneHeight-1);
                final int slantRangeToFirstPixel = _imageFiles[0].getSlantRangeToFirstPixel(y); // meters
                final int slantRangeToMidPixel = _imageFiles[0].getSlantRangeToMidPixel(y);
                final int slantRangeToLastPixel = _imageFiles[0].getSlantRangeToLastPixel(y);
                final double[] polyCoef = computePolynomialCoefficients(slantRangeToFirstPixel,
                                                                        slantRangeToMidPixel,
                                                                        slantRangeToLastPixel,
                                                                        sceneWidth);

                for(int i = 0; i < gridWidth; i++) {
                    final int x = i*subSamplingX;
                    rangeDist[k++] = (float)(polyCoef[0] + polyCoef[1]*x + polyCoef[2]*x*x);
                }
            }
        }

        // get slant range time in nanoseconds from range distance in meters
        for(int k = 0; k < rangeDist.length; k++) {
             rangeTime[k] = (float)(rangeDist[k] / Constants.halfLightSpeed * 1000000000.0); // in ns
        }

        final TiePointGrid slantRangeGrid = new TiePointGrid(OperatorUtils.TPG_SLANT_RANGE_TIME,
                gridWidth, gridHeight, 0, 0, subSamplingX, subSamplingY, rangeTime);
        slantRangeGrid.setUnit(Unit.NANOSECONDS);
        product.addTiePointGrid(slantRangeGrid);

        // incidence angle
        final double a0 = sceneRec.getAttributeDouble("Incidence angle constant term");
        final double a1 = sceneRec.getAttributeDouble("Incidence angle linear term");
        final double a2 = sceneRec.getAttributeDouble("Incidence angle quadratic term");
        final double a3 = sceneRec.getAttributeDouble("Incidence angle cubic term");
        final double a4 = sceneRec.getAttributeDouble("Incidence angle fourth term");
        final double a5 = sceneRec.getAttributeDouble("Incidence angle fifth term");

        final float[] angles = new float[gridWidth*gridHeight];
        int k = 0;
        for(int j = 0; j < gridHeight; j++) {
            for (int i = 0; i < gridWidth; i++) {
                angles[k] = (float)((a0 + a1*rangeDist[k]/1000.0 +
                                     a2*Math.pow(rangeDist[k]/1000.0, 2.0) +
                                     a3*Math.pow(rangeDist[k]/1000.0, 3.0) +
                                     a4*Math.pow(rangeDist[k]/1000.0, 4.0) +
                                     a5*Math.pow(rangeDist[k]/1000.0, 5.0) ) * MathUtils.RTOD);
                k++;
            }
        }

        final TiePointGrid incidentAngleGrid = new TiePointGrid(OperatorUtils.TPG_INCIDENT_ANGLE,
                gridWidth, gridHeight, 0, 0, subSamplingX, subSamplingY, angles);

        incidentAngleGrid.setUnit(Unit.DEGREES);
        product.addTiePointGrid(incidentAngleGrid);
    }

    private static double[] computePolynomialCoefficients(
                    int slantRangeToFirstPixel, int slantRangeToMidPixel, int slantRangeToLastPixel, int imageWidth) {

        final int firstPixel = 0;
        final int midPixel = imageWidth/2;
        final int lastPixel = imageWidth - 1;
        final double[] idxArray = {firstPixel, midPixel, lastPixel};
        final double[] rangeArray = {slantRangeToFirstPixel, slantRangeToMidPixel, slantRangeToLastPixel};
        final Matrix A = org.esa.nest.util.MathUtils.createVandermondeMatrix(idxArray, 2);
        final Matrix b = new Matrix(rangeArray, 3);
        final Matrix x = A.solve(b);
        return x.getColumnPackedCopy();
    }

    private static void addGeoCodingFromWorkReport(Product product) {

        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(product);
        final MetadataElement workReportElem = product.getMetadataRoot().getElement("Work Report");
        if(workReportElem != null) {

            try {
                float latUL = Float.parseFloat(workReportElem.getAttributeString("Brs_ImageSceneLeftTopLatitude", "0"));
                float latUR = Float.parseFloat(workReportElem.getAttributeString("Brs_ImageSceneRightTopLatitude", "0"));
                float latLL = Float.parseFloat(workReportElem.getAttributeString("Brs_ImageSceneLeftBottomLatitude", "0"));
                float latLR = Float.parseFloat(workReportElem.getAttributeString("Brs_ImageSceneRightBottomLatitude", "0"));

                float lonUL = Float.parseFloat(workReportElem.getAttributeString("Brs_ImageSceneLeftTopLongitude", "0"));
                float lonUR = Float.parseFloat(workReportElem.getAttributeString("Brs_ImageSceneRightTopLongitude", "0"));
                float lonLL = Float.parseFloat(workReportElem.getAttributeString("Brs_ImageSceneLeftBottomLongitude", "0"));
                float lonLR = Float.parseFloat(workReportElem.getAttributeString("Brs_ImageSceneRightBottomLongitude", "0"));

                // The corner point geo positions above are given with respect to the scene, not the SAR image.
                // Therefore, they should first be mapped to the corner points of the SAR image before being used
                // in generating the geocoding of the SAR image. Here we assume that the SAR image is always
                // displayed with the first azimuth line on the top and the near range side on the left.
                // For level 1.5 detective product, this is not a problem because it is projected product.
                String pass = absRoot.getAttributeString("PASS");
                String prodType = absRoot.getAttributeString("PRODUCT_TYPE");
                if (prodType.contains("1.1")) {
                    float temp;
                    if (pass.equals("ASCENDING")) {
                        temp = latUL; latUL = latLL; latLL = temp;
                        temp = latUR; latUR = latLR; latLR = temp;
                        temp = lonUL; lonUL = lonLL; lonLL = temp;
                        temp = lonUR; lonUR = lonLR; lonLR = temp;
                    } else { // DESCENDING
                        temp = latUL; latUL = latUR; latUR = temp;
                        temp = latLL; latLL = latLR; latLR = temp;
                        temp = lonUL; lonUL = lonUR; lonUR = temp;
                        temp = lonLL; lonLL = lonLR; lonLR = temp;
                    }
                }

                final float[] latCorners = new float[]{latUL, latUR, latLL, latLR};
                final float[] lonCorners = new float[]{lonUL, lonUR, lonLL, lonLR};

                absRoot.setAttributeDouble(AbstractMetadata.first_near_lat, latUL);
                absRoot.setAttributeDouble(AbstractMetadata.first_near_long, lonUL);
                absRoot.setAttributeDouble(AbstractMetadata.first_far_lat, latUR);
                absRoot.setAttributeDouble(AbstractMetadata.first_far_long, lonUR);
                absRoot.setAttributeDouble(AbstractMetadata.last_near_lat, latLL);
                absRoot.setAttributeDouble(AbstractMetadata.last_near_long, lonLL);
                absRoot.setAttributeDouble(AbstractMetadata.last_far_lat, latLR);
                absRoot.setAttributeDouble(AbstractMetadata.last_far_long, lonLR);

                ReaderUtils.addGeoCoding(product, latCorners, lonCorners);
            } catch(Exception e) {
                Debug.trace(e.toString());       
            }
        }
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

    private Band createBand(final Product product, final String name, final String unit, final AlosPalsarImageFile imageFile) {
        int dataType = ProductData.TYPE_UINT16;
        if(_leaderFile.getProductLevel() == AlosPalsarConstants.LEVEL1_1)
            dataType = ProductData.TYPE_FLOAT32;
        else if(_leaderFile.getProductLevel() == AlosPalsarConstants.LEVEL1_0)
            dataType = ProductData.TYPE_UINT8;

        final Band band = new Band(name, dataType, _sceneWidth, _sceneHeight);
        band.setDescription(name);
        band.setUnit(unit);
        product.addBand(band);
        bandImageFileMap.put(name, imageFile);

        return band;
    }

    private void addMetaData(final Product product) throws IOException {
        final MetadataElement root = product.getMetadataRoot();

        if(_leaderFile != null) {
            final MetadataElement leadMetadata = new MetadataElement("Leader");
            _leaderFile.addMetadata(leadMetadata);
            root.addElement(leadMetadata);
        }

        if(_trailerFile != null) {
            final MetadataElement trailMetadata = new MetadataElement("Trailer");
            _trailerFile.addMetadata(trailMetadata);
            root.addElement(trailMetadata);
        }

        final MetadataElement volMetadata = new MetadataElement("Volume");
        _volumeDirectoryFile.assignMetadataTo(volMetadata);
        root.addElement(volMetadata);

        int c = 1;
        for (final AlosPalsarImageFile imageFile : _imageFiles) {
            imageFile.assignMetadataTo(root, c++);
        }

        addSummaryMetadata(new File(_baseDir, AlosPalsarConstants.SUMMARY_FILE_NAME), "Summary Information", root);
        addSummaryMetadata(new File(_baseDir, AlosPalsarConstants.WORKREPORT_FILE_NAME), "Work Report", root);
        
        addAbstractedMetadataHeader(product, root);
    }

    private void addAbstractedMetadataHeader(Product product, MetadataElement root) {

        final MetadataElement absRoot = AbstractMetadata.addAbstractedMetadataHeader(root);

        final BinaryRecord sceneRec = _leaderFile.getSceneRecord();
        final BinaryRecord mapProjRec = _leaderFile.getMapProjRecord();
        final BinaryRecord radiometricRec = _leaderFile.getRadiometricRecord();

        //mph
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT, getProductName());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT_TYPE, getProductType());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.SPH_DESCRIPTOR,
                sceneRec.getAttributeString("Product type descriptor"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.MISSION, getMission());

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PROC_TIME,
                getProcTime(_volumeDirectoryFile.getVolumeDescriptorRecord()));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ProcessingSystemIdentifier,
                sceneRec.getAttributeString("Processing system identifier").trim() );
        // cycle n/a?

        //AbstractMetadata.setAttribute(absRoot, AbstractMetadata.REL_ORBIT,
        //        Integer.parseInt(sceneRec.getAttributeString("Orbit number").trim()));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ABS_ORBIT,
                Integer.parseInt(sceneRec.getAttributeString("Orbit number").trim()));

        final ProductData.UTC startTime = getStartTime(sceneRec, root, "StartDateTime", "Brs_SceneStartDateTime");
        product.setStartTime(startTime);
        final ProductData.UTC endTime = getEndTime(sceneRec, root, "EndDateTime", "Brs_SceneCenterDateTime", startTime);
        product.setEndTime(endTime);

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
        } else if(sceneRec != null) {
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spacing,
                sceneRec.getAttributeDouble("Pixel spacing"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_spacing,
                sceneRec.getAttributeDouble("Line spacing"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PASS, getPass(mapProjRec, sceneRec));
        }

        //sph
        AbstractMetadata.setAttribute(absRoot, "SAMPLE_TYPE", getSampleType());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.algorithm,
                sceneRec.getAttributeString("Processing algorithm identifier"));

        for(int i=0; i < _imageFiles.length; ++i) {
            if(_imageFiles[i] != null) {
                AbstractMetadata.setAttribute(absRoot, AbstractMetadata.polarTags[i], _imageFiles[i].getPolarization());
            }
        }

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_looks,
                sceneRec.getAttributeDouble("Nominal number of looks processed in azimuth"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_looks,
                sceneRec.getAttributeDouble("Nominal number of looks processed in range"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.pulse_repetition_frequency,
                sceneRec.getAttributeDouble("Pulse Repetition Frequency"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.radar_frequency, getRadarFrequency(sceneRec));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.slant_range_to_first_pixel,
                _imageFiles[0].getSlantRangeToFirstPixel(0));

        // add Range and Azimuth bandwidth
        final double rangeBW = sceneRec.getAttributeDouble("Total processor bandwidth in range"); // MHz
        final double azimuthBW = sceneRec.getAttributeDouble("Total processor bandwidth in azimuth"); // Hz

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_bandwidth, rangeBW);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_bandwidth, azimuthBW);

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.line_time_interval,
                ReaderUtils.getLineTimeInterval(startTime, endTime, _sceneHeight));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_output_lines,
                product.getSceneRasterHeight());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_samples_per_line,
                product.getSceneRasterWidth());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.TOT_SIZE, ReaderUtils.getTotalSize(product));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.srgr_flag, isGroundRange());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.map_projection, getMapProjection());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.geo_ref_system,
                sceneRec.getAttributeString("Ellipsoid designator"));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ant_elev_corr_flag, 1);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spread_comp_flag, 1);        
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.replica_power_corr_flag, 0);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.abs_calibration_flag, 0);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.coregistered_stack, 0);
        if(radiometricRec != null) {
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.calibration_factor,
                radiometricRec.getAttributeDouble("Calibration factor"));
            absRoot.getAttribute(AbstractMetadata.calibration_factor).setUnit("dB");
        }                                                                                   
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_sampling_rate,
                sceneRec.getAttributeDouble("Range sampling rate"));

        addOrbitStateVectors(absRoot, _leaderFile.getPlatformPositionRecord());
    }

    private static double getRadarFrequency(BinaryRecord sceneRec) {
        final double wavelength = sceneRec.getAttributeDouble("Radar wavelength");
        return (Constants.lightSpeed / wavelength) / Constants.oneMillion; // MHz
    }

    private int isGroundRange() {
        if(_leaderFile.getMapProjRecord() == null) return isProductSLC ? 0 : 1;
        final String projDesc = _leaderFile.getMapProjRecord().getAttributeString("Map projection descriptor").toLowerCase();
        if(projDesc.contains("slant"))
            return 0;
        return 1;
    }

    private String getMapProjection() {
        if(_leaderFile.getMapProjRecord() == null) return " ";
        //final String projDesc = _leaderFile.getMapProjRecord().getAttributeString("Map projection descriptor").toLowerCase();
        //if(projDesc.contains("geo"))
        if(getProductType().contains("1.5G"))
            return "Geocoded";
        return " ";
    }

    private static ProductData.UTC getStartTime(BinaryRecord sceneRec, MetadataElement root,
                                                   String tagInSummary, String tagInWorkReport) {
        ProductData.UTC time = getUTCScanStartTime(sceneRec, null);
        if(time.equalElems(new ProductData.UTC(0))) {
            try {
                final MetadataElement summaryElem = root.getElement("Summary Information");
                if(summaryElem != null) {
                    for(MetadataAttribute sum : summaryElem.getAttributes()) {
                        if(sum.getName().contains(tagInSummary)) {
                            return AbstractMetadata.parseUTC(summaryElem.getAttributeString(sum.getName().trim()),
                                    dateFormat2);
                        }
                    }
                }

                final MetadataElement workReportElem = root.getElement("Work Report");
                if(workReportElem != null) {
                    for(MetadataAttribute workRep : workReportElem.getAttributes()) {
                        if(workRep.getName().contains(tagInWorkReport)) {
                            return AbstractMetadata.parseUTC(workReportElem.getAttributeString(workRep.getName().trim()),
                                    dateFormat2);
                        }
                    }
                }

                final MetadataElement imageDescriptorElem = root.getElement("Image Descriptor 1");
                if (imageDescriptorElem != null) {
                    final MetadataElement imageRecordElem = imageDescriptorElem.getElement("Image Record");
                    if (imageRecordElem != null) {
                        final int year = imageRecordElem.getAttributeInt("Sensor acquisition year", 0);
                        final int days = imageRecordElem.getAttributeInt("Sensor acquisition day of year", 0);
                        final int milliseconds = imageRecordElem.getAttributeInt("Sensor acquisition milliseconds of day", 0);
                        final int days_since_2000 = (year - 2000)*365 + days + 1;
                        final int seconds = milliseconds / 1000;
                        final int microseconds = (milliseconds - seconds*1000) * 1000;
                        return new ProductData.UTC(days_since_2000, seconds, microseconds);
                    }
                }

                final String centreTimeStr = sceneRec.getAttributeString("Scene centre time");
                return AbstractMetadata.parseUTC(centreTimeStr.trim(), dateFormat1);
            } catch(Exception e) {
                time = new ProductData.UTC(0);
            }
        }
        return time;
    }

    private static ProductData.UTC getEndTime(BinaryRecord sceneRec, MetadataElement root,
                                                   String tagInSummary, String tagInWorkReport,
                                                   ProductData.UTC startTime) {
        ProductData.UTC time = getUTCScanStartTime(sceneRec, null);
        if(time.equalElems(new ProductData.UTC(0))) {
            try {
                final MetadataElement summaryElem = root.getElement("Summary Information");
                if(summaryElem != null) {
                    for(MetadataAttribute sum : summaryElem.getAttributes()) {
                        if(sum.getName().contains(tagInSummary)) {
                            return AbstractMetadata.parseUTC(summaryElem.getAttributeString(sum.getName().trim()),
                                    dateFormat2);
                        }
                    }
                }

                final MetadataElement workReportElem = root.getElement("Work Report");
                if(workReportElem != null) {
                    for(MetadataAttribute workRep : workReportElem.getAttributes()) {
                        if(workRep.getName().contains(tagInWorkReport)) {
                            final ProductData.UTC centreTime = AbstractMetadata.parseUTC(
                                    workReportElem.getAttributeString(workRep.getName().trim()), dateFormat2);
                            final double diff = centreTime.getMJD() - startTime.getMJD();
                            return new ProductData.UTC(startTime.getMJD() + (diff *2.0));
                        }
                    }
                }

                final MetadataElement imageDescriptorElem = root.getElement("Image Descriptor 1");
                if (imageDescriptorElem != null) {
                    final int numRecords = imageDescriptorElem.getAttributeInt("Number of SAR DATA records", 0);
                    final MetadataElement imageRecordElem = imageDescriptorElem.getElement("Image Record");
                    if (imageRecordElem != null) {
                        final int year = imageRecordElem.getAttributeInt("Sensor acquisition year", 0);
                        final int days = imageRecordElem.getAttributeInt("Sensor acquisition day of year", 0);
                        int milliseconds = imageRecordElem.getAttributeInt("Sensor acquisition milliseconds of day", 0);
                        final int prf = imageRecordElem.getAttributeInt("PRF", 0); // in mHz
                        final int days_since_2000 = (year - 2000)*365 + days + 1;
                        milliseconds += (int)((double)(numRecords - 1) * 1000000.0 / (double)prf);
                        final int seconds = milliseconds / 1000;
                        final int microseconds = (milliseconds - seconds*1000) * 1000;
                        return new ProductData.UTC(days_since_2000, seconds, microseconds);
                    }
                }
                
                final String centreTimeStr = sceneRec.getAttributeString("Scene centre time");
                final ProductData.UTC centreTime =  AbstractMetadata.parseUTC(centreTimeStr.trim(), dateFormat1);
                final double diff = centreTime.getMJD() - startTime.getMJD();
                return new ProductData.UTC(startTime.getMJD() + (diff *2.0));
            } catch(Exception e) {
                time = new ProductData.UTC(0);
            }
        }
        return time;
    }

    private String getProductName() {
        return getMission() + '-' + _volumeDirectoryFile.getProductName();
    }

    private String getProductDescription() {
        return AlosPalsarConstants.PRODUCT_DESCRIPTION_PREFIX + _leaderFile.getProductLevel();
    }

    /**
     * Update target product GEOCoding. A new tie point grid is generated.
     * @param product The product.
     * @param sceneRec The scene record.
     * @throws IOException The exceptions.
     */
    private static void addTPGGeoCoding(final Product product, final BinaryRecord sceneRec) throws IOException {

        final int gridWidth = 11;
        final int gridHeight = 11;
        final float[] targetLatTiePoints = new float[gridWidth*gridHeight];
        final float[] targetLonTiePoints = new float[gridWidth*gridHeight];
        final int sourceImageWidth = product.getSceneRasterWidth();
        final int sourceImageHeight = product.getSceneRasterHeight();

        final float subSamplingX = sourceImageWidth / (float)(gridWidth - 1);
        final float subSamplingY = sourceImageHeight / (float)(gridHeight - 1);

        final TiePointGrid slantRangeTime = product.getTiePointGrid(OperatorUtils.TPG_SLANT_RANGE_TIME);
        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(product);

        final double firstLineUTC = absRoot.getAttributeUTC(AbstractMetadata.first_line_time).getMJD();
        final double lastLineUTC = absRoot.getAttributeUTC(AbstractMetadata.last_line_time).getMJD();
        final double lineTimeInterval = absRoot.getAttributeDouble(AbstractMetadata.line_time_interval) / 86400.0; // s to day
        final double latMid = sceneRec.getAttributeDouble("scene centre geodetic latitude");
        final double lonMid = sceneRec.getAttributeDouble("scene centre geodetic longitude");

        AbstractMetadata.OrbitStateVector[] orbitStateVectors;
        try {
            orbitStateVectors = AbstractMetadata.getOrbitStateVectors(absRoot);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }

        if(!checkStateVectorValidity(orbitStateVectors))
            return;

        final int numVectors = orbitStateVectors.length;
        int startIdx = 0;
        int endIdx = 0;
        final double t1 = Math.min(firstLineUTC, lastLineUTC);
        final double t2 = Math.max(firstLineUTC, lastLineUTC);
        for (int i = 0; i < numVectors; i++) {
            double time = orbitStateVectors[i].time_mjd;
            if (time < t1) {
                startIdx = i;
            }

            if (time < t2) {
                endIdx = i;
            }
        }

        while (endIdx - startIdx + 1 < Math.min(5, numVectors)) {
            startIdx = Math.max(startIdx - 1, 0);
            endIdx = Math.min(endIdx + 1, numVectors - 1);
        }
        final int numVectorsUsed = endIdx - startIdx + 1;

        final double[] timeArray = new double[numVectorsUsed];
        final double[] xPosArray = new double[numVectorsUsed];
        final double[] yPosArray = new double[numVectorsUsed];
        final double[] zPosArray = new double[numVectorsUsed];
        final double[] xVelArray = new double[numVectorsUsed];
        final double[] yVelArray = new double[numVectorsUsed];
        final double[] zVelArray = new double[numVectorsUsed];

        for (int i = startIdx; i <= endIdx; i++) {
            timeArray[i - startIdx] = orbitStateVectors[i].time_mjd;
            xPosArray[i - startIdx] = orbitStateVectors[i].x_pos; // m
            yPosArray[i - startIdx] = orbitStateVectors[i].y_pos; // m
            zPosArray[i - startIdx] = orbitStateVectors[i].z_pos; // m
            xVelArray[i - startIdx] = orbitStateVectors[i].x_vel; // m/s
            yVelArray[i - startIdx] = orbitStateVectors[i].y_vel; // m/s
            zVelArray[i - startIdx] = orbitStateVectors[i].z_vel; // m/s
        }

        // Create new tie point grid
        int k = 0;
        for (int r = 0; r < gridHeight; r++) {
            // get the zero Doppler time for the rth line
            int y;
            if (r == gridHeight - 1) { // last row
                y = sourceImageHeight - 1;
            } else { // other rows
                y = (int)(r * subSamplingY);
            }
            final double curLineUTC = firstLineUTC + y*lineTimeInterval;
            //System.out.println((new ProductData.UTC(curLineUTC)).toString());

            // compute the satellite position and velocity for the zero Doppler time using cubic interpolation
            final Orbits.OrbitData data = getOrbitData(curLineUTC, timeArray, xPosArray, yPosArray, zPosArray,
                                                xVelArray, yVelArray, zVelArray);

            for (int c = 0; c < gridWidth; c++) {
                int x;
                if (c == gridWidth - 1) { // last column
                    x = sourceImageWidth - 1;
                } else { // other columns
                    x = (int)(c * subSamplingX);
                }

                final double slrgTime = slantRangeTime.getPixelFloat((float)x, (float)y) / 1000000000.0; // ns to s;
                final GeoPos geoPos = computeLatLon(latMid, lonMid, slrgTime, data);
                targetLatTiePoints[k] = geoPos.lat;
                targetLonTiePoints[k] = geoPos.lon;
                ++k;
            }
        }

        final TiePointGrid latGrid = new TiePointGrid(OperatorUtils.TPG_LATITUDE, gridWidth, gridHeight,
                0.0f, 0.0f, subSamplingX, subSamplingY, targetLatTiePoints);

        final TiePointGrid lonGrid = new TiePointGrid(OperatorUtils.TPG_LONGITUDE, gridWidth, gridHeight,
                0.0f, 0.0f, subSamplingX, subSamplingY, targetLonTiePoints, TiePointGrid.DISCONT_AT_180);

        final TiePointGeoCoding tpGeoCoding = new TiePointGeoCoding(latGrid, lonGrid, Datum.WGS_84);

        product.addTiePointGrid(latGrid);
        product.addTiePointGrid(lonGrid);
        product.setGeoCoding(tpGeoCoding);
    }

    /**
     * Compute accurate target geo position.
     * @param latMid The scene latitude.
     * @param lonMid The scene longitude.
     * @param slrgTime The slant range time of the given pixel.
     * @param data The orbit data.
     * @return The geo position of the target.
     */
    private static GeoPos computeLatLon(final double latMid, final double lonMid, double slrgTime, Orbits.OrbitData data) {

        final double[] xyz = new double[3];
        final GeoPos geoPos = new GeoPos((float)latMid, (float)lonMid);

        // compute initial (x,y,z) coordinate from lat/lon
        GeoUtils.geo2xyz(geoPos, xyz);

        // compute accurate (x,y,z) coordinate using Newton's method
        GeoUtils.computeAccurateXYZ(data, xyz, slrgTime);

        // compute (lat, lon, alt) from accurate (x,y,z) coordinate
        GeoUtils.xyz2geo(xyz, geoPos);

        return geoPos;
    }

    private static boolean checkStateVectorValidity(AbstractMetadata.OrbitStateVector[] orbitStateVectors) {

        if(orbitStateVectors == null) {
            return false;
        }
        if(orbitStateVectors.length <= 1) {
            return false;
        }

        for (int i = 1; i < orbitStateVectors.length; i++) {
            if (orbitStateVectors[i].time_mjd == orbitStateVectors[0].time_mjd) {
                return false;
            }
        }

        return true;
    }

    /**
     * Get orbit information for given time.
     * @param utc The UTC in days.
     * @param timeArray Array holding zeros Doppler times for all state vectors.
     * @param xPosArray Array holding x coordinates for sensor positions in all state vectors.
     * @param yPosArray Array holding y coordinates for sensor positions in all state vectors.
     * @param zPosArray Array holding z coordinates for sensor positions in all state vectors.
     * @param xVelArray Array holding x velocities for sensor positions in all state vectors.
     * @param yVelArray Array holding y velocities for sensor positions in all state vectors.
     * @param zVelArray Array holding z velocities for sensor positions in all state vectors.
     * @return The orbit information.
     */
    private static Orbits.OrbitData getOrbitData(final double utc, final double[] timeArray,
                                         final double[] xPosArray, final double[] yPosArray, final double[] zPosArray,
                                         final double[] xVelArray, final double[] yVelArray, final double[] zVelArray) {

        // Lagrange polynomial interpolation
        final Orbits.OrbitData orbitData = new Orbits.OrbitData();
        orbitData.xPos = org.esa.nest.util.MathUtils.lagrangeInterpolatingPolynomial(timeArray, xPosArray, utc);
        orbitData.yPos = org.esa.nest.util.MathUtils.lagrangeInterpolatingPolynomial(timeArray, yPosArray, utc);
        orbitData.zPos = org.esa.nest.util.MathUtils.lagrangeInterpolatingPolynomial(timeArray, zPosArray, utc);
        orbitData.xVel = org.esa.nest.util.MathUtils.lagrangeInterpolatingPolynomial(timeArray, xVelArray, utc);
        orbitData.yVel = org.esa.nest.util.MathUtils.lagrangeInterpolatingPolynomial(timeArray, yVelArray, utc);
        orbitData.zVel = org.esa.nest.util.MathUtils.lagrangeInterpolatingPolynomial(timeArray, zVelArray, utc);

        return orbitData;
    }
    
}