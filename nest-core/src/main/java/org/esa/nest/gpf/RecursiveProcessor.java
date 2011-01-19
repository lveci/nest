/*
 * Copyright (C) 2010 Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.nest.gpf;

import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Product;
import org.esa.nest.util.TestUtils;

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
