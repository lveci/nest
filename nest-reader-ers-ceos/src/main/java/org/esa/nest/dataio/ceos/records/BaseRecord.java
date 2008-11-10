package org.esa.nest.dataio.ceos.records;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.nest.dataio.ceos.CeosDB;
import org.esa.nest.dataio.ceos.CeosFileReader;
import org.esa.nest.dataio.ceos.IllegalCeosFormatException;

import java.io.IOException;

public class BaseRecord {

    private final long _startPos;
    private final CeosFileReader _reader;

    private CeosDB db;
    private static String ceosDBPath = "org/esa/nest/ceosFormatDB";
    private final int recordLength;

    public BaseRecord(final CeosFileReader reader, final long startPos,
                      final String mission, final String recordDefinitionFileName) throws
                                                                        IOException,
                                                                        IllegalCeosFormatException {
        _reader = reader;
        // reposition start if needed
        if (startPos != -1) {
            _startPos = startPos;
            reader.seek(startPos);
        } else {
            _startPos = reader.getCurrentPos();
        }

        String resPath = ceosDBPath + '/' + mission + '/' + recordDefinitionFileName;

        db = new CeosDB(resPath);
        db.readRecord(reader);

        recordLength = getAttributeInt("Record Length");
    }

    /*
    Quick read using exiting ceosDB for ImageRecord
     */
    public BaseRecord(final CeosFileReader reader, final long startPos, CeosDB db) throws
                                                                        IOException,
                                                                        IllegalCeosFormatException {
        _reader = reader;
        // reposition start if needed
        if (startPos != -1) {
            _startPos = startPos;
            reader.seek(startPos);
        } else {
            _startPos = reader.getCurrentPos();
        }

        db.readRecord(reader);

        recordLength = getAttributeInt("Record Length");
    }

    public final String getAttributeString(final String name) {
        return db.getAttributeString(name);
    }

    public final int getAttributeInt(final String name) {
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

    public CeosFileReader getReader() {
        return _reader;
    }

    public final CeosDB getCeosDatabase() {
        return db; 
    }

    public long getAbsolutPosition(final long relativePosition) {
        return _startPos + relativePosition;
    }

    public void assignMetadataTo(final MetadataElement elem) {
        db.assignMetadataTo(elem);
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
