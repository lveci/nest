package org.esa.nest.dataio.dem.srtm;

import junit.framework.TestCase;
import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.dem.ElevationModelRegistry;

/**
 * Created by Marco Peters.
 *
 * @author Marco Peters
 * @version $Revision: 1.1 $ $Date: 2008-04-28 13:57:34 $
 */
public class TestDescriptorLoadedAsService extends TestCase {

    public void testDescriptorIsLoaded() {
        ElevationModelRegistry registry = ElevationModelRegistry.getInstance();
        ElevationModelDescriptor descriptor = registry.getDescriptor("SRTM30");

        assertNotNull(descriptor);

    }

}
