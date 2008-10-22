package org.esa.nest.dataio.ceos.alos;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.nest.dataio.ceos.CeosFileReader;
import org.esa.nest.dataio.ceos.CeosHelper;
import org.esa.nest.dataio.ceos.IllegalCeosFormatException;
import org.esa.nest.dataio.ceos.records.BaseRecord;
import org.esa.nest.dataio.ceos.records.FilePointerRecord;

import javax.imageio.stream.FileImageInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;


/**
 * This class represents a volume directory file of Palsar product.
 *
 */
class AlosPalsarVolumeDirectoryFile {

    private CeosFileReader _ceosReader;
    private BaseRecord _volumeDescriptorRecord;
    private FilePointerRecord[] _filePointerRecords;
    private BaseRecord _textRecord;

    private static String mission = "alos";
    private static String volume_desc_recordDefinitionFile = "volume_descriptor.xml";
    private static String text_recordDefinitionFile = "text_record.xml";

    public AlosPalsarVolumeDirectoryFile(final File baseDir) throws IOException,
                                                                IllegalCeosFormatException {
        final File volumeFile = CeosHelper.getVolumeFile(baseDir);
        _ceosReader = new CeosFileReader(new FileImageInputStream(volumeFile));
        _volumeDescriptorRecord = new BaseRecord(_ceosReader, -1, mission, volume_desc_recordDefinitionFile);
        _filePointerRecords = CeosHelper.readFilePointers(_volumeDescriptorRecord, mission);
        _textRecord = new BaseRecord(_ceosReader, -1, mission, text_recordDefinitionFile);
    }

    public BaseRecord getTextRecord() {
        return _textRecord;
    }

    public BaseRecord getVolumeDescriptorRecord() {
        return _volumeDescriptorRecord;
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

    public String getProductType() {
        return CeosHelper.getProductType(_textRecord);
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