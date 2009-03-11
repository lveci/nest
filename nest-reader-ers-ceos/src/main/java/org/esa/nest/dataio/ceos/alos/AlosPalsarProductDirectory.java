package org.esa.nest.dataio.ceos.alos;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.util.Guardian;
import org.esa.beam.util.math.MathUtils;
import org.esa.nest.dataio.IllegalBinaryFormatException;
import org.esa.nest.dataio.ReaderUtils;
import org.esa.nest.dataio.ceos.CEOSImageFile;
import org.esa.nest.dataio.ceos.CEOSProductDirectory;
import org.esa.nest.dataio.ceos.CeosHelper;
import org.esa.nest.dataio.ceos.records.BaseRecord;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;

import javax.imageio.stream.FileImageInputStream;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * This class represents a product directory.
 * <p/>
 * <p>This class is public for the benefit of the implementation of another (internal) class and its API may
 * change in future releases of the software.</p>
 *
 */
class AlosPalsarProductDirectory extends CEOSProductDirectory {

    private static final double UTM_FALSE_EASTING = 500000.00;
    private static final double UTM_FALSE_NORTHING = 10000000.00;
    private static final int METER_PER_KILOMETER = 1000;

    private final File _baseDir;
    private AlosPalsarVolumeDirectoryFile _volumeDirectoryFile = null;
    private AlosPalsarImageFile[] _imageFiles = null;
    private AlosPalsarLeaderFile _leaderFile = null;

    private int _sceneWidth = 0;
    private int _sceneHeight = 0;

    private transient Map<String, AlosPalsarImageFile> bandImageFileMap = new HashMap<String, AlosPalsarImageFile>(1);

    public AlosPalsarProductDirectory(final File dir) {
        Guardian.assertNotNull("dir", dir);

        _baseDir = dir;
    }

    @Override
    protected void readProductDirectory() throws IOException, IllegalBinaryFormatException {
        readVolumeDirectoryFile();
        _leaderFile = new AlosPalsarLeaderFile(new FileImageInputStream(CeosHelper.getCEOSFile(_baseDir, "LED")));

        final String[] imageFileNames = CEOSImageFile.getImageFileNames(_baseDir, "IMG-");
        final int numImageFiles = imageFileNames.length;
        _imageFiles = new AlosPalsarImageFile[numImageFiles];
        for (int i = 0; i < numImageFiles; i++) {
            _imageFiles[i] = new AlosPalsarImageFile(
                    createInputStream(new File(_baseDir, imageFileNames[i])), getProductLevel(), imageFileNames[i]);
        }

        _sceneWidth = _imageFiles[0].getRasterWidth();
        _sceneHeight = _imageFiles[0].getRasterHeight();
        assertSameWidthAndHeightForAllImages(_imageFiles, _sceneWidth, _sceneHeight);

        if(_leaderFile.getProductLevel() == AlosPalsarConstants.LEVEL1_0 ||
           _leaderFile.getProductLevel() == AlosPalsarConstants.LEVEL1_1) {
            isProductSLC = true;
        }
    }

    private void readVolumeDirectoryFile() throws IOException, IllegalBinaryFormatException {
        if(_volumeDirectoryFile == null)
            _volumeDirectoryFile = new AlosPalsarVolumeDirectoryFile(_baseDir);

        productType = _volumeDirectoryFile.getProductType();
    }

    public static boolean isALOS() {
        //if(productType == null || _volumeDirectoryFile == null)
        //    readVolumeDirectoryFile();
        return true;
    }

    public static String getMission() {
        return "ALOS";
    }

    public int getProductLevel() {
        return _leaderFile.getProductLevel();
    }

