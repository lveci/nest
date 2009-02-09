package org.esa.nest.dataio.ceos.ers;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.nest.dataio.BinaryFileReader;
import org.esa.nest.dataio.IllegalBinaryFormatException;
import org.esa.nest.dataio.ceos.CeosHelper;
import org.esa.nest.dataio.ceos.records.BaseRecord;
import org.esa.nest.dataio.ceos.records.FilePointerRecord;

import javax.imageio.stream.FileImageInputStream;
import java.io.File;
import java.io.IOException;

/**
 * This class represents a volume directory file of ERS product.
 *
 */
class ERSVolumeDirectoryFile {

    private BinaryFileReader _binaryReader;
    private BaseRecord _volumeDescriptorRecord;
    private FilePointerRecord[] _filePointerRecords;
    private BaseRecord _textRecord;

    private final static String mission = "ers";
    private final static String volume_desc_recordDefinitionFile = "volume_descriptor.xml";
    private final static String text_recordDefinitionFile = "text_record.xml";

    public ERSVolumeDirectoryFile(final File baseDir) throws IOException, IllegalBinaryFormatException {
        final File volumeFile = CeosHelper.getVolumeFile(baseDir);
        _binaryReader = new BinaryFileReader(new FileImageInputStream(volumeFile));
        _volumeDescriptorRecord = new BaseRecord(_binaryReader, -1, mission, volume_desc_recordDefinitionFile);
        _filePointerRecords = CeosHelper.readFilePointers(_volumeDescriptorRecord, mission);
        _textRecord = new BaseRecord(_binaryReader, -1, mission, text_recordDefinitionFile);
    }

    public BaseRecord getTextRecord() {
        return _textRecord;
    }

    public BaseRecord getVolumeDescriptorRecord() {
        return _volumeDescriptorRecord;
    }

    public static String getLeaderFileName() {
        return "LEA_01.001";
    }

    public static String getTrailerFileName() {
        return "NUL_DAT.001";
    }

    public void close() throws IOException {
        _binaryReader.close();
        _binaryReader = null;
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
