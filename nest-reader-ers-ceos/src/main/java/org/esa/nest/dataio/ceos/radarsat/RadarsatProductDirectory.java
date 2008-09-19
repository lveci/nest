package org.esa.nest.dataio.ceos.radarsat;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.util.Guardian;
import org.esa.nest.dataio.ceos.CEOSImageFile;
import org.esa.nest.dataio.ceos.CEOSProductDirectory;
import org.esa.nest.dataio.ceos.IllegalCeosFormatException;
import org.esa.nest.datamodel.AbstractMetadata;

import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * This class represents a product directory.
 * <p/>
 * <p>This class is public for the benefit of the implementation of another (internal) class and its API may
 * change in future releases of the software.</p>
 *
 */
class RadarsatProductDirectory extends CEOSProductDirectory {

    private final File _baseDir;
    private RadarsatVolumeDirectoryFile _volumeDirectoryFile;
    private RadarsatImageFile[] _imageFiles;
    private RadarsatLeaderFile _leaderFile;

    private int _sceneWidth;
    private int _sceneHeight;

    private transient Map<String, RadarsatImageFile> bandImageFileMap = new HashMap<String, RadarsatImageFile>(1);

    public RadarsatProductDirectory(final File dir) throws IOException,
                                                         IllegalCeosFormatException {
        Guardian.assertNotNull("dir", dir);

        _baseDir = dir;

    }

    protected void readProductDirectory() throws IOException, IllegalCeosFormatException {
        _volumeDirectoryFile = new RadarsatVolumeDirectoryFile(_baseDir);
        _leaderFile = new RadarsatLeaderFile(createInputStream(RadarsatVolumeDirectoryFile.getLeaderFileName()));

        final String[] imageFileNames = CEOSImageFile.getImageFileNames(_baseDir, "DAT_");
        _imageFiles = new RadarsatImageFile[imageFileNames.length];
        for (int i = 0; i < _imageFiles.length; i++) {
            _imageFiles[i] = new RadarsatImageFile(createInputStream(imageFileNames[i]));
        }

        productType = _volumeDirectoryFile.getProductType();
        _sceneWidth = _imageFiles[0].getRasterWidth();
        _sceneHeight = _imageFiles[0].getRasterHeight();
        assertSameWidthAndHeightForAllImages();
    }

    private void readVolumeDirectoryFile() throws IOException, IllegalCeosFormatException {
        if(_volumeDirectoryFile == null)
            _volumeDirectoryFile = new RadarsatVolumeDirectoryFile(_baseDir);

        productType = _volumeDirectoryFile.getProductType();
        isProductSLC = productType.contains("SLC") || productType.contains("COMPLEX");
    }

    public Product createProduct() throws IOException,
                                          IllegalCeosFormatException {
        assert(productType != null);
        final Product product = new Product(getProductName(),
                                            productType,
                                            _sceneWidth, _sceneHeight);

        if(_imageFiles.length > 1) {
            int index = 1;
            for (final RadarsatImageFile imageFile : _imageFiles) {

                if(isProductSLC) {
                    String bandName = "i_" + index;
                    Band bandI = createBand(bandName);
                    product.addBand(bandI);
                    bandImageFileMap.put(bandName, imageFile);
                    bandName = "q_" + index;
                    Band bandQ = createBand(bandName);
                    product.addBand(bandQ);
                    bandImageFileMap.put(bandName, imageFile);

                    createVirtualIntensityBand(product, bandI, bandQ, "_"+index);
                    ++index;
                } else {
                    String bandName = "amplitude_" + index;
                    Band band = createBand(bandName);
                    product.addBand(band);
                    bandImageFileMap.put(bandName, imageFile);
                    createVirtualIntensityBand(product, band, "_"+index);
                    ++index;
                }
            }
        } else {
            RadarsatImageFile imageFile = _imageFiles[0];
            if(isProductSLC) {
                Band bandI = createBand("i");
                product.addBand(bandI);
                bandImageFileMap.put("i", imageFile);
                Band bandQ = createBand("q");
                product.addBand(bandQ);
                bandImageFileMap.put("q", imageFile);
                createVirtualIntensityBand(product, bandI, bandQ, "");
            } else {
                Band band = createBand("amplitude");
                product.addBand(band);
                bandImageFileMap.put("amplitude", imageFile);
                createVirtualIntensityBand(product, band, "");
            }
        }

        //product.setStartTime(getUTCScanStartTime());
        //product.setEndTime(getUTCScanStopTime());
        product.setDescription(getProductDescription());

        addGeoCoding(product);
        addMetaData(product);

        return product;
    }

    public boolean isRadarsat() throws IOException, IllegalCeosFormatException {
        if(productType == null || _volumeDirectoryFile == null)
            readVolumeDirectoryFile();
        return (productType.contains("RSAT") || productType.contains("RADARSAT"));
    }

