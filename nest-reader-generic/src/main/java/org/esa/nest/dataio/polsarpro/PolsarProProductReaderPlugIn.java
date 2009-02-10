package org.esa.nest.dataio.polsarpro;

import org.esa.beam.dataio.envi.EnviProductReaderPlugIn;
import org.esa.beam.framework.dataio.ProductReader;

public class PolsarProProductReaderPlugIn extends EnviProductReaderPlugIn {

    @Override
    public ProductReader createReaderInstance() {
        return new PolsarProProductReader(this);
    }
}