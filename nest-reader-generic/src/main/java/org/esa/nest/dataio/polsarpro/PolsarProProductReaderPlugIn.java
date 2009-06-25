package org.esa.nest.dataio.polsarpro;

import org.esa.beam.dataio.envi.EnviProductReaderPlugIn;
import org.esa.beam.framework.dataio.DecodeQualification;
import org.esa.beam.framework.dataio.ProductReader;

import java.io.File;

public class PolsarProProductReaderPlugIn extends EnviProductReaderPlugIn {

    @Override
    public ProductReader createReaderInstance() {
        return new PolsarProProductReader(this);
    }

    @Override
    public DecodeQualification getDecodeQualification(Object input) {
        if (input instanceof File) {
            final File folder = (File) input;
            if(folder.isDirectory()) {
                DecodeQualification folderQualification = DecodeQualification.UNABLE;
                for(File file : folder.listFiles()) {
                    final DecodeQualification fileQualification = super.getDecodeQualification(file);
                    if(fileQualification != DecodeQualification.UNABLE)
                        return fileQualification;
                }
                return folderQualification;
            } else {
                return super.getDecodeQualification(input);
            }
        } 

        return super.getDecodeQualification(input);
    }
}