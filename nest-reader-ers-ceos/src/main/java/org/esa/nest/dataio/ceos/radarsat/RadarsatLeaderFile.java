package org.esa.nest.dataio.ceos.radarsat;

import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.nest.dataio.ceos.CeosFileReader;
import org.esa.nest.dataio.ceos.IllegalCeosFormatException;
import org.esa.nest.dataio.ceos.records.BaseRecord;
import org.esa.nest.dataio.ceos.records.BaseSceneHeaderRecord;

import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.util.Calendar;


class RadarsatLeaderFile {

    private static final String UNIT_METER = "meter";
    private static final String UNIT_KILOMETER = "kilometer";
    private static final String UNIT_DEGREE = "degree";
    private static final String UNIT_SECOND = "second";
    private static final String UNIT_METER_PER_SECOND = "m/sec";
    private static final String UNIT_DEGREE_PER_SECOND = "deg/sec";

    private static final String PROJECTION_KEY_RAW = "NNNNN";
    private static final String PROJECTION_KEY_UTM = "YNNNN";
    private static final String PROJECTION_KEY_PS = "NNNNY";

    public final BaseRecord _leaderFDR;
    public final BaseRecord _mapProjRecord;
    public final BaseRecord _radiometricRecord;
    public final BaseRecord _radiometricCompRecord;
    //public final BaseSceneHeaderRecord _sceneHeaderRecord;

    public CeosFileReader _reader;

    private static String mission = "radarsat";
    private static String leader_recordDefinitionFile = "leader_file.xml";
    private static String mapproj_recordDefinitionFile = "map_proj_record.xml";
    private static String radiometric_recordDefinitionFile = "radiometric_record.xml";
    private static String radiometric_comp_recordDefinitionFile = "radiometric_compensation_record.xml";
    private static String scene_recordDefinitionFile = "scene_record.xml";

    public RadarsatLeaderFile(final ImageInputStream leaderStream) throws IOException,
                                                                        IllegalCeosFormatException {
        _reader = new CeosFileReader(leaderStream);
        _leaderFDR = new BaseRecord(_reader, -1, mission, leader_recordDefinitionFile);
        _reader.seek(_leaderFDR.getAbsolutPosition(_leaderFDR.getRecordLength()));
        _mapProjRecord = new BaseRecord(_reader, -1, mission, mapproj_recordDefinitionFile);
        _reader.seek(_mapProjRecord.getAbsolutPosition(_mapProjRecord.getRecordLength()));
        _radiometricRecord = new BaseRecord(_reader, -1, mission, radiometric_recordDefinitionFile);
        _reader.seek(_radiometricRecord.getAbsolutPosition(_radiometricRecord.getRecordLength()));
        _radiometricCompRecord = new BaseRecord(_reader, -1, mission, radiometric_comp_recordDefinitionFile);
        _reader.seek(_radiometricCompRecord.getAbsolutPosition(_radiometricCompRecord.getRecordLength()));
        //_sceneHeaderRecord = new BaseSceneHeaderRecord(_reader, -1, mission, scene_recordDefinitionFile);


    }

    public BaseRecord getMapProjRecord() {
        return _mapProjRecord;
    }



    public String getProductLevel() {
        return "ref num";//_sceneHeaderRecord.getAttributeString("Scene reference number");
    }

    public Calendar getDateImageWasTaken() {
        return null;//_sceneHeaderRecord.getDateImageWasTaken();
    }

    public float[] getLatCorners() throws IOException,
                                           IllegalCeosFormatException {
        final double latUL = _mapProjRecord.getAttributeDouble("1st line 1st pixel geodetic latitude");
        final double latUR = _mapProjRecord.getAttributeDouble("1st line last valid pixel geodetic latitude");
        final double latLL = _mapProjRecord.getAttributeDouble("Last line 1st pixel geodetic latitude");
        final double latLR = _mapProjRecord.getAttributeDouble("Last line last valid pixel geodetic latitude");
        return new float[]{(float)latUL, (float)latUR, (float)latLL, (float)latLR};
    }

    public float[] getLonCorners() throws IOException,
                                           IllegalCeosFormatException {
        final double lonUL = _mapProjRecord.getAttributeDouble("1st line 1st pixel longitude");
        final double lonUR = _mapProjRecord.getAttributeDouble("1st line last valid pixel longitude");
        final double lonLL = _mapProjRecord.getAttributeDouble("Last line 1st pixel longitude");
        final double lonLR = _mapProjRecord.getAttributeDouble("Last line last valid pixel longitude");
        return new float[]{(float)lonUL, (float)lonUR, (float)lonLL, (float)lonLR};
    }

    public void addLeaderMetadata(MetadataElement sphElem) {
        MetadataElement metadata = new MetadataElement("Leader File Descriptor");
         _leaderFDR.assignMetadataTo(metadata);
        sphElem.addElement(metadata);

        metadata = new MetadataElement("Map Projection");
        _mapProjRecord.assignMetadataTo(metadata);
        sphElem.addElement(metadata);

        metadata = new MetadataElement("Radiometric");
        _radiometricRecord.assignMetadataTo(metadata);
        sphElem.addElement(metadata);

        metadata = new MetadataElement("Radiometric Compensation");
        _radiometricCompRecord.assignMetadataTo(metadata);
        sphElem.addElement(metadata);

    }

    public void close() throws IOException {
        _reader.close();
        _reader = null;
    }

    private MetadataAttribute createAttribute(final String name, final ProductData data) {
        return new MetadataAttribute(name.toUpperCase(), data, true);
    }

    private MetadataAttribute addAttribute(final MetadataElement platformMetadata, final String name,
                                           final ProductData data) {
        return addAttribute(platformMetadata, name, data, null);
    }

    private MetadataAttribute addAttribute(final MetadataElement platformMetadata, final String name,
                                           final ProductData data, final String unit) {
        final MetadataAttribute attribute = createAttribute(name, data);
        if (unit != null) {
            attribute.setUnit(unit);
        }

        platformMetadata.addAttribute(attribute);
        return attribute;
    }
}