package org.esa.nest.util;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.nest.dataio.ReaderUtils;
import org.esa.nest.datamodel.AbstractMetadata;

/**
 * Utilities for Operator unit tests
 */
public class TestUtils {

    public static Product createProduct(String type, int w, int h) {
        Product product = new Product("name", type, w, h);

        product.setStartTime(AbstractMetadata.parseUTC("10-MAY-2008 20:30:46.890683"));
        product.setEndTime(AbstractMetadata.parseUTC("10-MAY-2008 20:35:46.890683"));
        product.setDescription("description");

        addGeoCoding(product);

        AbstractMetadata.addAbstractedMetadataHeader(product.getMetadataRoot());

        return product;
    }

    private static void addGeoCoding(final Product product) {

        TiePointGrid latGrid = new TiePointGrid("lat", 2, 2, 0.5f, 0.5f,
                product.getSceneRasterWidth(), product.getSceneRasterHeight(),
                      new float[]{10.0f, 10.0f, 5.0f, 5.0f});
        TiePointGrid lonGrid = new TiePointGrid("lon", 2, 2, 0.5f, 0.5f,
                product.getSceneRasterWidth(), product.getSceneRasterHeight(),
                      new float[]{10.0f, 10.0f, 5.0f, 5.0f},
                      TiePointGrid.DISCONT_AT_360);
        TiePointGeoCoding tpGeoCoding = new TiePointGeoCoding(latGrid, lonGrid, Datum.WGS_84);

        product.addTiePointGrid(latGrid);
        product.addTiePointGrid(lonGrid);
        product.setGeoCoding(tpGeoCoding);
    }


    public static void verifyProduct(Product product) throws Exception {
        ReaderUtils.verifyProduct(product);
    }

    public static void attributeEquals(MetadataElement elem, String name, double trueValue) throws Exception {
        double val = elem.getAttributeDouble(name, 0);
        if(Double.compare(val, trueValue) != 0) {
            if(Float.compare((float)val, (float)trueValue) != 0)
                throwErr(name + " is " + val + ", expecting " + trueValue);
        }
    }

    public static void attributeEquals(MetadataElement elem, String name, String trueValue) throws Exception {
        String val = elem.getAttributeString(name, "");
        if(!val.equals(trueValue))
            throwErr(name + " is " + val + ", expecting " + trueValue);
    }

    public static void compareMetadata(Product testProduct, Product expectedProduct) throws Exception {
        final MetadataElement testAbsRoot = testProduct.getMetadataRoot().getElement(AbstractMetadata.ABSTRACT_METADATA_ROOT);
        if(testAbsRoot == null)
            throwErr("Metadata is null");
        final MetadataElement expectedAbsRoot = expectedProduct.getMetadataRoot().getElement(AbstractMetadata.ABSTRACT_METADATA_ROOT);
        if(expectedAbsRoot == null)
            throwErr("Metadata is null");

        final MetadataAttribute[] attribList = expectedAbsRoot.getAttributes();
        for(MetadataAttribute expectedAttrib : attribList) {
            final MetadataAttribute result = testAbsRoot.getAttribute(expectedAttrib.getName());
            if(result == null)
                throwErr("Metadata attribute "+expectedAttrib.getName()+" is missing");

            if(!result.getData().equalElems(expectedAttrib.getData()))
                throwErr("Metadata attribute "+expectedAttrib.getName()+" expecting "+expectedAttrib.getData().toString()
                        +" got "+ result.getData().toString());
        }
    }

    static void throwErr(String description) throws Exception {
        throw new Exception(description);
    }
}