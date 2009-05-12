package org.esa.nest.dataio.ceos.records;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.nest.dataio.BinaryFileReader;
import org.esa.nest.dataio.IllegalBinaryFormatException;
import org.esa.nest.dataio.ceos.CeosDB;
import org.esa.nest.util.ResourceUtils;

import java.io.File;
import java.io.IOException;

public class BaseRecord {

    private final long _startPos;
    private final BinaryFileReader _reader;

    private CeosDB db;
    private final static String sep = File.separator;
    private final static String ceosDBPath = sep+"org"+sep+"esa"+sep+"nest"+sep+"ceos_db";
    private final int recordLength;

    public BaseRecord(final BinaryFileReader reader, final long startPos,
                      final String mission, final String recordDefinitionFileName)
            throws IOException, IllegalBinaryFormatException {
        _reader = reader;
        // reposition start if needed
        if (startPos != -1) {
            _startPos = startPos;
            reader.seek(startPos);
        } else {
            _startPos = reader.getCurrentPos();
        }

        final String resPath = mission + sep + recordDefinitionFileName;
        db = new CeosDB(getResFile(resPath).getAbsolutePath());
        db.readRecord(reader);

        recordLength = getAttributeInt("Record Length");
    }

    /*
    Quick read using exiting ceosDB for ImageRecord
     */
    public BaseRecord(final BinaryFileReader reader, final long startPos, CeosDB theDB)
            throws IOException, IllegalBinaryFormatException {
        _reader = reader;
        // reposition start if needed
        if (startPos != -1) {
            _startPos = startPos;
            reader.seek(startPos);
        } else {
            _startPos = reader.getCurrentPos();
        }
        
        db = theDB;
        db.readRecord(reader);

        recordLength = getAttributeInt("Record Length");
    }

    private static File getResFile(String fileName) {
        final String homeUrl = ResourceUtils.findHomeFolder().getAbsolutePath();
        final String path = homeUrl + sep + "res" + sep + "ceos_db" + sep + fileName;
        return new File(path);
    }

    public final String getAttributeString(final String name) {
        return db.getAttributeString(name);
    }

    public final int getAttributeInt(final String name) {
        return db.getAttributeInt(name);
    }

    public final double getAttributeDouble(final String name) {
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
