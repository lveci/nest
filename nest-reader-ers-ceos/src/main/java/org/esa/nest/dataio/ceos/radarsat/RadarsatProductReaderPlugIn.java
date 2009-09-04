package org.esa.nest.dataio.ceos.radarsat;

import org.esa.beam.framework.dataio.DecodeQualification;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.nest.dataio.ceos.CEOSProductReaderPlugIn;

import java.io.File;

/**
 * The ReaderPlugIn for Radarsat 1 products.
 *
 */
public class RadarsatProductReaderPlugIn extends CEOSProductReaderPlugIn {

    public RadarsatProductReaderPlugIn() {
        constants = new RadarsatConstants();
    }

    /**
     * Creates an instance of the actual product reader class. This method should never return <code>null</code>.
     *
     * @return a new reader instance, never <code>null</code>
     */
    @Override
    public ProductReader createReaderInstance() {
        return new RadarsatProductReader(this);
    }

    @Override
    protected DecodeQualification checkProductQualification(File file) {
        if(file.getName().toUpperCase().startsWith(constants.getVolumeFilePrefix())) {
            RadarsatProductReader reader = new RadarsatProductReader(this);
            return reader.checkProductQualification(file);
        }
        return DecodeQualification.UNABLE;
    }
}