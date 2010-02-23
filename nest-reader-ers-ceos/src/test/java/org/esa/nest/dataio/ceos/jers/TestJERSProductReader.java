package org.esa.nest.dataio.ceos.jers;


import junit.framework.TestCase;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Product;
import org.esa.nest.dataio.ReaderUtils;
import org.esa.nest.util.TestUtils;

import java.io.File;

/**
 * Test JERS CEOS Product Reader.
 *
 * @author lveci
 */
public class TestJERSProductReader extends TestCase {

    private JERSProductReaderPlugIn readerPlugin;
    private ProductReader reader;

    private final static String filePath = "P:\\nest\\nest\\ESA Data\\RADAR\\JERS\\acres\\ceos\\SCENE1\\VDF_DAT.001";

    public TestJERSProductReader(String name) {
        super(name);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        readerPlugin = new JERSProductReaderPlugIn();
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
        final File folder = new File(TestUtils.rootPathJERS);
        if(!folder.exists()) return;

        if(TestUtils.canTestReadersOnAllProducts())
            TestUtils.recurseReadFolder(folder, readerPlugin, reader, null, null);
    }

}