    private void addGeoCoding(final Product product) throws IllegalCeosFormatException,
                                                            IOException {

        TiePointGrid latGrid = new TiePointGrid("lat", 2, 2, 0.5f, 0.5f, 
                product.getSceneRasterWidth(), product.getSceneRasterHeight(),
                                                _leaderFile.getLatCorners());
        TiePointGrid lonGrid = new TiePointGrid("lon", 2, 2, 0.5f, 0.5f,
                product.getSceneRasterWidth(), product.getSceneRasterHeight(),
                                                _leaderFile.getLonCorners(),
                                                TiePointGrid.DISCONT_AT_360);
        TiePointGeoCoding tpGeoCoding = new TiePointGeoCoding(latGrid, lonGrid, Datum.WGS_84);

        product.addTiePointGrid(latGrid);
        product.addTiePointGrid(lonGrid);
        product.setGeoCoding(tpGeoCoding);
    }

    public CEOSImageFile getImageFile(final Band band) throws IOException,
                                                                IllegalCeosFormatException {
        return bandImageFileMap.get(band.getName());
    }

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

        band.setUnit(RadarsatImageFile.getGeophysicalUnit());

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

    private void addMetaData(final Product product) throws IOException,
                                                           IllegalCeosFormatException {

        final MetadataElement root = product.getMetadataRoot();
        root.addElement(new MetadataElement("Abstracted Metadata"));

        final MetadataElement leadMetadata = new MetadataElement("Leader");
        _leaderFile.addLeaderMetadata(leadMetadata);
        root.addElement(leadMetadata);

        final MetadataElement volMetadata = new MetadataElement("Volume");
        _volumeDirectoryFile.assignMetadataTo(volMetadata);
        root.addElement(volMetadata);

        addSummaryMetadata(root);
        addAbstractedMetadataHeader(root);
    }

    private void addAbstractedMetadataHeader(MetadataElement root) {

        AbstractMetadata.addAbstractedMetadataHeader(root);

        MetadataElement absRoot = root.getElement(Product.ABSTRACTED_METADATA_ROOT_NAME);


        //mph
        AbstractMetadata.setAttribute(absRoot, "PRODUCT", getProductName());
        AbstractMetadata.setAttribute(absRoot, "PRODUCT_TYPE", getProductType());
        AbstractMetadata.setAttribute(absRoot, "MISSION", "RADARSAT-1");

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PROC_TIME, getProcTime() );


        //sph
        AbstractMetadata.setAttribute(absRoot, "SAMPLE_TYPE", getSampleType());

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spacing,
                _leaderFile.getMapProjRecord().getAttributeDouble("Nominal inter-pixel distance in output scene"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_spacing,
                _leaderFile.getMapProjRecord().getAttributeDouble("Nominal inter-line distance in output scene"));
    }

    private String getProcTime() {
        String procTime = _volumeDirectoryFile.getTextRecord().getAttributeString("Location and datetime of product creation").trim();
        int i = procTime.indexOf("  ");
        if(i >= 0)
            return procTime.substring(i+1, procTime.length()).trim();
        return procTime;
    }

    private void addSummaryMetadata(final MetadataElement parent) throws IOException {
        final MetadataElement summaryMetadata = new MetadataElement("Summary Information");
        final Properties properties = new Properties();
        final File file = new File(_baseDir, RadarsatConstants.SUMMARY_FILE_NAME);
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
            // stripp of double quotes
            final String strippedData = data.substring(1, data.length() - 1);
            final MetadataAttribute attribute = new MetadataAttribute((String) entry.getKey(),
                    new ProductData.ASCII(strippedData),
                    true);
            summaryMetadata.addAttribute(attribute);
        }

        parent.addElement(summaryMetadata);
    }

    private int getMinSampleValue(final int[] histogram) {
        // search for first non zero value
        for (int i = 0; i < histogram.length; i++) {
            if (histogram[i] != 0) {
                return i;
            }
        }
        return 0;
    }

    private int getMaxSampleValue(final int[] histogram) {
        // search for first non zero value backwards
        for (int i = histogram.length - 1; i >= 0; i--) {
            if (histogram[i] != 0) {
                return i;
            }
        }
        return 0;
    }

    private String getProductName() {
        return _volumeDirectoryFile.getProductName();
    }

    private String getProductDescription() throws IOException,
                                                  IllegalCeosFormatException {
        return RadarsatConstants.PRODUCT_DESCRIPTION_PREFIX + _leaderFile.getProductLevel();
    }

    private void assertSameWidthAndHeightForAllImages() throws IOException,
                                                               IllegalCeosFormatException {
        for (int i = 0; i < _imageFiles.length; i++) {
            final RadarsatImageFile imageFile = _imageFiles[i];
            Guardian.assertTrue("_sceneWidth == imageFile[" + i + "].getRasterWidth()",
                                _sceneWidth == imageFile.getRasterWidth());
            Guardian.assertTrue("_sceneHeight == imageFile[" + i + "].getRasterHeight()",
                                _sceneHeight == imageFile.getRasterHeight());
        }
    }

    private ImageInputStream createInputStream(final String fileName) throws IOException {
        return new FileImageInputStream(new File(_baseDir, fileName));
    }

}