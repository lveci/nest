package org.esa.nest.dataio.ceos.radarsat;

import org.esa.beam.framework.dataio.ProductReader;
import org.esa.nest.dataio.ceos.CEOSProductReaderPlugIn;

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
    public ProductReader createReaderInstance() {
        return new RadarsatProductReader(this);
    }

}