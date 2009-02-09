package org.esa.nest.dataio.ceos.jers;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.nest.dataio.BinaryFileReader;
import org.esa.nest.dataio.IllegalBinaryFormatException;
import org.esa.nest.dataio.ceos.records.BaseRecord;
import org.esa.nest.dataio.ceos.records.BaseSceneHeaderRecord;

import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.util.Calendar;


class JERSLeaderFile {

    public final BaseRecord _leaderFDR;
    public final BaseSceneHeaderRecord _sceneHeaderRecord;
    public final BaseRecord _mapProjRecord;
    public final BaseRecord _platformPositionRecord;
    public final BaseRecord _facilityRecord;
    public BinaryFileReader _reader;

    private final static String mission = "jers";
    private final static String leader_recordDefinitionFile = "leader_file.xml";
    private final static String scene_recordDefinitionFile = "scene_record.xml";
    private final static String mapproj_recordDefinitionFile = "map_proj_record.xml";
    private final static String platform_recordDefinitionFile = "platform_position_record.xml";
    private final static String facility_recordDefinitionFile = "facility_record.xml";

    public JERSLeaderFile(final ImageInputStream leaderStream) throws IOException, IllegalBinaryFormatException {
        _reader = new BinaryFileReader(leaderStream);
        _leaderFDR = new BaseRecord(_reader, -1, mission, leader_recordDefinitionFile);
        _reader.seek(_leaderFDR.getAbsolutPosition(_leaderFDR.getRecordLength()));
        _sceneHeaderRecord = new BaseSceneHeaderRecord(_reader, -1, mission, scene_recordDefinitionFile);
        _reader.seek(_sceneHeaderRecord.getAbsolutPosition(_sceneHeaderRecord.getRecordLength()));
        _mapProjRecord = new BaseRecord(_reader, -1, mission, mapproj_recordDefinitionFile);
        _reader.seek(_mapProjRecord.getAbsolutPosition(_mapProjRecord.getRecordLength()));
        _platformPositionRecord = new BaseRecord(_reader, -1, mission, platform_recordDefinitionFile);
        _reader.seek(_platformPositionRecord.getAbsolutPosition(_platformPositionRecord.getRecordLength()));
        _facilityRecord = new BaseRecord(_reader, -1, mission, facility_recordDefinitionFile);
    }

    public String getProductLevel() {
        return _sceneHeaderRecord.getAttributeString("Scene reference number").trim();
    }

    public Calendar getDateImageWasTaken() {
        return _sceneHeaderRecord.getDateImageWasTaken();
    }

    public BaseRecord getSceneRecord() {
        return _sceneHeaderRecord;
    }

    public BaseRecord getFacilityRecord() {
        return _facilityRecord;
    }

    public BaseRecord getMapProjRecord() {
        return _mapProjRecord;
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
        MetadataElement metadata = new MetadataElement("Leader File Descriptor");
         _leaderFDR.assignMetadataTo(metadata);
        sphElem.addElement(metadata);

        metadata = new MetadataElement("Scene Parameters");
        _sceneHeaderRecord.assignMetadataTo(metadata);
        sphElem.addElement(metadata);

        metadata = new MetadataElement("Map Projection");
        _mapProjRecord.assignMetadataTo(metadata);
        sphElem.addElement(metadata);

        metadata = new MetadataElement("Platform Position");
        _platformPositionRecord.assignMetadataTo(metadata);
        sphElem.addElement(metadata);

        metadata = new MetadataElement("Facility Related");
        _facilityRecord.assignMetadataTo(metadata);
        sphElem.addElement(metadata);
    }

    public void close() throws IOException {
        _reader.close();
        _reader = null;
    }
}