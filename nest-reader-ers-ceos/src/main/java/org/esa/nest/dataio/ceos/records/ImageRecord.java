
package org.esa.nest.dataio.ceos.records;

import org.esa.nest.dataio.BinaryFileReader;

import java.io.IOException;

public class ImageRecord extends BaseRecord {

    public ImageRecord(final BinaryFileReader reader, String mission, String definitionFile)
            throws IOException {
        this(reader, -1, mission, definitionFile);
    }

    public ImageRecord(final BinaryFileReader reader, final long startPos, String mission, String definitionFile)
            throws IOException {
        super(reader, startPos, mission, definitionFile);
    }
}
