
package org.esa.nest.dataio.ceos.records;

import org.esa.nest.dataio.BinaryFileReader;
import org.esa.nest.dataio.IllegalBinaryFormatException;
import org.esa.nest.dataio.ceos.CeosDB;

import java.io.IOException;

public class ImageRecord extends BaseRecord {

    public long _imageDataStart;

    public ImageRecord(final BinaryFileReader reader, String mission, String definitionFile)
            throws IOException, IllegalBinaryFormatException {
        this(reader, -1, mission, definitionFile);
    }

    public ImageRecord(final BinaryFileReader reader, final long startPos, String mission, String definitionFile)
            throws IOException, IllegalBinaryFormatException {
        super(reader, startPos, mission, definitionFile);

        _imageDataStart = reader.getCurrentPos();
    }

    public ImageRecord(final BinaryFileReader reader, final long startPos, CeosDB db)
            throws IOException, IllegalBinaryFormatException {
        super(reader, startPos, db);

        _imageDataStart = reader.getCurrentPos();
    }

    public long getImageDataStart() {
        return _imageDataStart;
    }
}
