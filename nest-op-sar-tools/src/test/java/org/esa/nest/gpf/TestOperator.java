package org.esa.nest.gpf;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.nest.datamodel.AbstractMetadata;
import junit.framework.TestCase;

/**
 * Utilities for Operator unit tests
 */
public class TestOperator extends TestCase {

    public TestOperator(String name) {
        super(name);
    }

    public void setUp() throws Exception {
    }

    public void tearDown() throws Exception {
    }

    public void testOp() {
        
    }

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

        for(Band b : product.getBands()) {
            if(b.getUnit() == null || b.getUnit().isEmpty())
                throwErr("band " + b.getName() + " has null unit");    
        }
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

    static void throwErr(String description) throws Exception {
        throw new Exception(description);
    }
}
