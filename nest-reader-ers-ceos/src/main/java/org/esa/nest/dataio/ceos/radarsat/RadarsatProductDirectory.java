package org.esa.nest.dataio.ceos.radarsat;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.maptransf.Datum;
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
import org.esa.nest.util.Constants;

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

        if(_leaderFile.getLatCorners() == null || _leaderFile.getLonCorners() == null) {
            addGeoCoding(product, _leaderFile.getLatCorners(), _leaderFile.getLonCorners());
        } else {
            addTPGGeoCoding(product);
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
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.radar_frequency,
                    sceneRec.getAttributeDouble("Radar frequency") * 1000.0);
            //final double slantRangeTime = sceneRec.getAttributeDouble("Zero-doppler range time of first range pixel")*0.001; //s
            //final double lightSpeed = 299792458.0; //  m / s
            //AbstractMetadata.setAttribute(absRoot, AbstractMetadata.slant_range_to_first_pixel,
            //        slantRangeTime*lightSpeed*0.5);

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

        final int gridWidth = 11;
        final int gridHeight = 11;

        final int subSamplingX = product.getSceneRasterWidth() / (gridWidth - 1);
        final int subSamplingY = product.getSceneRasterHeight() / (gridHeight - 1);

        final double r = calculateEarthRadius(sceneRec);    // earth radius
        final double eph_orb_data = detProcRec.getAttributeDouble("Ephemeris orbit data1");
        final double h = eph_orb_data - r;                  // orbital altitude

        final double pixelSpacing = sceneRec.getAttributeDouble("Pixel spacing");

        AbstractMetadata.SRGRCoefficientList[] srgCoefList = null;
        try {
            srgCoefList = AbstractMetadata.getSRGRCoefficients(AbstractMetadata.getAbstractedMetadata(product));
        } catch(Exception e) {
            srgCoefList = null;
        }
        if(srgCoefList != null) {
            final double[] coeff = srgCoefList[0].coefficients;

            final double dRg = subSamplingX * pixelSpacing;
            final float[] rangeDist = new float[gridWidth*gridHeight];
            final float[] rangeTime = new float[gridWidth*gridHeight];

            // slant range distance in m
            int k = 0;
            for (int j = 0; j < gridHeight; j++) {

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

            product.addTiePointGrid(slantRangeGrid);
            slantRangeGrid.setUnit(Unit.NANOSECONDS);

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
     */
    private void addTPGGeoCoding(final Product product) {

     /*   final int gridWidth = 10;
        final int gridHeight = 10;
        final float[] targetLatTiePoints = new float[gridWidth*gridHeight];
        final float[] targetLonTiePoints = new float[gridWidth*gridHeight];

        computeSubSamplingXY();

        // Create new tie point grid
        int k = 0;
        for (int r = 0; r < gridHeight; r++) {

            // get the zero Doppler time for the rth line
            final int y = getLineIndex(r);
            final double curLineUTC = computeCurrentLineUTC(y);
            //System.out.println((new ProductData.UTC(curLineUTC)).toString());

            // compute the satellite position and velocity for the zero Doppler time using cubic interpolation
            final OrbitData data = getOrbitData(curLineUTC);

            for (int c = 0; c < targetTiePointGridWidth; c++) {

                final int x = getSampleIndex(c);
                targetIncidenceAngleTiePoints[k] = incidenceAngle.getPixelFloat((float)x, (float)y);
                targetSlantRangeTimeTiePoints[k] = slantRangeTime.getPixelFloat((float)x, (float)y);

                final double slrgTime = targetSlantRangeTimeTiePoints[k] / 1000000000.0; // ns to s;
                final GeoPos geoPos = computeLatLon(x, y, slrgTime, data);
                targetLatTiePoints[k] = geoPos.lat;
                targetLonTiePoints[k] = geoPos.lon;
                k++;
            }
        }

        final TiePointGrid latGrid = new TiePointGrid(OperatorUtils.TPG_LATITUDE, targetTiePointGridWidth, targetTiePointGridHeight,
                0.0f, 0.0f, (float)subSamplingX, (float)subSamplingY, targetLatTiePoints);

        final TiePointGrid lonGrid = new TiePointGrid(OperatorUtils.TPG_LONGITUDE, targetTiePointGridWidth, targetTiePointGridHeight,
                0.0f, 0.0f, (float)subSamplingX, (float)subSamplingY, targetLonTiePoints, TiePointGrid.DISCONT_AT_180);

        final TiePointGeoCoding tpGeoCoding = new TiePointGeoCoding(latGrid, lonGrid, Datum.WGS_84);

        product.addTiePointGrid(latGrid);
        product.addTiePointGrid(lonGrid);
        product.setGeoCoding(tpGeoCoding);    */
    }
}