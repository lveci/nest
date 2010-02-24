package org.esa.nest.util;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.DecodeQualification;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.util.PropertyMap;
import org.esa.nest.dataio.ReaderUtils;
import org.esa.nest.datamodel.AbstractMetadata;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * Utilities for Operator unit tests
 * In order to test the datasets at Array set the foloowing to true in the nest.config
 * nest.test.ReadersOnAllProducts=true nest.test.ProcessingOnAllProducts=true
 */
public class TestUtils {

    private static final PropertyMap testPreferences = createTestPreferences();

    public final static String rootPathExpectedProducts = testPreferences.getPropertyString("nest.test.rootPathExpectedProducts");
    public final static String rootPathTerraSarX = testPreferences.getPropertyString("nest.test.rootPathTerraSarX");
    public final static String rootPathASAR= testPreferences.getPropertyString("nest.test.rootPathASAR");
    public final static String rootPathRadarsat2 = testPreferences.getPropertyString("nest.test.rootPathRadarsat2");
    public final static String rootPathRadarsat1 = testPreferences.getPropertyString("nest.test.rootPathRadarsat1");
    public final static String rootPathERS = testPreferences.getPropertyString("nest.test.rootPathERS");
    public final static String rootPathJERS = testPreferences.getPropertyString("nest.test.rootPathJERS");
    public final static String rootPathALOS = testPreferences.getPropertyString("nest.test.rootPathALOS");
    public final static String rootPathCosmoSkymed = testPreferences.getPropertyString("nest.test.rootPathCosmoSkymed");
    public final static String rootPathMixProducts = testPreferences.getPropertyString("nest.test.rootPathMixProducts");

    private static String[] nonValidExtensions = { "xsd", "xls", "pdf", "txt", "doc", "ps", "db", "ief", "ord", "tgz",
                                                   "tif", "tiff", "tfw", "gif", "jpg", "jgw", "hdr", "self", "report",
                                                   "log", "html", "htm", "png", "bmp", "ps", "aux", "ovr" };
    private static String[] nonValidprefixes = { "led", "trl", "nul", "lea", "dat", "img", "dfas", "dfdn", "lut" };

    private static final int maxIteration = Integer.parseInt(testPreferences.getPropertyString("nest.test.maxProductsPerRootFolder"));

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

