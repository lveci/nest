package org.esa.nest.dataio.ceos.ers;

import junit.framework.TestCase;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.nest.util.TestUtils;

import java.io.File;

/**
 * Test ERS CEOS Product Reader.
 *
 * @author lveci
 */
public class TestERSProductReader extends TestCase {

    private ERSProductReaderPlugIn readerPlugin;
    private ProductReader reader;

    public TestERSProductReader(String name) {
        super(name);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        readerPlugin = new ERSProductReaderPlugIn();
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
        final File folder = new File(TestUtils.rootPathERS);
        if(!folder.exists()) return;

        if(TestUtils.canTestReadersOnAllProducts())
            TestUtils.recurseReadFolder(folder, readerPlugin, reader, null, null);
    }
}
