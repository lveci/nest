package org.esa.nest.dataio.envi;

import org.esa.beam.dataio.envi.EnviProductReaderPlugIn;
import org.esa.beam.framework.dataio.ProductReader;

public class NestEnviProductReaderPlugIn extends EnviProductReaderPlugIn {

    
    public ProductReader createReaderInstance() {
        return new NestEnviProductReader(this);
    }
}