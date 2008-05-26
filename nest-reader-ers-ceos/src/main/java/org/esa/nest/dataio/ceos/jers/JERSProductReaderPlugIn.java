package org.esa.nest.dataio.ceos.jers;

import org.esa.beam.framework.dataio.ProductReader;
import org.esa.nest.dataio.ceos.CEOSProductReaderPlugIn;

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
    public ProductReader createReaderInstance() {
        return new JERSProductReader(this);
    }


}