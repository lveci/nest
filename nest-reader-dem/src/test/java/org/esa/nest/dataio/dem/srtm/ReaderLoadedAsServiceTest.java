
package org.esa.nest.dataio.dem.srtm;

import junit.framework.Assert;
import junit.framework.TestCase;
import org.esa.beam.framework.dataio.ProductIOPlugInManager;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;

import java.util.Iterator;

/**
 * Created by Marco Peters.
 *
 * @author Marco Peters
 * @version $Revision: 1.1 $ $Date: 2007-12-20 18:23:30 $
 */
public class ReaderLoadedAsServiceTest extends TestCase {

    public void testReaderIsLoaded() {
        int readerCount = 0;

        ProductIOPlugInManager plugInManager = ProductIOPlugInManager.getInstance();
        Iterator readerPlugIns = plugInManager.getReaderPlugIns("SRTM");

        while (readerPlugIns.hasNext()) {
            readerCount++;
            ProductReaderPlugIn plugIn = (ProductReaderPlugIn) readerPlugIns.next();
            System.out.println("readerPlugIn.Class = " + plugIn.getClass());
            System.out.println("readerPlugIn.Descr = " + plugIn.getDescription(null));
        }

        Assert.assertEquals(1, readerCount);

    }

}
