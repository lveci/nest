package org.esa.nest.dataio.ceos.ers;

import org.esa.nest.dataio.ceos.IllegalCeosFormatException;
import org.esa.nest.dataio.ceos.CeosHelper;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.maptransf.*;
import org.esa.beam.util.Guardian;
import org.esa.beam.util.Debug;
import org.esa.beam.util.math.FXYSum;

import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * This class represents a product directory of an Avnir-2 product.
 * <p/>
 * <p>This class is public for the benefit of the implementation of another (internal) class and its API may
 * change in future releases of the software.</p>
 *
 * @author Marco Peters
 */
class ERSProductDirectory {

    private static final double UTM_FALSE_EASTING = 500000.00;
    private static final double UTM_FALSE_NORTHING = 10000000.00;
    private static final int METER_PER_KILOMETER = 1000;

    private final File _baseDir;
    private ERSVolumeDirectoryFile _volumeDirectoryFile;
    private ERSImageFile[] _imageFiles;
    private ERSLeaderFile _leaderFile;

    private final int _sceneWidth;
    private final int _sceneHeight;

    public ERSProductDirectory(final File dir) throws IOException,
                                                         IllegalCeosFormatException {
        Guardian.assertNotNull("dir", dir);

        _baseDir = dir;
        _volumeDirectoryFile = new ERSVolumeDirectoryFile(_baseDir);
        _leaderFile = new ERSLeaderFile(createInputStream(ERSVolumeDirectoryFile.getLeaderFileName()));

        final String[] imageFileNames = _volumeDirectoryFile.getImageFileNames();
        _imageFiles = new ERSImageFile[imageFileNames.length];
        for (int i = 0; i < _imageFiles.length; i++) {
            _imageFiles[i] = new ERSImageFile(createInputStream(imageFileNames[i]));
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

        int index = 0;
        for (final ERSImageFile ImageFile : _imageFiles) {
            product.addBand(createBand(ImageFile, index++));
        }
        //product.setStartTime(getUTCScanStartTime());
        //product.setEndTime(getUTCScanStopTime());
        product.setDescription(getProductDescription());

        addGeoCoding(product);
        addMetaData(product);

        return product;
    }

    private String getProductType() throws IOException,
                                           IllegalCeosFormatException {
        return ERSConstants.PRODUCT_TYPE_PREFIX + _leaderFile.getProductLevel();
    }

    private void addGeoCoding(final Product product) throws IllegalCeosFormatException,
                                                            IOException {

        final String usedProjection = _leaderFile.getUsedProjection();
       // if (ERSConstants.MAP_PROJECTION_RAW.equalsIgnoreCase(usedProjection)) {
            final Band[] bands = product.getBands();
            for (int i = 0; i < bands.length; i++) {
                final Band band = bands[i];
                /*final int bandIndex = i;
                final double[][] uncorrectedCoeffs = _leaderFile.getUncorrectedTransformationCoeffs(bandIndex);

                final FXYSum.Cubic funcLat = new FXYSum.Cubic(CeosHelper.sortToFXYSumOrder(uncorrectedCoeffs[0]));
                final FXYSum.Cubic funcLon = new FXYSum.Cubic(CeosHelper.sortToFXYSumOrder(uncorrectedCoeffs[1]));
                final FXYSum.Cubic funcX = new FXYSum.Cubic(CeosHelper.sortToFXYSumOrder(uncorrectedCoeffs[2]));
                final FXYSum.Cubic funcY = new FXYSum.Cubic(CeosHelper.sortToFXYSumOrder(uncorrectedCoeffs[3]));  */

                final FXYSum.Linear funcLat = new FXYSum.Linear();
                final FXYSum.Linear funcLon = new FXYSum.Linear();
                final FXYSum.Linear funcX = new FXYSum.Linear();
                final FXYSum.Linear funcY = new FXYSum.Linear();

                final FXYGeoCoding gc = new FXYGeoCoding(0.0f, 0.0f, 1.0f, 1.0f,
                                                         funcX, funcY,
                                                         funcLat, funcLon,
                                                         Datum.WGS_84);
                band.setGeoCoding(gc);
            }

       /* }  else if (ERSConstants.MAP_PROJECTION_UTM.equalsIgnoreCase(usedProjection)) {
            final int zoneIndex = (int) _leaderFile.getUTMZoneIndex();

            final boolean isSouth = _leaderFile.isUTMSouthHemisphere();

            double easting = _leaderFile.getUTMEasting() * METER_PER_KILOMETER;     // km -> meter
            easting += UTM_FALSE_EASTING;
            double northing = _leaderFile.getUTMNorthing() * METER_PER_KILOMETER;    // km -> meter
            if (northing < 0) {
                northing += UTM_FALSE_NORTHING;
            }

            final double pixelSizeX = _leaderFile.getNominalInterPixelDistance();
            final double pixelSizeY = _leaderFile.getNominalInterLineDistance();
            final float orientationAngle = (float) _leaderFile.getUTMOrientationAngle();

            final MapInfo mapInfo = new MapInfo(UTM.createProjection(zoneIndex - 1, isSouth),
                                                _sceneWidth * 0.5f, _sceneHeight * 0.5f,
                                                (float) easting, (float) northing,
                                                (float) pixelSizeX, (float) pixelSizeY, Datum.ITRF_97);
            mapInfo.setOrientation(orientationAngle);
            mapInfo.setSceneWidth(_sceneWidth);
            mapInfo.setSceneHeight(_sceneHeight);
            product.setGeoCoding(new MapGeoCoding(mapInfo));
                        

        } else if (ERSConstants.MAP_PROJECTION_PS.equalsIgnoreCase(usedProjection)) {
            final double[] parameterValues = StereographicDescriptor.PARAMETER_DEFAULT_VALUES;
            parameterValues[0] = Ellipsoid.GRS_80.getSemiMajor();
            parameterValues[1] = Ellipsoid.GRS_80.getSemiMinor();
            final GeoPos psReferencePoint = _leaderFile.getPSReferencePoint();
            final GeoPos psProjectionOrigin = _leaderFile.getPSProjectionOrigin();

            parameterValues[2] = psProjectionOrigin.getLat();         // Latitude_True_Scale
            parameterValues[3] = psReferencePoint.getLon();       // Central_Meridian


            final MapTransform transform = MapTransformFactory.createTransform(StereographicDescriptor.TYPE_ID,
                                                                               parameterValues);
            final MapProjection projection = new MapProjection(StereographicDescriptor.NAME, transform);
            final double pixelSizeX = _leaderFile.getNominalInterPixelDistance();
            final double pixelSizeY = _leaderFile.getNominalInterLineDistance();
            final double easting = _leaderFile.getPSXCoordinate() * METER_PER_KILOMETER;
            final double northing = _leaderFile.getPSYCoordinate() * METER_PER_KILOMETER;
            final int sceneRasterWidth = product.getSceneRasterWidth();
            final int sceneRasterHeight = product.getSceneRasterHeight();
            final MapInfo mapInfo = new MapInfo(projection,
                                                sceneRasterWidth * 0.5f, sceneRasterHeight * 0.5f,
                                                (float) easting, (float) northing,
                                                (float) pixelSizeX, (float) pixelSizeY, Datum.ITRF_97);
            mapInfo.setOrientation((float) _leaderFile.getPSOrientationAngle());
            mapInfo.setSceneWidth(sceneRasterWidth);
            mapInfo.setSceneHeight(sceneRasterHeight);
            product.setGeoCoding(new MapGeoCoding(mapInfo));

        } else {
            Debug.trace("Unknown map projection method. Could not create geo-coding.");
        }                          */


    }

    public ERSImageFile getImageFile(final Band band) throws IOException,
                                                                IllegalCeosFormatException {
        for (final ERSImageFile imageFile : _imageFiles) {
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

    private Band createBand(final ERSImageFile ImageFile, int index) throws IOException,
                                                                          IllegalCeosFormatException {
        final Band band = new Band(ImageFile.getBandName(), ProductData.TYPE_INT16,
                                   _sceneWidth, _sceneHeight);
        final int bandIndex = index;
        band.setUnit(ERSImageFile.getGeophysicalUnit());
        
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
        addSummaryMetadata(metadata);

        product.getMetadataRoot().addElement(metadata);

        final MetadataElement volumeDescriptor = new MetadataElement("VOLUME_DESCRIPTOR");
        _volumeDirectoryFile.assignMetadataTo(volumeDescriptor);
        product.getMetadataRoot().addElement(volumeDescriptor);
    }

    private void addSummaryMetadata(final MetadataElement parent) throws IOException {
        final MetadataElement summaryMetadata = new MetadataElement("Summary Information");
        final Properties properties = new Properties();
        final File file = new File(_baseDir, ERSConstants.SUMMARY_FILE_NAME);
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
        return ERSConstants.PRODUCT_DESCRIPTION_PREFIX + _leaderFile.getProductLevel();
    }

    private void assertSameWidthAndHeightForAllImages() throws IOException,
                                                               IllegalCeosFormatException {
        for (int i = 0; i < _imageFiles.length; i++) {
            final ERSImageFile imageFile = _imageFiles[i];
            Guardian.assertTrue("_sceneWidth == imageFile[" + i + "].getRasterWidth()",
                                _sceneWidth == imageFile.getRasterWidth());
            Guardian.assertTrue("_sceneHeight == imageFile[" + i + "].getRasterHeight()",
                                _sceneHeight == imageFile.getRasterHeight());
        }
    }

    /*
    private ProductData.UTC getUTCScanStartTime() throws IOException,
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
    }  */

    private ImageInputStream createInputStream(final String fileName) throws IOException {
        return new FileImageInputStream(new File(_baseDir, fileName));
    }

}
