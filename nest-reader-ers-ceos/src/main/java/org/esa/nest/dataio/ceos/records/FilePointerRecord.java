package org.esa.nest.dataio.ceos.records;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.nest.dataio.ceos.CeosFileReader;
import org.esa.nest.dataio.ceos.IllegalCeosFormatException;

import java.io.IOException;

public class FilePointerRecord extends BaseRecord {

    private static final String IMAGE_FILE_CLASS_CODE = "IM";

    private static String recordDefinitionFile = "file_pointer_record.xml";

    public FilePointerRecord(final CeosFileReader reader, String mission) throws IOException, IllegalCeosFormatException {
        this(reader, mission, -1);
    }

    public FilePointerRecord(final CeosFileReader reader, String mission, final long startPos) throws IOException,
                                                                                      IllegalCeosFormatException {
        super(reader, startPos, mission, recordDefinitionFile);
    }

    public boolean isImageFileRecord() {
        return getAttributeString("File class code").toUpperCase().startsWith(IMAGE_FILE_CLASS_CODE);
    }

    public void assignMetadataTo(final MetadataElement root, final String suffix) {
        final MetadataElement elem = createMetadataElement("FilePointerRecord", suffix);
        root.addElement(elem);

        super.assignMetadataTo(elem);
    }
}
