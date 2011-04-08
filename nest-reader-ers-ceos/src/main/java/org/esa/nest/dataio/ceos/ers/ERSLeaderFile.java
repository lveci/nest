/*
 * Copyright (C) 2011 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.nest.dataio.ceos.ers;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.nest.dataio.BinaryFileReader;
import org.esa.nest.dataio.ceos.CeosHelper;
import org.esa.nest.dataio.ceos.records.BaseRecord;
import org.esa.nest.dataio.ceos.records.BaseSceneHeaderRecord;

import javax.imageio.stream.ImageInputStream;
import java.io.IOException;

/**
 * This class represents a leader file of a product.
 *
 */
class ERSLeaderFile {

    private final BaseRecord _leaderFDR;
    private final BaseSceneHeaderRecord _sceneHeaderRecord;
    private final BaseRecord _mapProjRecord;
    private final BaseRecord _platformPositionRecord;
    private final BaseRecord _facilityRecord;
    private final BaseRecord _facilityRelatedPCSRecord;

    private final static String mission = "ers";
    private final static String leader_recordDefinitionFile = "leader_file.xml";
    private final static String scene_recordDefinitionFile = "scene_record.xml";
    private final static String mapproj_recordDefinitionFile = "map_proj_record.xml";
    private final static String platform_recordDefinitionFile = "platform_position_record.xml";
    private final static String facility_recordDefinitionFile = "facility_record.xml";
    private final static String facilityRelatedPCS_recordDefinitionFile = "facility_related_pcs_record.xml";

    public ERSLeaderFile(final ImageInputStream leaderStream)
            throws IOException {

        final BinaryFileReader reader = new BinaryFileReader(leaderStream);
        _leaderFDR = new BaseRecord(reader, -1, mission, leader_recordDefinitionFile);
        reader.seek(_leaderFDR.getRecordEndPosition());
        _sceneHeaderRecord = new BaseSceneHeaderRecord(reader, -1, mission, scene_recordDefinitionFile);
        reader.seek(_sceneHeaderRecord.getRecordEndPosition());
        _mapProjRecord = new BaseRecord(reader, -1, mission, mapproj_recordDefinitionFile);
        reader.seek(_mapProjRecord.getRecordEndPosition());
        _platformPositionRecord = new BaseRecord(reader, -1, mission, platform_recordDefinitionFile);
        reader.seek(_platformPositionRecord.getRecordEndPosition());
        _facilityRecord = new BaseRecord(reader, -1, mission, facility_recordDefinitionFile);
        reader.seek(_facilityRecord.getRecordEndPosition());
        if(reader.getCurrentPos() + 4000 < reader.getLength()) {
            _facilityRelatedPCSRecord = new BaseRecord(reader, -1, mission, facilityRelatedPCS_recordDefinitionFile);
            reader.seek(_facilityRelatedPCSRecord.getRecordEndPosition());
        } else {
            _facilityRelatedPCSRecord = null;
        }
        reader.close();
    }

    public String getProductLevel() {
        return _sceneHeaderRecord.getAttributeString("Scene reference number").trim();
    }

    public final BaseRecord getSceneRecord() {
        return _sceneHeaderRecord;
    }

    public final BaseRecord getFacilityRecord() {
        return _facilityRecord;
    }

    public final BaseRecord getMapProjRecord() {
        return _mapProjRecord;
    }

    public final BaseRecord getPlatformPositionRecord() {
        return _platformPositionRecord;
    }

    public float[] getLatCorners() {
        if(_mapProjRecord == null) return null;

        final double latUL = _mapProjRecord.getAttributeDouble("1st line 1st pixel geodetic latitude");
        final double latUR = _mapProjRecord.getAttributeDouble("1st line last valid pixel geodetic latitude");
        final double latLL = _mapProjRecord.getAttributeDouble("Last line 1st pixel geodetic latitude");
        final double latLR = _mapProjRecord.getAttributeDouble("Last line last valid pixel geodetic latitude");
        return new float[]{(float)latUL, (float)latUR, (float)latLL, (float)latLR};
    }

    public float[] getLonCorners() {
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
        CeosHelper.addMetadata(sphElem, _facilityRecord, "Facility Related");
        CeosHelper.addMetadata(sphElem, _facilityRelatedPCSRecord, "Facility Related PCS");
    }
}
