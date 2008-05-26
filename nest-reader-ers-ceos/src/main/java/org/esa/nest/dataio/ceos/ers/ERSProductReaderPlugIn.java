package org.esa.nest.dataio.ceos.ers;

import org.esa.beam.framework.dataio.ProductReader;
import org.esa.nest.dataio.ceos.CEOSProductReaderPlugIn;

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
    public ProductReader createReaderInstance() {
        return new ERSProductReader(this);
    }

}
