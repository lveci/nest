package org.esa.nest.dataio.dem;

import junit.framework.Assert;
import junit.framework.TestCase;
import org.esa.beam.framework.dataio.ProductIOPlugInManager;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;

import java.util.Iterator;

/**

 */
public class TestReaderLoadedAsService extends TestCase {

    public void testACEReaderIsLoaded() {
        testReaderIsLoaded("ACE");
    }

    public void testSRTM30ReaderIsLoaded() {
        //testReaderIsLoaded("SRTM30");
    }

    private static void testReaderIsLoaded(String name) {
        int readerCount = 0;
        final ProductIOPlugInManager plugInManager = ProductIOPlugInManager.getInstance();
        final Iterator readerPlugIns = plugInManager.getReaderPlugIns(name);

        while (readerPlugIns.hasNext()) {
            readerCount++;
            final ProductReaderPlugIn plugIn = (ProductReaderPlugIn) readerPlugIns.next();
            //System.out.println("readerPlugIn.Class = " + plugIn.getClass());
            //System.out.println("readerPlugIn.Descr = " + plugIn.getDescription(null));
        }
        Assert.assertEquals(1, readerCount);
    }

}
