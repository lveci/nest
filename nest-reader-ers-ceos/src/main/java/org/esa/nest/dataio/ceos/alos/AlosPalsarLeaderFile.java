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
package org.esa.nest.dataio.ceos.alos;

import org.esa.nest.dataio.BinaryFileReader;
import org.esa.nest.dataio.ceos.CEOSLeaderFile;
import org.esa.nest.dataio.ceos.records.BaseRecord;
import org.esa.nest.dataio.ceos.records.BaseSceneHeaderRecord;

import javax.imageio.stream.ImageInputStream;
import java.io.IOException;

/**
 * This class represents a leader file of a product.
 *
 */
class AlosPalsarLeaderFile extends CEOSLeaderFile {

    private final static String mission = "alos";
    private final static String leader_recordDefinitionFile = "leader_file.xml";

    private int productLevel = -1;

    public AlosPalsarLeaderFile(final ImageInputStream stream) throws IOException {
                final BinaryFileReader reader = new BinaryFileReader(stream);

        _leaderFDR = new BaseRecord(reader, -1, mission, leader_recordDefinitionFile);
        reader.seek(_leaderFDR.getRecordEndPosition());
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of data set summary records"); ++i) {
            _sceneHeaderRecord = new BaseSceneHeaderRecord(reader, -1, mission, scene_recordDefinitionFile);
            reader.seek(_sceneHeaderRecord.getRecordEndPosition());
        }
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of map projection data records"); ++i) {
            _mapProjRecord = new BaseRecord(reader, -1, mission, mapproj_recordDefinitionFile);
            reader.seek(_mapProjRecord.getRecordEndPosition());
        }
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of platform pos. data records"); ++i) {
            _platformPositionRecord = new BaseRecord(reader, -1, mission, platformPosition_recordDefinitionFile);
            reader.seek(_platformPositionRecord.getRecordEndPosition());
        }
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of data histograms records"); ++i) {
            _histogramRecord = new BaseRecord(reader, -1, mission, histogram_recordDefinitionFile);
            reader.seek(_histogramRecord.getRecordEndPosition());
        }
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of det. processing records"); ++i) {
            _detailedProcessingRecord = new BaseRecord(reader, -1, mission, detailedProcessing_recordDefinitionFile);
            reader.seek(_detailedProcessingRecord.getRecordEndPosition());
        }
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of attitude data records"); ++i) {
            _attitudeRecord = new BaseRecord(reader, -1, mission, attitude_recordDefinitionFile);
            reader.seek(_attitudeRecord.getRecordEndPosition());
        }
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of radiometric data records"); ++i) {
            _radiometricRecord = new BaseRecord(reader, -1, mission, radiometric_recordDefinitionFile);
            reader.seek(_radiometricRecord.getRecordEndPosition());
        }
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of data quality summary records"); ++i) {
            _dataQualityRecord = new BaseRecord(reader, -1, mission, dataQuality_recordDefinitionFile);
            reader.seek(_dataQualityRecord.getRecordEndPosition());
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

  /*  public AlosPalsarLeaderFile(final ImageInputStream leaderStream)
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
    }        */

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

    public String getProductType() {
        return _sceneHeaderRecord.getAttributeString("Product type specifier");
    }
}
