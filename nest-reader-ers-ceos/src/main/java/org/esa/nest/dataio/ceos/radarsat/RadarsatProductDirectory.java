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
import org.esa.nest.dataio.ceos.CeosHelper;
import org.esa.nest.dataio.ceos.records.BaseRecord;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * This class represents a product directory.
 *
 */
class RadarsatProductDirectory extends CEOSProductDirectory {

    private RadarsatImageFile[] _imageFiles = null;
    private RadarsatLeaderFile _leaderFile = null;
    private RadarsatTrailerFile _trailerFile = null;

    private int _sceneWidth = 0;
    private int _sceneHeight = 0;

    private transient Map<String, RadarsatImageFile> bandImageFileMap = new HashMap<String, RadarsatImageFile>(1);

    public RadarsatProductDirectory(final File dir) {
        Guardian.assertNotNull("dir", dir);

        constants = new RadarsatConstants();
        _baseDir = dir;
    }

    @Override
    protected void readProductDirectory() throws IOException, IllegalBinaryFormatException {
        readVolumeDirectoryFile();

        _leaderFile = new RadarsatLeaderFile(createInputStream(CeosHelper.getCEOSFile(_baseDir, "LEA")));
        _trailerFile = new RadarsatTrailerFile(createInputStream(CeosHelper.getCEOSFile(_baseDir, "TRA")));

        BaseRecord histogramRec = _leaderFile.getHistogramRecord();
        if(histogramRec == null)
            histogramRec = _trailerFile.getHistogramRecord();

        final String[] imageFileNames = CEOSImageFile.getImageFileNames(_baseDir, "DAT_");
        _imageFiles = new RadarsatImageFile[imageFileNames.length];
        for (int i = 0; i < _imageFiles.length; i++) {
            _imageFiles[i] = new RadarsatImageFile(createInputStream(new File(_baseDir, imageFileNames[i])), histogramRec);
        }

        _sceneWidth = _imageFiles[0].getRasterWidth();
        _sceneHeight = _imageFiles[0].getRasterHeight();
        assertSameWidthAndHeightForAllImages(_imageFiles, _sceneWidth, _sceneHeight);
    }

    @Override
    public Product createProduct() throws IOException, IllegalBinaryFormatException {
        assert(productType != null);
        final Product product = new Product(getProductName(), productType, _sceneWidth, _sceneHeight);

        if(_imageFiles.length > 1) {
            int index = 1;
            for (final RadarsatImageFile imageFile : _imageFiles) {

                if(isProductSLC) {
                    final Band bandI = createBand(product, "i_" + index, Unit.REAL, imageFile);
                    final Band bandQ = createBand(product, "q_" + index, Unit.IMAGINARY, imageFile);
                    ReaderUtils.createVirtualIntensityBand(product, bandI, bandQ, "_"+index);
                    ReaderUtils.createVirtualPhaseBand(product, bandI, bandQ, "_"+index);
                } else {
                    final Band band = createBand(product, "Amplitude_" + index, Unit.AMPLITUDE, imageFile);
                    ReaderUtils.createVirtualIntensityBand(product, band, "_"+index);
                }
                ++index;
            }
        } else {
            final RadarsatImageFile imageFile = _imageFiles[0];
            if(isProductSLC) {
                final Band bandI = createBand(product, "i", Unit.REAL, imageFile);
                final Band bandQ = createBand(product, "q", Unit.IMAGINARY, imageFile);
                ReaderUtils.createVirtualIntensityBand(product, bandI, bandQ, "");
                ReaderUtils.createVirtualPhaseBand(product, bandI, bandQ, "");
            } else {
                final Band band = createBand(product, "Amplitude", Unit.AMPLITUDE, imageFile);
                ReaderUtils.createVirtualIntensityBand(product, band, "");
            }
        }

        //product.setStartTime(getUTCScanStartTime());
        //product.setEndTime(getUTCScanStopTime());
        product.setDescription(getProductDescription());

        addGeoCoding(product, _leaderFile.getLatCorners(), _leaderFile.getLonCorners());

        BaseRecord facilityRec = _leaderFile.getFacilityRecord();
        if(facilityRec == null)
            facilityRec = _trailerFile.getFacilityRecord();
        BaseRecord sceneRec = _leaderFile.getSceneRecord();
        if(sceneRec == null)
            sceneRec = _trailerFile.getSceneRecord();

        addTiePointGrids(product, facilityRec, sceneRec);
        addMetaData(product);

        return product;
    }

