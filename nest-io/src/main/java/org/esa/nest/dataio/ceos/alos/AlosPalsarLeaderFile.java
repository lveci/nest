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
package org.esa.nest.dataio.ceos.alos;

import org.esa.nest.dataio.BinaryDBReader;
import org.esa.nest.dataio.BinaryFileReader;
import org.esa.nest.dataio.BinaryRecord;
import org.esa.nest.dataio.ceos.CEOSLeaderFile;

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

    private final static org.jdom.Document leaderXML = BinaryDBReader.loadDefinitionFile(mission, leader_recordDefinitionFile);
    private final static org.jdom.Document sceneXML = BinaryDBReader.loadDefinitionFile(mission, scene_recordDefinitionFile);
    private final static org.jdom.Document mapProjXML = BinaryDBReader.loadDefinitionFile(mission, mapproj_recordDefinitionFile);
    private final static org.jdom.Document platformXML = BinaryDBReader.loadDefinitionFile(mission, platformPosition_recordDefinitionFile);
    private final static org.jdom.Document histogramXML = BinaryDBReader.loadDefinitionFile(mission, histogram_recordDefinitionFile);
    private final static org.jdom.Document detailedProcXML = BinaryDBReader.loadDefinitionFile(mission, detailedProcessing_recordDefinitionFile);
    private final static org.jdom.Document attitudeXML = BinaryDBReader.loadDefinitionFile(mission, attitude_recordDefinitionFile);
    private final static org.jdom.Document radiometricXML = BinaryDBReader.loadDefinitionFile(mission, radiometric_recordDefinitionFile);
    private final static org.jdom.Document dataQualityXML = BinaryDBReader.loadDefinitionFile(mission, dataQuality_recordDefinitionFile);
    private final static org.jdom.Document radiometricCompXML = BinaryDBReader.loadDefinitionFile(mission, radiometric_comp_recordDefinitionFile);
    private final static org.jdom.Document facilityXML = BinaryDBReader.loadDefinitionFile(mission, facility_recordDefinitionFile);
        
    public AlosPalsarLeaderFile(final ImageInputStream stream) throws IOException {
                final BinaryFileReader reader = new BinaryFileReader(stream);

        _leaderFDR = new BinaryRecord(reader, -1, leaderXML);
        reader.seek(_leaderFDR.getRecordEndPosition());
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of data set summary records"); ++i) {
            _sceneHeaderRecord = new BinaryRecord(reader, -1, sceneXML);
            reader.seek(_sceneHeaderRecord.getRecordEndPosition());
        }
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of map projection data records"); ++i) {
            _mapProjRecord = new BinaryRecord(reader, -1, mapProjXML);
            reader.seek(_mapProjRecord.getRecordEndPosition());
        }
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of platform pos. data records"); ++i) {
            _platformPositionRecord = new BinaryRecord(reader, -1, platformXML);
            reader.seek(_platformPositionRecord.getRecordEndPosition());
        }
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of data histograms records"); ++i) {
            _histogramRecord = new BinaryRecord(reader, -1, histogramXML);
            reader.seek(_histogramRecord.getRecordEndPosition());
        }
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of det. processing records"); ++i) {
            _detailedProcessingRecord = new BinaryRecord(reader, -1, detailedProcXML);
            reader.seek(_detailedProcessingRecord.getRecordEndPosition());
        }
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of attitude data records"); ++i) {
            _attitudeRecord = new BinaryRecord(reader, -1, attitudeXML);
            reader.seek(_attitudeRecord.getRecordEndPosition());
        }
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of radiometric data records"); ++i) {
            _radiometricRecord = new BinaryRecord(reader, -1, radiometricXML);
            reader.seek(_radiometricRecord.getRecordEndPosition());
        }
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of data quality summary records"); ++i) {
            _dataQualityRecord = new BinaryRecord(reader, -1, dataQualityXML);
            reader.seek(_dataQualityRecord.getRecordEndPosition());
        }
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of rad. compensation records"); ++i) {
            _radiometricCompRecord = new BinaryRecord(reader, -1, radiometricCompXML);
            reader.seek(_radiometricCompRecord.getRecordEndPosition());
        }
        for(int i=0; i < _leaderFDR.getAttributeInt("Number of facility data records"); ++i) {
            _facilityRecord = new BinaryRecord(reader, -1, facilityXML);
            reader.seek(_facilityRecord.getRecordEndPosition());
        }

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

    public String getProductType() {
        return _sceneHeaderRecord.getAttributeString("Product type specifier");
    }
}
