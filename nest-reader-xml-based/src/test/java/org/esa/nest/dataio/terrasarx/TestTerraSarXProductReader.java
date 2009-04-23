package org.esa.nest.dataio.terrasarx;

import junit.framework.TestCase;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.dataio.DecodeQualification;
import org.esa.beam.framework.datamodel.Product;
import org.esa.nest.dataio.ReaderUtils;

import java.io.File;

/**
 * Test Product Reader.
 *
 * @author lveci
 */
public class TestTerraSarXProductReader extends TestCase {

    private TerraSarXProductReaderPlugIn readerPlugin;
    private ProductReader reader;

    private final static String rootPath = "P:\\nest\\nest\\ESA Data\\RADAR\\TerraSarX";
    private final static String filePath = "P:\\nest\\nest\\ESA Data\\RADAR\\TerraSarX\\2007-12-15_Toronto_EEC-SE\\TSX1_SAR__EEC_SE___SL_S_SRA_20071215T112105_20071215T112107\\TSX1_SAR__EEC_SE___SL_S_SRA_20071215T112105_20071215T112107.xml";

    public TestTerraSarXProductReader(String name) {
        super(name);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        readerPlugin = new TerraSarXProductReaderPlugIn();
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
        ReaderUtils.verifyProduct(product);
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
                    ReaderUtils.verifyProduct(product);
                } catch(Exception e) {
                    System.out.println("Failed to read "+ file.toString());
                    throw e;
                }
            }
        }
    }
}