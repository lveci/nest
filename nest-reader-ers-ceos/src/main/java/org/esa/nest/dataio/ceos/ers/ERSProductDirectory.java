package org.esa.nest.dataio.ceos.ers;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.util.Guardian;
import org.esa.nest.dataio.IllegalBinaryFormatException;
import org.esa.nest.dataio.ReaderUtils;
import org.esa.nest.dataio.ceos.CEOSImageFile;
import org.esa.nest.dataio.ceos.CEOSProductDirectory;
import org.esa.nest.dataio.ceos.records.BaseRecord;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * This class represents a product directory.
 *
 */
class ERSProductDirectory extends CEOSProductDirectory {

    private final File _baseDir;
    private ERSVolumeDirectoryFile _volumeDirectoryFile = null;
    private ERSImageFile[] _imageFiles = null;
    private ERSLeaderFile _leaderFile = null;

    private int _sceneWidth = 0;
    private int _sceneHeight = 0;

    private transient Map<String, ERSImageFile> bandImageFileMap = new HashMap<String, ERSImageFile>(1);

    public ERSProductDirectory(final File dir) {
        Guardian.assertNotNull("dir", dir);

        _baseDir = dir;
    }

    @Override
    protected void readProductDirectory() throws IOException, IllegalBinaryFormatException {
        readVolumeDirectoryFile();
        _leaderFile = new ERSLeaderFile(createInputStream(new File(_baseDir, ERSVolumeDirectoryFile.getLeaderFileName())));

        final String[] imageFileNames = CEOSImageFile.getImageFileNames(_baseDir, "DAT_");
        final int numImageFiles = imageFileNames.length;
        _imageFiles = new ERSImageFile[numImageFiles];
        for (int i = 0; i < numImageFiles; i++) {
            _imageFiles[i] = new ERSImageFile(createInputStream(new File(_baseDir, imageFileNames[i])));
        }

        _sceneWidth = _imageFiles[0].getRasterWidth();
        _sceneHeight = _imageFiles[0].getRasterHeight();
        assertSameWidthAndHeightForAllImages(_imageFiles, _sceneWidth, _sceneHeight);
    }

    private void readVolumeDirectoryFile() throws IOException, IllegalBinaryFormatException {
        if(_volumeDirectoryFile == null)
            _volumeDirectoryFile = new ERSVolumeDirectoryFile(_baseDir);

        productType = _volumeDirectoryFile.getProductType();
        isProductSLC = productType.contains("SLC") || productType.contains("COMPLEX");
    }

    public boolean isERS() throws IOException, IllegalBinaryFormatException {
        if(productType == null || _volumeDirectoryFile == null)
            readVolumeDirectoryFile();
        return isERS1() || isERS2();
    }

    private boolean isERS1() {
        return productType.contains("ERS1") || productType.contains("ERS-1") || productType.contains("ERS_1");
    }

    private boolean isERS2() {
        return productType.contains("ERS2") || productType.contains("ERS-2") || productType.contains("ERS_2");
    }

    public String getMission() {
        if(isERS1())
            return "ERS1";
        else if(isERS2())
            return "ERS2";
        return "";
    }

    @Override
    public Product createProduct() throws IOException, IllegalBinaryFormatException {
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

        product.setStartTime(getUTCScanStartTime(_leaderFile.getSceneRecord()));
        product.setEndTime(getUTCScanStopTime(_leaderFile.getSceneRecord()));
        product.setDescription(getProductDescription());

        addGeoCoding(product, _leaderFile.getLatCorners(), _leaderFile.getLonCorners());
        addTiePointGrids(product);
        addMetaData(product);
        
        return product;
    }

