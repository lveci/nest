package org.esa.nest.dataio.ceos.ers;

import org.esa.nest.dataio.ceos.CeosFileReader;
import org.esa.nest.dataio.ceos.CeosHelper;
import org.esa.nest.dataio.ceos.IllegalCeosFormatException;
import org.esa.nest.dataio.ceos.records.FilePointerRecord;
import org.esa.nest.dataio.ceos.records.BaseRecord;
import org.esa.beam.framework.datamodel.MetadataElement;

import javax.imageio.stream.FileImageInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/*
 * $Id: ERSVolumeDirectoryFile.java,v 1.4 2008-06-27 19:35:52 lveci Exp $
 *
 * Copyright (C) 2002 by Brockmann Consult (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

/**
 * This class represents a volume directory file of Palsar product.
 *
 */
class ERSVolumeDirectoryFile {

    private CeosFileReader _ceosReader;
    private BaseRecord _volumeDescriptorRecord;
    private FilePointerRecord[] _filePointerRecords;
    private BaseRecord _textRecord;

    private static String mission = "ers";
    private static String volume_desc_recordDefinitionFile = "volume_descriptor.xml";
    private static String text_recordDefinitionFile = "text_record.xml";

    public ERSVolumeDirectoryFile(final File baseDir) throws IOException,
                                                                IllegalCeosFormatException {
        final File volumeFile = CeosHelper.getVolumeFile(baseDir);
        _ceosReader = new CeosFileReader(new FileImageInputStream(volumeFile));
        _volumeDescriptorRecord = new BaseRecord(_ceosReader, -1, mission, volume_desc_recordDefinitionFile);
        _filePointerRecords = CeosHelper.readFilePointers(_volumeDescriptorRecord);
        _textRecord = new BaseRecord(_ceosReader, -1, mission, text_recordDefinitionFile);
    }

    public static String getLeaderFileName() {
        return "LEA_01.001";
    }

    public static String getTrailerFileName() {
        return "NUL_DAT.001";
    }

    public String[] getImageFileNames() throws IOException,
                                               IllegalCeosFormatException {
        final ArrayList list = new ArrayList();
        for (final FilePointerRecord filePointerRecord : _filePointerRecords) {
            if (filePointerRecord.isImageFileRecord()) {
                final String fileID = filePointerRecord.getAttributeString("File ID");
                list.add(CeosHelper.getImageFileName(_textRecord, fileID.substring(15)));
            }
        }
        list.add("DAT_01.001");
        return (String[]) list.toArray(new String[list.size()]);
    }

    public void close() throws IOException {
        _ceosReader.close();
        _ceosReader = null;
        for (int i = 0; i < _filePointerRecords.length; i++) {
            _filePointerRecords[i] = null;
        }
        _filePointerRecords = null;
        _volumeDescriptorRecord = null;
        _textRecord = null;
    }

    public String getProductName() {
        return CeosHelper.getProductName(_textRecord);
    }

    public void assignMetadataTo(final MetadataElement rootElem) {
        MetadataElement metadata = new MetadataElement("Volume Descriptor");
        _volumeDescriptorRecord.assignMetadataTo(metadata);
        rootElem.addElement(metadata);

        metadata = new MetadataElement("Text Record");
        _textRecord.assignMetadataTo(metadata);
        rootElem.addElement(metadata);

        int i = 1;
        for(FilePointerRecord fp : _filePointerRecords) {
            metadata = new MetadataElement("File Pointer Record " + i++);
            fp.assignMetadataTo(metadata);
            rootElem.addElement(metadata);
        }
    }

}
