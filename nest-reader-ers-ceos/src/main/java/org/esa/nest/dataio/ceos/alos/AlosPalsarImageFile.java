package org.esa.nest.dataio.ceos.alos;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.nest.dataio.BinaryFileReader;
import org.esa.nest.dataio.IllegalBinaryFormatException;
import org.esa.nest.dataio.ceos.CEOSImageFile;
import org.esa.nest.dataio.ceos.records.BaseRecord;
import org.esa.nest.dataio.ceos.records.ImageRecord;

import javax.imageio.stream.ImageInputStream;
import java.io.IOException;


class AlosPalsarImageFile extends CEOSImageFile {

    private BaseRecord _processedData = null;

    private final static String mission = "alos";
    private final static String image_DefinitionFile = "image_file.xml";
    private final static String image_recordDefinition = "image_record.xml";
    private final static String processedData_recordDefinition = "processed_data_record.xml";
    private final String imageFileName;
    private final int slantRangeToFirstSample;

    public AlosPalsarImageFile(final ImageInputStream imageStream, int productLevel, String fileName)
            throws IOException, IllegalBinaryFormatException {
        imageFileName = fileName.toUpperCase();
        binaryReader = new BinaryFileReader(imageStream);
        _imageFDR = new BaseRecord(binaryReader, -1, mission, image_DefinitionFile);
        binaryReader.seek(_imageFDR.getAbsolutPosition(_imageFDR.getRecordLength()));
        _imageRecords = new ImageRecord[_imageFDR.getAttributeInt("Number of lines per data set")];

        _imageRecords[0] = new ImageRecord(binaryReader, -1, mission, image_recordDefinition);
        _imageRecordLength = _imageRecords[0].getRecordLength();
        _startPosImageRecords = _imageRecords[0].getStartPos();
        _imageHeaderLength = 412;
        slantRangeToFirstSample = _imageRecords[0].getAttributeInt("Slant range to 1st data sample");

        if(productLevel == AlosPalsarConstants.LEVEL1_5) {
            binaryReader.seek(_imageFDR.getAbsolutPosition(_imageRecordLength * _imageRecords.length));
            _processedData = new BaseRecord(binaryReader, -1, mission, processedData_recordDefinition);
            _imageHeaderLength = 192;
        }
    }

    public int getSlantRangeToFirstSample() {
        return slantRangeToFirstSample;
    }

    public String getPolarization() {
        if(imageFileName.startsWith("IMG-") && imageFileName.length() > 6) {
            String pol = imageFileName.substring(4, 6);
            if(pol.equals("HH") || pol.equals("VV") || pol.equals("HV") || pol.equals("VH")) {
                return pol;
            } else if(_imageRecords[0] != null) {
                final int tx = _imageRecords[0].getAttributeInt("Transmitted polarization");
                final int rx = _imageRecords[0].getAttributeInt("Received polarization");
                if(tx == 1) pol = "V";
                else pol = "H";

                if(rx == 1) pol += "V";
                else pol += "H";

                return pol;
            }
        }
        return "";
    }

    @Override
    public void assignMetadataTo(MetadataElement rootElem, int count) {
        final MetadataElement imgDescElem = new MetadataElement("Image Descriptor " + count);
        _imageFDR.assignMetadataTo(imgDescElem);
        rootElem.addElement(imgDescElem);

        if(_imageRecords[0] != null) {
            final MetadataElement imgRecElem = new MetadataElement("Image Record ");
            _imageRecords[0].assignMetadataTo(imgRecElem);
            imgDescElem.addElement(imgRecElem);
        }

        if(_processedData != null) {
            final MetadataElement procDataMetadata = new MetadataElement("Processed Data " + count);
            _processedData.assignMetadataTo(procDataMetadata);
            rootElem.addElement(procDataMetadata);    
        }
    }

}