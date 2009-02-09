package org.esa.nest.dataio.ceos.alos;

import junit.framework.TestCase;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Product;

import java.io.File;
import java.io.IOException;

/**
 * Test ALOS PALSAR CEOS Product Reader.
 *
 * @author lveci
 */
public class TestAlosPalsarProductReader extends TestCase {

    AlosPalsarProductReaderPlugIn readerPlugin;
    ProductReader reader;

    String filePath = "P:\\nest\\nest\\ESA Data\\RADAR\\ALOS PALSAR\\acres\\solomon_islands_tsunami\\04651_03_PALSAR_WB1_west_PRE\\scene01\\VOL-ALPSRS042143750-W1.5GUD";

    public TestAlosPalsarProductReader(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        super.setUp();

        readerPlugin = new AlosPalsarProductReaderPlugIn();
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