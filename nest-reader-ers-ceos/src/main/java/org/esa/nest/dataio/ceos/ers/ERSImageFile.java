package org.esa.nest.dataio.ceos.ers;

import org.esa.nest.dataio.BinaryFileReader;
import org.esa.nest.dataio.IllegalBinaryFormatException;
import org.esa.nest.dataio.ceos.CEOSImageFile;
import org.esa.nest.dataio.ceos.records.BaseRecord;
import org.esa.nest.dataio.ceos.records.ImageRecord;

import javax.imageio.stream.ImageInputStream;
import java.io.IOException;

/**
 * This class represents an image file of a CEOS product.
 *
 * @version $Revision: 1.19 $ $Date: 2010-02-10 15:08:32 $
 */
class ERSImageFile extends CEOSImageFile {

    private final static String mission = "ers";
    private final static String image_DefinitionFile = "image_file.xml";
    private final static String image_recordDefinition = "image_record.xml";

    public ERSImageFile(final ImageInputStream imageStream) throws IOException, IllegalBinaryFormatException {
        binaryReader = new BinaryFileReader(imageStream);
        _imageFDR = new BaseRecord(binaryReader, -1, mission, image_DefinitionFile);
        binaryReader.seek(_imageFDR.getAbsolutPosition(_imageFDR.getRecordLength()));
        _imageRecords = new ImageRecord[_imageFDR.getAttributeInt("Number of lines per data set")];
        _imageRecords[0] = createNewImageRecord(0);

        _imageRecordLength = _imageRecords[0].getRecordLength();
        _startPosImageRecords = _imageRecords[0].getStartPos();
        _imageHeaderLength = 12;
    }

    protected ImageRecord createNewImageRecord(final int line) throws IOException, IllegalBinaryFormatException {
        final long pos = _imageFDR.getAbsolutPosition(_imageFDR.getRecordLength()) + (line*_imageRecordLength);
        return new ImageRecord(binaryReader, pos, mission, image_recordDefinition);
    }
}
