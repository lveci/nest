package org.esa.beam.dataio.envisat;

import junit.framework.TestCase;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.dataio.IllegalFileFormatException;
import org.esa.beam.framework.datamodel.Product;

import java.io.File;
import java.io.IOException;

public class EnvisatProductReaderPluginTest extends TestCase {

    private static String inputIMMFilePath = "P:\\nest\\nest\\ESA Data\\RADAR\\ASAR\\Image Mode Medium Resolution\\ASA_IMM_1PNIPA20080529_110052_000000132069_00037_32656_3143.N1";
    private static String inputWVWFilePath = "P:\\nest\\nest\\ESA Data\\RADAR\\ASAR\\Wave Mode\\wvw\\ASA_WVW_2PNPDE20071119_064411_000005992063_00292_29905_3419.N1";
    private static String inputWSSFilePath = "P:\\nest\\nest\\ESA Data\\RADAR\\ASAR\\Wide Swath Single Look Complex\\ASA_WSS_1PNPDE20050614_163426_000000622038_00112_17200_0000.N1";

    public void testGetDefaultFileExtension() {
        final EnvisatProductReaderPlugIn plugIn = new EnvisatProductReaderPlugIn();

        final String[] defaultFileExtensions = plugIn.getDefaultFileExtensions();
        assertEquals(".N1", defaultFileExtensions[0]);
        assertEquals(".E1", defaultFileExtensions[1]);
        assertEquals(".E2", defaultFileExtensions[2]);
        assertEquals(".zip", defaultFileExtensions[3]);
        assertEquals(".gz", defaultFileExtensions[4]);
    }


    public void testRead() throws IOException, IllegalFileFormatException {
        File inputFile = new File(inputWVWFilePath);
        if(!inputFile.exists())
            return;

        final EnvisatProductReaderPlugIn plugIn = new EnvisatProductReaderPlugIn();
        ProductReader reader = plugIn.createReaderInstance();

        Product product = reader.readProductNodes(inputFile, null);
        assertNotNull(product);
    }
}
