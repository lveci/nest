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
import org.esa.nest.dataio.ceos.records.FilePointerRecord;

import javax.imageio.stream.FileImageInputStream;
import java.io.File;
import java.io.IOException;


/**
 * This class represents a volume directory file of a product.
 *
 */
public class CEOSVolumeDirectoryFile {

    private BaseRecord _volumeDescriptorRecord;
    private FilePointerRecord[] _filePointerRecords;
    private BaseRecord _textRecord;

    private final static String volume_desc_recordDefinitionFile = "volume_descriptor.xml";
    private final static String text_recordDefinitionFile = "text_record.xml";

    public CEOSVolumeDirectoryFile(final File baseDir, CEOSConstants constants)
            throws IOException, IllegalBinaryFormatException {
        final File volumeFile = CeosHelper.getVolumeFile(baseDir, constants);
        final BinaryFileReader binaryReader = new BinaryFileReader(new FileImageInputStream(volumeFile));
        _volumeDescriptorRecord = new BaseRecord(binaryReader, -1, constants.getMission(), volume_desc_recordDefinitionFile);
        _filePointerRecords = CeosHelper.readFilePointers(_volumeDescriptorRecord, constants.getMission());
        _textRecord = new BaseRecord(binaryReader, -1, constants.getMission(), text_recordDefinitionFile);

        binaryReader.close();
    }

    public BaseRecord getTextRecord() {
        return _textRecord;
    }

    public BaseRecord getVolumeDescriptorRecord() {
        return _volumeDescriptorRecord;
    }

    public String getProductName() {
        return CeosHelper.getProductName(_textRecord);
    }

    public String getProductType() {
        return CeosHelper.getProductType(_textRecord);
    }

    public void assignMetadataTo(final MetadataElement rootElem) {
        CeosHelper.addMetadata(rootElem, _volumeDescriptorRecord, "Volume Descriptor");
        CeosHelper.addMetadata(rootElem, _textRecord, "Text Record");

        int i = 1;
        for(FilePointerRecord fp : _filePointerRecords) {
            CeosHelper.addMetadata(rootElem, fp, "File Pointer Record " + i++);
        }
    }

}