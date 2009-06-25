package org.esa.nest.dataio.radarsat2;

import junit.framework.TestCase;
import org.esa.beam.framework.dataio.DecodeQualification;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Product;
import org.esa.nest.dataio.ReaderUtils;

import java.io.File;

/**
 * Test Product Reader.
 *
 * @author lveci
 */
public class TestRadarsat2ProductReader extends TestCase {

    private Radarsat2ProductReaderPlugIn readerPlugin;
    private ProductReader reader;

    private final static String rootPath = "P:\\nest\\nest\\ESA Data\\RADAR\\Radarsat2";
    private final static String filePath = "P:\\nest\\nest\\ESA Data\\RADAR\\Radarsat2\\Vancouver\\RS2_OK871_PK6636_DK3211_SCNA_20080422_143357_VV_VH_SGF\\product.xml";

    public TestRadarsat2ProductReader(String name) {
        super(name);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        readerPlugin = new Radarsat2ProductReaderPlugIn();
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
                    ReaderUtils.verifyProduct(product, true);
                } catch(Exception e) {
                    System.out.println("Failed to read "+ file.toString());
                    throw e;
                }
            }
        }
    }
}