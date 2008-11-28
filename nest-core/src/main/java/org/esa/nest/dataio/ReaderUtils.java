package org.esa.nest.dataio;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.VirtualBand;
import org.esa.beam.framework.datamodel.ProductData;

/**
 * Common functions for readers
 */
public class ReaderUtils {


    public static void createVirtualPhaseBand(Product product, Band bandI, Band bandQ, String countStr) {
        String expression = "atan2("+bandQ.getName()+ ',' +bandI.getName()+ ')';

        VirtualBand virtBand = new VirtualBand("Phase" + countStr,
                ProductData.TYPE_FLOAT64,
                product.getSceneRasterWidth(),
                product.getSceneRasterHeight(),
                expression);
        virtBand.setSynthetic(true);
        virtBand.setUnit("phase");
        virtBand.setDescription("Phase from complex data");
        product.addBand(virtBand);
    }

    public static void createVirtualIntensityBand(Product product, Band bandI, Band bandQ, String countStr) {
        String expression = bandI.getName() + " * " + bandI.getName() + " + " +
                bandQ.getName() + " * " + bandQ.getName();

        VirtualBand virtBand = new VirtualBand("Intensity" + countStr,
                ProductData.TYPE_FLOAT64,
                product.getSceneRasterWidth(),
                product.getSceneRasterHeight(),
                expression);
        virtBand.setSynthetic(true);
        virtBand.setUnit("intensity");
        virtBand.setDescription("Intensity from complex data");
        product.addBand(virtBand);

        // set as band to use for quicklook
        product.setQuicklookBandName(virtBand.getName());
    }

    public static void createVirtualIntensityBand(Product product, Band band, String countStr) {
        String expression = band.getName() + " * " + band.getName();

        VirtualBand virtBand = new VirtualBand("Intensity" + countStr,
                ProductData.TYPE_FLOAT64,
                product.getSceneRasterWidth(),
                product.getSceneRasterHeight(),
                expression);
        virtBand.setSynthetic(true);
        virtBand.setUnit("intensity");
        virtBand.setDescription("Intensity from complex data");
        product.addBand(virtBand);
    }
}