    private void addTiePointGrids(final Product product) throws IllegalBinaryFormatException, IOException {

        // add incidence angle tie point grid
        final BaseRecord facility = _leaderFile.getFacilityRecord();

        final double angle1 = facility.getAttributeDouble("Incidence angle at first range pixel");
        final double angle2 = facility.getAttributeDouble("Incidence angle at centre range pixel");
        final double angle3 = facility.getAttributeDouble("Incidence angle at last valid range pixel");

        final int gridWidth = 11;
        final int gridHeight = 11;

        final float subSamplingX = (float)product.getSceneRasterWidth() / (float)(gridWidth - 1);
        final float subSamplingY = (float)product.getSceneRasterHeight() / (float)(gridHeight - 1);

        final float[] angles = new float[]{(float)angle1, (float)angle2, (float)angle3};
        final float[] fineAngles = new float[gridWidth*gridHeight];

        ReaderUtils.createFineTiePointGrid(3, 1, gridWidth, gridHeight, angles, fineAngles);

        final TiePointGrid incidentAngleGrid = new TiePointGrid("incident_angle", gridWidth, gridHeight, 0, 0,
                subSamplingX, subSamplingY, fineAngles);

        product.addTiePointGrid(incidentAngleGrid);

        // add slant range time tie point grid
        final BaseRecord scene = _leaderFile.getSceneRecord();

        final double time1 = scene.getAttributeDouble("Zero-doppler range time of first range pixel")*1000000; // ms to ns
        final double time2 = scene.getAttributeDouble("Zero-doppler range time of centre range pixel")*1000000; // ms to ns
        final double time3 = scene.getAttributeDouble("Zero-doppler range time of last range pixel")*1000000; // ms to ns

        final float[] times = new float[]{(float)time1, (float)time2, (float)time3};
        final float[] fineTimes = new float[gridWidth*gridHeight];

        ReaderUtils.createFineTiePointGrid(3, 1, gridWidth, gridHeight, times, fineTimes);

        final TiePointGrid slantRangeTimeGrid = new TiePointGrid("slant_range_time", gridWidth, gridHeight, 0, 0,
                subSamplingX, subSamplingY, fineTimes);

        product.addTiePointGrid(slantRangeTimeGrid);        
    }

    @Override
    public CEOSImageFile getImageFile(final Band band) throws IOException, IllegalBinaryFormatException {
        return bandImageFileMap.get(band.getName());
    }

    @Override
    public void close() throws IOException {
        for (int i = 0; i < _imageFiles.length; i++) {
            _imageFiles[i].close();
            _imageFiles[i] = null;
        }
        _imageFiles = null;
        _volumeDirectoryFile.close();
        _volumeDirectoryFile = null;
        _leaderFile.close();
        _leaderFile = null;
    }

