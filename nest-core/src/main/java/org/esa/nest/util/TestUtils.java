package org.esa.nest.util;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.DecodeQualification;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.util.PropertyMap;
import org.esa.nest.dataio.ReaderUtils;
import org.esa.nest.datamodel.AbstractMetadata;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

/**
 * Utilities for Operator unit tests
 * In order to test the datasets at Array set the foloowing to true in the nest.config
 * nest.test.ReadersOnAllProducts=true nest.test.ProcessingOnAllProducts=true
 */
public class TestUtils {

    private static final PropertyMap testPreferences = createTestPreferences();

    public final static String rootPathTerraSarX = testPreferences.getPropertyString("nest.test.rootPathTerraSarX");
    public final static String rootPathASAR= testPreferences.getPropertyString("nest.test.rootPathASAR");
    public final static String rootPathRadarsat2 = testPreferences.getPropertyString("nest.test.rootPathRadarsat2");
    public final static String rootPathRadarsat1 = testPreferences.getPropertyString("nest.test.rootPathRadarsat1");
    public final static String rootPathERS = testPreferences.getPropertyString("nest.test.rootPathERS");
    public final static String rootPathJERS = testPreferences.getPropertyString("nest.test.rootPathJERS");
    public final static String rootPathALOS = testPreferences.getPropertyString("nest.test.rootPathALOS");

    private static PropertyMap createTestPreferences() {
        final PropertyMap prefs = new PropertyMap();
        try {
            prefs.load(ResourceUtils.findConfigFile("nest.config"));
        } catch(IOException e) {
            System.out.println("Unable to load test preferences "+e.getMessage());
        }
        return prefs;
    }

    public static boolean canTestReadersOnAllProducts() {
        final String testAllProducts = testPreferences.getPropertyString("nest.test.ReadersOnAllProducts");
        return testAllProducts != null && testAllProducts.equalsIgnoreCase("true");
    }

    public static boolean canTestProcessingOnAllProducts() {
        final String testAllProducts = testPreferences.getPropertyString("nest.test.ProcessingOnAllProducts");
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

    public static void verifyProduct(Product product, boolean verifyTimes, boolean verifyGeoCoding) throws Exception {
        ReaderUtils.verifyProduct(product, verifyTimes, verifyGeoCoding);
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

    private static void compareMetadata(Product testProduct, Product expectedProduct, String[] excemptionList) throws Exception {
        final MetadataElement testAbsRoot = AbstractMetadata.getAbstractedMetadata(testProduct);
        if(testAbsRoot == null)
            throwErr("Metadata is null");
        final MetadataElement expectedAbsRoot = AbstractMetadata.getAbstractedMetadata(expectedProduct);
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

    public static void compareProducts(Operator op, Product targetProduct,
                                       String expectedPath, String[] excemptionList) throws Exception {

        final Band targetBand = targetProduct.getBandAt(0);
        if(targetBand == null)
            throwErr("targetBand at 0 is null");

        // readPixels: execute computeTiles()
        final float[] floatValues = new float[10000];
        targetBand.readPixels(100, 101, 100, 99, floatValues, ProgressMonitor.NULL);

        // compare with expected outputs:
        final File expectedFile = new File(expectedPath);
        if(!expectedFile.exists()) {
            throwErr("Expected file not found "+expectedFile.toString());
        }

        final ProductReader reader2 = ProductIO.getProductReaderForFile(expectedFile);

        final Product expectedProduct = reader2.readProductNodes(expectedFile, null);
        final Band expectedBand = expectedProduct.getBandAt(0);

        final float[] expectedValues = new float[10000];
        expectedBand.readPixels(100, 101, 100, 99, expectedValues, ProgressMonitor.NULL);
        if(!Arrays.equals(floatValues, expectedValues))
                throwErr("Pixels are different");

        // compare updated metadata
        compareMetadata(targetProduct, expectedProduct, excemptionList);
    }

    public static void executeOperator(Operator op) throws Exception {
        // get targetProduct: execute initialize()
        final Product targetProduct = op.getTargetProduct();
        TestUtils.verifyProduct(targetProduct, false, true);

        final Band targetBand = targetProduct.getBandAt(0);
        if(targetBand == null)
            throwErr("targetBand at 0 is null");

        // readPixels: execute computeTiles()
        final float[] floatValues = new float[10000];
        targetBand.readPixels(100, 100, 100, 100, floatValues, ProgressMonitor.NULL);
    }

    public static void recurseReadFolder(File folder, ProductReaderPlugIn readerPlugin, ProductReader reader) throws Exception {
        for(File file : folder.listFiles()) {
            if(file.isDirectory()) {
                recurseReadFolder(file, readerPlugin, reader);
            } else if(readerPlugin.getDecodeQualification(file) == DecodeQualification.INTENDED) {

                try {
                    final Product product = reader.readProductNodes(file, null);
                    ReaderUtils.verifyProduct(product, true);
                } catch(Exception e) {
                    System.out.println("Failed to read "+ file.toString());
                    throw e;
                }
            }
        }
    }

    private static void throwErr(String description) throws Exception {
        throw new Exception(description);
    }
}