    public static Product createProduct(final String type, final int w, final int h) {
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

    public static void verifyProduct(final Product product, final boolean verifyTimes,
                                     final boolean verifyGeoCoding) throws Exception {
        ReaderUtils.verifyProduct(product, verifyTimes, verifyGeoCoding);
    }

    public static void attributeEquals(final MetadataElement elem, final String name,
                                       final double trueValue) throws Exception {
        double val = elem.getAttributeDouble(name, 0);
        if(Double.compare(val, trueValue) != 0) {
            if(Float.compare((float)val, (float)trueValue) != 0)
                throwErr(name + " is " + val + ", expecting " + trueValue);
        }
    }

    public static void attributeEquals(final MetadataElement elem, String name,
                                       final String trueValue) throws Exception {
        String val = elem.getAttributeString(name, "");
        if(!val.equals(trueValue))
            throwErr(name + " is " + val + ", expecting " + trueValue);
    }

    private static void compareMetadata(final Product testProduct, final Product expectedProduct,
                                        final String[] excemptionList) throws Exception {
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

    public static void compareProducts(final Operator op, final Product targetProduct,
                                       final String expectedPath, final String[] excemptionList) throws Exception {

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

    public static void executeOperator(final Operator op) throws Exception {
        // get targetProduct: execute initialize()
        final Product targetProduct = op.getTargetProduct();
        TestUtils.verifyProduct(targetProduct, false, !isAlos(targetProduct));

        final Band targetBand = targetProduct.getBandAt(0);
        if(targetBand == null)
            throwErr("targetBand at 0 is null");

        // readPixels: execute computeTiles()
        final float[] floatValues = new float[10000];
        targetBand.readPixels(100, 100, 100, 100, floatValues, ProgressMonitor.NULL);
    }

    private static boolean isAlos(Product prod) {
        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(prod);
        if(absRoot != null) {
            return absRoot.getAttributeString(AbstractMetadata.MISSION).contains("ALOS");
        }
        return false;
    }

    public static int recurseProcessFolder(final OperatorSpi spi, final File folder, int iterations,
                                            final String[] productTypeExemptions,
                                            final String[] exceptionExemptions) throws Exception {
        for(File file : folder.listFiles()) {
            if(maxIteration > 0 && iterations > maxIteration)
                break;

            if(file.isDirectory()) {
                if(!file.getName().contains("skipTest")) {
                    iterations = recurseProcessFolder(spi, file, iterations, productTypeExemptions, exceptionExemptions);
                }
            } else {
                try {
                    if(isNotProduct(file))
                        continue;
                    final ProductReader reader = ProductIO.getProductReaderForFile(file);
                    if(reader != null) {
                        final Product sourceProduct = reader.readProductNodes(file, null);
                        if(contains(sourceProduct.getProductType(), productTypeExemptions))
                            continue;

                        final Operator op = spi.createOperator();
                        op.setSourceProduct(sourceProduct);

                        System.out.println(spi.getOperatorAlias()+" Processing "+ file.toString());
                        TestUtils.executeOperator(op);

                        ++iterations;
                    } //else {
                      //  System.out.println(file.getName() + " is non valid");
                    //}
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

    private static boolean isNotProduct(final File file) {
        final String name = file.getName().toLowerCase();
        for(String ext : nonValidExtensions) {
            if(name.endsWith(ext))
                return true;
        }
        for(String pre : nonValidprefixes) {
            if(name.startsWith(pre))
                return true;
        }
        return false;
    }

    private static boolean contains(final String value, final String[] exemptions) {
        if(exemptions != null) {
            for(String type : exemptions) {
                if(value.contains(type))
                    return true;
            }
        }
        return false;
    }

    /**
     * Processes all products in a folder
     * @param spi the OperatorSpi to create the operator
     * @param folderPath the path to recurse through
     * @param productTypeExemptions product types to ignore
     * @param exceptionExemptions exceptions that are ok and can be ignored for the test
     * @throws Exception general exception
     */
    public static void testProcessAllInPath(final OperatorSpi spi, final String folderPath,
                                            final String[] productTypeExemptions,
                                            final String[] exceptionExemptions) throws Exception
    {
        final File folder = new File(folderPath);
        if(!folder.exists()) return;

        if(canTestProcessingOnAllProducts()) {
            int iterations = 0;
            recurseProcessFolder(spi, folder, iterations, productTypeExemptions, exceptionExemptions);
        }
    }

    public static void recurseReadFolder(final File folder,
                                         final ProductReaderPlugIn readerPlugin,
                                         final ProductReader reader,
                                         final String[] productTypeExemptions,
                                         final String[] exceptionExemptions) throws Exception {
        for(File file : folder.listFiles()) {
            if(file.isDirectory()) {
                if(!file.getName().contains("skipTest")) {
                    recurseReadFolder(file, readerPlugin, reader, productTypeExemptions, exceptionExemptions);
                }
            } else if(readerPlugin.getDecodeQualification(file) == DecodeQualification.INTENDED) {

                try {
                    System.out.println("Reading "+ file.toString());

                    final Product product = reader.readProductNodes(file, null);
                    if(contains(product.getProductType(), productTypeExemptions))
                            continue;
                    ReaderUtils.verifyProduct(product, true);
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
                        System.out.println("Failed to read "+ file.toString());
                        throw e;
                    }
                }
            }
        }
    }

    private static void throwErr(final String description) throws Exception {
        throw new Exception(description);
    }
}