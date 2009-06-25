package org.esa.nest.dataio.ceos.alos;

import junit.framework.TestCase;
import org.esa.beam.framework.dataio.DecodeQualification;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.nest.dataio.ReaderUtils;

import java.io.File;

/**
 * Test ALOS PALSAR CEOS Product Reader.
 *
 * @author lveci
 */
public class TestAlosPalsarProductReader extends TestCase {

    private AlosPalsarProductReaderPlugIn readerPlugin;
    private ProductReader reader;

    private final static String rootPath = "P:\\nest\\nest\\ESA Data\\RADAR\\ALOS PALSAR";
    private final static String filePath = "P:\\nest\\nest\\ESA Data\\RADAR\\ALOS PALSAR\\acres\\solomon_islands_tsunami\\04651_03_PALSAR_WB1_west_PRE\\scene01\\VOL-ALPSRS042143750-W1.5GUD";

    public TestAlosPalsarProductReader(String name) {
        super(name);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        readerPlugin = new AlosPalsarProductReaderPlugIn();
        reader = readerPlugin.createReaderInstance();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        reader = null;
        readerPlugin = null;
    }

    public void testOpen() throws Exception
    {
        final File file = new File(filePath);
        if(!file.exists()) return;

        final Product product = reader.readProductNodes(file, null);
        ReaderUtils.verifyProduct(product, true);
    }

    public void testOpenAll() throws Exception
    {
        final File folder = new File(rootPath);
        if(!folder.exists()) return;

        final String testAllProducts = System.getProperty("nest.testReadersOnAllProducts");
        if(testAllProducts != null && testAllProducts.equalsIgnoreCase("true"))
            recurseFolder(folder);
    }

    private void recurseFolder(File folder) throws Exception {
        for(File file : folder.listFiles()) {
            if(file.isDirectory()) {
                recurseFolder(file);
            } else if(readerPlugin.getDecodeQualification(file) == DecodeQualification.INTENDED) {

                try {
                    final Product product = reader.readProductNodes(file, null);
                    verifyProduct(product);
                } catch(Exception e) {
                    System.out.println("Failed to read "+ file.toString());
                    throw e;
                }
            }
        }
    }

    public static void verifyProduct(Product product) throws Exception {
        if(product == null)
            throw new Exception("product is null");
        if(!product.getProductType().contains("1.1") && !product.getProductType().contains("1.0") && product.getGeoCoding() == null)
            throw new Exception("geocoding is null");
        if(product.getMetadataRoot() == null)
            throw new Exception("metadataroot is null");
        if(product.getNumBands() == 0)
            throw new Exception("numbands is zero");
        if(product.getProductType() == null || product.getProductType().isEmpty())
            throw new Exception("productType is null");
        if(product.getStartTime() == null)
            throw new Exception("startTime is null");

        for(Band b : product.getBands()) {
            if(b.getUnit() == null || b.getUnit().isEmpty())
                throw new Exception("band " + b.getName() + " has null unit");
        }
    }
}