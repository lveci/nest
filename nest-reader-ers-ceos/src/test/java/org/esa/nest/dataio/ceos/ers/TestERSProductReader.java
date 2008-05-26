package org.esa.nest.dataio.ceos.ers;

import junit.framework.TestCase;

import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Product;

import java.io.File;
import java.io.IOException;

/**
 * Test ERS CEOS Product Reader.
 *
 * @author lveci
 */
public class TestERSProductReader extends TestCase {

    ERSProductReaderPlugIn readerPlugin;
    ProductReader reader;

    String filePath = "P:\\nest\\nest\\ESA Data\\RADAR\\ERS_products\\ERS_VMP_CEOS\\E1_GEC_VMP CEOS_19980811_orbit 17297 frame 2493_UKpaf\\SCENE1\\VDF_DAT.001";

    public TestERSProductReader(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        super.setUp();

        readerPlugin = new ERSProductReaderPlugIn();
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
