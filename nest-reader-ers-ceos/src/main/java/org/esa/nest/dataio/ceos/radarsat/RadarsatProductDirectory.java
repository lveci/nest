package org.esa.nest.dataio.ceos.radarsat;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.util.Guardian;
import org.esa.beam.util.Debug;
import org.esa.beam.util.math.MathUtils;
import org.esa.nest.dataio.IllegalBinaryFormatException;
import org.esa.nest.dataio.ReaderUtils;
import org.esa.nest.dataio.ceos.CEOSImageFile;
import org.esa.nest.dataio.ceos.CEOSProductDirectory;
import org.esa.nest.dataio.ceos.CeosHelper;
import org.esa.nest.dataio.ceos.records.BaseRecord;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.util.Constants;
import org.esa.nest.util.GeoUtils;
import org.esa.nest.gpf.OperatorUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import Jama.Matrix;

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

        BaseRecord facilityRec = _leaderFile.getFacilityRecord();
        if(facilityRec == null)
            facilityRec = _trailerFile.getFacilityRecord();
        BaseRecord sceneRec = _leaderFile.getSceneRecord();
        if(sceneRec == null)
            sceneRec = _trailerFile.getSceneRecord();
        BaseRecord detProcRec = _leaderFile.getDetailedProcessingRecord();
        if(detProcRec == null)
            detProcRec = _trailerFile.getDetailedProcessingRecord();

        product.setStartTime(getUTCScanStartTime(sceneRec, detProcRec));
        product.setEndTime(getUTCScanStopTime(sceneRec, detProcRec));
        product.setDescription(getProductDescription());

        addMetaData(product);

        addRSATTiePointGrids(product, sceneRec, detProcRec);

        // set slant_range_to_first_pixel in metadata
        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(product);
        final TiePointGrid slantRangeTimeTPG = product.getTiePointGrid("slant_range_time");
        final int numOutputLines = absRoot.getAttributeInt(AbstractMetadata.num_output_lines);
        double slantRangeTime = slantRangeTimeTPG.getPixelFloat(numOutputLines/2, 0) / 1000000000.0; //s
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.slant_range_to_first_pixel,
                slantRangeTime*Constants.halfLightSpeed);
        
        if(_leaderFile.getLatCorners() != null && _leaderFile.getLonCorners() != null) {
            addGeoCoding(product, _leaderFile.getLatCorners(), _leaderFile.getLonCorners());
        }
        
        if(product.getGeoCoding() == null) {
            addGeoCodingFromSceneLabel(product);
        }

        if(product.getGeoCoding() == null) {
            addTPGGeoCoding(product, sceneRec);
        }

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

        int dataType = ProductData.TYPE_UINT16;
        if(imageFile.getBitsPerSample() == 8) {
            dataType = ProductData.TYPE_UINT8;
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
        _leaderFile.addMetadata(leadMetadata);
        root.addElement(leadMetadata);

        final MetadataElement trailMetadata = new MetadataElement("Trailer");
        _trailerFile.addMetadata(trailMetadata);
        root.addElement(trailMetadata);

        final MetadataElement volMetadata = new MetadataElement("Volume");
        _volumeDirectoryFile.assignMetadataTo(volMetadata);
        root.addElement(volMetadata);

        int c = 1;
        for (final RadarsatImageFile imageFile : _imageFiles) {
            imageFile.assignMetadataTo(root, c++);
        }

        addSummaryMetadata(new File(_baseDir, RadarsatConstants.SUMMARY_FILE_NAME), "Summary Information", root);
        addSummaryMetadata(new File(_baseDir, RadarsatConstants.SCENE_LABEL_FILE_NAME), "Scene Label", root);
        addSummaryMetadata(new File(_baseDir.getParentFile(), RadarsatConstants.SCENE_LABEL_FILE_NAME), "Scene Label", root);
        
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
        BaseRecord detProcRec = _leaderFile.getDetailedProcessingRecord();
        if(detProcRec == null)
            detProcRec = _trailerFile.getDetailedProcessingRecord();

        //mph
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT, getProductName());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT_TYPE, getProductType());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.SPH_DESCRIPTOR,
                sceneRec.getAttributeString("Product type descriptor"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.MISSION, "RS1");

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PROC_TIME,
                getProcTime(_volumeDirectoryFile.getVolumeDescriptorRecord()));

        final ProductData.UTC startTime = getUTCScanStartTime(sceneRec, detProcRec);
        final ProductData.UTC endTime = getUTCScanStopTime(sceneRec, detProcRec);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_line_time, startTime);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_line_time, endTime);

        if(sceneRec != null) {
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.algorithm,
                    sceneRec.getAttributeString("Processing algorithm identifier"));

            final int absOrbit = Integer.parseInt(sceneRec.getAttributeString("Orbit number").trim());
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ABS_ORBIT, absOrbit);
        }

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

            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PASS, getPass(mapProjRec));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spacing,
                mapProjRec.getAttributeDouble("Nominal inter-pixel distance in output scene"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_spacing,
                mapProjRec.getAttributeDouble("Nominal inter-line distance in output scene"));

            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.srgr_flag, isGroundRange(mapProjRec));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.map_projection, getMapProjection(mapProjRec));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.geo_ref_system,
                mapProjRec.getAttributeString("Name of reference ellipsoid"));

        } else if(sceneRec != null) {
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spacing,
                sceneRec.getAttributeDouble("Pixel spacing"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_spacing,
                sceneRec.getAttributeDouble("Line spacing"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PASS,
                sceneRec.getAttributeDouble("Ascending or Descending flag"));
        }

        //sph
        if(sceneRec != null) {
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.algorithm,
                    sceneRec.getAttributeString("Processing algorithm identifier"));

            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.mds1_tx_rx_polar,
                    getPolarization(sceneRec.getAttributeString("Sensor ID and mode of operation for this channel")));

            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_looks,
                    sceneRec.getAttributeDouble("Nominal number of looks processed in azimuth"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_looks,
                    sceneRec.getAttributeDouble("Nominal number of looks processed in range"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.pulse_repetition_frequency,
                    sceneRec.getAttributeDouble("Pulse Repetition Frequency"));
            double radarFreq = sceneRec.getAttributeDouble("Radar frequency");
            if (Double.compare(radarFreq, 0.0) == 0) {
                final double radarWaveLength = sceneRec.getAttributeDouble("Radar wavelength"); // in m
                if (Double.compare(radarWaveLength, 0.0) != 0) {
                    radarFreq = Constants.lightSpeed / radarWaveLength / Constants.oneMillion; // in MHz
                }
            }
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.radar_frequency, radarFreq);

            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_sampling_rate,
                sceneRec.getAttributeDouble("Range sampling rate"));
        }

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.SAMPLE_TYPE, getSampleType());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.line_time_interval,
                ReaderUtils.getLineTimeInterval(startTime, endTime, _sceneHeight));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_output_lines,
                product.getSceneRasterHeight());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_samples_per_line,
                product.getSceneRasterWidth());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.TOT_SIZE, ReaderUtils.getTotalSize(product));

        if(facilityRec != null) {
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.STATE_VECTOR_TIME, AbstractMetadata.parseUTC(
                facilityRec.getAttributeString("Time of input state vector used to processed the image")));
            
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ant_elev_corr_flag,
                    facilityRec.getAttributeInt("Antenna pattern correction flag"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spread_comp_flag,
                    facilityRec.getAttributeInt("Range spreading loss compensation flag"));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.replica_power_corr_flag, 0);
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.abs_calibration_flag, 0);
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.calibration_factor,
                    facilityRec.getAttributeDouble("Absolute calibration constant K"));
        }

        if(radiometricRec != null) {
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.calibration_factor,
                radiometricRec.getAttributeDouble("Calibration constant"));
        }

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.replica_power_corr_flag, 0);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.abs_calibration_flag, 0);

        addOrbitStateVectors(absRoot, _leaderFile.getPlatformPositionRecord());
        if(facilityRec != null)
            addSRGRCoefficients(absRoot, facilityRec);
        else
            addSRGRCoefficients(absRoot, detProcRec);
    }

    private String getMapProjection(final BaseRecord mapProjRec) {
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

    private static void addGeoCodingFromSceneLabel(Product product) {

        final MetadataElement sceneLabelElem = product.getMetadataRoot().getElement("Scene Label");
        if (sceneLabelElem != null) {

            try {
                final String ulLatLon = sceneLabelElem.getAttributeString("UL_CORNER_LAT_LON");
                final String urLatLon = sceneLabelElem.getAttributeString("UR_CORNER_LAT_LON");
                final String llLatLon = sceneLabelElem.getAttributeString("LL_CORNER_LAT_LON");
                final String lrLatLon = sceneLabelElem.getAttributeString("LR_CORNER_LAT_LON");

                final float latUL = Float.parseFloat(ulLatLon.substring(0, ulLatLon.indexOf(',')));
                final float latUR = Float.parseFloat(urLatLon.substring(0, urLatLon.indexOf(',')));
                final float latLL = Float.parseFloat(llLatLon.substring(0, llLatLon.indexOf(',')));
                final float latLR = Float.parseFloat(lrLatLon.substring(0, lrLatLon.indexOf(',')));
                final float[] latCorners = new float[]{latUL, latUR, latLL, latLR};

                final float lonUL = Float.parseFloat(ulLatLon.substring(ulLatLon.indexOf(',')+1, ulLatLon.length()-1));
                final float lonUR = Float.parseFloat(urLatLon.substring(urLatLon.indexOf(',')+1, urLatLon.length()-1));
                final float lonLL = Float.parseFloat(llLatLon.substring(llLatLon.indexOf(',')+1, llLatLon.length()-1));
                final float lonLR = Float.parseFloat(lrLatLon.substring(lrLatLon.indexOf(',')+1, lrLatLon.length()-1));
                final float[] lonCorners = new float[]{lonUL, lonUR, lonLL, lonLR};

                addGeoCoding(product, latCorners, lonCorners);
            } catch (Exception e) {
                Debug.trace(e.toString());
            }
        }
    }

    protected static void addOrbitStateVectors(final MetadataElement absRoot, final BaseRecord platformPosRec) {
        if(platformPosRec == null) return;

        final MetadataElement orbitVectorListElem = absRoot.getElement(AbstractMetadata.orbit_state_vectors);
        final int numPoints = platformPosRec.getAttributeInt("Number of data points");
        final double theta = platformPosRec.getAttributeDouble("Greenwich mean hour angle");

        final double firstLineUTC = absRoot.getAttributeUTC(AbstractMetadata.first_line_time).getMJD();
        final double lastLineUTC = absRoot.getAttributeUTC(AbstractMetadata.last_line_time).getMJD();
        int startIdx = 0;
        int endIdx = 0;
        for (int i = 1; i <= numPoints; i++) {
            double time = getOrbitTime(platformPosRec, i).getMJD();
            if (time < firstLineUTC) {
                startIdx = i;
            }

            if (time < lastLineUTC) {
                endIdx = i;
            }
        }
        startIdx = Math.max(startIdx - 1, 1);
        endIdx = Math.min(endIdx+1, numPoints);

//        for(int i=1; i <= numPoints; ++i) {
        for(int i=startIdx; i <= endIdx; ++i) {
            addVector(AbstractMetadata.orbit_vector, orbitVectorListElem, platformPosRec, theta, i, startIdx);
        }

        if(absRoot.getAttributeUTC(AbstractMetadata.STATE_VECTOR_TIME, new ProductData.UTC(0)).
                equalElems(new ProductData.UTC(0))) {

            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.STATE_VECTOR_TIME,
                getOrbitTime(platformPosRec, 1));
        }
    }

    private static void addVector(String name, MetadataElement orbitVectorListElem,
                                  BaseRecord platformPosRec, double theta, int num, int startIdx) {

        final MetadataElement orbitVectorElem = new MetadataElement(name+(num-startIdx+1));

        final double xPosECI = platformPosRec.getAttributeDouble("Position vector X "+num);
        final double yPosECI = platformPosRec.getAttributeDouble("Position vector Y "+num);
        final double zPosECI = platformPosRec.getAttributeDouble("Position vector Z "+num);

        final double xVelECI = platformPosRec.getAttributeDouble("Velocity vector X' "+num);
        final double yVelECI = platformPosRec.getAttributeDouble("Velocity vector Y' "+num);
        final double zVelECI = platformPosRec.getAttributeDouble("Velocity vector Z' "+num);

        final double thetaInRd = theta*MathUtils.DTOR;
        final double cosTheta = Math.cos(thetaInRd);
        final double sinTheta = Math.sin(thetaInRd);

        final double xPosECEF =  cosTheta*xPosECI + sinTheta*yPosECI;
        final double yPosECEF = -sinTheta*xPosECI + cosTheta*yPosECI;
        final double zPosECEF = zPosECI;

        final double xVelECEF =  cosTheta*xVelECI + sinTheta*yVelECI;
        final double yVelECEF = -sinTheta*xVelECI + cosTheta*yVelECI;
        final double zVelECEF = zVelECI;

        orbitVectorElem.setAttributeUTC(AbstractMetadata.orbit_vector_time, getOrbitTime(platformPosRec, num));
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_x_pos, xPosECEF);
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_y_pos, yPosECEF);
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_z_pos, zPosECEF);
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_x_vel, xVelECEF);
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_y_vel, yVelECEF);
        orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_z_vel, zVelECEF);

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
                                  hour+':'+minute+':'+second, "yyyy-MM-dd HH:mm:ss");
    }
    
    protected static void addSRGRCoefficients(final MetadataElement absRoot, final BaseRecord detailedProcRec) {
        if(detailedProcRec == null) return;

        final MetadataElement srgrCoefficientsElem = absRoot.getElement(AbstractMetadata.srgr_coefficients);
        final int numSRGRCoefSets = detailedProcRec.getAttributeInt("Number of SRGR coefficient sets");

        for(int i=1; i <= numSRGRCoefSets; ++i) {

            final MetadataElement srgrListElem = new MetadataElement(AbstractMetadata.srgr_coef_list+" "+i);
            srgrCoefficientsElem.addElement(srgrListElem);

            final String updateTimeStr = detailedProcRec.getAttributeString("SRGR update date/time "+i);
            final ProductData.UTC utcTime = AbstractMetadata.parseUTC(updateTimeStr, "yyyy-DDD-HH:mm:ss");
            srgrListElem.setAttributeUTC(AbstractMetadata.srgr_coef_time, utcTime);
            AbstractMetadata.addAbstractedAttribute(srgrListElem, AbstractMetadata.ground_range_origin,
                    ProductData.TYPE_FLOAT64, "m", "Ground Range Origin");
            AbstractMetadata.setAttribute(srgrListElem, AbstractMetadata.ground_range_origin, 0.0);

            addSRGRCoef(srgrListElem, detailedProcRec, "SRGR coefficients1 "+i, 1);
            addSRGRCoef(srgrListElem, detailedProcRec, "SRGR coefficients2 "+i, 2);
            addSRGRCoef(srgrListElem, detailedProcRec, "SRGR coefficients3 "+i, 3);
            addSRGRCoef(srgrListElem, detailedProcRec, "SRGR coefficients4 "+i, 4);
            addSRGRCoef(srgrListElem, detailedProcRec, "SRGR coefficients5 "+i, 5);
            addSRGRCoef(srgrListElem, detailedProcRec, "SRGR coefficients6 "+i, 6);
        }
    }

    private static void addRSATTiePointGrids(final Product product, final BaseRecord sceneRec, final BaseRecord detProcRec)
            throws IllegalBinaryFormatException, IOException {

        if(detProcRec == null)
            return;

        final int gridWidth = 11;
        final int gridHeight = 11;

        final int subSamplingX = product.getSceneRasterWidth() / (gridWidth - 1);
        final int subSamplingY = product.getSceneRasterHeight() / (gridHeight - 1);

        final double r = calculateEarthRadius(sceneRec);    // earth radius
        final double eph_orb_data = detProcRec.getAttributeDouble("Ephemeris orbit data1");
        final double h = eph_orb_data - r;                  // orbital altitude

        final double pixelSpacing = sceneRec.getAttributeDouble("Pixel spacing");

        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(product);
        final double firstLineUTC = absRoot.getAttributeUTC(AbstractMetadata.first_line_time).getMJD();
        final double lineTimeInterval = absRoot.getAttributeDouble(AbstractMetadata.line_time_interval) / 86400.0; // s to day

        AbstractMetadata.SRGRCoefficientList[] srgCoefList = null;
        try {
            srgCoefList = AbstractMetadata.getSRGRCoefficients(AbstractMetadata.getAbstractedMetadata(product));
        } catch(Exception e) {
            srgCoefList = null;
        }

        if(srgCoefList != null) {

            final double dRg = subSamplingX * pixelSpacing;
            final float[] rangeDist = new float[gridWidth*gridHeight];
            final float[] rangeTime = new float[gridWidth*gridHeight];

            // slant range distance in m
            int k = 0;
            for (int j = 0; j < gridHeight; j++) {

                // get UTC for j
                int y;
                if (j == gridHeight - 1) { // last row
                    y = product.getSceneRasterHeight() - 1;
                } else { // other rows
                    y = j * subSamplingY;
                }
                final double curLineUTC = firstLineUTC + y*lineTimeInterval;

                // get SRGR coeffs for given utc using linear interpolation
                int idx = 0;
                for (int i = 0; i < srgCoefList.length && curLineUTC >= srgCoefList[i].timeMJD; i++) {
                    idx = i;
                }

                final double[] coeff = new double[srgCoefList[idx].coefficients.length];
                if (idx == srgCoefList.length - 1) {
                    idx--;
                }

                final double mu = (curLineUTC - srgCoefList[idx].timeMJD) /
                                  (srgCoefList[idx+1].timeMJD - srgCoefList[idx].timeMJD);
                for (int i = 0; i < coeff.length; i++) {
                    coeff[i] = org.esa.nest.util.MathUtils.interpolationLinear(
                            srgCoefList[idx].coefficients[i], srgCoefList[idx+1].coefficients[i], mu);
                }

                for(int i = 0; i < gridWidth; i++) {
                    final double groundRange = i*dRg;
                    rangeDist[k++] = (float)(coeff[0] +
                                             groundRange * coeff[1] +
                                             groundRange * groundRange * coeff[2] +
                                             groundRange * groundRange * groundRange * coeff[3] +
                                             groundRange * groundRange * groundRange * groundRange * coeff[4] +
                                             groundRange * groundRange * groundRange * groundRange * groundRange * coeff[5]);
                }
            }

            // get slant range time in nanoseconds from range distance in meters
            for(k = 0; k < rangeDist.length; k++) {
                 rangeTime[k] = (float)(rangeDist[k] / Constants.halfLightSpeed)*1000000000;// in ns
            }

            final TiePointGrid slantRangeGrid = new TiePointGrid(
                    "slant_range_time", gridWidth, gridHeight, 0, 0, subSamplingX, subSamplingY, rangeTime);

            slantRangeGrid.setUnit(Unit.NANOSECONDS);
            product.addTiePointGrid(slantRangeGrid);

            // incidence angle
            final float[] angles = new float[gridWidth*gridHeight];

            k = 0;
            for(int j = 0; j < gridHeight; j++) {
                for (int i = 0; i < gridWidth; i++) {
                    final double RS = rangeDist[k];
                    final double a = ( (h*h) - (RS*RS) + (2.0*r*h) ) / (2.0*RS*r);
                    angles[k] = (float)(Math.acos( a ) * MathUtils.RTOD);
                    k++;
                }
            }

            final TiePointGrid incidentAngleGrid = new TiePointGrid(
                "incident_angle", gridWidth, gridHeight, 0, 0, subSamplingX, subSamplingY, angles);

            incidentAngleGrid.setUnit(Unit.DEGREES);
            product.addTiePointGrid(incidentAngleGrid);
        }
    }

    private static double calculateEarthRadius(BaseRecord sceneRec) {

        final double platLat = sceneRec.getAttributeDouble("Sensor platform geodetic latitude at nadir");
        final double a = Math.tan(platLat * MathUtils.DTOR);
        final double a2 = a*a;
        final double ellipmin = Constants.semiMinorAxis;
        final double ellipmin2 = ellipmin * ellipmin;
        final double ellipmaj = Constants.semiMajorAxis;
        final double ellipmaj2 = ellipmaj * ellipmaj;

        return Constants.semiMinorAxis * (Math.sqrt(1+a2) / Math.sqrt((ellipmin2/ellipmaj2) + a2));
    }

    /**
     * Update target product GEOCoding. A new tie point grid is generated.
     * @param product The product.
     * @param sceneRec The scene record.
     * @throws IOException The exceptions.
     */
    private static void addTPGGeoCoding(final Product product, final BaseRecord sceneRec) throws IOException {

        final int gridWidth = 11;
        final int gridHeight = 11;
        final float[] targetLatTiePoints = new float[gridWidth*gridHeight];
        final float[] targetLonTiePoints = new float[gridWidth*gridHeight];
        final int sourceImageWidth = product.getSceneRasterHeight();

        final float subSamplingX = product.getSceneRasterWidth() / (float)(gridWidth - 1);
        final float subSamplingY = sourceImageWidth / (float)(gridHeight - 1);

        final TiePointGrid slantRangeTime = product.getTiePointGrid("slant_range_time");
        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(product);
        final double firstLineUTC = absRoot.getAttributeUTC(AbstractMetadata.first_line_time).getMJD();
        final double lastLineUTC = absRoot.getAttributeUTC(AbstractMetadata.last_line_time).getMJD();
        final double lineTimeInterval = absRoot.getAttributeDouble(AbstractMetadata.line_time_interval) / 86400.0; // s to day

        final double latMid = sceneRec.getAttributeDouble("scene centre geodetic latitude");
        final double lonMid = sceneRec.getAttributeDouble("scene centre geodetic longitude");

        AbstractMetadata.OrbitStateVector[] orbitStateVectors;
        try {
            orbitStateVectors = AbstractMetadata.getOrbitStateVectors(absRoot);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }

        final int numVectors = orbitStateVectors.length;
        int startIdx = 0;
        int endIdx = 0;
        for (int i = 0; i < numVectors; i++) {
            double time = orbitStateVectors[i].time_mjd;
            if (time < firstLineUTC) {
                startIdx = i;
            }

            if (time < lastLineUTC) {
                endIdx = i;
            }
        }
        startIdx = Math.max(startIdx - 1, 0);
        endIdx = Math.min(endIdx + 1, numVectors-1);
        final int numVectorsUsed = endIdx - startIdx + 1;

        final double[] timeArray = new double[numVectorsUsed];
        final double[] xPosArray = new double[numVectorsUsed];
        final double[] yPosArray = new double[numVectorsUsed];
        final double[] zPosArray = new double[numVectorsUsed];
        final double[] xVelArray = new double[numVectorsUsed];
        final double[] yVelArray = new double[numVectorsUsed];
        final double[] zVelArray = new double[numVectorsUsed];

        for (int i = startIdx; i <= endIdx; i++) {
            timeArray[i - startIdx] = orbitStateVectors[i].time_mjd;
            xPosArray[i - startIdx] = orbitStateVectors[i].x_pos; // m
            yPosArray[i - startIdx] = orbitStateVectors[i].y_pos; // m
            zPosArray[i - startIdx] = orbitStateVectors[i].z_pos; // m
            xVelArray[i - startIdx] = orbitStateVectors[i].x_vel; // m/s
            yVelArray[i - startIdx] = orbitStateVectors[i].y_vel; // m/s
            zVelArray[i - startIdx] = orbitStateVectors[i].z_vel; // m/s
        }

        // Create new tie point grid
        int k = 0;
        for (int r = 0; r < gridHeight; r++) {
            // get the zero Doppler time for the rth line
            int y;
            if (r == gridHeight - 1) { // last row
                y = product.getSceneRasterHeight() - 1;
            } else { // other rows
                y = (int)(r * subSamplingY);
            }
            final double curLineUTC = firstLineUTC + y*lineTimeInterval;
            //System.out.println((new ProductData.UTC(curLineUTC)).toString());

            // compute the satellite position and velocity for the zero Doppler time using cubic interpolation
            final OrbitData data = getOrbitData(curLineUTC, timeArray, xPosArray, yPosArray, zPosArray,
                                                xVelArray, yVelArray, zVelArray);

            for (int c = 0; c < gridWidth; c++) {
                int x;
                if (c == gridWidth - 1) { // last column
                    x = sourceImageWidth - 1;
                } else { // other columns
                    x = (int)(c * subSamplingX);
                }

                final double slrgTime = slantRangeTime.getPixelFloat((float)x, (float)y) / 1000000000.0; // ns to s;
                final GeoPos geoPos = computeLatLon(latMid, lonMid, slrgTime, data);
                targetLatTiePoints[k] = geoPos.lat;
                targetLonTiePoints[k] = geoPos.lon; 
                ++k;
            }
        }

        final TiePointGrid latGrid = new TiePointGrid(OperatorUtils.TPG_LATITUDE, gridWidth, gridHeight,
                0.0f, 0.0f, subSamplingX, subSamplingY, targetLatTiePoints);

        final TiePointGrid lonGrid = new TiePointGrid(OperatorUtils.TPG_LONGITUDE, gridWidth, gridHeight,
                0.0f, 0.0f, subSamplingX, subSamplingY, targetLonTiePoints, TiePointGrid.DISCONT_AT_180);

        final TiePointGeoCoding tpGeoCoding = new TiePointGeoCoding(latGrid, lonGrid, Datum.WGS_84);

        product.addTiePointGrid(latGrid);
        product.addTiePointGrid(lonGrid);
        product.setGeoCoding(tpGeoCoding);
    }


    /**
     * Compute accurate target geo position.
     * @param latMid The scene latitude.
     * @param lonMid The scene longitude.
     * @param slrgTime The slant range time of the given pixel.
     * @param data The orbit data.
     * @return The geo position of the target.
     */
    private static GeoPos computeLatLon(final double latMid, final double lonMid, double slrgTime, OrbitData data) {

        final double[] xyz = new double[3];
        final GeoPos geoPos = new GeoPos((float)latMid, (float)lonMid);

        // compute initial (x,y,z) coordinate from lat/lon
        GeoUtils.geo2xyz(geoPos, xyz);

        // compute accurate (x,y,z) coordinate using Newton's method
        computeAccurateXYZ(data, xyz, slrgTime);

        // compute (lat, lon, alt) from accurate (x,y,z) coordinate
        GeoUtils.xyz2geo(xyz, geoPos);

        return geoPos;
    }

    /**
     * Compute accurate target position for given orbit information using Newton's method.
     * @param data The orbit data.
     * @param xyz The xyz coordinate for the target.
     * @param time The slant range time in seconds.
     */
    private static void computeAccurateXYZ(OrbitData data, double[] xyz, double time) {

        final double a = Constants.semiMajorAxis;
        final double b = Constants.semiMinorAxis;
        final double a2 = a*a;
        final double b2 = b*b;
        final double del = 0.002;
        final int maxIter = 200;

        Matrix X = new Matrix(3, 1);
        final Matrix F = new Matrix(3, 1);
        final Matrix J = new Matrix(3, 3);

        X.set(0, 0, xyz[0]);
        X.set(1, 0, xyz[1]);
        X.set(2, 0, xyz[2]);

        J.set(0, 0, data.xVel);
        J.set(0, 1, data.yVel);
        J.set(0, 2, data.zVel);

        for (int i = 0; i < maxIter; i++) {

            final double x = X.get(0,0);
            final double y = X.get(1,0);
            final double z = X.get(2,0);

            final double dx = x - data.xPos;
            final double dy = y - data.yPos;
            final double dz = z - data.zPos;

            F.set(0, 0, data.xVel*dx + data.yVel*dy + data.zVel*dz);
            F.set(1, 0, dx*dx + dy*dy + dz*dz - Math.pow(time*Constants.halfLightSpeed, 2.0));
            F.set(2, 0, x*x/a2 + y*y/a2 + z*z/b2 - 1);

            J.set(1, 0, 2.0*dx);
            J.set(1, 1, 2.0*dy);
            J.set(1, 2, 2.0*dz);
            J.set(2, 0, 2.0*x/a2);
            J.set(2, 1, 2.0*y/a2);
            J.set(2, 2, 2.0*z/b2);

            X = X.minus(J.inverse().times(F));

            if (Math.abs(F.get(0,0)) <= del && Math.abs(F.get(1,0)) <= del && Math.abs(F.get(2,0)) <= del)  {
                break;
            }
        }

        xyz[0] = X.get(0,0);
        xyz[1] = X.get(1,0);
        xyz[2] = X.get(2,0);
    }

    /**
     * Get orbit information for given time.
     * @param utc The UTC in days.
     * @param timeArray Array holding zeros Doppler times for all state vectors.
     * @param xPosArray Array holding x coordinates for sensor positions in all state vectors.
     * @param yPosArray Array holding y coordinates for sensor positions in all state vectors.
     * @param zPosArray Array holding z coordinates for sensor positions in all state vectors.
     * @param xVelArray Array holding x velocities for sensor positions in all state vectors.
     * @param yVelArray Array holding y velocities for sensor positions in all state vectors.
     * @param zVelArray Array holding z velocities for sensor positions in all state vectors.
     * @return The orbit information.
     */
    public static OrbitData getOrbitData(final double utc, final double[] timeArray,
                                         final double[] xPosArray, final double[] yPosArray, final double[] zPosArray,
                                         final double[] xVelArray, final double[] yVelArray, final double[] zVelArray) {

        // Lagrange polynomial interpolation
        final OrbitData orbitData = new OrbitData();
        orbitData.xPos = org.esa.nest.util.MathUtils.lagrangeInterpolatingPolynomial(timeArray, xPosArray, utc);
        orbitData.yPos = org.esa.nest.util.MathUtils.lagrangeInterpolatingPolynomial(timeArray, yPosArray, utc);
        orbitData.zPos = org.esa.nest.util.MathUtils.lagrangeInterpolatingPolynomial(timeArray, zPosArray, utc);
        orbitData.xVel = org.esa.nest.util.MathUtils.lagrangeInterpolatingPolynomial(timeArray, xVelArray, utc);
        orbitData.yVel = org.esa.nest.util.MathUtils.lagrangeInterpolatingPolynomial(timeArray, yVelArray, utc);
        orbitData.zVel = org.esa.nest.util.MathUtils.lagrangeInterpolatingPolynomial(timeArray, zVelArray, utc);

        return orbitData;
    }

    private final static class OrbitData {
        public double xPos;
        public double yPos;
        public double zPos;
        public double xVel;
        public double yVel;
        public double zVel;
    }
    
}