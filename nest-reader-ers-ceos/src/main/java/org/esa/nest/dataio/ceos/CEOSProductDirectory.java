package org.esa.nest.dataio.ceos;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.VirtualBand;
import org.esa.beam.framework.datamodel.ProductData;

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
    
    public String getSampleType() {
        if(isProductSLC)
            return "COMPLEX";
        else
            return "DETECTED";
    }

    public String getProductType() {
        return productType;
    }

    protected static void createVirtualIntensityBand(Product product, Band bandI, Band bandQ, String countStr) {
        String expression = bandI.getName() + " * " + bandI.getName() + " + " +
                bandQ.getName() + " * " + bandQ.getName();

        VirtualBand virtBand = new VirtualBand("Intensity" + countStr,
                ProductData.TYPE_FLOAT32,
                product.getSceneRasterWidth(),
                product.getSceneRasterHeight(),
                expression);
        virtBand.setSynthetic(true);
        virtBand.setUnit("intensity");
        virtBand.setDescription("Intensity from complex data");
        product.addBand(virtBand);
    }

    protected static void createVirtualIntensityBand(Product product, Band band, String countStr) {
        String expression = band.getName() + " * " + band.getName();

        VirtualBand virtBand = new VirtualBand("Intensity" + countStr,
                ProductData.TYPE_FLOAT32,
                product.getSceneRasterWidth(),
                product.getSceneRasterHeight(),
                expression);
        virtBand.setSynthetic(true);
        virtBand.setUnit("intensity");
        virtBand.setDescription("Intensity from complex data");
        product.addBand(virtBand);
    }
}