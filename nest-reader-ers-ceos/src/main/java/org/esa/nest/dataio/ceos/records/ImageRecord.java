/*
 * $Id: ImageRecord.java,v 1.5 2008-08-19 20:53:18 lveci Exp $
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
