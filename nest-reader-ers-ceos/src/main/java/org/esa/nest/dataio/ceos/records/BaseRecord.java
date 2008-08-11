/*
 * $Id: BaseRecord.java,v 1.5 2008-08-10 21:33:55 lveci Exp $
 *
 * Copyright (C) 2002 by Brockmann Consult (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.esa.nest.dataio.ceos.records;

import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.nest.dataio.ceos.CeosDB;
import org.esa.nest.dataio.ceos.CeosFileReader;
import org.esa.nest.dataio.ceos.IllegalCeosFormatException;
import org.esa.nest.util.DatUtils;

import java.io.File;
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
        return getStartPos() + relativePosition;
    }

    public void assignMetadataTo(final MetadataElement elem) {

        Map metadata = db.getMetadataElement();
        Set keys = metadata.keySet();                           // The set of keys in the map.
        for (Object key : keys) {
            Object value = metadata.get(key);                   // Get the value for that key.
            if (value == null) continue;

            if(value instanceof String)
                elem.setAttributeString((String)key, value.toString());
            else if(value instanceof Integer)
                elem.setAttributeInt((String)key, (Integer)value);
            else 
                elem.setAttributeString((String)key, String.valueOf(value));
        }
    }

    public static void addIntAttributte(final MetadataElement elem, final String name, final int value) {
        final ProductData data = ProductData.createInstance(ProductData.TYPE_INT8);
        data.setElemInt(value);
        elem.addAttribute(new MetadataAttribute(name, data, true));
    }

    protected final long[] readLongs(final int numLongs, final int relativePosition) throws
                                                                                     IOException,
                                                                                     IllegalCeosFormatException {
        _reader.seek(getAbsolutPosition(relativePosition));
        final long[] coeffs = new long[numLongs];
        _reader.readB8(coeffs);
        return coeffs;
    }

    protected final long[][] readLongs(final int numArrays, final int numLongs, final int relativePosition) throws
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
