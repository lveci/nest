/*
 * Copyright (C) 2010 Array Systems Computing Inc. http://www.array.ca
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
package org.esa.nest.dataio.ceos;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.nest.dataio.BinaryFileReader;
import org.esa.nest.dataio.IllegalBinaryFormatException;
import org.esa.nest.dataio.ceos.records.BaseRecord;
import org.esa.nest.dataio.ceos.records.BaseSceneHeaderRecord;

import javax.imageio.stream.ImageInputStream;
import java.io.IOException;


public class CEOSLeaderFile {

    protected BaseRecord _leaderFDR = null;
    protected BaseSceneHeaderRecord _sceneHeaderRecord = null;
    protected BaseRecord _platformPositionRecord = null;
    protected BaseRecord _mapProjRecord = null;
    protected BaseRecord _dataQualityRecord = null;
    protected BaseRecord _histogramRecord = null;
    protected BaseRecord _attitudeRecord = null;
    protected BaseRecord _radiometricRecord = null;
    protected BaseRecord _radiometricCompRecord = null;
    protected BaseRecord _detailedProcessingRecord = null;
    protected BaseRecord _facilityRecord = null;

    protected final static String scene_recordDefinitionFile = "scene_record.xml";
    protected final static String platformPosition_recordDefinitionFile = "platform_position_record.xml";
    protected final static String mapproj_recordDefinitionFile = "map_proj_record.xml";
    protected final static String dataQuality_recordDefinitionFile = "data_quality_summary_record.xml";
    protected final static String histogram_recordDefinitionFile = "data_histogram_record.xml";
    protected final static String attitude_recordDefinitionFile = "attitude_record.xml";
    protected final static String radiometric_recordDefinitionFile = "radiometric_record.xml";
    protected final static String radiometric_comp_recordDefinitionFile = "radiometric_compensation_record.xml";
    protected final static String detailedProcessing_recordDefinitionFile = "detailed_processing_record.xml";
    protected final static String facility_recordDefinitionFile = "facility_record.xml";

    protected CEOSLeaderFile() {
    }

    protected CEOSLeaderFile(final ImageInputStream stream, final String mission, final String defnFile)
            throws IOException, IllegalBinaryFormatException {
        final BinaryFileReader reader = new BinaryFileReader(stream);

        _leaderFDR = new BaseRecord(reader, -1, mission, defnFile);
        reader.seek(_leaderFDR.getRecordEndPosition());
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of data set summary records"); ++i) {
            _sceneHeaderRecord = new BaseSceneHeaderRecord(reader, -1, mission, scene_recordDefinitionFile);
            reader.seek(_sceneHeaderRecord.getRecordEndPosition());
        }
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of map projection data records"); ++i) {
            _mapProjRecord = new BaseRecord(reader, -1, mission, mapproj_recordDefinitionFile);
            reader.seek(_mapProjRecord.getRecordEndPosition());
        }
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of data quality summary records"); ++i) {
            _dataQualityRecord = new BaseRecord(reader, -1, mission, dataQuality_recordDefinitionFile);
            reader.seek(_dataQualityRecord.getRecordEndPosition());
        }
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of data histograms records"); ++i) {
            _histogramRecord = new BaseRecord(reader, -1, mission, histogram_recordDefinitionFile);
            reader.seek(_histogramRecord.getRecordEndPosition());
        }
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of det. processing records"); ++i) {
            _detailedProcessingRecord = new BaseRecord(reader, -1, mission, detailedProcessing_recordDefinitionFile);
            reader.seek(_detailedProcessingRecord.getRecordEndPosition());
        }
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of platform pos. data records"); ++i) {
            _platformPositionRecord = new BaseRecord(reader, -1, mission, platformPosition_recordDefinitionFile);
            reader.seek(_platformPositionRecord.getRecordEndPosition());
        }
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of attitude data records"); ++i) {
            _attitudeRecord = new BaseRecord(reader, -1, mission, attitude_recordDefinitionFile);
            reader.seek(_attitudeRecord.getRecordEndPosition());
        }
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of radiometric data records"); ++i) {
            _radiometricRecord = new BaseRecord(reader, -1, mission, radiometric_recordDefinitionFile);
            reader.seek(_radiometricRecord.getRecordEndPosition());
        }
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of rad. compensation records"); ++i) {
            _radiometricCompRecord = new BaseRecord(reader, -1, mission, radiometric_comp_recordDefinitionFile);
            reader.seek(_radiometricCompRecord.getRecordEndPosition());
        }
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of facility data records"); ++i) {
            _facilityRecord = new BaseRecord(reader, -1, mission, facility_recordDefinitionFile);
            reader.seek(_facilityRecord.getRecordEndPosition());
        }

        reader.close();
    }

    public final BaseRecord getSceneRecord() {
        return _sceneHeaderRecord;
    }

    public BaseRecord getMapProjRecord() {
        return _mapProjRecord;
    }

    public BaseRecord getPlatformPositionRecord() {
        return _platformPositionRecord;
    }

    public BaseRecord getHistogramRecord() {
        return _histogramRecord;
    }

    public BaseRecord getRadiometricRecord() {
        return _radiometricRecord;
    }

    public BaseRecord getFacilityRecord() {
        return _facilityRecord;
    }

    public BaseRecord getDetailedProcessingRecord() {
        return _detailedProcessingRecord;
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

    public void addMetadata(MetadataElement sphElem) {

        CeosHelper.addMetadata(sphElem, _leaderFDR, "File Descriptor");
        CeosHelper.addMetadata(sphElem, _sceneHeaderRecord, "Scene Parameters");
        CeosHelper.addMetadata(sphElem, _mapProjRecord, "Map Projection");
        CeosHelper.addMetadata(sphElem, _platformPositionRecord, "Platform Position");
        CeosHelper.addMetadata(sphElem, _dataQualityRecord, "Data Quality");
        CeosHelper.addMetadata(sphElem, _histogramRecord, "Histogram");
        CeosHelper.addMetadata(sphElem, _attitudeRecord, "Attitude");
        CeosHelper.addMetadata(sphElem, _radiometricRecord, "Radiometric");
        CeosHelper.addMetadata(sphElem, _radiometricCompRecord, "Radiometric Compensation");
        CeosHelper.addMetadata(sphElem, _detailedProcessingRecord, "Detailed Processing");
        CeosHelper.addMetadata(sphElem, _facilityRecord, "Facility Related");
    }
}