    public boolean isRadarsat() throws IOException, IllegalBinaryFormatException {
        if(productType == null || _volumeDirectoryFile == null)
            readVolumeDirectoryFile();
        return (productType.contains("RSAT") || productType.contains("RADARSAT"));
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
    }

    private Band createBand(final Product product, final String name, final String unit, final RadarsatImageFile imageFile) {

        int dataType = ProductData.TYPE_INT16;
        if(imageFile.getBitsPerSample() == 8) {
            dataType = ProductData.TYPE_INT8;
        }
        final Band band = new Band(name, dataType,  _sceneWidth, _sceneHeight);
        band.setDescription(name);
        band.setUnit(unit);
        product.addBand(band);
        bandImageFileMap.put(name, imageFile);

        return band;
    }

    private void addMetaData(final Product product) throws IOException, IllegalBinaryFormatException {

        final MetadataElement root = product.getMetadataRoot();

        final MetadataElement leadMetadata = new MetadataElement("Leader");
        _leaderFile.addLeaderMetadata(leadMetadata);
        root.addElement(leadMetadata);

        final MetadataElement trailMetadata = new MetadataElement("Trailer");
        _trailerFile.addLeaderMetadata(trailMetadata);
        root.addElement(trailMetadata);

        final MetadataElement volMetadata = new MetadataElement("Volume");
        _volumeDirectoryFile.assignMetadataTo(volMetadata);
        root.addElement(volMetadata);

        int c = 1;
        for (final RadarsatImageFile imageFile : _imageFiles) {
            imageFile.assignMetadataTo(root, c++);
        }

        addSummaryMetadata(new File(_baseDir, RadarsatConstants.SUMMARY_FILE_NAME), "Summary Information", root);
        addAbstractedMetadataHeader(product, root);
    }

    private void addAbstractedMetadataHeader(Product product, MetadataElement root) {

        final MetadataElement absRoot = AbstractMetadata.addAbstractedMetadataHeader(root);

        BaseRecord mapProjRec = _leaderFile.getMapProjRecord();
        if(mapProjRec == null)
            mapProjRec = _trailerFile.getMapProjRecord();   
        BaseRecord sceneRec = _leaderFile.getSceneRecord();
        if(sceneRec == null)
            sceneRec = _trailerFile.getSceneRecord();
        final BaseRecord radiometricRec = _leaderFile.getRadiometricRecord();
        BaseRecord facilityRec = _leaderFile.getFacilityRecord();
        if(facilityRec == null)
            facilityRec = _trailerFile.getFacilityRecord();

        //mph
        AbstractMetadata.setAttribute(absRoot, "PRODUCT", getProductName());
        AbstractMetadata.setAttribute(absRoot, "PRODUCT_TYPE", getProductType());
        AbstractMetadata.setAttribute(absRoot, "MISSION", "RS1");

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PROC_TIME,
                getProcTime(_volumeDirectoryFile.getVolumeDescriptorRecord()));

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

            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spacing,
                mapProjRec.getAttributeDouble("Nominal inter-pixel distance in output scene"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_spacing,
                mapProjRec.getAttributeDouble("Nominal inter-line distance in output scene"));

            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.map_projection, getMapProjection(mapProjRec));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.geo_ref_system,
                mapProjRec.getAttributeString("Name of reference ellipsoid"));
        }

        //sph
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PASS, getPass(mapProjRec));
        AbstractMetadata.setAttribute(absRoot, "SAMPLE_TYPE", getSampleType());

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_output_lines,
                product.getSceneRasterHeight());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_samples_per_line,
                product.getSceneRasterWidth());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.TOT_SIZE, ReaderUtils.getTotalSize(product));

        if(radiometricRec != null) {
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.calibration_factor,
                radiometricRec.getAttributeDouble("Calibration constant"));
        }

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.replica_power_corr_flag, 0);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.abs_calibration_flag, 0);
    }

    private String getMapProjection(BaseRecord mapProjRec) {
        if(productType.contains("IMG") || productType.contains("GEC") || productType.contains("SSG")) {
            return mapProjRec.getAttributeString("Map projection descriptor");
        }
        return " ";
    }

    private String getProductName() {
        return _volumeDirectoryFile.getProductName();
    }

    private String getProductDescription() {
        BaseRecord sceneRecord = _leaderFile.getSceneRecord();
        if(sceneRecord == null)
            sceneRecord = _trailerFile.getSceneRecord();

        String level = "";
        if(sceneRecord != null) {
            level = sceneRecord.getAttributeString("Scene reference number").trim();
        }
        return RadarsatConstants.PRODUCT_DESCRIPTION_PREFIX + level;
    }
}