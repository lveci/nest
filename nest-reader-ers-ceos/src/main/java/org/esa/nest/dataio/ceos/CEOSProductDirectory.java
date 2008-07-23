package org.esa.nest.dataio.ceos;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;

import java.io.IOException;

/**

 */
public abstract class CEOSProductDirectory {

    protected boolean isProductSLC = false;
    protected String productType;

    protected abstract void readProductDirectory() throws IOException, IllegalCeosFormatException;

    public abstract Product createProduct() throws IOException, IllegalCeosFormatException;

    public abstract CEOSImageFile getImageFile(final Band band) throws IOException, IllegalCeosFormatException;

    public abstract void close() throws IOException;

    public boolean isSLC() {
        return isProductSLC;
    }

    public String getProductType() {
        return productType;
    }
}