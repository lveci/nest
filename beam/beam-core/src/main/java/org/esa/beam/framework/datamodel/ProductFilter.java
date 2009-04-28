package org.esa.beam.framework.datamodel;

/**
 * A filter for products.
 *
 * @author Marco Peters
 * @version $Revision: 1.1 $ $Date: 2009-04-28 14:39:33 $
 */
public interface ProductFilter {

    /**
     * @param product The product.
     * @return {@code true}, if the given {@code product} is accepted by this filter.
     */
    boolean accept(Product product);
}
