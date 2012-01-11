package org.esa.nest.dataio.binary;

import org.esa.beam.framework.datamodel.MetadataElement;

import java.io.IOException;

public class BinaryRecord {

    private final long _startPos;
    private final BinaryFileReader _reader;

    private BinaryDBReader db;
    private final Integer recordLength;

    public BinaryRecord(final BinaryFileReader reader, final long startPos,
                        final org.jdom.Document recordDefinitionXML)
            throws IOException {
        _reader = reader;
        // reposition start if needed
        if (startPos != -1) {
            _startPos = startPos;
            reader.seek(startPos);
        } else {
            _startPos = reader.getCurrentPos();
        }

        if(_startPos >= reader.getLength()) {
            recordLength = 0;
            return;
        }

        db = new BinaryDBReader(recordDefinitionXML);
        db.readRecord(reader);

        recordLength = getAttributeInt("Record Length");
    }

    public final String getAttributeString(final String name) {
        return db.getAttributeString(name);
    }

    public final Integer getAttributeInt(final String name) {
        return db.getAttributeInt(name);
    }

    public final Double getAttributeDouble(final String name) {
        return db.getAttributeDouble(name);
    }

    public final int getRecordLength() {
        return recordLength;
    }

    public final long getStartPos() {
        return _startPos;
    }

    public BinaryFileReader getReader() {
        return _reader;
    }

    public final BinaryDBReader getBinaryDatabase() {
        return db; 
    }

    public long getRecordEndPosition() {
        return _startPos + recordLength;
    }

    public long getAbsolutPosition(final long relativePosition) {
        return _startPos + relativePosition;
    }

    public void assignMetadataTo(final MetadataElement elem) {
        if(db != null) {
            db.assignMetadataTo(elem);
        }
    }

    protected static MetadataElement createMetadataElement(final String name, final String suffix) {
        final MetadataElement elem;
        if (suffix != null && suffix.trim().length() > 0) {
            elem = new MetadataElement(name + ' ' + suffix.trim());
        } else {
            elem = new MetadataElement(name);
        }
        return elem;
    }
}
