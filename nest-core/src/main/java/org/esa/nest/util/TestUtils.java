package org.esa.nest.util;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.framework.gpf.Operator;
import org.esa.nest.dataio.ReaderUtils;
import org.esa.nest.datamodel.AbstractMetadata;

import java.io.File;
import java.util.Arrays;

/**
 * Utilities for Operator unit tests
 */
public class TestUtils {

    public final static String rootPathTerraSarX = "P:\\nest\\nest\\ESA Data\\RADAR\\TerraSarX";
    public final static String rootPathASAR= "P:\\nest\\nest\\ESA Data\\RADAR\\ASAR";


    public static boolean canTestProcessingOnAllProducts() {
        final String testAllProducts = System.getProperty("nest.testProcessingOnAllProducts");
        return testAllProducts != null && testAllProducts.equalsIgnoreCase("true");
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


    public static void verifyProduct(Product product, boolean verifyTimes) throws Exception {
        ReaderUtils.verifyProduct(product, verifyTimes);
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

    public static void compareMetadata(Product testProduct, Product expectedProduct, String[] excemptionList) throws Exception {
        final MetadataElement testAbsRoot = testProduct.getMetadataRoot().getElement(AbstractMetadata.ABSTRACT_METADATA_ROOT);
        if(testAbsRoot == null)
            throwErr("Metadata is null");
        final MetadataElement expectedAbsRoot = expectedProduct.getMetadataRoot().getElement(AbstractMetadata.ABSTRACT_METADATA_ROOT);
        if(expectedAbsRoot == null)
            throwErr("Metadata is null");

        if(excemptionList != null) {
            Arrays.sort(excemptionList);
        }

        final MetadataAttribute[] attribList = expectedAbsRoot.getAttributes();
        for(MetadataAttribute expectedAttrib : attribList) {
            if(excemptionList != null && Arrays.binarySearch(excemptionList, expectedAttrib.getName()) >= 0)
                continue;

            final MetadataAttribute result = testAbsRoot.getAttribute(expectedAttrib.getName());
            if(result == null) {
                throwErr("Metadata attribute "+expectedAttrib.getName()+" is missing");
            }
            if(!result.getData().equalElems(expectedAttrib.getData())) {
                if(expectedAttrib.getData().toString().trim().equalsIgnoreCase(result.getData().toString().trim())) {

                } else {
                    throwErr("Metadata attribute "+expectedAttrib.getName()+" expecting "+expectedAttrib.getData().toString()
                        +" got "+ result.getData().toString());
                }
            }
        }
    }

    public static void compareProducts(Operator op, String expectedPath, String[] excemptionList) throws Exception {
        // get targetProduct: execute initialize()
        final Product targetProduct = op.getTargetProduct();
        TestUtils.verifyProduct(targetProduct, false);

        final Band targetBand = targetProduct.getBandAt(0);
        if(targetBand == null)
            throwErr("targetBand at 0 is null");

        // readPixels: execute computeTiles()
        final float[] floatValues = new float[10000];
        targetBand.readPixels(100, 100, 100, 100, floatValues, ProgressMonitor.NULL);

        // compare with expected outputs:
        final File expectedFile = new File(expectedPath);
        if(!expectedFile.exists()) {
            throwErr("Expected file not found "+expectedFile.toString());
        }

        final ProductReader reader2 = ProductIO.getProductReaderForFile(expectedFile);

        final Product expectedProduct = reader2.readProductNodes(expectedFile, null);
        final Band expectedBand = expectedProduct.getBandAt(0);

        final float[] expectedValues = new float[10000];
        expectedBand.readPixels(100, 100, 100, 100, expectedValues, ProgressMonitor.NULL);
        if(!Arrays.equals(floatValues, expectedValues))
                throwErr("Pixels are different");

        // compare updated metadata
        compareMetadata(targetProduct, expectedProduct, excemptionList);
    }

    public static void executeOperator(Operator op) throws Exception {
        // get targetProduct: execute initialize()
        final Product targetProduct = op.getTargetProduct();
        TestUtils.verifyProduct(targetProduct, false);

        final Band targetBand = targetProduct.getBandAt(0);
        if(targetBand == null)
            throwErr("targetBand at 0 is null");

        // readPixels: execute computeTiles()
        final float[] floatValues = new float[10000];
        targetBand.readPixels(100, 100, 100, 100, floatValues, ProgressMonitor.NULL);
    }

    static void throwErr(String description) throws Exception {
        throw new Exception(description);
    }
}