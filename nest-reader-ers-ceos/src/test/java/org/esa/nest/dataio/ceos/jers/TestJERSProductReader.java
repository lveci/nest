package org.esa.nest.dataio.ceos.jers;


import junit.framework.TestCase;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Product;

import java.io.File;
import java.io.IOException;

/**
 * Test JERS CEOS Product Reader.
 *
 * @author lveci
 */
public class TestJERSProductReader extends TestCase {

    JERSProductReaderPlugIn readerPlugin;
    ProductReader reader;

    String filePath = "P:\\nest\\nest\\ESA Data\\RADAR\\JERS\\acres\\ceos\\SCENE1\\VDF_DAT.001";

    public TestJERSProductReader(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        super.setUp();

        readerPlugin = new JERSProductReaderPlugIn();
        reader = readerPlugin.createReaderInstance();
    }

    public void tearDown() throws Exception {
        super.tearDown();

        reader = null;
        readerPlugin = null;
    }

    public void testOpen() throws IOException
    {
        File file = new File(filePath);
        if(!file.exists()) return;

        Product product = reader.readProductNodes(file, null);
    }

}