    @Override
    public Product createProduct() throws IOException, IllegalBinaryFormatException {
        final Product product = new Product(getProductName(),
                                            productType,
                                            _sceneWidth, _sceneHeight);

        for (final AlosPalsarImageFile imageFile : _imageFiles) {

            if(isProductSLC) {
                String bandName = "i_" + imageFile.getPolarization();
                final Band bandI = createBand(bandName, Unit.REAL);
                product.addBand(bandI);
                bandImageFileMap.put(bandName, imageFile);
                bandName = "q_" + imageFile.getPolarization();
                final Band bandQ = createBand(bandName, Unit.IMAGINARY);
                product.addBand(bandQ);
                bandImageFileMap.put(bandName, imageFile);

                ReaderUtils.createVirtualIntensityBand(product, bandI, bandQ, "_"+imageFile.getPolarization());
            } else {
                final String bandName = "Amplitude_" + imageFile.getPolarization();
                final Band band = createBand(bandName, Unit.AMPLITUDE);
                product.addBand(band);
                bandImageFileMap.put(bandName, imageFile);
                ReaderUtils.createVirtualIntensityBand(product, band, "_"+imageFile.getPolarization());
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

        // slant range
        final BaseRecord sceneRec = _leaderFile.getSceneRecord();
        final int slantRangeToFirstSample = _imageFiles[0].getSlantRangeToFirstSample() / 1000;
        final double samplingRate = sceneRec.getAttributeDouble("Range sampling rate") * 1000000.0;
        final double halfSpeedOfLight = 299792458 / 2.0;

        final int gridWidth = 6, gridHeight = 6;
        final float subSamplingX = (float)product.getSceneRasterWidth() / (float)(gridWidth - 1);
        final float subSamplingY = (float)product.getSceneRasterHeight() / (float)(gridHeight - 1);

        final float[] range = new float[gridWidth];
        if(_leaderFile.getProductLevel() == AlosPalsarConstants.LEVEL1_1) {

            for(int j = 0; j < gridWidth; ++j) {
                range[j] = (float)(slantRangeToFirstSample + (halfSpeedOfLight * ((j*subSamplingX)) / samplingRate));
            }
        }

        final float[] fineRanges = new float[gridWidth*gridHeight];
        ReaderUtils.createFineTiePointGrid(6, 1, gridWidth, gridHeight, range, fineRanges);

        final TiePointGrid slantRangeGrid = new TiePointGrid("slant_range_time", gridWidth, gridHeight, 0, 0,
                subSamplingX, subSamplingY, fineRanges);

        product.addTiePointGrid(slantRangeGrid);
        slantRangeGrid.setUnit("ns");

        // incidence angle
        final double a0 = sceneRec.getAttributeDouble("Incidence angle constant term");
        final double a1 = sceneRec.getAttributeDouble("Incidence angle linear term");
        final double a2 = sceneRec.getAttributeDouble("Incidence angle quadratic term");
        final double a3 = sceneRec.getAttributeDouble("Incidence angle cubic term");
        final double a4 = sceneRec.getAttributeDouble("Incidence angle fourth term");
        final double a5 = sceneRec.getAttributeDouble("Incidence angle fifth term");

        final float[] angles = new float[gridWidth];
        for(int j = 0; j < gridWidth; ++j) {
            angles[j] = (float)((a0 + a1*range[j] +
                                a2*Math.pow(range[j]/1000.0,2) +
                                a3*Math.pow(range[j]/1000.0,3) +
                                a4*Math.pow(range[j]/1000.0,4) +
                                a5*Math.pow(range[j]/1000.0,5) ) * MathUtils.RTOD);
        }

        final float[] fineAngles = new float[gridWidth*gridHeight];
        ReaderUtils.createFineTiePointGrid(6, 1, gridWidth, gridHeight, angles, fineAngles);

        final TiePointGrid incidentAngleGrid = new TiePointGrid("incident_angle", gridWidth, gridHeight, 0, 0,
                subSamplingX, subSamplingY, fineAngles);
        incidentAngleGrid.setUnit("deg");

        product.addTiePointGrid(incidentAngleGrid);

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

    private Band createBand(final String name, final String unit) {
        int dataType = ProductData.TYPE_INT16;
        if(_leaderFile.getProductLevel() == AlosPalsarConstants.LEVEL1_1)
            dataType = ProductData.TYPE_FLOAT32;
        else if(_leaderFile.getProductLevel() == AlosPalsarConstants.LEVEL1_0)
            dataType = ProductData.TYPE_INT8;

        final Band band = new Band(name, dataType, _sceneWidth, _sceneHeight);
        band.setDescription(name);
        band.setUnit(unit);


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
        for (final AlosPalsarImageFile imageFile : _imageFiles) {
            imageFile.assignMetadataTo(root, c++);
        }

        addSummaryMetadata(new File(_baseDir, AlosPalsarConstants.SUMMARY_FILE_NAME), root);
        addAbstractedMetadataHeader(product, root);
    }

    private void addAbstractedMetadataHeader(Product product, MetadataElement root) {

        final MetadataElement absRoot = AbstractMetadata.addAbstractedMetadataHeader(root);

        final BaseRecord sceneRec = _leaderFile.getSceneRecord();
        final BaseRecord mapProjRec = _leaderFile.getMapProjRecord();
        final BaseRecord radiometricRec = _leaderFile.getRadiometricRecord();

        //mph
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT, getProductName());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT_TYPE, getProductType());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.SPH_DESCRIPTOR,
                _leaderFile.getSceneRecord().getAttributeString("Product type descriptor"));
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
        //AbstractMetadata.setAttribute(absRoot, AbstractMetadata.STATE_VECTOR_TIME,
        //        _leaderFile.getFacilityRecord().getAttributeString("Time of input state vector used to processed the image"));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_line_time, getUTCScanStartTime(sceneRec));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_line_time, getUTCScanStopTime(sceneRec));

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
        }

