package org.esa.nest.dataio.dem.ace;

import junit.framework.TestCase;
import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.dem.ElevationModelRegistry;

/**
 * Created by Marco Peters.
 *
 * @author Marco Peters
 * @version $Revision: 1.1 $ $Date: 2007-12-21 17:21:40 $
 */
public class TestDescriptorLoadedAsService extends TestCase {

    public void testDescriptorIsLoaded() {
        ElevationModelRegistry registry = ElevationModelRegistry.getInstance();
        ElevationModelDescriptor descriptor = registry.getDescriptor("ACE");

        assertNotNull(descriptor);

    }

}
