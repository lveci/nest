package org.esa.nest.dataio.dem;

import junit.framework.TestCase;
import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.dem.ElevationModelRegistry;

/**

 */
public class TestDescriptorLoadedAsService extends TestCase {

    public void testACEDescriptorIsLoaded() {
        testDescriptorIsLoaded("ACE");
    }

    public void testSRTM30DescriptorIsLoaded() {
        //testDescriptorIsLoaded("SRTM30");
    }

    private static void testDescriptorIsLoaded(String name) {
        final ElevationModelRegistry registry = ElevationModelRegistry.getInstance();
        final ElevationModelDescriptor descriptor = registry.getDescriptor(name);
        assertNotNull(descriptor);
    }

}
