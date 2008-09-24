
package org.esa.nest.dataio.ceos.records;

import org.esa.nest.dataio.ceos.CeosDB;
import org.esa.nest.dataio.ceos.CeosFileReader;
import org.esa.nest.dataio.ceos.IllegalCeosFormatException;

import java.io.IOException;

public class ImageRecord extends BaseRecord {

    public long _imageDataStart;

    public ImageRecord(final CeosFileReader reader, String mission, String definitionFile)
            throws IOException, IllegalCeosFormatException {
        this(reader, -1, mission, definitionFile);
    }

    public ImageRecord(final CeosFileReader reader, final long startPos, String mission, String definitionFile)
            throws IOException, IllegalCeosFormatException {
        super(reader, startPos, mission, definitionFile);

        _imageDataStart = reader.getCurrentPos();
    }

    public ImageRecord(final CeosFileReader reader, final long startPos, CeosDB db)
            throws IOException, IllegalCeosFormatException {
        super(reader, startPos, db);

        _imageDataStart = reader.getCurrentPos();
    }

    public long getImageDataStart() {
        return _imageDataStart;
    }
}
