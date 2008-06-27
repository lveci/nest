package org.esa.nest.dataio.ceos.jers;

import org.esa.nest.dataio.ceos.CeosHelper;
import org.esa.nest.dataio.ceos.IllegalCeosFormatException;
import org.esa.nest.dataio.ceos.ers.*;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.framework.dataop.maptransf.Ellipsoid;
import org.esa.beam.framework.dataop.maptransf.MapInfo;
import org.esa.beam.framework.dataop.maptransf.MapProjection;
import org.esa.beam.framework.dataop.maptransf.MapTransform;
import org.esa.beam.framework.dataop.maptransf.MapTransformFactory;
import org.esa.beam.framework.dataop.maptransf.StereographicDescriptor;
import org.esa.beam.framework.dataop.maptransf.UTM;
import org.esa.beam.util.Debug;
import org.esa.beam.util.Guardian;
import org.esa.beam.util.math.FXYSum;

import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * This class represents a product directory of an Avnir-2 product.
 * <p/>
 * <p>This class is public for the benefit of the implementation of another (internal) class and its API may
 * change in future releases of the software.</p>
 *
 * @author Marco Peters
 */
class JERSProductDirectory {

    private static final double UTM_FALSE_EASTING = 500000.00;
    private static final double UTM_FALSE_NORTHING = 10000000.00;
    private static final int METER_PER_KILOMETER = 1000;

    private final File _baseDir;
    private JERSVolumeDirectoryFile _volumeDirectoryFile;
    private JERSImageFile[] _imageFiles;
    private JERSLeaderFile _leaderFile;

    private final int _sceneWidth;
    private final int _sceneHeight;

    public JERSProductDirectory(final File dir) throws IOException,
                                                         IllegalCeosFormatException {
        Guardian.assertNotNull("dir", dir);

        _baseDir = dir;
        _volumeDirectoryFile = new JERSVolumeDirectoryFile(_baseDir);
        _leaderFile = new JERSLeaderFile(createInputStream(_volumeDirectoryFile.getLeaderFileName()));

        final String[] imageFileNames = _volumeDirectoryFile.getImageFileNames();
        _imageFiles = new JERSImageFile[imageFileNames.length];
        for (int i = 0; i < _imageFiles.length; i++) {
            _imageFiles[i] = new JERSImageFile(createInputStream(imageFileNames[i]));
        }

        _sceneWidth = _imageFiles[0].getRasterWidth();
        _sceneHeight = _imageFiles[0].getRasterHeight();
        assertSameWidthAndHeightForAllImages();
    }

    public Product createProduct() throws IOException,
                                          IllegalCeosFormatException {
        final Product product = new Product(getProductName(),
                                            getProductType(),
                                            _sceneWidth, _sceneHeight);
        product.setFileLocation(_baseDir);

        for (final JERSImageFile ImageFile : _imageFiles) {
            product.addBand(createBand(ImageFile));
        }
        /*product.setStartTime(getUTCScanStartTime());
        product.setEndTime(getUTCScanStopTime());*/
        product.setDescription(getProductDescription());


        addGeoCoding(product);
        addMetaData(product);

        return product;
    }

    private String getProductType() throws IOException,
                                           IllegalCeosFormatException {
        return JERSConstants.PRODUCT_TYPE_PREFIX + _leaderFile.getProductLevel();
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

    public JERSImageFile getImageFile(final Band band) throws IOException,
                                                                IllegalCeosFormatException {
        for (final JERSImageFile imageFile : _imageFiles) {
            if (band.getName().equals(imageFile.getBandName())) {
                return imageFile;
            }
        }
        return null;
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

    private Band createBand(final JERSImageFile ImageFile) throws IOException,
                                                                          IllegalCeosFormatException {
        final Band band = new Band(ImageFile.getBandName(), ProductData.TYPE_INT16,
                                   _sceneWidth, _sceneHeight);
        final int bandIndex = ImageFile.getBandIndex();
        band.setSpectralBandIndex(bandIndex - 1);
        band.setSpectralWavelength(ImageFile.getSpectralWavelength());
        band.setSpectralBandwidth(ImageFile.getSpectralBandwidth());
        band.setUnit(ImageFile.getGeophysicalUnit());
      /*  final double scalingFactor = _leaderFile.getAbsoluteCalibrationGain(bandIndex);
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
        final MetadataElement metadata = new MetadataElement("SPH");
        _leaderFile.addLeaderMetadata(metadata);
        _volumeDirectoryFile.assignMetadataTo(metadata);
        addSummaryMetadata(metadata);

        product.getMetadataRoot().addElement(metadata);
    }

    private void addSummaryMetadata(final MetadataElement parent) throws IOException {
        final MetadataElement summaryMetadata = new MetadataElement("Summary Information");
        final Properties properties = new Properties();
        final File file = new File(_baseDir, JERSConstants.SUMMARY_FILE_NAME);
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
        return JERSConstants.PRODUCT_DESCRIPTION_PREFIX + _leaderFile.getProductLevel();
    }

    private void assertSameWidthAndHeightForAllImages() throws IOException,
                                                               IllegalCeosFormatException {
        for (int i = 0; i < _imageFiles.length; i++) {
            final JERSImageFile imageFile = _imageFiles[i];
            Guardian.assertTrue("_sceneWidth == imageFile[" + i + "].getRasterWidth()",
                                _sceneWidth == imageFile.getRasterWidth());
            Guardian.assertTrue("_sceneHeight == imageFile[" + i + "].getRasterHeight()",
                                _sceneHeight == imageFile.getRasterHeight());
        }
    }

  /*  private ProductData.UTC getUTCScanStartTime() throws IOException,
                                                         IllegalCeosFormatException {
        final Calendar imageStartDate = _leaderFile.getDateImageWasTaken();
        imageStartDate.add(Calendar.MILLISECOND, _imageFiles[0].getTotalMillisInDayOfLine(0));
        return ProductData.UTC.create(imageStartDate.getTime(), _imageFiles[0].getMicrosecondsOfLine(0));
    }

    private ProductData.UTC getUTCScanStopTime() throws IOException,
                                                        IllegalCeosFormatException {
        final Calendar imageStartDate = _leaderFile.getDateImageWasTaken();
        imageStartDate.add(Calendar.MILLISECOND, _imageFiles[0].getTotalMillisInDayOfLine(_sceneHeight - 1));
        return ProductData.UTC.create(imageStartDate.getTime(), _imageFiles[0].getMicrosecondsOfLine(_sceneHeight - 1));
    }   */

    private ImageInputStream createInputStream(final String fileName) throws IOException {
        return new FileImageInputStream(new File(_baseDir, fileName));
    }

}