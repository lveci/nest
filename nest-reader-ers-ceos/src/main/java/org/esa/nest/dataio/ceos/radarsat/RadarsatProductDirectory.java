package org.esa.nest.dataio.ceos.radarsat;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.util.Guardian;
import org.esa.nest.dataio.IllegalBinaryFormatException;
import org.esa.nest.dataio.ReaderUtils;
import org.esa.nest.dataio.ceos.CEOSImageFile;
import org.esa.nest.dataio.ceos.CEOSProductDirectory;
import org.esa.nest.dataio.ceos.records.BaseRecord;
import org.esa.nest.datamodel.AbstractMetadata;

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
class RadarsatProductDirectory extends CEOSProductDirectory {

    private final File _baseDir;
    private RadarsatVolumeDirectoryFile _volumeDirectoryFile = null;
    private RadarsatImageFile[] _imageFiles = null;
    private RadarsatLeaderFile _leaderFile = null;

    private int _sceneWidth = 0;
    private int _sceneHeight = 0;

    private transient Map<String, RadarsatImageFile> bandImageFileMap = new HashMap<String, RadarsatImageFile>(1);

    public RadarsatProductDirectory(final File dir) throws IOException, IllegalBinaryFormatException {
        Guardian.assertNotNull("dir", dir);

        _baseDir = dir;

    }

    @Override
    protected void readProductDirectory() throws IOException, IllegalBinaryFormatException {
        _volumeDirectoryFile = new RadarsatVolumeDirectoryFile(_baseDir);
        _leaderFile = new RadarsatLeaderFile(createInputStream(new File(_baseDir, RadarsatVolumeDirectoryFile.getLeaderFileName())));

        final String[] imageFileNames = CEOSImageFile.getImageFileNames(_baseDir, "DAT_");
        _imageFiles = new RadarsatImageFile[imageFileNames.length];
        for (int i = 0; i < _imageFiles.length; i++) {
            _imageFiles[i] = new RadarsatImageFile(createInputStream(new File(_baseDir, imageFileNames[i])));
        }

        productType = _volumeDirectoryFile.getProductType();
        _sceneWidth = _imageFiles[0].getRasterWidth();
        _sceneHeight = _imageFiles[0].getRasterHeight();
        assertSameWidthAndHeightForAllImages(_imageFiles, _sceneWidth, _sceneHeight);
    }

    private void readVolumeDirectoryFile() throws IOException, IllegalBinaryFormatException {
        if(_volumeDirectoryFile == null)
            _volumeDirectoryFile = new RadarsatVolumeDirectoryFile(_baseDir);

        productType = _volumeDirectoryFile.getProductType();
        isProductSLC = productType.contains("SLC") || productType.contains("COMPLEX");
    }

    @Override
    public Product createProduct() throws IOException, IllegalBinaryFormatException {
        assert(productType != null);
        final Product product = new Product(getProductName(),
                                            productType,
                                            _sceneWidth, _sceneHeight);

        if(_imageFiles.length > 1) {
            int index = 1;
            for (final RadarsatImageFile imageFile : _imageFiles) {

                if(isProductSLC) {
                    String bandName = "i_" + index;
                    final Band bandI = createBand(bandName);
                    product.addBand(bandI);
                    bandImageFileMap.put(bandName, imageFile);
                    bandName = "q_" + index;
                    final Band bandQ = createBand(bandName);
                    product.addBand(bandQ);
                    bandImageFileMap.put(bandName, imageFile);

                    ReaderUtils.createVirtualIntensityBand(product, bandI, bandQ, "_"+index);
                    ++index;
                } else {
                    String bandName = "Amplitude_" + index;
                    Band band = createBand(bandName);
                    product.addBand(band);
                    bandImageFileMap.put(bandName, imageFile);
                    ReaderUtils.createVirtualIntensityBand(product, band, "_"+index);
                    ++index;
                }
            }
        } else {
            final RadarsatImageFile imageFile = _imageFiles[0];
            if(isProductSLC) {
                final Band bandI = createBand("i");
                product.addBand(bandI);
                bandImageFileMap.put("i", imageFile);
                final Band bandQ = createBand("q");
                product.addBand(bandQ);
                bandImageFileMap.put("q", imageFile);
                ReaderUtils.createVirtualIntensityBand(product, bandI, bandQ, "");
            } else {
                final Band band = createBand("Amplitude");
                product.addBand(band);
                bandImageFileMap.put("Amplitude", imageFile);
                ReaderUtils.createVirtualIntensityBand(product, band, "");
            }
        }

        //product.setStartTime(getUTCScanStartTime());
        //product.setEndTime(getUTCScanStopTime());
        product.setDescription(getProductDescription());

        addGeoCoding(product, _leaderFile.getLatCorners(), _leaderFile.getLonCorners());
        addTiePointGrids(product);
        addMetaData(product);

        return product;
    }

