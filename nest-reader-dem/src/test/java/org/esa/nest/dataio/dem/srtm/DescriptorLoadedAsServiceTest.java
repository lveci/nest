package org.esa.nest.dataio.dem.srtm;

import junit.framework.TestCase;
import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.dem.ElevationModelRegistry;

/**
 * Created by Marco Peters.
 *
 * @author Marco Peters
 * @version $Revision: 1.1 $ $Date: 2007-12-20 18:23:30 $
 */
public class DescriptorLoadedAsServiceTest extends TestCase {

    public void testDescriptorIsLoaded() {
        ElevationModelRegistry registry = ElevationModelRegistry.getInstance();
        ElevationModelDescriptor descriptor = registry.getDescriptor("SRTM");

        assertNotNull(descriptor);

    }

}
