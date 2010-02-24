package org.esa.nest.dataio.ceos.alos;

import junit.framework.TestCase;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.nest.util.TestUtils;

import java.io.File;

/**
 * Test ALOS PALSAR CEOS Product Reader.
 *
 * @author lveci
 */
public class TestAlosPalsarProductReader extends TestCase {

    private AlosPalsarProductReaderPlugIn readerPlugin;
    private ProductReader reader;

    private String[] exceptionExemptions = { "geocoding is null" };

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

    /**
     * Open all files in a folder recursively
     * @throws Exception anything
     */
    public void testOpenAll() throws Exception
    {
        final File folder = new File(TestUtils.rootPathALOS);
        if(!folder.exists()) return;

        if(TestUtils.canTestReadersOnAllProducts())
            TestUtils.recurseReadFolder(folder, readerPlugin, reader, null, exceptionExemptions);
    }
}