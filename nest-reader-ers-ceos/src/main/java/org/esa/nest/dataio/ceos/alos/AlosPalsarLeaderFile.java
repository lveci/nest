package org.esa.nest.dataio.ceos.alos;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.nest.dataio.ceos.CeosFileReader;
import org.esa.nest.dataio.ceos.IllegalCeosFormatException;
import org.esa.nest.dataio.ceos.records.BaseRecord;
import org.esa.nest.dataio.ceos.records.BaseSceneHeaderRecord;

import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.util.Calendar;

/**
 * This class represents a leader file of a product.
 *
 */
class AlosPalsarLeaderFile {

    public final BaseRecord _leaderFDR;
    public final BaseSceneHeaderRecord _sceneHeaderRecord;
    public final BaseRecord _mapProjRecord;
    public final BaseRecord _platformPositionRecord;
    public final BaseRecord _attitudeRecord;
    public final BaseRecord _radiometricRecord;
    public final BaseRecord _dataQualityRecord;
    public final BaseRecord _level0CalibrationRecord;
    public final BaseRecord _facilityRecord;
    public CeosFileReader _reader;

    private static String mission = "alos";
    private static String leader_recordDefinitionFile = "leader_file.xml";
    private static String scene_recordDefinitionFile = "scene_record.xml";
    private static String mapproj_recordDefinitionFile = "map_proj_record.xml";
    private static String platform_recordDefinitionFile = "platform_position_record.xml";
    private static String attitude_recordDefinitionFile = "attitude_record.xml";
    private static String radiometric_recordDefinitionFile = "radiometric_record.xml";
    private static String dataQuality_recordDefinitionFile = "data_quality_summary_record.xml";
    private static String calibration_recordDefinitionFile = "calibration_record.xml";
    private static String facility_recordDefinitionFile = "facility_record.xml";

    private int productLevel = -1;

    public AlosPalsarLeaderFile(final ImageInputStream leaderStream) throws IOException,
                                                                        IllegalCeosFormatException {
        _reader = new CeosFileReader(leaderStream);
        _leaderFDR = new BaseRecord(_reader, -1, mission, leader_recordDefinitionFile);
        _reader.seek(_leaderFDR.getAbsolutPosition(_leaderFDR.getRecordLength()));
        _sceneHeaderRecord = new BaseSceneHeaderRecord(_reader, -1, mission, scene_recordDefinitionFile);
        _reader.seek(_sceneHeaderRecord.getAbsolutPosition(_sceneHeaderRecord.getRecordLength()));

        if(getProductLevel() != AlosPalsarConstants.LEVEL1_1 && getProductLevel() != AlosPalsarConstants.LEVEL1_0) {
            _mapProjRecord = new BaseRecord(_reader, -1, mission, mapproj_recordDefinitionFile);
            _reader.seek(_mapProjRecord.getAbsolutPosition(_mapProjRecord.getRecordLength()));
        } else _mapProjRecord = null;

        _platformPositionRecord = new BaseRecord(_reader, -1, mission, platform_recordDefinitionFile);
        _reader.seek(_platformPositionRecord.getAbsolutPosition(_platformPositionRecord.getRecordLength()));
        _attitudeRecord = new BaseRecord(_reader, -1, mission, attitude_recordDefinitionFile);
        _reader.seek(_attitudeRecord.getAbsolutPosition(_attitudeRecord.getRecordLength()));

        if(getProductLevel() != AlosPalsarConstants.LEVEL1_0) {
            _radiometricRecord = new BaseRecord(_reader, -1, mission, radiometric_recordDefinitionFile);
            _reader.seek(_radiometricRecord.getAbsolutPosition(_radiometricRecord.getRecordLength()));
            _dataQualityRecord = new BaseRecord(_reader, -1, mission, dataQuality_recordDefinitionFile);
            _reader.seek(_dataQualityRecord.getAbsolutPosition(_dataQualityRecord.getRecordLength()));
            _level0CalibrationRecord = null;
        } else {
            _radiometricRecord = null;
            _dataQualityRecord = null;
            _level0CalibrationRecord = new BaseRecord(_reader, -1, mission, calibration_recordDefinitionFile);
            _reader.seek(_level0CalibrationRecord.getAbsolutPosition(_level0CalibrationRecord.getRecordLength()));
        }

        //_facilityRecord = null;
        _facilityRecord = new BaseRecord(_reader, -1, mission, facility_recordDefinitionFile);
        _reader.seek(_facilityRecord.getAbsolutPosition(_facilityRecord.getRecordLength()));
    }

