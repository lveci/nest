package org.esa.nest.dataio.ceos.alos;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.nest.dataio.BinaryFileReader;
import org.esa.nest.dataio.IllegalBinaryFormatException;
import org.esa.nest.dataio.ceos.records.BaseRecord;
import org.esa.nest.dataio.ceos.records.BaseSceneHeaderRecord;
import org.esa.nest.dataio.ceos.CeosHelper;

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

    private final static String mission = "alos";
    private final static String leader_recordDefinitionFile = "leader_file.xml";
    private final static String scene_recordDefinitionFile = "scene_record.xml";
    private final static String mapproj_recordDefinitionFile = "map_proj_record.xml";
    private final static String platform_recordDefinitionFile = "platform_position_record.xml";
    private final static String attitude_recordDefinitionFile = "attitude_record.xml";
    private final static String radiometric_recordDefinitionFile = "radiometric_record.xml";
    private final static String dataQuality_recordDefinitionFile = "data_quality_summary_record.xml";
    private final static String calibration_recordDefinitionFile = "calibration_record.xml";
    private final static String facility_recordDefinitionFile = "facility_record.xml";

    private int productLevel = -1;

    public AlosPalsarLeaderFile(final ImageInputStream leaderStream)
            throws IOException, IllegalBinaryFormatException {

        BinaryFileReader reader = new BinaryFileReader(leaderStream);
        _leaderFDR = new BaseRecord(reader, -1, mission, leader_recordDefinitionFile);
        reader.seek(_leaderFDR.getRecordEndPosition());
        _sceneHeaderRecord = new BaseSceneHeaderRecord(reader, -1, mission, scene_recordDefinitionFile);
        reader.seek(_sceneHeaderRecord.getRecordEndPosition());

        if(getProductLevel() != AlosPalsarConstants.LEVEL1_1 && getProductLevel() != AlosPalsarConstants.LEVEL1_0) {
            _mapProjRecord = new BaseRecord(reader, -1, mission, mapproj_recordDefinitionFile);
            reader.seek(_mapProjRecord.getRecordEndPosition());
        } else _mapProjRecord = null;

        _platformPositionRecord = new BaseRecord(reader, -1, mission, platform_recordDefinitionFile);
        reader.seek(_platformPositionRecord.getRecordEndPosition());
        _attitudeRecord = new BaseRecord(reader, -1, mission, attitude_recordDefinitionFile);
        reader.seek(_attitudeRecord.getRecordEndPosition());

        if(getProductLevel() != AlosPalsarConstants.LEVEL1_0) {
            _radiometricRecord = new BaseRecord(reader, -1, mission, radiometric_recordDefinitionFile);
            reader.seek(_radiometricRecord.getRecordEndPosition());
            _dataQualityRecord = new BaseRecord(reader, -1, mission, dataQuality_recordDefinitionFile);
            reader.seek(_dataQualityRecord.getRecordEndPosition());
            _level0CalibrationRecord = null;
        } else {
            _radiometricRecord = null;
            _dataQualityRecord = null;
            _level0CalibrationRecord = new BaseRecord(reader, -1, mission, calibration_recordDefinitionFile);
            reader.seek(_level0CalibrationRecord.getRecordEndPosition());
        }

        //_facilityRecord = null;
        _facilityRecord = new BaseRecord(reader, -1, mission, facility_recordDefinitionFile);
        reader.seek(_facilityRecord.getRecordEndPosition());

        reader.close();
    }

    public final int getProductLevel() {
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

    public BaseRecord getPlatformPositionRecord() {
        return _platformPositionRecord;
    }

    public float[] getLatCorners() throws IOException, IllegalBinaryFormatException {
        if(_mapProjRecord == null) return null;

        final double latUL = _mapProjRecord.getAttributeDouble("1st line 1st pixel geodetic latitude");
        final double latUR = _mapProjRecord.getAttributeDouble("1st line last valid pixel geodetic latitude");
        final double latLL = _mapProjRecord.getAttributeDouble("Last line 1st pixel geodetic latitude");
        final double latLR = _mapProjRecord.getAttributeDouble("Last line last valid pixel geodetic latitude");
        return new float[]{(float)latUL, (float)latUR, (float)latLL, (float)latLR};
    }

    public float[] getLonCorners() throws IOException, IllegalBinaryFormatException {
        if(_mapProjRecord == null) return null;
        
        final double lonUL = _mapProjRecord.getAttributeDouble("1st line 1st pixel geodetic longitude");
        final double lonUR = _mapProjRecord.getAttributeDouble("1st line last valid pixel geodetic longitude");
        final double lonLL = _mapProjRecord.getAttributeDouble("Last line 1st pixel geodetic longitude");
        final double lonLR = _mapProjRecord.getAttributeDouble("Last line last valid pixel geodetic longitude");
        return new float[]{(float)lonUL, (float)lonUR, (float)lonLL, (float)lonLR};
    }

    public void addLeaderMetadata(MetadataElement sphElem) {

        CeosHelper.addMetadata(sphElem, _leaderFDR, "Leader File Descriptor");
        CeosHelper.addMetadata(sphElem, _sceneHeaderRecord, "Scene Parameters");
        CeosHelper.addMetadata(sphElem, _mapProjRecord, "Map Projection");
        CeosHelper.addMetadata(sphElem, _platformPositionRecord, "Platform Position");
        CeosHelper.addMetadata(sphElem, _attitudeRecord, "Attitude");
        CeosHelper.addMetadata(sphElem, _radiometricRecord, "Radiometric");
        CeosHelper.addMetadata(sphElem, _dataQualityRecord, "Data Quality");
        CeosHelper.addMetadata(sphElem, _level0CalibrationRecord, "Calibration");
        CeosHelper.addMetadata(sphElem, _facilityRecord, "Facility Related");
    }
}