        //sph
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PASS, getPass(mapProjRec));
        AbstractMetadata.setAttribute(absRoot, "SAMPLE_TYPE", getSampleType());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.algorithm,
                sceneRec.getAttributeString("Processing algorithm identifier"));
        
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.mds1_tx_rx_polar,
                getPolarization(_leaderFile.getSceneRecord().getAttributeString("Sensor ID and mode of operation for this channel")));

        if(mapProjRec != null) {
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spacing,
                _leaderFile.getMapProjRecord().getAttributeDouble("Nominal inter-pixel distance in output scene"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_spacing,
                _leaderFile.getMapProjRecord().getAttributeDouble("Nominal inter-line distance in output scene"));
        }
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_looks,
                sceneRec.getAttributeDouble("Nominal number of looks processed in azimuth"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_looks,
                sceneRec.getAttributeDouble("Nominal number of looks processed in range"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.pulse_repetition_frequency,
                sceneRec.getAttributeDouble("Pulse Repetition Frequency"));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.line_time_interval,
                getLineTimeInterval(sceneRec, _sceneHeight));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.data_type,
                "UInt"+_imageFiles[0].getImageFileDescriptor().getAttributeInt("Number of bits per sample"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_output_lines,
                product.getSceneRasterHeight());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_samples_per_line,
                product.getSceneRasterWidth());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.TOT_SIZE, getTotalSize(product));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.srgr_flag, isGroundRange());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.isMapProjected, isMapProjected());

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ant_elev_corr_flag, 1);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spread_comp_flag, 1);        
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.replica_power_corr_flag, 0);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.abs_calibration_flag, 0);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.calibration_factor,
                radiometricRec.getAttributeDouble("Calibration factor"));
        absRoot.getAttribute(AbstractMetadata.calibration_factor).setUnit("dB");
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_sampling_rate,
                sceneRec.getAttributeDouble("Range sampling rate"));

        addOrbitStateVectors(absRoot, _leaderFile.getPlatformPositionRecord());
    }

    private static void addOrbitStateVectors(final MetadataElement absRoot, final BaseRecord platformPosRec) {
        final MetadataElement orbitVectorListElem = absRoot.getElement(AbstractMetadata.orbit_state_vectors);

        addVector(AbstractMetadata.orbit_vector, orbitVectorListElem, platformPosRec, 1);
        /*addVector(AbstractMetadata.orbit_vector, orbitVectorListElem, platformPosRec, 2);
        addVector(AbstractMetadata.orbit_vector, orbitVectorListElem, platformPosRec, 3);
        addVector(AbstractMetadata.orbit_vector, orbitVectorListElem, platformPosRec, 4);
        addVector(AbstractMetadata.orbit_vector, orbitVectorListElem, platformPosRec, 5);   */
    }

    private static void addVector(String name, MetadataElement orbitVectorListElem,
                                  BaseRecord platformPosRec, int num) {
        final MetadataElement orbitVectorElem = new MetadataElement(name+num);

        orbitVectorElem.setAttributeUTC(AbstractMetadata.orbit_vector_time, getOrbitTime(platformPosRec, num));
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_x_pos,
                platformPosRec.getAttributeDouble("1st orbital element x"));
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_y_pos,
                platformPosRec.getAttributeDouble("2nd orbital element y"));
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_z_pos,
                platformPosRec.getAttributeDouble("3rd orbital element z"));
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_x_vel,
                platformPosRec.getAttributeDouble("4th orbital element x'"));
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_y_vel,
                platformPosRec.getAttributeDouble("5th orbital element y'"));
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_z_vel,
                platformPosRec.getAttributeDouble("6th orbital element z'"));

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
                                  hour+':'+minute+':'+(int)second, "yyyy-mm-dd HH:mm:ss");
    }

    private int isGroundRange() {
        if(_leaderFile.getMapProjRecord() == null) return isProductSLC ? 0 : 1;
        final String projDesc = _leaderFile.getMapProjRecord().getAttributeString("Map projection descriptor").toLowerCase();
        if(projDesc.contains("slant"))
            return 0;
        return 1;
    }

    private int isMapProjected() {
        if(_leaderFile.getMapProjRecord() == null) return 0;
        final String projDesc = _leaderFile.getMapProjRecord().getAttributeString("Map projection descriptor").toLowerCase();
        if(projDesc.contains("geo"))
            return 1;
        return 0;
    }

    private String getProductName() {
        return getMission() + '-' + _volumeDirectoryFile.getProductName();
    }

    private String getProductDescription() {
        return AlosPalsarConstants.PRODUCT_DESCRIPTION_PREFIX + _leaderFile.getProductLevel();
    }
}