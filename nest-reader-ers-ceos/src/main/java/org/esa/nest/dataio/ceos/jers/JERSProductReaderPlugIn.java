package org.esa.nest.dataio.ceos.jers;

import org.esa.beam.framework.dataio.DecodeQualification;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.nest.dataio.ceos.CEOSProductReaderPlugIn;

import java.io.File;

/**
 * The ReaderPlugIn for JERS products.
 *
 */
public class JERSProductReaderPlugIn extends CEOSProductReaderPlugIn {

    public JERSProductReaderPlugIn() {
        constants = new JERSConstants();
    }

    /**
     * Creates an instance of the actual product reader class. This method should never return <code>null</code>.
     *
     * @return a new reader instance, never <code>null</code>
     */
    @Override
    public ProductReader createReaderInstance() {
        return new JERSProductReader(this);
    }

    @Override
    protected DecodeQualification checkProductQualification(File file) {
        if (file.getName().toUpperCase().startsWith(constants.getVolumeFilePrefix())) {
            final JERSProductReader reader = new JERSProductReader(this);
            return reader.checkProductQualification(file);
        }
        return DecodeQualification.UNABLE;
    }
}