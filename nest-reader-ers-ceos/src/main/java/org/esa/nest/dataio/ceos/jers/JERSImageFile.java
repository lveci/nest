package org.esa.nest.dataio.ceos.jers;

import org.esa.nest.dataio.ceos.CEOSImageFile;
import org.esa.nest.dataio.ceos.CeosFileReader;
import org.esa.nest.dataio.ceos.IllegalCeosFormatException;
import org.esa.nest.dataio.ceos.records.ImageRecord;
import org.esa.nest.dataio.ceos.records.BaseRecord;

import javax.imageio.stream.ImageInputStream;
import java.io.IOException;


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