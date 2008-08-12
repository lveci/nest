package org.esa.nest.dataio.ceos.jers;

import org.esa.nest.dataio.ceos.CEOSImageFile;
import org.esa.nest.dataio.ceos.CeosFileReader;
import org.esa.nest.dataio.ceos.IllegalCeosFormatException;
import org.esa.nest.dataio.ceos.records.ImageRecord;
import org.esa.nest.dataio.ceos.records.BaseRecord;

import javax.imageio.stream.ImageInputStream;
import java.io.IOException;

/*
 * $Id: JERSImageFile.java,v 1.4 2008-08-12 19:49:23 lveci Exp $
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


/**
 * This class represents an image file of an Avnir-2 product.
 *
 * @author Marco Peters
 * @version $Revision: 1.4 $ $Date: 2008-08-12 19:49:23 $
 */
class JERSImageFile extends CEOSImageFile {

    private final BaseRecord _imageFDR;
    private final int _imageNumber;

    private static String mission = "jers";
    private static String image_DefinitionFile = "image_file.xml";
    private static String image_recordDefinition = "image_record.xml";

    public JERSImageFile(final ImageInputStream imageStream) throws IOException,
                                                                      IllegalCeosFormatException {
        _ceosReader = new CeosFileReader(imageStream);
        _imageFDR = new BaseRecord(_ceosReader, -1, mission, image_DefinitionFile);
        _ceosReader.seek(_imageFDR.getAbsolutPosition(_imageFDR.getRecordLength()));
        _imageRecords = new ImageRecord[_imageFDR.getAttributeInt("Number of lines per data set")];
        _imageRecords[0] = new ImageRecord(_ceosReader, -1, mission, image_recordDefinition);
        _imageRecordLength = _imageRecords[0].getRecordLength();
        _startPosImageRecords = _imageRecords[0].getStartPos();
        _imageNumber = 1;
    }

    public String getBandName() {
        return JERSConstants.BANDNAME_PREFIX + _imageNumber;
    }

    public String getBandDescription() {
        return "";
    }

    public int getBandIndex() {
        return _imageNumber;
    }

    public int getRasterWidth() {
        return _imageFDR.getAttributeInt("Number of pixels per line per SAR channel");//getNumImagePixelsPerLine();
    }

    public int getRasterHeight() {
        return _imageFDR.getAttributeInt("Number of lines per data set");
    }

    public static String getGeophysicalUnit() {
        return JERSConstants.GEOPHYSICAL_UNIT;
    }



 /*   public int getTotalMillisInDayOfLine(final int y) throws IOException,
                                                             IllegalCeosFormatException {
        return getImageRecord(y).getScanStartTimeMillisAtDay();
    }

    public int getMicrosecondsOfLine(final int y) throws IOException,
                                                         IllegalCeosFormatException {
        return getImageRecord(y).getScanStartTimeMicros();
    } */


}