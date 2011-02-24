package org.esa.nest.dataio.ceos.records;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.nest.dataio.BinaryFileReader;

import java.io.IOException;

public class FilePointerRecord extends BaseRecord {

    private static final String IMAGE_FILE_CLASS_CODE = "IM";

    private final static String recordDefinitionFile = "file_pointer_record.xml";

    public FilePointerRecord(final BinaryFileReader reader, String mission) throws IOException {
        this(reader, mission, -1);
    }

    public FilePointerRecord(final BinaryFileReader reader, String mission, final long startPos) throws IOException {
        super(reader, startPos, mission, recordDefinitionFile);
    }

    public void assignMetadataTo(final MetadataElement root, final String suffix) {
        final MetadataElement elem = createMetadataElement("FilePointerRecord", suffix);
        root.addElement(elem);

        super.assignMetadataTo(elem);
    }
}
