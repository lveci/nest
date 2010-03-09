package org.esa.nest.util;

import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Product;

import java.io.File;

/**

 */
public abstract class RecursiveProcessor {


    public int recurseProcessFolder(final File folder, int iterations,
                                    final String[] productTypeExemptions,
                                    final String[] exceptionExemptions) throws Exception {
        final int maxIteration = TestUtils.getMaxIterations();
        for(File file : folder.listFiles()) {
            if(maxIteration > 0 && iterations >= maxIteration)
                break;

            if(file.isDirectory()) {
                if(!file.getName().contains("skipTest")) {
                    iterations = recurseProcessFolder(file, iterations, productTypeExemptions, exceptionExemptions);
                }
            } else {
                try {
                    if(TestUtils.isNotProduct(file))
                        continue;
                    final ProductReader reader = ProductIO.getProductReaderForFile(file);
                    if(reader != null) {
                        final Product sourceProduct = reader.readProductNodes(file, null);
                        if(TestUtils.contains(sourceProduct.getProductType(), productTypeExemptions))
                            continue;

                        process(sourceProduct);

                        ++iterations;
                    } else {
                        //System.out.println(file.getName() + " is non valid");
                    }
                } catch(Exception e) {
                    boolean ok = false;
                    if(exceptionExemptions != null) {
                        for(String excemption : exceptionExemptions) {
                            if(e.getMessage().contains(excemption)) {
                                ok = true;
                                System.out.println("Excemption for "+e.getMessage());
                                break;
                            }
                        }
                    }
                    if(!ok) {
                        System.out.println("Failed to process "+ file.toString());
                        throw e;
                    }
                }
            }
        }
        return iterations;
    }

    protected abstract void process(final Product sourceProduct) throws Exception;
}
