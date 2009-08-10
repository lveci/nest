package org.esa.nest.dataio.ceos.ers;

import org.esa.beam.framework.dataio.DecodeQualification;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.nest.dataio.ceos.CEOSProductReaderPlugIn;

import java.io.File;

/**
 * The ReaderPlugIn for ERS CEOS products.
 *
 */
public class ERSProductReaderPlugIn extends CEOSProductReaderPlugIn {

    public ERSProductReaderPlugIn() {
        constants = new ERSConstants();
    }

    /**
     * Creates an instance of the actual product reader class. This method should never return <code>null</code>.
     *
     * @return a new reader instance, never <code>null</code>
     */
    @Override
    public ProductReader createReaderInstance() {
        return new ERSProductReader(this);
    }

    @Override
    protected DecodeQualification checkProductQualification(File file) {
        if(file.getName().toUpperCase().startsWith(constants.getVolumeFilePrefix())) {
            final ERSProductReader reader = new ERSProductReader(this);
            return reader.checkProductQualification(file);
        }
        return DecodeQualification.UNABLE;
    }

}
