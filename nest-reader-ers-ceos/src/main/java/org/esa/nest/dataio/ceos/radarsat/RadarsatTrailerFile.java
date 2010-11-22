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
package org.esa.nest.dataio.ceos.radarsat;

import org.esa.nest.dataio.IllegalBinaryFormatException;
import org.esa.nest.dataio.BinaryFileReader;
import org.esa.nest.dataio.ceos.CEOSLeaderFile;
import org.esa.nest.dataio.ceos.records.BaseRecord;
import org.esa.nest.dataio.ceos.records.BaseSceneHeaderRecord;

import javax.imageio.stream.ImageInputStream;
import java.io.IOException;


class RadarsatTrailerFile extends CEOSLeaderFile {

    private final static String mission = "radarsat";
    private final static String trailer_recordDefinitionFile = "trailer_file.xml";

    public RadarsatTrailerFile(final ImageInputStream stream) throws IOException, IllegalBinaryFormatException {
        final BinaryFileReader reader = new BinaryFileReader(stream);

        _leaderFDR = new BaseRecord(reader, -1, mission, trailer_recordDefinitionFile);
        reader.seek(_leaderFDR.getRecordEndPosition());
        int num = _leaderFDR.getAttributeInt("Number of data set summary records");
        for(int i=0; i < num; ++i) {
            _sceneHeaderRecord = new BaseSceneHeaderRecord(reader, -1, mission, scene_recordDefinitionFile);
            reader.seek(_sceneHeaderRecord.getRecordEndPosition());
        }
        num = _leaderFDR.getAttributeInt("Number of map projection data records");
        for(int i=0; i < num; ++i) {
            _mapProjRecord = new BaseRecord(reader, -1, mission, mapproj_recordDefinitionFile);
            reader.seek(_mapProjRecord.getRecordEndPosition());
        }
        num = _leaderFDR.getAttributeInt("Number of data quality summary records");
        for(int i=0; i < num; ++i) {
            _dataQualityRecord = new BaseRecord(reader, -1, mission, dataQuality_recordDefinitionFile);
            reader.seek(_dataQualityRecord.getRecordEndPosition());
        }
        num = _leaderFDR.getAttributeInt("Number of data histograms records");
        for(int i=0; i < num; ++i) {
            _histogramRecord = new BaseRecord(reader, -1, mission, histogram_recordDefinitionFile);
            reader.seek(_histogramRecord.getRecordEndPosition());
        }
        num = _leaderFDR.getAttributeInt("Number of det. processing records");
        for(int i=0; i < num; ++i) {
            _detailedProcessingRecord = new BaseRecord(reader, -1, mission, detailedProcessing_recordDefinitionFile);
            reader.seek(_detailedProcessingRecord.getRecordEndPosition());
        }
        num = _leaderFDR.getAttributeInt("Number of platform pos. data records");
        for(int i=0; i < num; ++i) {
            _platformPositionRecord = new BaseRecord(reader, -1, mission, platformPosition_recordDefinitionFile);
            reader.seek(_platformPositionRecord.getRecordEndPosition());
        }
        num = _leaderFDR.getAttributeInt("Number of attitude data records");
        for(int i=0; i < num; ++i) {
            _attitudeRecord = new BaseRecord(reader, -1, mission, attitude_recordDefinitionFile);
            reader.seek(_attitudeRecord.getRecordEndPosition());
        }
        num = _leaderFDR.getAttributeInt("Number of radiometric data records");
        for(int i=0; i < num; ++i) {
            _radiometricRecord = new BaseRecord(reader, -1, mission, radiometric_recordDefinitionFile);
            reader.seek(_radiometricRecord.getRecordEndPosition());
        }
        num = _leaderFDR.getAttributeInt("Number of rad. compensation records");
        for(int i=0; i < num; ++i) {
            _radiometricCompRecord = new BaseRecord(reader, -1, mission, radiometric_comp_recordDefinitionFile);
            reader.seek(_radiometricCompRecord.getRecordEndPosition());
        }
        num = _leaderFDR.getAttributeInt("Number of facility data records");
        for(int i=0; i < num; ++i) {
            _facilityRecord = new BaseRecord(reader, -1, mission, facility_recordDefinitionFile);
            reader.seek(_facilityRecord.getRecordEndPosition());
        }

        reader.close();
    }
}