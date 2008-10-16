package org.esa.nest.util;

import org.esa.beam.framework.datamodel.Product;

/**
 * Utilities for Operator unit tests
 */
public class TestOperator {


    public static void verifyProduct(Product product) throws Exception {
        if(product == null)
            throwErr("product is null");
        if(product.getGeoCoding() == null)
            throwErr("geocoding is null");
        if(product.getMetadataRoot() == null)
            throwErr("metadataroot is null");
        if(product.getNumBands() == 0)
            throwErr("numbands is zero");
        if(product.getProductType() == null || product.getProductType().isEmpty())
            throwErr("productType is null");
        if(product.getStartTime() == null)
            throwErr("startTime is null");

    }

    static void throwErr(String description) throws Exception {
        throw new Exception(description);
    }
}