    public boolean isRadarsat() throws IOException, IllegalBinaryFormatException {
        if(productType == null || _volumeDirectoryFile == null)
            readVolumeDirectoryFile();
        return (productType.contains("RSAT") || productType.contains("RADARSAT"));
    }

    private void addTiePointGrids(final Product product) throws IllegalBinaryFormatException, IOException {
      /*  BaseRecord facility = _leaderFile.getFacilityRecord();

        double angle1 = facility.getAttributeDouble("Incidence angle at first range pixel");
        double angle2 = facility.getAttributeDouble("Incidence angle at centre range pixel");
        double angle3 = facility.getAttributeDouble("Incidence angle at last valid range pixel");

        TiePointGrid incidentAngleGrid = new TiePointGrid("incident_angle", 3, 2, 0, 0,
                product.getSceneRasterWidth(), product.getSceneRasterHeight(),
                new float[]{(float)angle1, (float)angle2, (float)angle3,   (float)angle1, (float)angle2, (float)angle3});

        product.addTiePointGrid(incidentAngleGrid);  */
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

    private Band createBand(String name) {
        final Band band = new Band(name, ProductData.TYPE_INT8,
                                   _sceneWidth, _sceneHeight);

        //band.setUnit(RadarsatImageFile.getGeophysicalUnit());

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

    private void addMetaData(final Product product) throws IOException, IllegalBinaryFormatException {

        final MetadataElement root = product.getMetadataRoot();

        final MetadataElement leadMetadata = new MetadataElement("Leader");
        _leaderFile.addLeaderMetadata(leadMetadata);
        root.addElement(leadMetadata);

        final MetadataElement volMetadata = new MetadataElement("Volume");
        _volumeDirectoryFile.assignMetadataTo(volMetadata);
        root.addElement(volMetadata);

        addSummaryMetadata(new File(_baseDir, RadarsatConstants.SUMMARY_FILE_NAME), "Summary Information", root);
        addAbstractedMetadataHeader(product, root);
    }

    private void addAbstractedMetadataHeader(Product product, MetadataElement root) {

        AbstractMetadata.addAbstractedMetadataHeader(root);

        final MetadataElement absRoot = root.getElement(AbstractMetadata.ABSTRACT_METADATA_ROOT);
        final BaseRecord mapProjRec = _leaderFile.getMapProjRecord();
        final BaseRecord radiometricRec = _leaderFile.getRadiometricRecord();

        //mph
        AbstractMetadata.setAttribute(absRoot, "PRODUCT", getProductName());
        AbstractMetadata.setAttribute(absRoot, "PRODUCT_TYPE", getProductType());
        AbstractMetadata.setAttribute(absRoot, "MISSION", "RS1");

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PROC_TIME,
                getProcTime(_volumeDirectoryFile.getVolumeDescriptorRecord()));

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
        AbstractMetadata.setAttribute(absRoot, "SAMPLE_TYPE", getSampleType());

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spacing,
                mapProjRec.getAttributeDouble("Nominal inter-pixel distance in output scene"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_spacing,
                mapProjRec.getAttributeDouble("Nominal inter-line distance in output scene"));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_output_lines,
                product.getSceneRasterHeight());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_samples_per_line,
                product.getSceneRasterWidth());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.TOT_SIZE, ReaderUtils.getTotalSize(product));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.calibration_factor,
                radiometricRec.getAttributeDouble("Calibration constant"));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.replica_power_corr_flag, 0);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.abs_calibration_flag, 0);
    }

    private String getProductName() {
        return _volumeDirectoryFile.getProductName();
    }

    private static String getProductDescription() {
        return RadarsatConstants.PRODUCT_DESCRIPTION_PREFIX + RadarsatLeaderFile.getProductLevel();
    }
}