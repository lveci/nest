package org.esa.nest.dataio.ceos.alos;

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

/**
 * This class represents a leader file of a product.
 *
 */
class AlosPalsarLeaderFile {

    private static final String UNIT_DEGREE = "degree";

    public final BaseRecord _leaderFDR;
    public final BaseSceneHeaderRecord _sceneHeaderRecord;
    public final BaseRecord _mapProjRecord;
    public final BaseRecord _platformPositionRecord;
    public final BaseRecord _facilityRecord;
    public final BaseRecord _facilityRelatedPCSRecord;
    public CeosFileReader _reader;

    private static String mission = "alos";
    private static String leader_recordDefinitionFile = "leader_file.xml";
    private static String scene_recordDefinitionFile = "scene_record.xml";
    private static String mapproj_recordDefinitionFile = "map_proj_record.xml";
    private static String platform_recordDefinitionFile = "platform_position_record.xml";
    private static String facility_recordDefinitionFile = "facility_record.xml";
    private static String facilityRelatedPCS_recordDefinitionFile = "facility_related_pcs_record.xml";

    public AlosPalsarLeaderFile(final ImageInputStream leaderStream) throws IOException,
                                                                        IllegalCeosFormatException {
        _reader = new CeosFileReader(leaderStream);
        _leaderFDR = new BaseRecord(_reader, -1, mission, leader_recordDefinitionFile);
        _reader.seek(_leaderFDR.getAbsolutPosition(_leaderFDR.getRecordLength()));
        _sceneHeaderRecord = new BaseSceneHeaderRecord(_reader, -1, mission, scene_recordDefinitionFile);
        _reader.seek(_sceneHeaderRecord.getAbsolutPosition(_sceneHeaderRecord.getRecordLength()));
        _mapProjRecord = new BaseRecord(_reader, -1, mission, mapproj_recordDefinitionFile);
        _reader.seek(_mapProjRecord.getAbsolutPosition(_mapProjRecord.getRecordLength()));
        _platformPositionRecord = new BaseRecord(_reader, -1, mission, platform_recordDefinitionFile);
        _reader.seek(_platformPositionRecord.getAbsolutPosition(_platformPositionRecord.getRecordLength()));
        _facilityRecord = new BaseRecord(_reader, -1, mission, facility_recordDefinitionFile);
        _reader.seek(_facilityRecord.getAbsolutPosition(_facilityRecord.getRecordLength()));
        if(_reader.getCurrentPos() + 4000 < _reader.getLength())
            _facilityRelatedPCSRecord = new BaseRecord(_reader, -1, mission, facilityRelatedPCS_recordDefinitionFile);
        else
            _facilityRelatedPCSRecord = null;
    }

    public String getProductLevel() {
        return _sceneHeaderRecord.getAttributeString("Scene reference number").trim();
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

    public BaseRecord getFacilityRecord() {
        return _facilityRecord;
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

    public String getUsedProjection() throws IOException,
                                             IllegalCeosFormatException {
        return _mapProjRecord.getAttributeString("Map projection descriptor");
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

        if(_facilityRelatedPCSRecord != null) {
            metadata = new MetadataElement("Facility Related PCS");
            _facilityRelatedPCSRecord.assignMetadataTo(metadata);
            sphElem.addElement(metadata);
        }
    }

    public MetadataElement getMapProjectionMetadata() throws IOException,
                                                             IllegalCeosFormatException {
        final MetadataElement projMetadata = new MetadataElement("Map Projection");

        addGeneralProjectionMetadata(projMetadata);

        return projMetadata;
    }

    public void close() throws IOException {
        _reader.close();
        _reader = null;
    }

    private void addGeneralProjectionMetadata(final MetadataElement projMeta) throws
                                                                              IOException,
                                                                              IllegalCeosFormatException {

        final float[] latCorners = getLatCorners();
        final float[] lonCorners = getLonCorners();

        addAttribute(projMeta, "SCENE_UPPER_LEFT_LATITUDE", ProductData.createInstance(new float[]{latCorners[0]}),
                     UNIT_DEGREE);
        addAttribute(projMeta, "SCENE_UPPER_LEFT_LONGITUDE", ProductData.createInstance(new float[]{lonCorners[0]}),
                     UNIT_DEGREE);
        addAttribute(projMeta, "SCENE_UPPER_RIGHT_LATITUDE", ProductData.createInstance(new float[]{latCorners[1]}),
                     UNIT_DEGREE);
        addAttribute(projMeta, "SCENE_UPPER_RIGHT_LONGITUDE", ProductData.createInstance(new float[]{lonCorners[1]}),
                     UNIT_DEGREE);
        addAttribute(projMeta, "SCENE_LOWER_LEFT_LATITUDE", ProductData.createInstance(new float[]{latCorners[2]}),
                     UNIT_DEGREE);
        addAttribute(projMeta, "SCENE_LOWER_LEFT_LONGITUDE", ProductData.createInstance(new float[]{lonCorners[2]}),
                     UNIT_DEGREE);
        addAttribute(projMeta, "SCENE_LOWER_RIGHT_LATITUDE", ProductData.createInstance(new float[]{latCorners[3]}),
                     UNIT_DEGREE);
        addAttribute(projMeta, "SCENE_LOWER_RIGHT_LONGITUDE", ProductData.createInstance(new float[]{lonCorners[3]}),
                     UNIT_DEGREE);
    }

    private static MetadataAttribute createAttribute(final String name, final ProductData data) {
        return new MetadataAttribute(name.toUpperCase(), data, true);
    }

    private static MetadataAttribute addAttribute(final MetadataElement platformMetadata, final String name,
                                           final ProductData data, final String unit) {
        final MetadataAttribute attribute = createAttribute(name, data);
        if (unit != null) {
            attribute.setUnit(unit);
        }

        platformMetadata.addAttribute(attribute);
        return attribute;
    }
}