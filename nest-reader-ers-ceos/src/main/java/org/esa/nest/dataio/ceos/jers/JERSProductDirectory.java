package org.esa.nest.dataio.ceos.jers;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.util.Guardian;
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
class JERSProductDirectory extends CEOSProductDirectory {

    private JERSImageFile[] _imageFiles = null;
    private JERSLeaderFile _leaderFile = null;

    private int _sceneWidth = 0;
    private int _sceneHeight = 0;

    private transient Map<String, JERSImageFile> bandImageFileMap = new HashMap<String, JERSImageFile>(1);

    public JERSProductDirectory(final File dir) throws IOException, IllegalBinaryFormatException {
        Guardian.assertNotNull("dir", dir);

        constants = new JERSConstants();
        _baseDir = dir;
    }

    @Override
    protected void readProductDirectory() throws IOException, IllegalBinaryFormatException {
        readVolumeDirectoryFile();
        _leaderFile = new JERSLeaderFile(createInputStream(CeosHelper.getCEOSFile(_baseDir, "LEA")));

        final String[] imageFileNames = CEOSImageFile.getImageFileNames(_baseDir, "DAT_");
        _imageFiles = new JERSImageFile[imageFileNames.length];
        for (int i = 0; i < _imageFiles.length; i++) {
            _imageFiles[i] = new JERSImageFile(createInputStream(new File(_baseDir, imageFileNames[i])));
        }

        productType = _volumeDirectoryFile.getProductType();
        _sceneWidth = _imageFiles[0].getRasterWidth();
        _sceneHeight = _imageFiles[0].getRasterHeight();
        assertSameWidthAndHeightForAllImages(_imageFiles, _sceneWidth, _sceneHeight);
    }

    @Override
    public Product createProduct() throws IOException, IllegalBinaryFormatException {
        final Product product = new Product(getProductName(),
                                            productType,
                                            _sceneWidth, _sceneHeight);

        if(_imageFiles.length > 1) {
            int index = 1;
            for (final JERSImageFile imageFile : _imageFiles) {

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
            final JERSImageFile imageFile = _imageFiles[0];
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
        addTiePointGrids(product, _leaderFile.getFacilityRecord(), _leaderFile.getSceneRecord());
        addMetaData(product);

        return product;
    }

    public boolean isJERS() throws IOException, IllegalBinaryFormatException {
        if(productType == null || _volumeDirectoryFile == null)
            readVolumeDirectoryFile();
        return (productType.contains("JERS"));
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

    private Band createBand(final Product product, final String name, final String unit, final JERSImageFile imageFile) {
        final Band band = new Band(name, ProductData.TYPE_INT16,
                                   _sceneWidth, _sceneHeight);
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

        final MetadataElement volMetadata = new MetadataElement("Volume");
        _volumeDirectoryFile.assignMetadataTo(volMetadata);
        root.addElement(volMetadata);

        int c = 1;
        for (final JERSImageFile imageFile : _imageFiles) {
            imageFile.assignMetadataTo(root, c++);
        }

        addSummaryMetadata(new File(_baseDir, JERSConstants.SUMMARY_FILE_NAME), "Summary Information", root);
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
                _leaderFile.getSceneRecord().getAttributeString("Product type descriptor"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.MISSION, "JERS1");

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PROC_TIME,
                getProcTime(_volumeDirectoryFile.getVolumeDescriptorRecord()));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ProcessingSystemIdentifier,
                sceneRec.getAttributeString("Processing system identifier").trim() );
        // cycle n/a?

        //AbstractMetadata.setAttribute(absRoot, AbstractMetadata.REL_ORBIT,
        //       Integer.parseInt(sceneRec.getAttributeString("Orbit number").trim()));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ABS_ORBIT,
                Integer.parseInt(sceneRec.getAttributeString("Orbit number").trim()));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.STATE_VECTOR_TIME,
                AbstractMetadata.parseUTC(_leaderFile.getFacilityRecord().getAttributeString(
                        "Time of input state vector used to processed the image")));

        addOrbitStateVectors(absRoot, _leaderFile.getPlatformPositionRecord());
        
        ProductData.UTC startTime = getUTCScanStartTime(sceneRec);
        ProductData.UTC endTime = getUTCScanStopTime(sceneRec);
        // fix times if not found
        if(startTime.equalElems(new ProductData.UTC(0))) {
            startTime = absRoot.getAttributeUTC(AbstractMetadata.STATE_VECTOR_TIME, new ProductData.UTC(0));
            endTime = startTime;
            product.setStartTime(startTime);
            product.setEndTime(endTime);
        }

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_line_time, startTime);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_line_time, endTime);

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
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.algorithm,
                sceneRec.getAttributeString("Processing algorithm identifier"));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.mds1_tx_rx_polar,
                getPolarization(_leaderFile.getSceneRecord().getAttributeString("Sensor ID and mode of operation for this channel")));

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
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.radar_frequency,
                sceneRec.getAttributeDouble("Radar frequency") * 1000.0);
        final double slantRangeTime = sceneRec.getAttributeDouble("Zero-doppler range time of first range pixel")*0.001; //s
        final double lightSpeed = 299792458.0; //  m / s
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.slant_range_to_first_pixel, slantRangeTime*lightSpeed*0.5);
        
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.line_time_interval,
                ReaderUtils.getLineTimeInterval(startTime, endTime, _sceneHeight));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_output_lines,
                product.getSceneRasterHeight());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_samples_per_line,
                product.getSceneRasterWidth());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.TOT_SIZE, ReaderUtils.getTotalSize(product));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.srgr_flag, isGroundRange());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.map_projection, getMapProjection(mapProjRec));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.geo_ref_system,
                mapProjRec.getAttributeString("Name of reference ellipsoid"));
        
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

    }

    private int isGroundRange() {
        final String projDesc = _leaderFile.getMapProjRecord().getAttributeString("Map projection descriptor").toLowerCase();
        if(projDesc.contains("slant"))
            return 0;
        return 1;
    }

    private String getMapProjection(BaseRecord mapProjRec) {
        if(productType.contains("IMG") || productType.contains("GEC")) {
            return mapProjRec.getAttributeString("Map projection descriptor");
        }
        return " ";
    }

    private String getProductName() {
        return _volumeDirectoryFile.getProductName();
    }

    private String getProductDescription() throws IOException, IllegalBinaryFormatException {
        BaseRecord sceneRecord = _leaderFile.getSceneRecord();
        
        String level = "";
        if(sceneRecord != null) {
            level = sceneRecord.getAttributeString("Scene reference number").trim();
        }
        return JERSConstants.PRODUCT_DESCRIPTION_PREFIX + level;
    }
}