    public int getProductLevel() {
        if(productLevel < 0) {
            String level = _sceneHeaderRecord.getAttributeString("Product level code").trim();
            if(level.contains("1.5"))
                productLevel = AlosPalsarConstants.LEVEL1_5;
            else if(level.contains("1.1"))
                productLevel = AlosPalsarConstants.LEVEL1_1;
            else if(level.contains("1.0"))
                productLevel = AlosPalsarConstants.LEVEL1_0;
            else if(level.contains("4.1"))
                productLevel = AlosPalsarConstants.LEVEL4_1;
            else if(level.contains("4.2"))
                productLevel = AlosPalsarConstants.LEVEL4_2;
        }
        return productLevel;
    }

    public Calendar getDateImageWasTaken() {
        return _sceneHeaderRecord.getDateImageWasTaken();
    }

    public String getProductType() {
        return _sceneHeaderRecord.getAttributeString("Product type specifier");
    }

    public BaseRecord getSceneRecord() {
        return _sceneHeaderRecord;
    }

    public BaseRecord getMapProjRecord() {
        return _mapProjRecord;
    }

    public BaseRecord getFacilityRecord() {
        return _facilityRecord;
    }

    public BaseRecord getRadiometricRecord() {
        return _radiometricRecord;
    }

    public float[] getLatCorners() throws IOException,
                                           IllegalCeosFormatException {
        if(_mapProjRecord == null) return null;

        final double latUL = _mapProjRecord.getAttributeDouble("1st line 1st pixel geodetic latitude");
        final double latUR = _mapProjRecord.getAttributeDouble("1st line last valid pixel geodetic latitude");
        final double latLL = _mapProjRecord.getAttributeDouble("Last line 1st pixel geodetic latitude");
        final double latLR = _mapProjRecord.getAttributeDouble("Last line last valid pixel geodetic latitude");
        return new float[]{(float)latUL, (float)latUR, (float)latLL, (float)latLR};
    }

    public float[] getLonCorners() throws IOException,
                                           IllegalCeosFormatException {
        if(_mapProjRecord == null) return null;
        
        final double lonUL = _mapProjRecord.getAttributeDouble("1st line 1st pixel geodetic longitude");
        final double lonUR = _mapProjRecord.getAttributeDouble("1st line last valid pixel geodetic longitude");
        final double lonLL = _mapProjRecord.getAttributeDouble("Last line 1st pixel geodetic longitude");
        final double lonLR = _mapProjRecord.getAttributeDouble("Last line last valid pixel geodetic longitude");
        return new float[]{(float)lonUL, (float)lonUR, (float)lonLL, (float)lonLR};
    }

    public void addLeaderMetadata(MetadataElement sphElem) {
        MetadataElement metadata = new MetadataElement("Leader File Descriptor");
         _leaderFDR.assignMetadataTo(metadata);
        sphElem.addElement(metadata);

        metadata = new MetadataElement("Scene Parameters");
        _sceneHeaderRecord.assignMetadataTo(metadata);
        sphElem.addElement(metadata);

        if(_mapProjRecord != null) {
            metadata = new MetadataElement("Map Projection");
            _mapProjRecord.assignMetadataTo(metadata);
            sphElem.addElement(metadata);
        }

        if(_platformPositionRecord != null) {
            metadata = new MetadataElement("Platform Position");
            _platformPositionRecord.assignMetadataTo(metadata);
            sphElem.addElement(metadata);
        }

        if(_attitudeRecord != null) {
            metadata = new MetadataElement("Attitude");
            _attitudeRecord.assignMetadataTo(metadata);
            sphElem.addElement(metadata);
        }

        if(_radiometricRecord != null) {
            metadata = new MetadataElement("Radiometric");
            _radiometricRecord.assignMetadataTo(metadata);
            sphElem.addElement(metadata);
        }

        if(_dataQualityRecord != null) {
            metadata = new MetadataElement("Data Quality");
            _dataQualityRecord.assignMetadataTo(metadata);
            sphElem.addElement(metadata);
        }

        if(_level0CalibrationRecord != null) {
            metadata = new MetadataElement("Calibration");
            _level0CalibrationRecord.assignMetadataTo(metadata);
            sphElem.addElement(metadata);
        }

        if(_facilityRecord != null) {
            metadata = new MetadataElement("Facility Related");
            _facilityRecord.assignMetadataTo(metadata);
            sphElem.addElement(metadata);
        }
    }

    public void close() throws IOException {
        _reader.close();
        _reader = null;
    }
}