    private Band createBand(final Product product, final String name, final String unit, final ERSImageFile imageFile) {
        final Band band = new Band(name, ProductData.TYPE_INT16,
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

        addSummaryMetadata(new File(_baseDir, ERSConstants.SUMMARY_FILE_NAME), root);
        addAbstractedMetadataHeader(product, root);
    }

    private void addAbstractedMetadataHeader(Product product, MetadataElement root) {

        AbstractMetadata.addAbstractedMetadataHeader(root);

        final MetadataElement absRoot = root.getElement(Product.ABSTRACTED_METADATA_ROOT_NAME);
        final BaseRecord sceneRec = _leaderFile.getSceneRecord();
        final BaseRecord mapProjRec = _leaderFile.getMapProjRecord();
        final BaseRecord facilityRec = _leaderFile.getFacilityRecord();

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

        final int absOrbit = Integer.parseInt(sceneRec.getAttributeString("Orbit number").trim());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.CYCLE, getCycle(absOrbit));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.REL_ORBIT, getRelOrbit(absOrbit));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ABS_ORBIT, absOrbit);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.STATE_VECTOR_TIME,
                AbstractMetadata.parseUTC(facilityRec.getAttributeString(
                        "Time of input state vector used to processed the image")));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_line_time, getUTCScanStartTime(sceneRec));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_line_time, getUTCScanStopTime(sceneRec));

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

        //sph

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PASS, getPass(mapProjRec));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.SAMPLE_TYPE, getSampleType());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.algorithm,
                sceneRec.getAttributeString("Processing algorithm identifier"));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.mds1_tx_rx_polar,
                getPolarization(sceneRec.getAttributeString("Sensor ID and mode of operation for this channel")));
        
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spacing,
                mapProjRec.getAttributeDouble("Nominal inter-pixel distance in output scene"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_spacing,
                mapProjRec.getAttributeDouble("Nominal inter-line distance in output scene"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_looks,
                sceneRec.getAttributeDouble("Nominal number of looks processed in azimuth"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_looks,
                sceneRec.getAttributeDouble("Nominal number of looks processed in range"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.pulse_repetition_frequency,
                sceneRec.getAttributeDouble("Pulse Repetition Frequency"));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.line_time_interval,
                getLineTimeInterval(sceneRec, _sceneHeight));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.data_type,
                ProductData.getTypeString(ProductData.TYPE_INT16));      
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_output_lines,
                product.getSceneRasterHeight());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_samples_per_line,
                product.getSceneRasterWidth());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.TOT_SIZE, getTotalSize(product));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.srgr_flag, isGroundRange());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.isMapProjected, isMapProjected());

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ant_elev_corr_flag,
                facilityRec.getAttributeInt("Antenna pattern correction flag"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spread_comp_flag,
                facilityRec.getAttributeInt("Range spreading loss compensation flag"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.replica_power_corr_flag, 0);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.abs_calibration_flag, 0);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.calibration_factor,
                facilityRec.getAttributeDouble("Absolute calibration constant K"));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_sampling_rate,
                sceneRec.getAttributeDouble("Range sampling rate"));

        addOrbitStateVectors(absRoot, _leaderFile.getPlatformPositionRecord());
    }

    private int isGroundRange() {
        final String projDesc = _leaderFile.getMapProjRecord().getAttributeString("Map projection descriptor").toLowerCase();
        if(projDesc.contains("slant"))
            return 0;
        return 1;
    }

    private int isMapProjected() {
        if(productType.contains("IMG") || productType.contains("GEC")) {
            return 1;
        }
        return 0;
    }

    private static void addOrbitStateVectors(final MetadataElement absRoot, final BaseRecord platformPosRec) {
        final MetadataElement orbitVectorListElem = absRoot.getElement(AbstractMetadata.orbit_state_vectors);

        addVector(AbstractMetadata.orbit_vector, orbitVectorListElem, platformPosRec, 1);
        addVector(AbstractMetadata.orbit_vector, orbitVectorListElem, platformPosRec, 2);
        addVector(AbstractMetadata.orbit_vector, orbitVectorListElem, platformPosRec, 3);
        addVector(AbstractMetadata.orbit_vector, orbitVectorListElem, platformPosRec, 4);
        addVector(AbstractMetadata.orbit_vector, orbitVectorListElem, platformPosRec, 5);
    }

    private static void addVector(String name, MetadataElement orbitVectorListElem,
                                  BaseRecord platformPosRec, int num) {
        final MetadataElement orbitVectorElem = new MetadataElement(name+num);

        orbitVectorElem.setAttributeUTC(AbstractMetadata.orbit_vector_time, getOrbitTime(platformPosRec, num));
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_x_pos,
                platformPosRec.getAttributeDouble("Position vector X "+num));
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_y_pos,
                platformPosRec.getAttributeDouble("Position vector Y "+num));
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_z_pos,
                platformPosRec.getAttributeDouble("Position vector Z "+num));
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_x_vel,
                platformPosRec.getAttributeDouble("Velocity vector X' "+num));
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_y_vel,
                platformPosRec.getAttributeDouble("Velocity vector Y' "+num));
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_z_vel,
                platformPosRec.getAttributeDouble("Velocity vector Z' "+num));

        orbitVectorListElem.addElement(orbitVectorElem);
    }

    private static ProductData.UTC getOrbitTime(BaseRecord platformPosRec, int num) {
        final int year = platformPosRec.getAttributeInt("Year of data point");
        final int month = platformPosRec.getAttributeInt("Month of data point");
        final int day = platformPosRec.getAttributeInt("Day of data point");
        final float secondsOfDay = (float)platformPosRec.getAttributeDouble("Seconds of day");
        final float hoursf = secondsOfDay / 3600f;
        final int hour = (int)hoursf;
        final float minutesf = (hoursf - hour) * 60f;
        final int minute = (int)minutesf;
        float second = (minutesf - minute) * 60f;

        final float interval = (float)platformPosRec.getAttributeDouble("Time interval between DATA points");
        second += interval * (num-1);

        return AbstractMetadata.parseUTC(String.valueOf(year)+'-'+month+'-'+day+' '+
                                  hour+':'+minute+':'+second, "yyyy-mm-dd HH:mm:ss");
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
