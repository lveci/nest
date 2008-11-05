package org.esa.nest.dataio.ceos.records;

import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.nest.dataio.ceos.CeosDB;
import org.esa.nest.dataio.ceos.CeosFileReader;
import org.esa.nest.dataio.ceos.IllegalCeosFormatException;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class BaseRecord {

    private final long _startPos;
    private final CeosFileReader _reader;

    private CeosDB db;
    private static String ceosDBPath = "org/esa/nest/ceosFormatDB";

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
    }

    public String getAttributeString(String name) {
        return db.getAttributeString(name);
    }

    public int getAttributeInt(String name) {
        return db.getAttributeInt(name);
    }

    public Double getAttributeDouble(String name) {
        return db.getAttributeDouble(name);
    }

    public int getRecordLength() {
        return getAttributeInt("Record Length");
    }

    public long getStartPos() {
        return _startPos;
    }

    public CeosFileReader getReader() {
        return _reader;
    }

    public CeosDB getCeosDatabase() {
        return db; 
    }

    public long getAbsolutPosition(final long relativePosition) {
        return _startPos + relativePosition;
    }

    public void assignMetadataTo(final MetadataElement elem) {
        db.assignMetadataTo(elem);
    }

    protected final long[] readLongs(final int numLongs, final int relativePosition) throws
                                                                                     IOException,
                                                                                     IllegalCeosFormatException {
        _reader.seek(getAbsolutPosition(relativePosition));
        final long[] coeffs = new long[numLongs];
        _reader.readB8(coeffs);
        return coeffs;
    }

    protected final long[][] readLongs(final int numArrays, final int numLongs,
                                       final int relativePosition) throws
                                                                         IOException,
                                                                         IllegalCeosFormatException {
        final long[][] longs = new long[numArrays][];
        _reader.seek(getAbsolutPosition(relativePosition));
        for (int i = 0; i < longs.length; i++) {
            final long[] coeffs = new long[numLongs];
            _reader.readB8(coeffs);
            longs[i] = coeffs;
        }
        return longs;
    }

    protected static MetadataElement createMetadataElement(String name, String suffix) {
        final MetadataElement elem;
        if (suffix != null && suffix.trim().length() > 0) {
            elem = new MetadataElement(name + ' ' + suffix.trim());
        } else {
            elem = new MetadataElement(name);
        }
        return elem;
    }
}
