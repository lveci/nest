package org.esa.nest.dataio.ceos.alos;

import org.esa.nest.dataio.ceos.CEOSImageFile;
import org.esa.nest.dataio.ceos.CeosFileReader;
import org.esa.nest.dataio.ceos.IllegalCeosFormatException;
import org.esa.nest.dataio.ceos.records.ImageRecord;
import org.esa.nest.dataio.ceos.records.BaseRecord;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.ProductData;

import javax.imageio.stream.ImageInputStream;
import java.io.IOException;

import com.bc.ceres.core.ProgressMonitor;


class AlosPalsarImageFile extends CEOSImageFile {

    private final int _imageNumber;

    private static String mission = "alos";
    private static String image_DefinitionFile = "image_file.xml";
    private static String image_recordDefinition = "image_record.xml";

    public AlosPalsarImageFile(final ImageInputStream imageStream) throws IOException,
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
        return AlosPalsarConstants.BANDNAME_PREFIX + _imageNumber;
    }

    public String getBandDescription() {
        return "";
    }

    public int getBandIndex() {
        return _imageNumber;
    }

    public static String getGeophysicalUnit() {
        return AlosPalsarConstants.GEOPHYSICAL_UNIT;
    }

}