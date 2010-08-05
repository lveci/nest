package org.esa.nest.gpf;

import junit.framework.TestCase;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.dataio.envisat.EnvisatProductReaderPlugIn;
import org.esa.nest.util.TestUtils;

import java.io.File;

/**
 * Test Product Reader.
 *
 * @author lveci
 */
public class TestEnvisatReader extends TestCase {

    private EnvisatProductReaderPlugIn readerPlugin;
    private ProductReader reader;

    private String[] productTypeExemptions = { "WVW", "WVI", "WVS", "WSS" };

    public TestEnvisatReader(String name) {
        super(name);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        readerPlugin = new EnvisatProductReaderPlugIn();
        reader = readerPlugin.createReaderInstance();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        reader = null;
        readerPlugin = null;
    }

    /**
     * Open all files in a folder recursively
     * @throws Exception anything
     */
    public void testOpenAll() throws Exception
    {
        final File folder = new File(TestUtils.rootPathASAR);
        if(!folder.exists()) return;

        //if(TestUtils.canTestReadersOnAllProducts())
        //    TestUtils.recurseReadFolder(folder, readerPlugin, reader, productTypeExemptions, null);
    }
}