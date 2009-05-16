package org.esa.nest.dataio.ceos.alos;

import Jama.Matrix;
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

        if(product.getGeoCoding() == null) {
            addGeoCodingFromWorkReport(product);
        }

        return product;
    }

    private void addTiePointGrids(final Product product) throws IllegalBinaryFormatException, IOException {

        // slant range time (2-way)
        final BaseRecord sceneRec = _leaderFile.getSceneRecord();
        final double samplingRate = sceneRec.getAttributeDouble("Range sampling rate") * 1000000.0;  // MHz to Hz
        final double halfSpeedOfLight = 299792458 / 2.0; // in m/s

        final int gridWidth = 11;
        final int gridHeight = 11;
        final int sceneWidth = product.getSceneRasterWidth();
        final int sceneHeight = product.getSceneRasterHeight();
        final int subSamplingX = sceneWidth / (gridWidth - 1);
        final int subSamplingY = sceneHeight / (gridHeight - 1);
        final float[] rangeDist = new float[gridWidth*gridHeight];
        final float[] rangeTime = new float[gridWidth*gridHeight];

        if(_leaderFile.getProductLevel() == AlosPalsarConstants.LEVEL1_1) {

            final double tmp = subSamplingX * halfSpeedOfLight / samplingRate;
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
                int y = Math.min(j*subSamplingY, sceneHeight-1);
                final int slantRangeToFirstPixel = _imageFiles[0].getSlantRangeToFirstPixel(y); // meters
                final int slantRangeToMidPixel = _imageFiles[0].getSlantRangeToMidPixel(y);
                final int slantRangeToLastPixel = _imageFiles[0].getSlantRangeToLastPixel(y);
                final double[] polyCoef = computePolynomialCoefficients(slantRangeToFirstPixel,
                                                                        slantRangeToMidPixel,
                                                                        slantRangeToLastPixel,
                                                                        sceneWidth);

                for(int i = 0; i < gridWidth; i++) {
                    int x = i*subSamplingX;
                    rangeDist[k++] = (float)(polyCoef[0] + polyCoef[1]*x + polyCoef[2]*x*x);
                }
            }
        }

        // get slant range time in nanoseconds from range distance in meters
        for(int k = 0; k < rangeDist.length; k++) {
             rangeTime[k] = (float)(rangeDist[k] / halfSpeedOfLight * 1000000000.0); // in ns
        }

        final TiePointGrid slantRangeGrid = new TiePointGrid(
                "slant_range_time", gridWidth, gridHeight, 0, 0, subSamplingX, subSamplingY, rangeTime);

        product.addTiePointGrid(slantRangeGrid);
        slantRangeGrid.setUnit(Unit.NANOSECONDS);

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

        final TiePointGrid incidentAngleGrid = new TiePointGrid(
                "incident_angle", gridWidth, gridHeight, 0, 0, subSamplingX, subSamplingY, angles);

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

        final MetadataElement workReportElem = product.getMetadataRoot().getElement("Work Report");
        if(workReportElem != null) {

            final float latUL = Float.parseFloat(workReportElem.getAttributeString("Brs_ImageSceneLeftTopLatitude", "0"));
            final float latUR = Float.parseFloat(workReportElem.getAttributeString("Brs_ImageSceneRightTopLatitude", "0"));
            final float latLL = Float.parseFloat(workReportElem.getAttributeString("Brs_ImageSceneLeftBottomLatitude", "0"));
            final float latLR = Float.parseFloat(workReportElem.getAttributeString("Brs_ImageSceneRightBottomLatitude", "0"));
            final float[] latCorners = new float[]{latUL, latUR, latLL, latLR};

            final float lonUL = Float.parseFloat(workReportElem.getAttributeString("Brs_ImageSceneLeftTopLongitude", "0"));
            final float lonUR = Float.parseFloat(workReportElem.getAttributeString("Brs_ImageSceneRightTopLongitude", "0"));
            final float lonLL = Float.parseFloat(workReportElem.getAttributeString("Brs_ImageSceneLeftBottomLongitude", "0"));
            final float lonLR = Float.parseFloat(workReportElem.getAttributeString("Brs_ImageSceneRightBottomLongitude", "0"));
            final float[] lonCorners = new float[]{lonUL, lonUR, lonLL, lonLR};

            addGeoCoding(product, latCorners, lonCorners);
        }
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

        addSummaryMetadata(new File(_baseDir, AlosPalsarConstants.SUMMARY_FILE_NAME), "Summary Information", root);
        addSummaryMetadata(new File(_baseDir, AlosPalsarConstants.WORKREPORT_FILE_NAME), "Work Report", root);
        
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

        final ProductData.UTC startTime = getStartEndTime(sceneRec, root, "StartDateTime");
        final ProductData.UTC endTime = getStartEndTime(sceneRec, root, "EndDateTime");
        product.setStartTime(startTime);
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
        }

        //sph
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PASS, getPass(mapProjRec));
        AbstractMetadata.setAttribute(absRoot, "SAMPLE_TYPE", getSampleType());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.algorithm,
                sceneRec.getAttributeString("Processing algorithm identifier"));

        if(_imageFiles.length > 0 && _imageFiles[0] != null)
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.mds1_tx_rx_polar,
                _imageFiles[0].getPolarization());
        if(_imageFiles.length > 1 && _imageFiles[1] != null)
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.mds2_tx_rx_polar,
                _imageFiles[1].getPolarization());

        if(mapProjRec != null) {
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spacing,
                mapProjRec.getAttributeDouble("Nominal inter-pixel distance in output scene"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_spacing,
                mapProjRec.getAttributeDouble("Nominal inter-line distance in output scene"));
        } else {
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spacing,
                sceneRec.getAttributeDouble("Pixel spacing"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_spacing,
                sceneRec.getAttributeDouble("Pixel spacing"));
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
        
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.line_time_interval,
                ReaderUtils.getLineTimeInterval(startTime, endTime, _sceneHeight));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_output_lines,
                product.getSceneRasterHeight());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_samples_per_line,
                product.getSceneRasterWidth());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.TOT_SIZE, ReaderUtils.getTotalSize(product));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.srgr_flag, isGroundRange());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.isMapProjected, isMapProjected());

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ant_elev_corr_flag, 1);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spread_comp_flag, 1);        
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.replica_power_corr_flag, 0);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.abs_calibration_flag, 0);
        if(radiometricRec != null) {
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.calibration_factor,
                radiometricRec.getAttributeDouble("Calibration factor"));
            absRoot.getAttribute(AbstractMetadata.calibration_factor).setUnit("dB");
        }                                                                                   
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_sampling_rate,
                sceneRec.getAttributeDouble("Range sampling rate"));

        addOrbitStateVectors(absRoot, _leaderFile.getPlatformPositionRecord());
    }

    private static double getRadarFrequency(BaseRecord sceneRec) {
        final double wavelength = sceneRec.getAttributeDouble("Radar wavelength");
        return (299792458.0 / wavelength) / 1000000;  // MHz
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
        //final String projDesc = _leaderFile.getMapProjRecord().getAttributeString("Map projection descriptor").toLowerCase();
        //if(projDesc.contains("geo"))
        if(getProductType().contains("1.5G"))
            return 1;
        return 0;
    }

    private static ProductData.UTC getStartEndTime(BaseRecord sceneRec, MetadataElement root, String tag) {
        ProductData.UTC time = getUTCScanStartTime(sceneRec);
        if(time.equalElems(new ProductData.UTC(0))) {
            try {
                final MetadataElement summaryElem = root.getElement("Summary Information");
                if(summaryElem != null) {
                    for(MetadataAttribute sum : summaryElem.getAttributes()) {
                        if(sum.getName().contains(tag)) {
                            return AbstractMetadata.parseUTC(summaryElem.getAttributeString(sum.getName().trim()),
                                    "yyyyMMdd HH:mm:ss");
                        }
                    }
                }
                final String centreTimeStr = sceneRec.getAttributeString("Scene centre time");
                return AbstractMetadata.parseUTC(centreTimeStr.trim(), "yyyyMMddHHmmssSSS");
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
}