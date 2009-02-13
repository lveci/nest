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
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;

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
            _imageFiles[i] = new AlosPalsarImageFile(createInputStream(imageFileNames[i]), getProductLevel());
        }

        _sceneWidth = _imageFiles[0].getRasterWidth();
        _sceneHeight = _imageFiles[0].getRasterHeight();
        assertSameWidthAndHeightForAllImages();

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

        if(_imageFiles.length > 1) {
            int index = 1;
            for (final AlosPalsarImageFile imageFile : _imageFiles) {

                if(isProductSLC) {
                    String bandName = "i_" + index;
                    final Band bandI = createBand(bandName, Unit.REAL);
                    product.addBand(bandI);
                    bandImageFileMap.put(bandName, imageFile);
                    bandName = "q_" + index;
                    final Band bandQ = createBand(bandName, Unit.IMAGINARY);
                    product.addBand(bandQ);
                    bandImageFileMap.put(bandName, imageFile);

                    ReaderUtils.createVirtualIntensityBand(product, bandI, bandQ, "_"+index);
                    ++index;
                } else {
                    String bandName = "Amplitude_" + index;
                    final Band band = createBand(bandName, Unit.AMPLITUDE);
                    product.addBand(band);
                    bandImageFileMap.put(bandName, imageFile);
                    ReaderUtils.createVirtualIntensityBand(product, band, "_"+index);
                    ++index;
                }
            }
        } else {
            final AlosPalsarImageFile imageFile = _imageFiles[0];
            if(isProductSLC) {
                final Band bandI = createBand("i", Unit.REAL);
                product.addBand(bandI);
                bandImageFileMap.put("i", imageFile);
                final Band bandQ = createBand("q", Unit.IMAGINARY);
                product.addBand(bandQ);
                bandImageFileMap.put("q", imageFile);
                ReaderUtils.createVirtualIntensityBand(product, bandI, bandQ, "");
            } else {
                final Band band = createBand("Amplitude", Unit.AMPLITUDE);
                product.addBand(band);
                bandImageFileMap.put("Amplitude", imageFile);
                ReaderUtils.createVirtualIntensityBand(product, band, "");
            }
        }

        product.setStartTime(getUTCScanStartTime());
        product.setEndTime(getUTCScanStopTime());
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

        addSummaryMetadata(root);
        addAbstractedMetadataHeader(product, root);
    }

    private void addAbstractedMetadataHeader(Product product, MetadataElement root) {

        AbstractMetadata.addAbstractedMetadataHeader(root);

        final MetadataElement absRoot = root.getElement(Product.ABSTRACTED_METADATA_ROOT_NAME);
        final BaseRecord sceneRec = _leaderFile.getSceneRecord();
        final BaseRecord mapProjRec = _leaderFile.getMapProjRecord();
        final BaseRecord radiometricRec = _leaderFile.getRadiometricRecord();

        //mph
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT, getProductName());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT_TYPE, getProductType());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.SPH_DESCRIPTOR,
                _leaderFile.getSceneRecord().getAttributeString("Product type descriptor"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.MISSION, getMission());

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PROC_TIME, getProcTime() );
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ProcessingSystemIdentifier,
                sceneRec.getAttributeString("Processing system identifier").trim() );
        // cycle n/a?

        //AbstractMetadata.setAttribute(absRoot, AbstractMetadata.REL_ORBIT,
        //        Integer.parseInt(sceneRec.getAttributeString("Orbit number").trim()));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ABS_ORBIT,
                Integer.parseInt(sceneRec.getAttributeString("Orbit number").trim()));
        //AbstractMetadata.setAttribute(absRoot, AbstractMetadata.STATE_VECTOR_TIME,
        //        _leaderFile.getFacilityRecord().getAttributeString("Time of input state vector used to processed the image"));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_line_time, getUTCScanStartTime());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_line_time, getUTCScanStopTime());

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
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PASS, getPass());
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

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.data_type,
                "UInt"+_imageFiles[0].getImageFileDescriptor().getAttributeInt("Number of bits per sample"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_output_lines,
                product.getSceneRasterHeight());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_samples_per_line,
                product.getSceneRasterWidth());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.TOT_SIZE,
                product.getRawStorageSize());

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.srgr_flag, isGroundRange());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.isMapProjected, isMapProjected());

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.replica_power_corr_flag, 0);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.abs_calibration_flag, 0);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.calibration_factor,
                radiometricRec.getAttributeDouble("Calibration factor"));
        absRoot.getAttribute(AbstractMetadata.calibration_factor).setUnit("dB");
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_sampling_rate,
                sceneRec.getAttributeDouble("Range sampling rate"));
    }

    private ProductData.UTC getProcTime() {
        try {
            String procTime = _volumeDirectoryFile.getVolumeDescriptorRecord().getAttributeString("Logical volume preparation date").trim();

            return ProductData.UTC.parse(procTime, "yyyyMMdd");
        } catch(ParseException e) {
            System.out.println(e.toString());
            return new ProductData.UTC(0);
        }
    }

    private String getPass() {
        if(_leaderFile.getMapProjRecord() == null) return " ";
        double heading = _leaderFile.getMapProjRecord().getAttributeDouble("Platform heading at nadir corresponding to scene centre");
        if(heading > 90 && heading < 270) return "DESCENDING";
        else return "ASCENDING";
    }

    private int isGroundRange() {
        final String projDesc = _leaderFile.getMapProjRecord().getAttributeString("Map projection descriptor").toLowerCase();
        if(projDesc.contains("slant"))
            return 0;
        return 1;
    }

    private int isMapProjected() {
        final String projDesc = _leaderFile.getMapProjRecord().getAttributeString("Map projection descriptor").toLowerCase();
        if(projDesc.contains("geo"))
            return 1;
        return 0;
    }

    private void addSummaryMetadata(final MetadataElement parent) throws IOException {
        final MetadataElement summaryMetadata = new MetadataElement("Summary Information");
        final Properties properties = new Properties();
        final File file = new File(_baseDir, AlosPalsarConstants.SUMMARY_FILE_NAME);
        if (!file.exists()) {
            return;
        }
        properties.load(new FileInputStream(file));
        final Set unsortedEntries = properties.entrySet();
        final TreeSet sortedEntries = new TreeSet(new Comparator() {
            public int compare(final Object a, final Object b) {
                final Map.Entry entryA = (Map.Entry) a;
                final Map.Entry entryB = (Map.Entry) b;
                return ((String) entryA.getKey()).compareTo((String) entryB.getKey());
            }
        });
        sortedEntries.addAll(unsortedEntries);
        for (Object sortedEntry : sortedEntries) {
            final Map.Entry entry = (Map.Entry) sortedEntry;
            final String data = (String) entry.getValue();
            // strip of double quotes
            final String strippedData = data.substring(1, data.length() - 1);
            final MetadataAttribute attribute = new MetadataAttribute((String) entry.getKey(),
                    new ProductData.ASCII(strippedData),
                    true);
            summaryMetadata.addAttribute(attribute);
        }

        parent.addElement(summaryMetadata);
    }

    private static int getMinSampleValue(final int[] histogram) {
        // search for first non zero value
        for (int i = 0; i < histogram.length; i++) {
            if (histogram[i] != 0) {
                return i;
            }
        }
        return 0;
    }

    private static int getMaxSampleValue(final int[] histogram) {
        // search for first non zero value backwards
        for (int i = histogram.length - 1; i >= 0; i--) {
            if (histogram[i] != 0) {
                return i;
            }
        }
        return 0;
    }

    private String getProductName() {
        return getMission() + '-' + _volumeDirectoryFile.getProductName();
    }

    private String getProductDescription() {
        return AlosPalsarConstants.PRODUCT_DESCRIPTION_PREFIX + _leaderFile.getProductLevel();
    }

    private void assertSameWidthAndHeightForAllImages() {
        for (int i = 0; i < _imageFiles.length; i++) {
            final AlosPalsarImageFile imageFile = _imageFiles[i];
            Guardian.assertTrue("_sceneWidth == imageFile[" + i + "].getRasterWidth()",
                                _sceneWidth == imageFile.getRasterWidth());
            Guardian.assertTrue("_sceneHeight == imageFile[" + i + "].getRasterHeight()",
                                _sceneHeight == imageFile.getRasterHeight());
        }
    }

    private ProductData.UTC getUTCScanStartTime() {
        return AbstractMetadata.parseUTC(_leaderFile.getSceneRecord().
                getAttributeString("Zero-doppler azimuth time of first azimuth pixel"));
    }

    private ProductData.UTC getUTCScanStopTime() {
        return AbstractMetadata.parseUTC(_leaderFile.getSceneRecord().
                getAttributeString("Zero-doppler azimuth time of last azimuth pixel"));
    }

    private ImageInputStream createInputStream(final String fileName) throws IOException {
        return new FileImageInputStream(new File(_baseDir, fileName));
    }

}