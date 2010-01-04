package org.esa.nest.dataio.terrasarx;

import junit.framework.TestCase;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Product;
import org.esa.nest.dataio.ReaderUtils;
import org.esa.nest.util.TestUtils;

import java.io.File;

/**
 * Test Product Reader.
 *
 * @author lveci
 */
public class TestTerraSarXProductReader extends TestCase {

    private TerraSarXProductReaderPlugIn readerPlugin;
    private ProductReader reader;

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
        ReaderUtils.verifyProduct(product, true);
    }

    /**
     * Open all files in a folder recursively
     * @throws Exception anything
     */
    public void testOpenAll() throws Exception
    {
        final File folder = new File(TestUtils.rootPathTerraSarX);
        if(!folder.exists()) return;

        if(TestUtils.canTestReadersOnAllProducts())
            TestUtils.recurseReadFolder(folder, readerPlugin, reader);
    }
}